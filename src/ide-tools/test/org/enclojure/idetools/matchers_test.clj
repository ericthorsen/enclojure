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
    org.enclojure.idetools.matchers-test
        (:require [org.enclojure.idetools.tokens :as tokens]
          [org.enclojure.idetools.token-set :as token-set])
  (:use clojure.test)
  (:require [org.enclojure.idetools.matchers :as matchers])
  (:import (org.enclojure.flex _Lexer ClojureSymbol)
    (Example ClojureSym ClojureParser)
    (java.io File FileReader FileInputStream StringReader)))

(defn- make-char-token
  [c]
   (tokens/make-token c :char (str c) :char c))

; For testing basic brace matching/corretion.
(def -char-token-pairs-
  {
   (make-char-token \{) (make-char-token \})
   (make-char-token \() (make-char-token \))
   (make-char-token \[) (make-char-token \])
   })

(deftest matchers-test
  (testing "string patterns"
    (is (= "()" (apply str (matchers/fix-pairs "(" -char-pairs-))))
    (is (= "[]" (apply str (matchers/fix-pairs "[" -char-pairs-))))
    (is (= "{}" (apply str (matchers/fix-pairs "{" -char-pairs-))))
    (is (= "()" (apply str (matchers/fix-pairs ")" -char-pairs-))))
    (is (= "\"\"" (apply str (matchers/fix-pairs "\"" -char-pairs-))))
    (is (= "[]" (apply str (matchers/fix-pairs "]" -char-pairs-))))
    (is (= "{}" (apply str (matchers/fix-pairs "}" -char-pairs-))))
    (is (= "()([])" (apply str (matchers/fix-pairs ")(]" -char-pairs-))))
    (is (= "(let [x 4] { []()})"
          (apply str (matchers/fix-pairs "(let [x 4] { ])" -char-pairs-))))
    )
  
 
  (testing "lexer/token patterns"
    (is (= (list tokens/RIGHT_PAREN)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string "(") matchers/*matched-pairs*))))
    (is (= (list tokens/LEFT_PAREN)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string ")") matchers/*matched-pairs*))))
    (is (= (list tokens/RIGHT_CURLY)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string "{") matchers/*matched-pairs*))))
    (is (= (list tokens/RIGHT_CURLY)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string "#{") matchers/*matched-pairs*))))
    (is (= (list tokens/LEFT_CURLY)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string "}") matchers/*matched-pairs*))))
    (is (= (list tokens/RIGHT_SQUARE)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string "[") matchers/*matched-pairs*))))
    (is (= (list tokens/LEFT_SQUARE)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string "]") matchers/*matched-pairs*))))
    (is (= (list tokens/RIGHT_SQUARE)
            (map :token (matchers/get-fix-pairs-fns
                          (matchers/lex-string "([)") matchers/*matched-pairs*))))
    )
  )

