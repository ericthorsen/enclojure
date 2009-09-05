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
;*    Author: Eric Thorsen
;*******************************************************************************
)

(ns org.enclojure.ide.navigator.analyze
  (:use
    org.enclojure.ide.nb.actions.token-navigator
    org.enclojure.ide.navigator.token-nav
    clojure.inspector clojure.set
    )
  (:require
    [clojure.contrib.repl-utils :as ru]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (javax.swing JEditorPane)
    (java.util.logging Level)
    (java.awt BorderLayout EventQueue)
    (java.awt.event ActionEvent ActionListener)
    (javax.swing.tree TreeModel DefaultTreeCellRenderer)
    (javax.swing.table TableModel AbstractTableModel)
    (javax.swing JPanel JTree JTable JScrollPane JFrame JToolBar JButton SwingUtilities)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token TokenHierarchy)
    (org.netbeans.editor BaseDocument)
    (org.openide.windows TopComponent)
    (org.openide.cookies EditorCookie)
    (org.openide.util ImageUtilities)))

; setup logging
(logger/ensure-logger)

(defn load-icon [n]
  (ImageUtilities/loadImage
    (str "org/enclojure/ide/navigator/resources/" n)
    true))

(defn read-form [doc {:keys [start end]}]
  (let [text (.getText doc start (- end start))]
        {:form test}))

;        form (read-string text)]
;    (merge {:form form}
;         (when (= 'comment (first form))
;                {:text text}))))

(defn get-forms
  "Given a Document, finds all valid forms within the doc.
Returns a vector with the form and start and end indexes into the document.
It is tolerant of incomplete forms."
  ([doc]
  (let [doc-length (.getLength doc)
        token-seq (.tokenSequence (TokenHierarchy/get doc))]
    (when (pos? doc-length)
      (loop [last-position {:end 0} forms []]
        (let [end (inc (:end last-position))]
          (if (< end doc-length)
            (let [form-pos (get-top-enclosing-form token-seq end)]
              (if (and form-pos
                    (not= form-pos last-position))
                (recur form-pos
                  (conj forms (merge {:pos form-pos}
                                (try
                                    (read-form doc form-pos)))))
                (recur (assoc last-position :end (inc end)) forms)))
            forms)))))))

(defn as-keyword [s]
  (keyword (if (= \: (.charAt s 0)) (subs s 1) s)))

(defn token-type [token]
    (as-keyword (-> token .id .name)))

(defn next-token [token-seq]
  (when (.moveNext token-seq)
    (.token token-seq)))

(defn do-delimeter-stack [stack token]
  (condp = (token-type token)
    :list-start (conj stack token)
    :list-end (pop stack)
    stack))

(defmulti walk-form
  (fn
    ([doc]
      (println "dispatch doc")
      (when (instance? BaseDocument doc)
            :doc-start))
    ([token-seq token]
      (println "seq token ->" (token-type token))
      (if-let [type (token-type token)]
          (if (= :list-start type)
            :top-form :scanning)))
    ([token-seq tokens delims form]
      (println "inner-form ->" (token-type (first tokens)))
      :inner-form)))

