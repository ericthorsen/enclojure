(comment
;*
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http:;opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Eric Thorsen
)

(ns #^{ :author "Eric Thorsen",
        :doc "Protocol for org.enclojure.idetools.analyzer"}
		org.enclojure.idetools.analyzer
  )

;/symbol->localbinding
(def *LOCAL_ENV*)
;vector<localbinding>
(def *LOOP_LOCALS*)
;Label
(def *LOOP_LABEL*)
;vector<object>
(def *CONSTANTS*)
;keyword->constid
(def *KEYWORDS*)
;var->constid
(def *VARS*)
;FnFrame
(def *METHOD*)
;null or not
(def *IN_CATCH_FINALLY*)
(def *LOADER*)
;String
(def *SOURCE* "NO_SOURCE_FILE")
;String
(def *SOURCE_PATH* "NO_SOURCE_PATH")
;String
(def *COMPILE_PATH*)
;boolean
(def *COMPILE_FILES* false)
;Integer
(def *LINE*)
;Integer
(def *LINE_BEFORE*)
(def *LINE_AFTER*)
;Integer
(def *NEXT_LOCAL_NUM*)
;Integer
(def *RET_LOCAL_NUM*)

(def *specials-syms*
    ['def 'loop 'loop* 'recur 'if 'let 'let* 'letfn* 'do 'fn* 'fn 'quote 'var
    '. 'set! 'try-finally 'try 'catch 'finally 'throw 'monitor-enter 'monitor-exit
    'import* 'import 'Class 'new 'unquote 'unquote-splicing 'syntax-quote 'list
    'hash-map 'vector 'identity '& 'clojure.lang.ISeq 'inline 'inline-arities
    'ns 'in-ns])

`(declare ~@(map #(symbol (str % "-expr")) *specials-syms*))

(def *specials*
  (reduce #(assoc %1
             %2 (symbol (str %2 "-expr"))) {} *specials-syms*))

