
(ns org.enclojure.ide.analyze.files
(:use clojure.test org.enclojure.ide.analyze.files)
  )

(def --base-dir--
  "/Users/ericthor/Dev/enclojure/")

(def --test-files--
  (filter #(.endsWith (.getPath %) ".clj")
    (file-seq (java.io.File. --base-dir--))))


(defn do-analyze-file [fname]
    (with-open [f (java.io.FileInputStream.
                    fname)]
     (try
        (analyze-file f "clj" {})
       (catch Throwable t 
         (println (.getMessage t))
         t))))

(defn collect-parse-errors []
  (filter #(instance? Throwable %)
    (into [] (map do-analyze-file --test-files--))))



