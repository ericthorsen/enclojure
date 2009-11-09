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
  (:require [org.enclojure.idetools.tokens :as tokens])
    (:import (org.enclojure.flex _Lexer ClojureSymbol)
    (Example ClojureSym ClojureParser)
    (java.io File FileReader FileInputStream StringReader)
      (org.enclojure.idetools PositionalPushbackReader)))

(def *context* (atom nil))

(def *matched-pairs*
  (reduce
    (fn [m [s e]]
        (assoc m (tokens/get-token s) (tokens/get-token e)))
    {}
    [
       [ClojureSym/LEFT_PAREN ClojureSym/RIGHT_PAREN]
       [ClojureSym/DISP_SET ClojureSym/RIGHT_CURLY]
       [ClojureSym/LEFT_CURLY ClojureSym/RIGHT_CURLY]
       [ClojureSym/LEFT_SQUARE ClojureSym/RIGHT_SQUARE]
    ]))

(defn set-conj [s v]
  (conj (or s #{}) v))

(def *match-map*
  (reduce (fn [m [k v :as p]]
            (assoc m k v)) {} *matched-pairs*))

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
    
(defn
  fix-pairs
  "Given a seq of tokens and a map of paired tokens,
  attempt to check/repair token stream by adding tokens where needed"
  ([token-stream keyfn pairs]
  (let [end-map (reduce (fn [m [k v]]
                            (update-in m [v]
                              (fn [c]
                                (if c (conj c k) #{k}))))
                         {} pairs)]
    (loop [tokens token-stream
           stack nil
           out []]
        (if-let [token (first tokens)]
            (let [token-key (keyfn token)
                  [nstack t]
                (cond
                  (pairs token-key) ;matches a start token
                    [(conj stack token) [token]]; push it onto the stack and keep the token
                   (end-map token-key) ; matches an end token
                    (if-let [s (first stack)] ;If there is something on the stack
                      (if ((end-map token-key) (keyfn s)) ;...see if it matches
                        [(pop stack) [token]];...keep it and pop the stack
                        (let [i (first (end-map token-key))]
                            [(conj stack i) [i]])) ;...else, insert the start token
                                                   ; in place and push it onto the stack
                      (let [i (first (end-map token-key))]
                        [stack [i token]]));Nothing on the stack,
                                                    ;so put the beginnning token
                                                    ;before the current token
                  :else [stack [token]])] ; Not a match-pair.
              (println "token " token " stack " nstack " add " t)
              (recur (rest tokens) nstack (concat out t)))
             (if (pos? (count stack))
                (concat out
                  (reduce #(conj %1 (pairs %2)) [] stack))
               out)))))
  ([token-stream pairs]
    (fix-pairs token-stream identity pairs)))