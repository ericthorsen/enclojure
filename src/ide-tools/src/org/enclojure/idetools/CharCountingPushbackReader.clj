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
        :doc "Protocol for org.enclojure.idetools.CharCountingPushbackReader"}
 org.enclojure.idetools.CharCountingPushbackReader
      (:gen-class
        :extends java.io.PushbackReader
        :state counter
        :constructors {[java.io.Reader] [java.io.Reader]}
        :init ctor
        :methods [[getPosition [] Integer]]
        :exposes-methods {read superRead unread superUnread}
        )
  (:import (java.io IOException LineNumberReader PushbackReader
             Reader LineNumberReader)))

(defn -ctor [reader]
  [[(LineNumberReader. reader)] (atom 0)])

(defn -getLineNumber[this] (inc (-> this .in .getLineNumber)))

(defn -read [this]
    (let [c (.superRead this)]
        (swap! (.counter this) inc)
      c))

(defn -read [this buff offset len]
  (let [c (.superRead this buff offset len)]
   (swap! (.counter this) + c)
  c))


(defn -unread [this c]
    (.superUnread this c)
    (swap! (.counter this) dec))

(defn -unread [this buff offset len]
  (let [c (.superUnread this buff offset len)]
   (swap! (.counter this) - c)
  c))
   
(defn -getPosition [this] @(.counter this))