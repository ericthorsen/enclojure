(ns build.zip-projects
 (:require
   [clojure.contrib.duck-streams :as duck-streams]
   [clojure.contrib.java-utils :as java-utils]
   [org.enclojure.commons.c-slf4j :as logger]
   [org.enclojure.repl.main :as repl.main]
   )
 (:import [java.util.zip ZipOutputStream ZipEntry]
         [java.io File FileOutputStream FileInputStream]
    [java.util.logging Logger Level]
    [java.io PipedOutputStream PipedInputStream LineNumberReader InputStreamReader File]
    [org.apache.commons.exec CommandLine ExecuteResultHandler
      PumpStreamHandler DefaultExecutor ExecuteException ExecuteWatchdog]
 ))

; setup logging
(logger/ensure-logger)

(def *src-base-dir* (str (System/getProperty "user.dir") "/../../../templates/netbeans"))
(def *dest-base-dir* (str (System/getProperty "user.dir") "/src/main/resources/org/enclojure/ide"))

(def *src-project-templates-dir* (str *src-base-dir* "/ProjectTemplates"))
(def *src-project-samples-dir* (str *src-base-dir* "/SampleProjects"))
(def *filter-files* [".jar" "/private" ".class" "/classes"])

(def *dest-project-templates-dir* (str *dest-base-dir* "/templates/project"))
(def *dest-project-samples-dir* (str *dest-base-dir* "/project/samples"))

(defn get-directories [path]
  (filter #(.isDirectory %) 
    (vec (.listFiles (java-utils/file path)))))

(defn process-completed [exit-value]
  (println  "Process terminated: {}" exit-value))

(defn process-failed3 [out-fn err-fn #^ExecuteException ex]
  (println  (format "Process failed: %s %s" (.getMessage ex)
    (if-let [c (.getCause ex)]
      (.getMessage c)))))

(defn monitor [stream read-fn]
    (repl.main/start-io-thread
          #(binding [*out* stream]
             (let [msg (read-fn)]
               (println "anything???")
               (print msg)
               (flush)))
          (fn [cause]
            (print "\nError in monitor\n"))))

(defn setup-monitors [out-fn err-fn]
  (monitor *out* out-fn)
  (monitor *out* err-fn))

(defn zip-dir [dest-zip-file src-dir]
(let [cmd-line (CommandLine. "zip")
        #^DefaultExecutor executor (DefaultExecutor.)
        [out-pipe err-pipe] [(PipedOutputStream.) (PipedOutputStream.)]
        out-pipe-reader (LineNumberReader.
                          (InputStreamReader. (PipedInputStream. out-pipe)))
        err-pipe-reader (LineNumberReader.
                          (InputStreamReader. (PipedInputStream. err-pipe)))
      stream-handler (PumpStreamHandler. out-pipe err-pipe)]
  (doall (map #(.addArgument cmd-line % true) ["-R" (str dest-zip-file) "*"]))
    (.setWorkingDirectory executor (if (instance? java.io.File src-dir)
                                     src-dir (java.io.File. src-dir)))
    (.setWatchdog executor (ExecuteWatchdog. Integer/MAX_VALUE))
    (.execute executor cmd-line (proxy [ExecuteResultHandler] []
                                  (onProcessComplete [exit-value]
                                    (process-completed exit-value))
                                  (onProcessFailed [ex]
                                    (println ex)
                                    (println (.getCause ex))
                                    (process-failed3
                                            #(.readLine out-pipe-reader)
                                            #(.readLine err-pipe-reader)
                                      ex))))))

(defn zip-files [target-path    
                 src-base-dir]  
  (let [name (.getName (java-utils/file src-base-dir))
        full-target (File. (str target-path "/" name)
                      (str name ".zip"))]    
  (when (.exists full-target)    
    (.delete full-target))
  (.mkdirs (java-utils/file (str target-path "/" name)))  
  (zip-dir full-target src-base-dir)))

(defn zip-it [target-path src-base-dir]
  (zip-files target-path src-base-dir))

(defn zip-project-templates []
  (doall (map #(zip-it *dest-project-templates-dir* %)
           (get-directories *src-project-templates-dir*))))

(defn zip-project-samples []
  (doall (map #(zip-it *dest-project-samples-dir* %)
           (get-directories *src-project-samples-dir*))))

(zip-project-templates)
(zip-project-samples)
