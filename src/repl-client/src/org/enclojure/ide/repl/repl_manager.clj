(comment
;*
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Eric Thorsen, Narayan Singhal
)

(ns org.enclojure.ide.repl.repl-manager
  (:use org.enclojure.repl.main)
  (:require [org.enclojure.commons.c-slf4j :as logger])
  (:import (java.util.logging Logger Level)
    (java.io PipedOutputStream PipedInputStream LineNumberReader InputStreamReader File)
    (org.apache.commons.exec CommandLine ExecuteResultHandler
      PumpStreamHandler DefaultExecutor ExecuteException ExecuteWatchdog)))


; setup logging
(logger/ensure-logger)

(defn bad-classpath?
  "Looks for clojure-contrib and clojure in a classpath string and makes sure 
the files exist. (Probably should look inside the jars????)"
  [classpath]
  (let [paths (.split classpath java.io.File/pathSeparator)
        contrib (some #(when (>= (.indexOf %
                                   (str File/separator "clojure-contrib")) 0) %) paths)
        clojure (some #(when (>= (.indexOf %
                                   (str File/separator "clojure")) 0) %) 
                  (filter #(not= contrib %) paths))
        clojure-exists? (and clojure (.exists (File. clojure)))
        contrib-exists? (and contrib (.exists (File. contrib)))]
    (when-not (and contrib clojure clojure-exists? contrib-exists?)
      {:clojure [clojure clojure-exists?]
       :contrib [contrib contrib-exists?]})))

(def default-repl-config {
                          :arguments ["-server" "-Xmx512m" "-Xms128m"]
                          :debug-port-arg "-Xrunjdwp:transport=dt_socket,server=y,suspend=n"
                          :classpath "/Users/nsinghal/Work/enclojure-google/third-party/Clojure/clojure.jar:/Users/nsinghal/Work/org.enclojure.repl.jar:/Users/nsinghal/Work/enclojure-google/enclojure-clojure-lib/org.enclojure.commons/dist/org.enclojure.commons.jar"
                          :java-main "org.enclojure.repl.launcher"
                          :repl-id "Repl identifier"
                          :port 0
                          :ack-port nil
                          })

(def ack-server-socket (atom nil))
(def running-repls (ref {}))

(defn register-repl [repl-id repl-config]
  (dosync
    (commute running-repls assoc repl-id
      (ref (merge default-repl-config repl-config)))))

(defn unregister-repl [repl-id]
  (dosync
    (commute running-repls dissoc repl-id)))

(defn get-repl-config [repl-id]
  (when (contains? @running-repls repl-id)
    @(@running-repls repl-id)))

(defn all-repl-configs []
  (map (fn [repl-ref] @repl-ref) (vals @running-repls)))

(defn update-repl [repl-id & kv-pairs]
  (dosync
    (let [config-ref (@running-repls repl-id)]
      (if config-ref
        (commute config-ref #(reduce conj % (apply hash-map kv-pairs)))
        (throw (Exception.
                 (str "Unable to locate repl settings for update using key " config-ref)))))))

(defn add-or-update-repl [repl-id & kv-pairs]
    (if-let [config-ref (@running-repls repl-id)]
      (apply update-repl repl-id kv-pairs)
      (register-repl repl-id (apply hash-map kv-pairs))))
     
(defn get-classpath [repl-id]
  (when-let [classpath (:classpath (get-repl-config repl-id))]
    (apply str (interpose java.io.File/pathSeparator (distinct (.split classpath java.io.File/pathSeparator))))))

(defn get-pretty-info [repl-id]
      (.replace (str (get-classpath repl-id))
        java.io.File/pathSeparator (str \newline)))

(defn ack-received [repl-id port]
  (update-repl repl-id :port port))

(defn get-ack-port []
  (when-not @ack-server-socket
    (dosync
      (when-not @ack-server-socket
        (swap! ack-server-socket (fn [_] (run-repl-server 0)))
        (set-repl-ack-fn ack-received))))
  (.getLocalPort @ack-server-socket))

(defn await-till
  "Wait for timeout-ms to see if the test passes."
  [pred timeout-ms]
  (let [test-thread (Thread. #(try
                                (when-not (Thread/interrupted)
                                  (when-not (pred)
                                    (Thread/yield)
                                    (recur)))
                                (catch Throwable t
                                  (logger/info  (.getMessage t)))))]
    (.start test-thread)
    (.join test-thread timeout-ms)
    (when (.isAlive test-thread)
      (.interrupt test-thread))
    (pred)))
    
