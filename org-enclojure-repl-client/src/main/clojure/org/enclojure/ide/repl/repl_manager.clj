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
  (:refer-clojure :exclude (with-bindings))
  (:use org.enclojure.repl.main)
  (:require [org.enclojure.commons.c-slf4j :as logger]
        [org.enclojure.commons.validation :as validation]
        [org.enclojure.ide.repl.repl-data :as repl-data])
  (:import (java.util.logging Logger Level)
    (java.io PipedOutputStream PipedInputStream LineNumberReader InputStreamReader File)
    (org.apache.commons.exec CommandLine ExecuteResultHandler
      PumpStreamHandler DefaultExecutor ExecuteException ExecuteWatchdog)))


; setup logging
(logger/ensure-logger)

(defn- find-versioned-jar-in-paths [jar-name paths]
  "Finds a jar in a sequence of path strings, where the jar has the name <jar-name>(.*).jar"
  (let [p (re-pattern (str ".*\\" File/separator jar-name "-.*\\.jar$"))]
    (some #(when (re-matches p %) %) paths)))

(defn bad-classpath?
  "Given a classpath string, attempt to locate a clojure.jar and a
clojure-contrib.jar.  The function just looks for jars with these names in the file
 and makes sure the files exists.  Returns nil if both are found.  Returns a map
with the name of the file reference found in the string and a boolean as to whether
or not the file exists.  This is useful for error reporting.
{:clojure [\"/Users/fred/clojure.jar\" true]
 :clojure-contrib [nil nil]}
In the above case, there was a clojure.jar reference found and the file exists.
For clojure-contrib, no reference was found."
  [classpath]
  (let [paths (.split classpath java.io.File/pathSeparator)
        contrib (find-versioned-jar-in-paths "clojure-contrib" paths)
        clojure (find-versioned-jar-in-paths "clojure" (filter #(not= contrib %) paths))
        clojure-exists? (and clojure (.exists (File. clojure)))
        contrib-exists? (and contrib (.exists (File. contrib)))]
    (when-not (and contrib clojure clojure-exists? contrib-exists?)
      {:clojure [clojure clojure-exists?]
       :contrib [contrib contrib-exists?]})))

(def ack-server-socket (atom nil))
(def #^{:doc "The set of running repls" :private true}
  -running-repls- (ref {}))

(defn new-repl-data-ref
  "Use this function to ask for a new reference for a repl instance.
Sets the validator function to ensure this is usable within the rest of the framework"
  [repl-config]
  (ref repl-config
    :validator #(validation/validate %1
                  repl-data/-repl-context-validation-)))

(defn register-repl 
  "Register a new repl using the repl-id as the key.  repl-config is a map. See
org.enclojure.ide.repl.repl-data for more info"
  [repl-id repl-config]
  (assert (map? repl-config))
  (dosync
    (commute -running-repls- assoc repl-id
      (new-repl-data-ref 
        (merge repl-data/-default-repl-data- repl-config)))))
      ;(ref (merge default-repl-config repl-config)))))

(defn unregister-repl [repl-id]
    (dosync
    (commute -running-repls- dissoc repl-id)))

(defn get-repl-config [repl-id]
  (when (contains? @-running-repls- repl-id)
    @(@-running-repls- repl-id)))

(defn all-repl-configs []
  (map (fn [repl-ref] @repl-ref) (vals @-running-repls-)))

(defn update-repl [repl-id & kv-pairs]
  (dosync
    (let [config-ref (@-running-repls- repl-id)]
      (if config-ref
        (commute config-ref #(reduce conj % (apply hash-map kv-pairs)))
        (throw (Exception.
                 (str "Unable to locate repl settings for update using key " repl-id)))))))

(defn add-or-update-repl [repl-id & kv-pairs]
    (if-let [config-ref (@-running-repls- repl-id)]
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
    
(defn java-cmd-array
  "takes a map which looks like @*default-config* and creates a java array to be used in the ProcessBuilder later on.
For seeing the command line use:"
  ;(apply str (interpose \" \" (org.enclojure.repl.e-repl-startup/java-cmd-array org.enclojure.repl.e-repl-startup/@*default-config*)))"
  [{:keys [java-exe jvm-additional-args debug-port-arg classpath java-main repl-id port ack-port]}]
  (logger/info  "Arguments are .....{}" jvm-additional-args)
  (apply conj jvm-additional-args "-DCOMMONS_EXEC_DEBUG=true"
    (map str
      (filter identity [debug-port-arg "-cp" (if classpath (str "\"" classpath "\"") "")
                        java-main (str "\"" repl-id "\"") port ack-port]))))

(defn launch-java-process 
  "This function launches a java process using the Apache exec lib for starting a 
    repl-server.  There are four arguments:
  repl-config -> A map with a set of startup options for the java process.  
                 These should include:
                 :java-exe -> java executable (defaults to java)
                 :jvm-additional-args -> startup arguments for the java 
                 :debug-port-arg -> port to use for the debugger
                 :classpath -> classpath for -cp arg
                 :java-main -> class for startup
                 :repl-id -> a string identifier for the repl
                 :port  -> socket port to listen for repl command 
                            (0 will use next available port)
                 :ack-port -> Acknowledgement port for the starting
                            process to listen on to ensure the remote repl server 
                            has successfully started.
  complete-fn -> Single arg function taking an int that gets called when the external
                repl server successfully shutdown.
  failed-fn -> Single arg function taking an int that gets called when the external
                java process fails.
  process-monitor-fn -> A function that takes 2 arguments. Each argument is a function
                that returns a string the result of which is the output from the
                *out* stream and *err* stream respectively.  For an example see:
                org.enclojure.ide.repl.repl-panel/bind-editor-pane and
                org.enclojure.ide.repl.repl-panel/bind-process-panel
  "
  [repl-config complete-fn failed-fn process-monitor-fn working-dir]
  (let [java-args (java-cmd-array repl-config)
        cmd-line (CommandLine/parse (or (:java-exe repl-config) "java"))
        _ (logger/info  "start java process with {}"
            (print-str
                (apply vector (or (:java-exe repl-config) "java") java-args)))
        _ (doall (map #(.addArguments cmd-line (str %) false) java-args))
        #^DefaultExecutor executor (DefaultExecutor.)
        [out-pipe err-pipe] [(PipedOutputStream.) (PipedOutputStream.)]
        out-pipe-reader (LineNumberReader.
                          (InputStreamReader. (PipedInputStream. out-pipe)))
        err-pipe-reader (LineNumberReader.
                          (InputStreamReader. (PipedInputStream. err-pipe)))
         stream-handler (PumpStreamHandler. out-pipe err-pipe)]
    (.setWorkingDirectory executor (if (instance? java.io.File working-dir)
                                     working-dir (java.io.File. working-dir)))
    (.setStreamHandler executor stream-handler)
    (.start stream-handler)
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
                            repl-monitor-fn
                            working-dir]
  (update-repl repl-id :classpath latest-classpath :port 0 :ack-port (get-ack-port)
    :working-dir working-dir)
  (let [repl-config (get-repl-config repl-id)
        {:keys [destroy-fn]} (launch-java-process repl-config
                               (partial process-completed repl-id)
                               (partial process-failed2 repl-id)
                               process-monitor-fn
                               working-dir)]
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
  (and (contains? @-running-repls- repl-id) (:connected (get-repl-config repl-id))))

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
    (loop [repl-ids (keys @-running-repls-)]
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

(defn get-IRepl
  [repl-id]
  (:irepl (get-repl-config repl-id)))
