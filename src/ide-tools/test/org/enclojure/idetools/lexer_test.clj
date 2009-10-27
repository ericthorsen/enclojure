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
        :doc "Protocol for org.enclojure.idetools.lexer-test"}
		org.enclojure.idetools.lexer-test
        (:require [org.enclojure.idetools.tokens :as tokens]
          [org.enclojure.idetools.token-set :as token-set])
  (:use clojure.test)
  (:import (org.enclojure.flex _Lexer ClojureSymbol)
    (Example ClojureSym ClojureParser)
    (java.io File FileReader FileInputStream StringReader)))
    
(def -lexer- (_Lexer. (StringReader. "")))
(def tkEOF 0)

(defn get-lex-tokens
  [in-str]
  (.yyreset -lexer- (StringReader. in-str))
  (loop [tokens []]
    (let [token (.next_token -lexer-)]
      (if (= (.sym token) tkEOF)
        (conj tokens token)
        (recur (conj tokens token))))))







