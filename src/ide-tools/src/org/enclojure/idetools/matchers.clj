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
    (java.io File FileReader FileInputStream StringReader)
      (org.enclojure.idetools PositionalPushbackReader)))


(def *context* (atom nil))

;(context :ns name (context :import (context :package) (context :package)) (context :require (context :libspec) (context :libspec)

(def *matched-pairs*
  [
   [ClojureSym/LEFT_PAREN ClojureSym/RIGHT_PAREN]
   [ClojureSym/DISP_SET ClojureSym/RIGHT_CURLY]
   [ClojureSym/LEFT_CURLY ClojureSym/RIGHT_CURLY]
   [ClojureSym/LEFT_SQUARE ClojureSym/RIGHT_SQUARE]
   ])

(defn set-conj [s v]
  (conj (or s #{}) v))

(def *match-map*
  (reduce (fn [m [k v :as p]]
            (assoc m k p)) {} *matched-pairs*))

(def *end-match-map*
  (reduce (fn [m [k v]]
            (update-in m [v] 
              (fn [c] 
                (if c (conj c k) #{k}))))
                 {} *matched-pairs*))

(defn match-pair
  [in-str]
  (let [reader (PositionalPushbackReader. (StringReader. in-str))
        lexer (_Lexer. reader)]
    (loop [tokens [] stack nil cnt 0]
        (let [token (.next_token lexer)
              sym (.sym token)]
          ;(println cnt " sym=" sym " start-match= " (*match-map* sym) " end-match= "
           ; (*end-match-map* sym))
          (if (= sym ClojureSym/EOF)
            stack
            (let [nstack
                (cond (*match-map* sym) 
                  (do ;(println  cnt " start")
                        (conj stack sym))
                  (*end-match-map* sym)
                    (let [s (first stack)]
                      ;(println  cnt  " end first = " s " should match "
                      ;      (*end-match-map* sym))
                      (if ((*end-match-map* sym) s)
                        (pop stack)
                        (throw (Exception. (format "Expected %d got %d"
                                             (*end-match-map* sym) ((*end-match-map* sym) s))))))
                  :else stack)]
              (println  cnt " " nstack)
              (recur (conj tokens token) nstack (inc cnt))))))))

(defn fix-pairs
  [in-str]
  (let [reader (PositionalPushbackReader. (StringReader. in-str))
        lexer (_Lexer. reader)]
    (loop [tokens [] stack nil cnt 0]
        (let [token (.next_token lexer)
              sym (.sym token)]
          ;(println cnt " sym=" sym " start-match= " (*match-map* sym) " end-match= "
           ; (*end-match-map* sym))
          (if (= sym ClojureSym/EOF)
            stack
            (let [nstack
                (cond (*match-map* sym)
                  (do ;(println  cnt " start")
                        (conj stack token))
                  (*end-match-map* sym)
                    (let [s (first stack)]
                      ;(println  cnt  " end first = " s " should match "
                      ;      (*end-match-map* sym))
                      (if ((*end-match-map* sym) (.sym s))
                        (pop stack)
                        (throw (Exception. (format "Should insert %s at %d. got %d instead. "
                                             (*end-match-map* sym)
                                             (.getPosition lexer)
                                             ((*end-match-map* sym) s))))))
                  :else stack)]
              (println  (format "%d:%d" cnt (.getPosition lexer)) " " nstack)
              (recur (conj tokens token) nstack (inc cnt))))))))



