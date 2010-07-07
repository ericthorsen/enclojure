(comment
;*******************************************************************************
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    GNU General Public License, version 2
;*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
;*    exception (http://www.gnu.org/software/classpath/license.html)
;*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
;*    of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*******************************************************************************
;*    Author: Eric Thorsen
;*******************************************************************************
)

(ns org.enclojure.ide.nb.classpaths.listeners
  (:use [clojure.main :exclude (with-bindings)])
  (:require
    [clojure.set :as set]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.analyze.symbol-meta :as symbol-meta]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    [org.enclojure.ide.nb.classpaths.resource-tracking :as resource-tracking]
    [org.enclojure.ide.analyze.core :as analyze.core]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.analyze.files :as analyze.files]
    )
  (:import
    (org.netbeans.api.java.classpath ClassPath GlobalPathRegistry
             GlobalPathRegistryEvent GlobalPathRegistryListener)
    (clojure.lang LineNumberingPushbackReader)
    (clojure.asm Opcodes)
    (org.netbeans.modules.java.classpath ClassPathAccessor)
    (java.lang ExceptionInInitializerError)
    (java.util.logging Level)
    (java.util.concurrent LinkedBlockingQueue CountDownLatch
      Executors ExecutorService TimeUnit ExecutorCompletionService)
    (org.openide.util WeakListeners)
    (org.netbeans.api.java.platform JavaPlatform)
    (org.netbeans.api.java.queries SourceForBinaryQuery)
    (org.netbeans.api.project Project ProjectUtils SourceGroup)
    (org.netbeans.api.java.classpath
            ClassPath
            ClassPath$Entry
            GlobalPathRegistryEvent
            GlobalPathRegistry
            GlobalPathRegistryListener)
    (org.netbeans.editor BaseDocument)
    (org.netbeans.modules.editor NbEditorUtilities)
    (org.netbeans.spi.java.classpath.support ClassPathSupport)
    (org.netbeans.api.java.platform JavaPlatformManager)
    (org.netbeans.api.java.project JavaProjectConstants)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
    (org.enclojure.ide.asm ClassReader ClassVisitor Type)
    (org.enclojure.ide.asm.tree ClassNode)
    (java.io StringReader)
    (java.util Calendar)
    (java.lang.StringBuilder)
    (java.util.jar JarFile$JarFileEntry JarFile JarEntry)
    (org.openide.filesystems FileObject FileStateInvalidException
      FileUtil JarFileSystem URLMapper)
    (java.io File FileWriter IOException StringReader StringWriter
      PrintStream PrintWriter OutputStream ByteArrayOutputStream)
    ;(com.sun.jdi VirtualMachine VirtualMachineManager ReferenceType ClassType)
    ))

; setup logging
(logger/ensure-logger)

(def -reader-queues- (ref {}))
(def -path-listener- (ref nil))

(defn- publish-stack-trace [throwable]
  (let [root-cause
            (loop [cause throwable]
                (if-let [cause (.getCause cause)]
                    (recur cause) cause))]
    (binding [*out* (StringWriter.)]
      (.printStackTrace root-cause (PrintWriter. *out*))
      (when (not= root-cause throwable)
         (.printStackTrace throwable (PrintWriter. *out*)))
      (logger/error (str *out*)))))

