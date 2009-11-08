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
        :doc "Protocol for org.enclojure.flex.CljSymbol"}
 org.enclojure.flex.CljSymbol
  (:gen-class
    :extends java_cup.runtime.Symbol
    :state state
    :init ctor
    :constructors {[int String int int int int int String Object]
                   [int Object]
                   [String int java_cup.runtime.Symbol java_cup.runtime.Symbol Object]
                   [int java_cup.runtime.Symbol java_cup.runtime.Symbol Object] }
    :factory newSymbol
    )
  (:import (java_cup.runtime Symbol))
  (:require [org.enclojure.idetools.tokens :as tokens]))


(defn -ctor
  ([id tag line col charp start length text data]
    [[id data] {:id id :tag tag :line line :col col :charp charp
                  :start start :length length :text text :data data
                  :token (tokens/get-token id)}])
  ([tag id left right data]
        [[tag id left right data]
         {:id id :tag tag :left left :right right :data data
          :form (keyword tag)}]))

(defn -toString [this]
  (str (.state this)))



