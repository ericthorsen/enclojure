(comment
Sample clojure source file
)
(ns default.core
    (:gen-class))

(defn -main
    ([greetee]
  (println (str "Hello " greetee "!")))
  ([] (-main "world")))