(defmacro #^{:private true}
    with-exception-handling [& body]
    `(try
      ~@body
       (catch Throwable t#
        (publish-stack-trace t#))))

(defn new-queue
  ([take-fn thread-label executor-service agent-data]
  (let [queue (LinkedBlockingQueue.)
        tfn (if agent-data
              #(let [item (.take queue)]
                 (.submit executor-service
                   (fn [] (apply take-fn agent-data item))
                   item))
              #(apply take-fn (.take queue)))
        thread (Thread.
                 (fn []
                   (try
                     (loop []
                       (try
                         (tfn)
                         (Thread/yield)
                         (catch java.lang.InterruptedException i
                           (logger/debug "shutting down queue {}" thread-label))
                         (catch Throwable t
                           (publish-stack-trace t)))
                       (recur))
                     (catch java.lang.InterruptedException i
                       (logger/debug "shutting down queue {}" thread-label))
                     (catch Throwable t
                       (publish-stack-trace t))))
                 thread-label)]
    (.start thread)
    {:stopfn #(.interrupt thread) :queue queue :name thread-label})))


;-------------------------------------------------------------------------------
; classpath type
;-------------------------------------------------------------------------------
(defmulti add-class-path
  (fn [classpath cp-type] cp-type))

(defmethod add-class-path :default
  [classpath cp-type]
  (logger/warn "add-class-path multi-method... no dispath for source type {} ignoring..." cp-type))

(defmethod add-class-path "classpath/source"
  [classpath cp-type]
  (with-exception-handling
    (resource-tracking/add-source-roots classpath)
    (when-let [roots (.getRoots classpath)]
      (doseq [item (seq roots)]
      (logger/debug "add-class-path :source to path Queue {}" item)
      (.put (:queue
                 (:path-reader-queue @-reader-queues-)) [item])))))


(defmethod add-class-path "classpath/compile"
  [classpath cp-type]
    (with-exception-handling
      (resource-tracking/add-source-roots classpath)
      (when-let [entries (.entries classpath)]
        (doseq [item (seq entries)]
          (logger/debug "add-class-path :compile to path Queue {}" item)
            (.put (:queue
                         (:path-reader-queue @-reader-queues-)) [item])))))

(defmethod add-class-path "classpath/boot"
  [classpath cp-type]
    (with-exception-handling
      (when-let [entries (.entries classpath)]
        (doseq [item (seq entries)]
          (logger/debug "add-class-path :boot to path Queue " item)
            (.put (:queue
                    (:path-reader-queue @-reader-queues-)) [item])))))

(defmulti rem-class-path
  (fn [classpath cp-type] cp-type))

(defmethod rem-class-path :default [& _])

;-------------------------------------------------------------------------------
; GlobalPathRegistryListener with writer queues for the changed paths
;-------------------------------------------------------------------------------
(defn start-path-listener []
  (let [permission (java.lang.RuntimePermission. "modifyThread")
        new-path-queue (new-queue add-class-path "add-classpaths" 4 nil)
        rem-path-queue (new-queue rem-class-path "rem-classpaths" 4 nil)
        queue-entries-fn
           (fn [queue evt]
             (try
               (let [cp-type (.getId evt)]
                (doseq [path (.getChangedPaths evt)]
                  (logger/debug "path listener queueing {}" path)
                    (.put queue [path cp-type])))
             (catch Throwable t (publish-stack-trace t))))
        add-paths-thread-pool (Executors/newFixedThreadPool 2)
        rem-paths-thread-pool (Executors/newFixedThreadPool 2)
        listener-proxy
            (proxy [GlobalPathRegistryListener] []
                (pathsAdded [event]
                  (.execute add-paths-thread-pool
                    #(queue-entries-fn (:queue new-path-queue) event)))
                (pathsRemoved [event]
                  (.execute rem-paths-thread-pool
                    #(queue-entries-fn (:queue rem-path-queue) event))))]
        (.addGlobalPathRegistryListener
          (GlobalPathRegistry/getDefault) listener-proxy)
    {:stopfn
     (fn []
       (.removeGlobalPathRegistryListener
          (GlobalPathRegistry/getDefault) listener-proxy)
       ((:stopfn new-path-queue))
       ((:stopfn rem-path-queue))
       (.shutdown add-paths-thread-pool)
       (.shutdown rem-paths-thread-pool)
       )}))

(defn analyze-cache-handler [parse-data-cache inputs]
  (with-exception-handling
    (let [parse-data (symbol-caching/analyze inputs)
          tkey (:key inputs)]
    (dosync
        (commute parse-data-cache assoc
           tkey {:parsed-data parse-data})))))

(defn get-nocache-handlerfn [proc-fn metastr]
  (fn [data-cache & inputs]
    (try
      (let [data (apply proc-fn inputs)] data)
        (catch Throwable t
            (logger/debug metastr " nocache. inputs {}" inputs)
          (publish-stack-trace t)))))

(defn get-cache-handlerfn [key-fn proc-fn metastr]
  (fn [data-cache & inputs]
    (with-exception-handling
      (let [tkey (apply key-fn inputs)]
        ;(logger/debug metastr " key " tkey)
        (if-let [ret (@data-cache tkey)]
          (do
            (logger/debug metastr " key cached.  Returning data for {}" tkey)
            ret)
          (do
            (let [data (apply proc-fn inputs)]          
            (dosync
                (commute data-cache assoc
                  tkey {:key tkey :processed-data data}))           
              )))))))

(defn get-sym-cache-handlerfn [key-fn proc-fn metastr]
  (fn [data-cache & inputs]
    (with-exception-handling
      (let [tkey (apply key-fn inputs)]
        (if-let [ret (@data-cache tkey)]
          (do
            (logger/debug metastr " key cached.  Returning data for {}" tkey)
            ret)
          (do            
            (let [data (apply proc-fn inputs)]          
              )))))))

(defmethod symbol-caching/process-path org.openide.filesystems.FileObject
;  "traverses a set of source paths and calls analyze on them"
  ([root]
  (logger/debug "process-path file-object {}" root)
    (with-exception-handling
      (doseq [f (filter (fn [e] (= "clj" (.getExt e)))
                  (symbol-caching/file-obj-traverse root))]
        (logger/debug "process-path - FileObject to File Queue {}" root)
        (.put (:queue (:file-reader-queue @-reader-queues-))
          [{:ext "clj" :source f :file-object f :key (.getPath f)}]))))
  ([root classpath-entry]
    (symbol-caching/process-path root)))

;-----------------------------------------------------------------------------
; enqueue funcs
;-----------------------------------------------------------------------------
(defn enqueue-to-file-processor [args]
  (.put (:queue (:file-reader-queue @-reader-queues-)) args))

(defn enqueue-to-path-processor [args]
  (.put (:queue (:path-reader-queue @-reader-queues-)) args))

(defn new-reader-queues
  []
  (let [completion-queue (LinkedBlockingQueue.)]
  {:file-reader-queue (new-queue
                        (get-cache-handlerfn symbol-caching/path-key symbol-caching/analyze "file-queue")
                            "file-reader-queue"
                                (ExecutorCompletionService.
                                    (Executors/newFixedThreadPool 8)
                                        completion-queue)
                                        symbol-caching/-symbol-data-cache-)
   :jar-reader-queue (new-queue
                        (get-nocache-handlerfn symbol-caching/process-jar "jar-queue")
                            "jar-reader-queue"
                                (Executors/newFixedThreadPool 2) symbol-caching/-file-cache-)
   :path-reader-queue (new-queue
                        (get-cache-handlerfn symbol-caching/path-key symbol-caching/process-path "path-queue")
                            "path-reader-queue"
                                (Executors/newFixedThreadPool 4) symbol-caching/-file-cache-)
   :completion-queue {:name "completion queue" :queue completion-queue :stopfn (fn[])}}))

(defn get-queue-status
  #^{:status true}
  []
  (doseq [{:keys [name queue]} (vals @-reader-queues-)]
    (println name " has " (.size queue) " items queued.")))
    ;(logger/debug (:name q) " has " (.size q) " items queued.")))


(defn monitor-until-empty
  ([]
    (if (pos? (reduce (comp (memfn size) +)
            0 (map :queue @-reader-queues-)))
        (get-queue-status)
    (logger/debug "queues are empty")))
  ([& _] monitor-until-empty))


(defn stop-service
  #^{:stop true}
  []
    (let [gpath (GlobalPathRegistry/getDefault)]
        (when-let [curr-listener @-path-listener-]
            (try ((:stopfn @-path-listener-))
              (catch Throwable t))
          (doseq [stop-q (map :stopfn (vals @-reader-queues-))]
                        (stop-q))
            (dosync (alter -path-listener- (fn [_]))
                (alter -reader-queues- (fn [_]))))))

(defn start-service
  #^{:start true}
  []
    (dosync
      (stop-service)
      (symbol-caching/clear-caches)
      (alter -reader-queues- merge (new-reader-queues))
      (alter -path-listener- (fn [_] (start-path-listener)))))

