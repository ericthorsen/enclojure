(comment
;*******************************************************************************
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    GNU General Public License, version 2
;*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
;*    exception (http://www.gnu.org/software/classpath/license.html)
;*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
;*    of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*******************************************************************************
;*    Author: Narayan Singhal
;*******************************************************************************
)

(ns org.enclojure.ide.navigator.token-nav
  (:use org.enclojure.ide.ClojureLexer)
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (javax.swing JEditorPane)
    (java.util.logging Level)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)))

; setup logging
(logger/ensure-logger)

(def start-tokens [:map-start :vec-start :list-start])
(def end-tokens [:map-end :vec-end :list-end])

(defn start-token? [#^TokenSequence ts]
  (let [token-type (keyword (-> ts .token .id .name))]
    (some #(= token-type %) start-tokens)))

(defn end-token? [#^TokenSequence ts]
  (let [token-type (keyword (-> ts .token .id .name))]
    (some #(= token-type %) end-tokens)))

(defn move-previous
  "Move backward until the predicate is satisfied. Return true on success else nil"
  [pred #^TokenSequence ts]
  (loop []
    (when (.movePrevious ts)
      (if (pred ts) true (recur)))))

(defn move-next
  "Move forward until the predicate is satisfied. Return true on success else nil"
  [pred #^TokenSequence ts]
  (loop []
    (when (.moveNext ts)
      (if (pred ts) true (recur)))))

(defn move-start-form
  "Traverse backward until a start of the expression is found. It is making sure
   that it walk past any complete form while looking for an unclosed open form."
  [#^TokenSequence ts]
  (let [token-counter 1]
    (loop [token-counter token-counter]
      (if (zero? token-counter) true
        (when (.movePrevious ts)
          (if (end-token? ts) (recur (inc token-counter))
            (if (start-token? ts) (recur (dec token-counter))
              (recur token-counter))))))))

(defn move-end-form
  "Traverse forward until a start of the expression is found. It is making sure
   that it walk past any complete form while looking for an unopened close form."
  [#^TokenSequence ts]
  (let [token-counter 1]
    (loop [token-counter token-counter]
      (if (zero? token-counter) true
        (when (.moveNext ts)
          (if (start-token? ts) (recur (inc token-counter))
            (if (end-token? ts) (recur (dec token-counter))
              (recur token-counter))))))))

;(defn count-leading-spaces [ts]
;  (let [token-text (-> ts .token .text)]
;    (loop [count 0]
;      (if (= (.charAt token-text count) \space)
;        (recur (inc count))
;        count))))

; patch from Dar’o Macchi - 2009-11-17
 (defn count-leading-spaces [ts]
   (let [token-text (-> ts .token .text)]
     (loop [count 0]
    (let [count-char (.charAt token-text count)]
        (if (or (= count-char \space) (= count-char \newline))
          (recur (inc count))
          count)))))

(defn get-enclosing-form [#^TokenSequence ts offset]
  (.move ts offset)
  (when (move-start-form ts)
    (when (.token ts)
      (let [start-index (+ (.offset ts) (count-leading-spaces ts)) ;Exclude spaces
            _ (.move ts offset)
            _ (move-end-form ts)
            end-token (.token ts)]
        (when (and end-token (-> end-token .text str .trim count pos?))
          {:start start-index :end (+ (.offset ts) (.length end-token))})))))

(defn get-top-enclosing-form [#^TokenSequence ts offset]
  (loop [expr-pos (get-enclosing-form ts offset)]
    (when expr-pos
      (let [next-pos (get-enclosing-form ts (:end expr-pos))]
        (if-not next-pos expr-pos
          (recur next-pos))))))

(defn select-current-form [#^JEditorPane pane]
  (let [caret-pos (.getCaretPosition pane)
        #^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        position (get-enclosing-form ts caret-pos)]
    (when position
      (.setCaretPosition pane (:start position))
      (.moveCaretPosition pane (:end position)))))

(defn find-top-form-doc
  ([doc last-position]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get doc))
        position (get-enclosing-form ts (inc (:end last-position)))]
    (if position
        (recur doc position)
      last-position)))
  ([doc]
    (find-top-form-doc doc {:end 0})))


(defn find-top-form
  ([#^JEditorPane pane last-position]
  (let [caret-pos (.getCaretPosition pane)
        #^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        position (get-enclosing-form ts caret-pos)]
    (if position
      (do
        (.setCaretPosition pane (:start position))
        (recur pane position))
      last-position)))
  ([#^JEditorPane pane]
    (find-top-form pane nil)))

(defn select-top-form
  ([#^JEditorPane pane last-position]
    (when-let [position (find-top-form pane last-position)]
        (.setCaretPosition pane (:start position))
        (.moveCaretPosition pane (:end position))))
  ([#^JEditorPane pane]
    (select-top-form pane nil)))


(defn get-top-form-text [#^JEditorPane pane]
  (let [caret-pos (.getCaretPosition pane)
        #^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        position (get-top-enclosing-form ts caret-pos)]
    (when position
        (.getText (.getDocument pane) (:start position) (- (:end position) (:start position))))))

(defn nav-current-form-start [#^JEditorPane pane]
  (let [caret-pos (.getCaretPosition pane)
        #^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        position (get-enclosing-form ts caret-pos)]
    (when position
      (.setCaretPosition pane (:start position)))))

(defn nav-current-form-end [#^JEditorPane pane]
  (let [caret-pos (.getCaretPosition pane)
        #^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        position (get-enclosing-form ts caret-pos)]
    (when position
      (.setCaretPosition pane (:end position)))))

(defn token-match [type text ts]
  (let [token-type (keyword (-> ts .token .id .name))
        token-text (-> ts .token .text .toString .trim)]
    (= token-text text)))    

(defmulti get-namespace-node class)

(defmethod get-namespace-node :default
  [pane]
  (get-namespace-node (.getDocument pane)))

(defmethod get-namespace-node javax.swing.text.Document  
  [document]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get document))
        ns-pred #(or (token-match :ns-publics "ns" %1)
                   (token-match :ns-publics "in-ns" %1))]
    (.moveStart ts)
    (when (move-next ns-pred ts)
      (when-let [position (get-top-enclosing-form ts (.offset ts))]
        (.getText document (:start position)
          (- (:end position) (:start position)))))))

(defmethod get-namespace-node javax.swing.text.AbstractDocument
  [document]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get document))
        ns-pred #(or (token-match :ns-publics "ns" %1)
                   (token-match :ns-publics "in-ns" %1))]
    (.moveStart ts)
    (when (move-next ns-pred ts)
      (when-let [position (get-top-enclosing-form ts (.offset ts))]
        (.getText document (:start position)
          (- (:end position) (:start position)))))))

(defn get-namespace [pane]
  (when-let [nsnode (get-namespace-node pane)]
    (str (second (read-string nsnode)))))

(defn get-top-level-forms
  ([#^org.netbeans.api.lexer.TokenSequence token-seq next-token]
    (try
      (loop [brace-stack [] offset 0 forms [] more? (next-token)]
        (if more?
          (let [#^org.netbeans.api.lexer.Token token (.token token-seq)
                id (-> token .id .name)]
            (cond (= "list-start" id) 
                (recur (conj brace-stack token) 
                  (if (zero? (count brace-stack))
                    (.offset token-seq) offset) forms (next-token))
              (= "list-end" id) 
                (cond
                  (= 1 (count brace-stack))
                    (recur [] (inc (.offset token-seq))
                        (conj forms {:start offset :end (.offset token-seq)})
                       (next-token))
                  (> (count brace-stack) 1)
                    (recur (pop brace-stack) offset forms  (next-token)))
              :else 
                  (recur brace-stack offset forms  (next-token))))
          forms))
      (catch Throwable t
        (logger/warn (.getMessage t)))))
  ([#^org.netbeans.api.lexer.TokenSequence token-seq]
      (let [next-token #(.moveNext token-seq)]
        (.moveStart token-seq)
        (get-top-level-forms token-seq next-token)))
  ([#^org.netbeans.api.lexer.TokenSequence token-seq start-offset end-offset]
    (.move token-seq start-offset)
    (let [next-token #(and (.moveNext token-seq) (<= (.offset token-seq) end-offset))]
        (get-top-level-forms token-seq next-token))))



