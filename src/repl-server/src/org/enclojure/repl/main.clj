(comment
 ;*
 ;* Copyright (c) ThorTech, L.L.C.. All rights reserved.
 ;* The use and distribution terms for this software are covered by the
 ;* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 ;* which can be found in the file epl-v10.html at the root of this distribution.
 ;* By using this software in any fashion, you are agreeing to be bound by
 ;* the terms of this license.
 ;* You must not remove this notice, or any other, from this software.
 ;*
 ;* Author: Eric Thorsen, Narayan Singhal
 )

(ns #^{:author "Eric Thorsen, Narayan Singhal"
       :doc "This is a socket layer over the REPL in clojure.main.  This can 
       be run from a java startup using the launcher class or by calling -main
       using clojure script or within a clojure application.  There is support
       for providing a socket based ack to a process that started the repl-server.
       Startup:
       (-main \"A string name for the REPL\" server-port ack-port)
       The server port can be 0 which will cause the server to use the next available
       socket port.
       Acknowledgment port
       ------------------------------------------------------------------------
       In cases where you want to be able to be nofified when the repl-server 
       has successfully been started up and/or you want to use the next available
       port and need to be notified of what that port was, the Enclojure repl-server
       uses and ack-port to create a repl-client to talk back to the starting process.
       For an example of this see:
       org.enclojure.ide.repl.repl-manager/get-ack-port
       If the ack-port is not provided no acknowledgment will be attempted.
       If the ack-port is provided a repl-client connection will be made and 
       will send a function call to:
       (repl-ack repl-id server-port)
       There is a function:
       (set-repl-ack-fn ack-fn) 
       where you can set the function to be called in your client application.
       "}
  org.enclojure.repl.main
  (:refer-clojure :exclude (with-bindings))
  (:use clojure.contrib.pprint)
  (:require [clojure.contrib.pprint :as pprint]
    [clojure.main :exclude (with-binding)])
  (:import (java.net Socket ServerSocket)
    (java.util.logging Level Logger)
    (java.io InputStreamReader DataOutputStream DataInputStream
      PipedReader PipedWriter CharArrayWriter PrintWriter)
    (java.util.concurrent CountDownLatch)))

(def *print-stack-trace-on-error* false)

(defn load-string-with-dbg
  "Load a string using the source-path and file name for debug info."
  [str-data source-path file]
  (clojure.lang.Compiler/load
    #^java.io.Reader (java.io.StringReader. str-data)
        #^String source-path #^String file))

(defn get-root-cause [throwable]
  (loop [cause throwable]
    (if-let [cause (.getCause cause)]
      (recur cause)
      cause)))

(defn is-eof-ex? [throwable]
  (and (instance? clojure.lang.LispReader$ReaderException throwable)
    (or
      (.startsWith (.getMessage throwable) "java.lang.Exception: EOF while reading")
      (.startsWith (.getMessage throwable) "java.io.IOException: Write end dead"))))

(defn prn-exception [e]
  "Prints an exception looking for the cause to w"
;  (.throwing *logger* (str (clojure.core/ns-name clojure.core/*ns*)) "" e)  
  (binding [*err* (if (instance? PrintWriter *err*)
                    *err*
                    (PrintWriter. *err*))]
    (loop [c e]
      (let [cause (and c (.. c getCause))]
        (if cause (recur cause)
          (do
            (. e (printStackTrace *err*))))))
    (.flush *err*)))

(defn start-io-thread
  "io-fn should have the blocking action such as a blocking read. If it is not
   blocking than this thread will be a hard loop."
  ([io-fn ex-fn]
    (let [io-thread-fn (fn []
                         (try
                           (io-fn)
                           (recur)
                           (catch Exception ex
                             (ex-fn (get-root-cause ex)))))
          io-thread (Thread. io-thread-fn)]
      (.start io-thread)
      #(.interrupt io-thread)))
  ([io-fn]
    (start-io-thread io-fn (fn [cause]
                             (when-not (or (instance? java.io.IOException cause)
                                         (instance? java.lang.InterruptedException cause)
                                         (instance? java.nio.channels.ClosedByInterruptException cause))
                               (prn-exception cause)
                               (pr-str "start-io-thread: exception occured: " cause))))))

(defn create-clojure-repl []
  "This function creates an instance of clojure repl using piped in and out.
   It returns a map of two functions repl-fn and result-fn - first function
   can be called with a valid clojure expression and the results are read using
   the result-fn."
  (let [cmd-wtr (PipedWriter.)
        result-rdr (PipedReader.)
        piped-in (clojure.lang.LineNumberingPushbackReader. (PipedReader. cmd-wtr))
        piped-out (PrintWriter. (PipedWriter. result-rdr))
        repl-thread-fn #(binding [;*print-base* *print-base* Not in 1.0
                                  *print-circle* *print-circle*
                                  *print-length* *print-length*
                                  *print-level* *print-level*
                                  *print-lines* *print-lines*  
                                  *print-miser-width* *print-miser-width*
                                  ;*print-radix* *print-radix* Not in 1.0
                                  *print-right-margin* *print-right-margin*
                                  ;*print-shared* *print-shared*  Not in 1.0
                                  *print-suppress-namespaces* *print-suppress-namespaces*
                                  *print-pretty* *print-pretty*
                                  *warn-on-reflection* *warn-on-reflection*
                                  *print-stack-trace-on-error* *print-stack-trace-on-error*
                                  *in* piped-in
                                  *out* piped-out
                                  *err* (PrintWriter. *out*)]
                          (try
                            (clojure.main/repl
                              :init (fn [] (in-ns 'user))
                              :read (fn [prompt exit]                                      
                                      (read))
                              :caught (fn [e]
                                        (when (is-eof-ex? e)
                                          (throw e))
                                        (if *print-stack-trace-on-error*
                                          (.printStackTrace e *out*)
                                          (prn (clojure.main/repl-exception e)))
                                        (flush))
                              :need-prompt (constantly true)
                              :print (fn [value]                                        
                                        (set! *3 *2)
                                        (set! *2 *1)
                                        (set! *1 value)
                                       (if *print-pretty*
                                         (pprint/pprint value)
                                        (prn value))))
                            (catch clojure.lang.LispReader$ReaderException ex
                              (prn "REPL closing"))
                            (catch java.lang.InterruptedException ex)
                            (catch java.nio.channels.ClosedByInterruptException ex)))]
    (.start (Thread. repl-thread-fn))
    {:repl-fn (fn [cmd]
                (if (= cmd ":CLOSE-REPL")
                  (do
                    (.close cmd-wtr)
                    (.close result-rdr))
                  (do
                    (.write cmd-wtr cmd)
                    (.flush cmd-wtr))))
     ;//??Using CharArrayWriter to build the string from each read of one byte
     ;Once there is nothing to read than this function returns the string read.
     ;Using partial so that CharArrayWriter is only created once and reused.
     ;There could be better way???
     :result-fn (partial
                  (fn [wtr]
                    (.write wtr (.read result-rdr))
                    (if (.ready result-rdr)
                      (recur wtr)
                      (let [result (.toString wtr)]
                        (.reset wtr)
                        result)))
                  (CharArrayWriter.))}))

(defn- read-socket [in]
  (let [stream (DataInputStream. in)
        chunks-len (.readInt stream)]
    (apply str (map (fn [_] (.readUTF stream)) (range chunks-len)))))

(defn split-string [string len]
  (loop [string string ret []]
    (if (>= (.length string) len)
      (recur (.substring string len) (conj ret (.substring string 0 len)))
      (conj ret string))))

;Java bug about - 64k bytes in the UTF-8 encoding - supported
;http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4071592
(defn- write-socket [out exp]
"Divide the string into 32K before writing to the UDF function."
  (let [stream (DataOutputStream. out)
        chunks (split-string exp 32768)
        chunks-len (count chunks)]
    (.writeInt stream chunks-len)
    (dorun (map #(.writeUTF stream %) chunks))))

(defn spawn-socket-repl [socket]
  (let [socket-in (.getInputStream socket)
        socket-out (.getOutputStream socket)
        {:keys [repl-fn result-fn]} (create-clojure-repl)]
    (start-io-thread #(write-socket socket-out (result-fn)))
    (start-io-thread #(repl-fn (read-socket socket-in)))))

(defn run-repl-server [port]
  (let [socket (new ServerSocket port)]
    (start-io-thread #(spawn-socket-repl (. socket (accept))))
    socket))

(def repl-ack-fn (atom nil))

(defn set-repl-ack-fn [ack-fn]
  (swap! repl-ack-fn (fn [_] ack-fn)))

(defn repl-ack [repl-id port]
  (@repl-ack-fn repl-id port))

(defn create-repl-client [host port]
  ;(javax.swing.JOptionPane/showMessageDialog nil (str host ";" port))
  (let [socket (java.net.Socket. host port)
        socket-in (.getInputStream socket)
        socket-out (.getOutputStream socket)]
    {:repl-fn (fn [cmd] (write-socket socket-out cmd))
     :result-fn (fn [] (read-socket socket-in))
     :close-fn (fn []
                 (do
                   (write-socket socket-out  ":CLOSE-REPL")
                   (.close socket)))}))

(defn create-repl-client-with-back-channel [host port]  
  (let [primary-client (create-repl-client host port)
        back-channel-client (create-repl-client host port)]
    (assoc primary-client
      :back-channel-repl-fn (:repl-fn back-channel-client)
      :back-channel-result-fn (:result-fn back-channel-client))))

  ;;Clean??
(defn notify-client-server-started [ack-port repl-id server-port]
  (prn (str "Connecting and acking at address: " ack-port))
  (let [{:keys [repl-fn close-fn]} (create-repl-client "127.0.0.1" ack-port)]
    (repl-fn (str "(org.enclojure.repl.main/repl-ack \"" repl-id "\"" server-port ")"))
    (close-fn)))

(def close-server-fn (atom nil))

(defn close-server []
  (@close-server-fn))

(defn -main [& args]
  (let [[repl-id port ack-port] args
        ;_
        ;(prn (str "repl " repl-id " using port " port " ack-port " ack-port))
        server-socket (run-repl-server (if port (Integer/parseInt port) 0))
        server-port (.getLocalPort server-socket)
        latch (CountDownLatch. 1)
        close-fn (fn []
                   (try
                    (.close server-socket))
                   (.countDown latch))]
    (swap! close-server-fn (fn [_] close-fn))
    (prn (str "Listening for repl socket at address: " server-port))
    (when ack-port
      (notify-client-server-started (Integer/parseInt ack-port) repl-id server-port))
    (.await latch)))
