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
;*    Author: Eric Thorsen
)

(ns #^{ :author "Eric Thorsen",
        :doc "Protocol for org.enclojure.idetools.matchers"}
 org.enclojure.idetools.matchers
    (:import (org.enclojure.flex _Lexer ClojureSymbol)
    (Example ClojureSym ClojureParser)
    (java.io File FileReader FileInputStream StringReader)))


(def *matched-pairs*
  [
   [ClojureSym/LEFT_PAREN ClojureSym/RIGHT_PAREN]
   [ClojureSym/DISP_SET ClojureSym/RIGHT_CURLY]
   [ClojureSym/LEFT_CURLY ClojureSym/RIGHT_CURLY]
   [ClojureSym/LEFT_SQUARE ClojureSym/RIGHT_SQUARE]
   ])

(defn set-conj [s v]
  (conj (or s #{}) v))

(def *start-match-map*
  (reduce (fn [m [k v]]
            (assoc m k v)) {} *matched-pairs*))

(def *end-match-map*
  (reduce (fn [m [k v]]
            (assoc m v k)) {} *matched-pairs*))


(defn match-pair
  [in-str]
  (let [lexer (_Lexer. (StringReader. in-str))]
    (loop [tokens [] stack nil cnt 0]
        (let [token (.next_token lexer)
              sym (.sym token)]
          (println cnt " sym=" sym " start-match= " (*start-match-map* sym) " end-match= " (*end-match-map* sym))
          (if (= sym ClojureSym/EOF)
            stack
            (let [nstack
                (cond (*start-match-map* sym) (do (println  cnt " start")
                        (conj stack sym))
                  (*end-match-map* sym)
                    (let [s (first stack)]
                      (println  cnt  " end first = " s " should match " (*end-match-map* sym))
                      (if (= s (*end-match-map* sym)) (pop stack)
                        (throw (Exception. (format "Expected %d got %d"
                                             (*end-match-map* sym) s))))
                  :else (do (println  cnt " squwatola")
                      stack)))]
              (println  cnt " " nstack)
              (recur (conj tokens token) nstack (inc cnt))))))))



  
  