(defn- java-cmd-array
  "takes a map which looks like @*default-config* and creates a java array to be used in the ProcessBuilder later on.
For seeing the command line use:"
  ;(apply str (interpose \" \" (org.enclojure.repl.e-repl-startup/java-cmd-array org.enclojure.repl.e-repl-startup/@*default-config*)))"
  [{:keys [arguments debug-port-arg classpath java-main repl-id port ack-port]}]
  (logger/info  "Arguments are .....{}" arguments)
  (apply conj arguments
    (map str
      (filter identity [debug-port-arg "-cp" (if classpath (str "\"" classpath "\"") "")
                        java-main (str "\"" repl-id "\"") port ack-port]))))

(defn launch-java-process [repl-config complete-fn failed-fn
                           process-monitor-fn]
  (let [java-args (java-cmd-array repl-config)
        cmd-line (CommandLine/parse "java")
        _ (logger/info  "start java process with {}"
            (apply vector java-args))
        _ (doall (map #(.addArguments cmd-line (str %) false) java-args))
        #^DefaultExecutor executor (DefaultExecutor.)
        [out-pipe err-pipe] [(PipedOutputStream.) (PipedOutputStream.)]
        out-pipe-reader (LineNumberReader. (InputStreamReader. (PipedInputStream. out-pipe)))
        err-pipe-reader (LineNumberReader. (InputStreamReader. (PipedInputStream. err-pipe)))]
    (.setStreamHandler executor (PumpStreamHandler. out-pipe err-pipe))
    (process-monitor-fn
      #(.readLine out-pipe-reader)
      #(.readLine err-pipe-reader))
    (.setWatchdog executor (ExecuteWatchdog. Integer/MAX_VALUE))
    (.execute executor cmd-line (proxy [ExecuteResultHandler] []
                                  (onProcessComplete [exit-value]
                                    (complete-fn exit-value))
                                  (onProcessFailed [ex]
                                    (.flush out-pipe)
                                    (.flush err-pipe)
                                    (failed-fn ex))))
    {:destroy-fn (fn []
                   (when-let [watchdog (.getWatchdog executor)]
                     (.destroyProcess watchdog)))})) ;//??do we need cleaning of streams "out-pipe err-pipe"?

(defn process-completed [repl-id exit-value]
  (logger/info  "Process terminated: repl-id={}" repl-id))

(defn process-failed2 [repl-id #^ExecuteException ex]
  (logger/info  "Process failed: repl-id={} {} {}" repl-id (.getMessage ex)
    (if-let [c (.getCause ex)]
      (.getMessage c))))

(defn create-internal-repl [repl-id
                            latest-classpath
                            process-monitor-fn
                            repl-monitor-fn]
  (update-repl repl-id :classpath latest-classpath :port 0 :ack-port (get-ack-port))
  (let [repl-config (get-repl-config repl-id)
        {:keys [destroy-fn]} (launch-java-process repl-config
                               (partial process-completed repl-id)
                               (partial process-failed2 repl-id)
                               process-monitor-fn)]
    (update-repl repl-id :destroy-fn destroy-fn)
    (if (await-till #(pos? (:port (get-repl-config repl-id))) 10000)
      (let [port (:port (get-repl-config repl-id))
            {:keys [repl-fn result-fn close-fn]}
            (create-repl-client-with-back-channel "127.0.0.1" port)]
        (repl-monitor-fn repl-fn result-fn)
        (update-repl repl-id :repl-fn repl-fn :close-fn close-fn :connected true))
      (throw (Exception. 
               (str "Timeout expired - could not create repl " repl-id
                 " Check *err* pane for additional info "
                 "\n\tconfig settings "
                 (apply str (interpose ",\n" (get-repl-config repl-id)))))))))

(defn repl-connected? [repl-id]
  (and (contains? @running-repls repl-id) (:connected (get-repl-config repl-id))))

(defn stop-internal-repl [repl-id]
  (let [{:keys [close-fn repl-fn destroy-fn]} (get-repl-config repl-id)]
    (try
      (when close-fn
        (close-fn))
      (catch Throwable t))
    (try
      (when repl-fn
        (repl-fn "(org.enclojure.repl.main/close-server)"))
      (catch Throwable t))
    (try
      (when destroy-fn
        (destroy-fn))
      (catch Throwable t))
    (update-repl repl-id :repl-fn nil :connected false :port 0)))

(defn stop-repl-servers []
    (loop [repl-ids (keys @running-repls)]
      (when-let [repl-id (first repl-ids)]
        (stop-internal-repl repl-id)
        (recur (next repl-ids)))))

(defn get-settings-set-expression
  "builds an expression of (set! var val) using all the symbols
in the repl-config map"
  [repl-id]
  `(do ~@(reduce (fn [l [k v]] (conj l `(set! ~k ~v))) []
           (filter (comp symbol? first)
             (get-repl-config repl-id)))
     nil))
