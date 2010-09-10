; Sample Clojure File

(ns clojure.sample
  (:use clojure.core))

(def my-list '(1 2 3 a b c))

(defn sample-method [x]
 (println x my-list))

(sample-method {:message "Hello World"})
