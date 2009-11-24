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
        :doc "Protocol for org.enclojure.flex.ClojureSymFactory"}
 org.enclojure.flex.ClojureSymFactory
  (:gen-class
    :implements [java_cup.runtime.SymbolFactory])
  (:import (java_cup.runtime SymbolFactory Symbol)
    (org.enclojure.flex CljSymbol)))

;These are called by the cup parser not the jflex parser.  Use the direct CljSymbol
; function there since you can provide more context.
(defn -newSymbol
  [this #^String tag id #^Symbol left #^Symbol right #^Object data]
  (CljSymbol/newSymbol tag id left right data))


(defn -newSymbol
  [this #^String tag id #^Symbol left #^Symbol right]
  (CljSymbol/newSymbol tag id left right nil))

(defn -newSymbol
  [this #^String tag id #^Object data]
  (CljSymbol/newSymbol tag id nil nil data))

(defn -newSymbol
  [this #^String tag id]
  (CljSymbol/newSymbol tag id nil nil nil))  


(defn -startSymbol
  [this #^String tag id id2]
  (CljSymbol/newSymbol tag id nil nil nil))
  





