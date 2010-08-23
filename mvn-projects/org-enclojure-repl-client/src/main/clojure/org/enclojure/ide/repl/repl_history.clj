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
        :doc "Support code for managing history for a REPL instance.
        There is a log which stores only unique commands (uses a hash of the expression)
        and there is a list used for history at the command line of the REPL.  Here
        dups are allowed but it won't repeat the last command.  Assume the following
        commands are typed in and executed in the REPL:
        (+ 1 1)
        (/ 23 1)
        (+ 1 1)
        (+ 1 1)
        The command line history list will contain:
        (+ 1 1)
        (/ 23 1)
        (+ 1 1)
        The history log will contain:
        (+ 1 1)
        (/ 23 1)

        There are 2 functions that need to be implemented in the IReplWindow
        interface that are used by this module for displaying the log.
            [showHistory [] java.awt.Component]
            [getHistoryLogFile [] java.lang.String]
        See the org.enclojure.ide.repl.interface-factory for definitions of these
        classes.
        "}
       org.enclojure.ide.repl.repl-history
  (:require [org.enclojure.commons.c-slf4j :as logger])
    (:import (java.util.logging Level Logger)      
            (java.awt.event KeyEvent)))
            
; setup logging
(logger/ensure-logger)

(defn- new-history
  "A blank history data item or you can pass in a list of items.
These functions just help out with the management
of navigating and keeping track of where you are in a list."
  ([]
  {:index nil
    :history-list []
   :forms-set #{}})
  ([history-list]
    (let [hlist (if (and history-list 
                      (pos? (count history-list)))
                  history-list [])
          has-elements? (pos? (count hlist))]
    {:history-list hlist
     :index (when has-elements? (count hlist))
     :forms-set (if has-elements?
                    (reduce conj #{} hlist)
                  #{})
     })))

(defn new-history-ref
  "A blank history data item or you can pass in a list of items.
These functions just help out with the management
of navigating and keeping track of where you are in a list."
  [& args]
    (ref
      (apply new-history args)
      :validator #(= 3 (count (select-keys %1 [:index :history-list :forms-set])))))    

(defn check-bounds
  [{:keys [index history-list] } inx]
  (let [c (count history-list)]
    (when (and inx index history-list
            (>= inx 0) (< inx c)))))
  
(defn nav-index 
  "Move the index of the history item based on the key-event"
  [history key-code]
  (let [{:keys [index history-list] } history]
    (if index
        (let [c (dec (count history-list))]
          (merge history
            {:index
               ({KeyEvent/VK_DOWN (min (inc index) c)
                 KeyEvent/VK_UP (max 0 (dec index))}
                    key-code)}))
      history)))

(defn nav-history
  "Based on the key-event, return the next history item"
  [history key-event]  
  (let [{:keys [index history-list] } history]
    (if (and (pos? (count history-list))
          (.isControlDown key-event) 
          (#{KeyEvent/VK_UP KeyEvent/VK_DOWN} (.getKeyCode key-event)))
      (do
        (.consume key-event)
        (nav-index history (.getKeyCode key-event)))
      history)))

(defn get-current-item [history]
  (let [{:keys [index history-list]} history]
    (when (and index history-list)
        ((:history-list history) index))))

(defn add-history-item 
  "Add the form to history unless it is a duplicate of the tail of the list.
    The index of the list is always set to the last item after this function is called."
  [{:keys [index history-list forms-set] :as history} form]
  (if (or (nil? index)
        (not= form (last history-list)))
    (assoc history
        :history-list (conj history-list form)
        :index (inc (count history-list)))
    (assoc history :index (count history-list))))

