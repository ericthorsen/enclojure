(ns org.enclojure.commons.control-engine
 (:import (java.util.concurrent Executors ExecutorService)))

(defn get-root-cause [throwable]
  (loop [cause throwable]
    (if-let [cause (.getCause cause)]
      (recur cause)
      cause)))

(defn interrupted-exception? [cause]
  (or (instance? java.lang.InterruptedException cause)
    (instance? java.nio.channels.ClosedByInterruptException cause)))

(defn start-io-thread
  "io-fn should have the blocking action such as a blocking read. If it is not
   blocking than this thread will be a hard loop."
  ([io-fn ex-fn]
    (let [io-thread-fn (fn []
                         (try
                           (io-fn)
                           (recur)
                           (catch Throwable t
                             (let [cause (get-root-cause t)]
                               (when (and ex-fn (not (interrupted-exception? cause)))
                                 (ex-fn cause))))))
          io-thread (Thread. io-thread-fn)]
      (.start io-thread)
      #(.interrupt io-thread)))
  ([io-fn]
    (start-io-thread io-fn nil)))

(defn start [blocking-dequeue-fn process-fn]
  (let [run-fn #(process-fn (blocking-dequeue-fn))
        #^ExecutorService thread-pool (Executors/newCachedThreadPool)
        stop-dequeue-fn (start-io-thread #(.execute thread-pool run-fn))]
    {:thread-pool thread-pool
     :stop-dequeue-fn stop-dequeue-fn}))

(defn stop [{:keys [thread-pool stop-dequeue-fn]}]
  (stop-dequeue-fn)
  (.shutdown thread-pool))
