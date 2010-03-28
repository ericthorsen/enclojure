(comment
;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;    The use and distribution terms for this software are covered by the
;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;    which can be found in the file epl-v10.html at the root of this distribution.
;    By using this software in any fashion, you are agreeing to be bound by
;    the terms of this license.
;    You must not remove this notice, or any other, from this software.
;
;    Author: Frank Failla
)

(ns org.enclojure.ide.LexerInputReader
  (:gen-class	
   :extends java.io.Reader
   :state data
   :init ctor
   :methods [ [unread [char] void] [index [] int]]
   :constructors {[org.netbeans.spi.lexer.LexerInput] []})
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  )
  
; setup logging
(logger/ensure-logger)

(defn- -ctor [input]
  [[] [input (atom -1)]])

(defn input [this]
  (first (.data this)))

(defn -index [this]
  @((.data this) 1))

(defn inc-count [this]
  (dosync (swap! ((.data this) 1) #(inc %))))

(defn dec-count [this]
  (dosync (swap! ((.data this) 1) #(dec %))))

(defn- -read [this]
  (let [c (.read (input this))]
    (if (= c org.netbeans.spi.lexer.LexerInput/EOF)
      -1
      (do (inc-count this)
      c))))

(defn- -unread [this ch]
  (dec-count this)
  (.backup (input this) 1))

(defn- -close [this])
