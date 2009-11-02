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

(defn lex-token-syms
  [in-str]
  (map #(.sym %) (get-lex-tokens in-str)))

(deftest test-tokens
  (testing "basic tokens"
    (is (= [ClojureSym/LEFT_CURLY ClojureSym/EOF]
            (lex-token-syms "{")))
    (is (= [ClojureSym/RIGHT_CURLY ClojureSym/EOF]
            (lex-token-syms "}")))
    (is (= [ClojureSym/LEFT_PAREN ClojureSym/EOF]
            (lex-token-syms "(")))
    (is (= [ClojureSym/RIGHT_PAREN ClojureSym/EOF]
            (lex-token-syms ")")))
    (is (= [ClojureSym/LEFT_SQUARE ClojureSym/EOF]
            (lex-token-syms "[")))
    (is (= [ClojureSym/RIGHT_SQUARE ClojureSym/EOF]
            (lex-token-syms "]")))
    (is (= [ClojureSym/BACKQUOTE ClojureSym/EOF]
            (lex-token-syms "`")))
    (is (= [ClojureSym/AT ClojureSym/EOF]
            (lex-token-syms "@")))
    (is (= [ClojureSym/TILDA ClojureSym/EOF]
            (lex-token-syms "~")))
    (is (= [ClojureSym/TILDAAT ClojureSym/EOF]
            (lex-token-syms "~@")))
    )

  (testing "whitespace and comments"
    (is (= [ClojureSym/EOF]
        (lex-token-syms ",")))
    (is (= [ClojureSym/EOF]
        (lex-token-syms ",,,,")))
    (is (= [ClojureSym/EOF]
        (lex-token-syms " ")))
    (is (= [ClojureSym/EOF]
        (lex-token-syms " , ,")))
    (is (= [ClojureSym/LINE_COMMENT ClojureSym/EOF]
        (lex-token-syms "; some comment")))
    )

  (testing "literals"
    (is (= [ClojureSym/NIL ClojureSym/EOF]
        (lex-token-syms "nil")))
    (is (= [ClojureSym/TRUE ClojureSym/EOF]
        (lex-token-syms "true")))
    (is (= [ClojureSym/FALSE ClojureSym/EOF]
        (lex-token-syms "false")))
    (is (= [ClojureSym/CHAR_LITERAL ClojureSym/EOF]
        (lex-token-syms "\\C")))
    (is (= [ClojureSym/RATIO ClojureSym/EOF]
        (lex-token-syms "12/34")))
    (is (= [ClojureSym/INTEGER_LITERAL ClojureSym/EOF]
        (lex-token-syms "12")))
    (is (= [ClojureSym/INTEGER_LITERAL ClojureSym/EOF]
        (lex-token-syms "0xe50")))
    (is (= [ClojureSym/BIG_DECIMAL_LITERAL ClojureSym/EOF]
        (lex-token-syms "12.3")))
    (is (= [ClojureSym/STRING_LITERAL ClojureSym/EOF]
        (lex-token-syms "\"This is a string.\"")))
    (is (= [ClojureSym/STRING_LITERAL ClojureSym/EOF]
        (lex-token-syms "\"\"")))
    (is (= [ClojureSym/STRING_LITERAL ClojureSym/EOF]
        (lex-token-syms "\"string with escapes \t \n \r \f \b \"")))
    )

  (testing "symbols, keywords"
    (is (= [ClojureSym/symATOM ClojureSym/EOF]
        (lex-token-syms "fred")))
    (is (= [ClojureSym/symATOM ClojureSym/EOF]
        (lex-token-syms "?fred")))
    (is (= [ClojureSym/symATOM ClojureSym/EOF]
        (lex-token-syms "!fred")))
    (is (= [ClojureSym/symATOM ClojureSym/EOF]
        (lex-token-syms ".fred")))
    (is (= [ClojureSym/KEYWORD ClojureSym/EOF]
        (lex-token-syms ":fred")))
    (is (= [ClojureSym/KEYWORD ClojureSym/EOF]
        (lex-token-syms "::fred")))
    (is (= [ClojureSym/symATOM ClojureSym/symNS_SEP
            ClojureSym/symATOM ClojureSym/EOF]
        (lex-token-syms "nss/func")))
    )

  (testing "dispatch forms"
    (is (= [ClojureSym/DISP_META ClojureSym/EOF]
            (lex-token-syms "#^")))
    (is (= [ClojureSym/DISP_VAR ClojureSym/EOF]
            (lex-token-syms "#\\")))
    (is (= [ClojureSym/DISP_REGEX ClojureSym/EOF]
            (lex-token-syms "#\"")))
    (is (= [ClojureSym/DISP_FN ClojureSym/EOF]
            (lex-token-syms "#(")))
    (is (= [ClojureSym/DISP_SET ClojureSym/EOF]
            (lex-token-syms "#{")))
    (is (= [ClojureSym/DISP_EVAL ClojureSym/EOF]
            (lex-token-syms "#=")))
    (is (= [ClojureSym/DISP_COMMENT ClojureSym/EOF]
            (lex-token-syms "#!")))
    (is (= [ClojureSym/DISP_UNREADABLE ClojureSym/EOF]
            (lex-token-syms "#<")))
    (is (= [ClojureSym/DISP_DISCARD_FORM ClojureSym/EOF]
            (lex-token-syms "#_")))
    )

  (testing "specials forms"
    (is (= [ClojureSym/DEF ClojureSym/EOF]
            (lex-token-syms "def")))
    (is (= [ClojureSym/DEFN ClojureSym/EOF]
            (lex-token-syms "defn")))
    (is (= [ClojureSym/LOOP ClojureSym/EOF]
            (lex-token-syms "loop")))
    (is (= [ClojureSym/RECUR ClojureSym/EOF]
            (lex-token-syms "recur")))
    (is (= [ClojureSym/DO ClojureSym/EOF]
            (lex-token-syms "do")))
    (is (= [ClojureSym/IF ClojureSym/EOF]
            (lex-token-syms "if")))
    (is (= [ClojureSym/NS ClojureSym/EOF]
            (lex-token-syms "ns")))
    (is (= [ClojureSym/LET ClojureSym/EOF]
            (lex-token-syms "let")))
    (is (= [ClojureSym/LET_STAR ClojureSym/EOF]
            (lex-token-syms "let*")))
    (is (= [ClojureSym/LETFN ClojureSym/EOF]
            (lex-token-syms "letfn")))
    (is (= [ClojureSym/QUOTE ClojureSym/EOF]
            (lex-token-syms "quote")))
    )

)