(defmethod walk-form
  :inner-form
  [token-seq tokens delims form]
  (println "inner-form " (when-let [t (first tokens)] (.text t)))
  (let [token (first tokens)
        delims (do-delimeter-stack delims token)]
    (println "inner " (count delims) token "<->" delims)(flush)
      (if (zero? (count delims))
        (merge form {:end (.index token-seq)
                     :text (apply str
                             (map #(.text %1) tokens))})
        (if-let [t (next-token token-seq)]
            (walk-form token-seq (conj tokens t) delims form)
          (throw (Exception. "inner-form incomplete!"))))))

(defmethod walk-form
  :top-form
  [token-seq token]
  (println "top-form" (when token (.text token)))
  (let [start (.index token-seq) ; store the start of this form
        form-tokens (reduce (fn [c _]
                              (conj c (next-token token-seq)))
                            [token] (range 3)) ; Take the list start, type, name and possible doc-str
        [start-tk type-tk name-tk doc-tk :as tk-indexes]
            (reduce (fn [c i]
                      (conj c #(nth form-tokens i))) [] (range 4)) ; some helper functions for readability
        doc-str (when (= (token-type (doc-tk)) :string)
                  (.text (doc-tk)))] ; Is there a doc string?
    (walk-form token-seq
      (if doc-str (conj form-tokens (next-token token-seq)) form-tokens)
       [(first form-tokens)]
         {:start start :type (type-tk) :name (name-tk) :doc (doc-tk)})))


(defmethod walk-form
  :doc-start
  ([doc]
    (println "doc-start")
    (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get doc))]
        (loop [next-form (walk-form ts (next-token ts)) forms []]
          (if next-form
            (recur (walk-form ts (next-token ts)) (conj forms next-form))
            forms)))))


(defmethod walk-form
  :default
    ([& args]
      (throw (Exception. (apply str "default called with " (count args) " args\n "
                           (next args))))))

(def -icons-
    {   'ns "package.gif"
        'defmacro "fqn.gif"
        'defn "static.gif"
        'def "decalration_action.png"
        'comment "inherited.gif"
        'defmulti "filterHideInherited.png"
        'defmethod "filterHideStatic.png"
        })

(defn swap-icon [renderer sym]
  (try
    (let [icon (load-icon (sym -icons-))]
        (.setLeafIcon renderer icon)
        (.setOpenIcon renderer icon)
        (.setClosedIcon renderer icon))
    (catch Throwable t
      (logger/error (.getMessage t))))
  renderer)

(defmulti get-cell-renderer
    (fn [this tree value selected expanded leaf row has-focus]
            (first (:form value))))

(defmethod get-cell-renderer 'ns
  [this tree value selected expanded leaf row has-focus]
    (swap-icon this 'ns))

(defmethod get-cell-renderer 'defmacro
  [this tree value selected expanded leaf row has-focus]
    (swap-icon this 'defmacro))

(defmethod get-cell-renderer 'defn
  [this tree value selected expanded leaf row has-focus]
    (swap-icon this 'defn))

(defmethod get-cell-renderer :default
  [this tree value selected expanded leaf row has-focus]
    (swap-icon this 'comment))

(defn get-cell-renderer-proxy []
  (proxy [DefaultTreeCellRenderer] []
    (getTreeCellRendererComponent
      [tree value selected expanded leaf row has-focus]
            (get-cell-renderer this tree value selected expanded leaf row has-focus)
      (proxy-super getTreeCellRendererComponent tree value selected expanded leaf row has-focus))))


 ;       (let [ret (proxy-super getTreeCellRendererComponent tree value selected expanded leaf row has-focus)]

(defmulti as-text (fn [v] (first (:form v))))

(defmethod as-text :default [v]
  (let [form (:form v)
        sym (fnext form)
        m (meta sym)]
    (str "(" (first form) " " sym (:arglists m) ")")))

(defmethod as-text 'comment [v]
  (str "(comment " (subs (:text v) 040)
    (when (> (count (:text v)) 40)
      "...") ")"))

(defn get-tree-proxy []
  (proxy [JTree] []
    (convertValueToText [value selected expanded leaf row hasFocus]
       (as-text value))))

(defn navigator
  [title data]
  (doto (JFrame. title)
    (.add
      (doto (JPanel. (BorderLayout.))
        (.add
          (JScrollPane.
            (let [jtree (get-tree-proxy)]
              (.setModel jtree (tree-model data))
              (.setCellRenderer jtree (get-cell-renderer-proxy))
              ;(.setCellRenderer jtree (DefaultTreeCellRenderer.))
              jtree))
          BorderLayout/CENTER)))
    (.setSize 400 400)
    (.setVisible true)))

(defn testthis []
  (navigator "fred" (get-forms (.getDocument (current-editor-pane)))))

(defmulti token-nav
  (fn [ts]
    (if (.moveNext ts)
       (token-type (-> ts .token))
       :stream-end)))

(defmethod token-nav
  :stream-end
  [ts]
  (println "This story is finished")(flush)
  nil)

(defmethod token-nav
  :list-start
  [ts]
  (println "start-list.....................")(flush)
  (-> ts .token))

(defmethod token-nav
  :list-end
  [ts]
  (println "end-list.....................")(flush)
  (-> ts .token))

(defmethod token-nav
  :default
  [ts]  (-> ts .token))

(defn help []
  (let [ts (.tokenSequence (TokenHierarchy/get (.getDocument (current-editor-pane-awt))))]
  (loop [ts ts]
    (if (.moveNext ts)
      (let [token (-> ts .token)
            type (token-type token)
            text (.text token)]
        (print "type = " (or type "!!YIKES!NO TYPE!!!") "$%$% "
          (if (= "" text) "!!NO TEXT!!!" text))
        (flush)
        (recur ts))))))

(defn help2 []
  (let [ts (.tokenSequence (TokenHierarchy/get (.getDocument (current-editor-pane-awt))))]
  (loop [token (token-nav ts)]
    (if token
      (recur (token-nav ts))))))

