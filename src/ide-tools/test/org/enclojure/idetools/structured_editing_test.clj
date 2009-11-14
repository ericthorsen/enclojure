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
        :doc "Protocol for org.enclojure.idetools.structured-editing-test"}
 org.enclojure.idetools.structured-editing-test
  (:require [org.enclojure.idetools.matchers :as matchers]
      [org.enclojure.commons.c-slf4j :as logger])
  (:import
    (java.io File OutputStreamWriter FileOutputStream StringReader)
        (org.enclojure.flex _Lexer ClojureSymbol)
        (Example ClojureSym ClojureParser)
        (org.enclojure.idetools PositionalPushbackReader)
        (javax.swing JFrame JScrollPane WindowConstants JEditorPane SwingUtilities)
        (java.awt EventQueue)
        (java.awt.event WindowAdapter WindowEvent)
        (javax.swing.event DocumentListener DocumentEvent)
        (javax.swing.text DefaultStyledDocument)
        (java.util.logging Level Logger)))

; setup logging
(logger/ensure-logger)

(defn cleanup-onclose
  "Add a window listener to a JFrame to shutdown the repl-servers
and exit the app on close."
  [jframe]
  (.setDefaultCloseOperation jframe WindowConstants/DISPOSE_ON_CLOSE)
  (.addWindowListener jframe
    (proxy [WindowAdapter][]
      (windowClosed [e]
        (.removeDocumentListener (.getDocument jframe))        
        (System/exit 0))
        )))

(defn match-pairs
  [document lexer]
  (try
    (.yyreset lexer
      (StringReader. (.getText document 0 (.getLength document))))
    (if-let [edits (matchers/get-fix-pairs-fns lexer)]
      (matchers/apply-edits document edits)
      document)
    (catch Throwable e
      (logger/error "Exception in match-pairs {}" (.getMessage e)))))

(defn get-listener
  [editor-pane]
  (let [-lexer- (_Lexer. (StringReader. ""))]
    (proxy [DocumentListener] []
        (changedUpdate [#^DocumentEvent e])
        (insertUpdate [#^DocumentEvent e]
          (println "insert " e " t:" (bean e))
          (matchers/check-pair-for-doc editor-pane e
            matchers/find-matching-pairs-in-doc))
        (removeUpdate [#^DocumentEvent e])
      )))

(defn refresh-listener
  [{:keys [frame editor doc listener] :as m}]
  (.removeDocumentListener doc listener)
  (let [ret  (assoc m :listener (get-listener editor))]
    (.addDocumentListener doc (:listener ret))
    ret))

(defn editor
  []
   (let [frame (JFrame. "Structured editing test.")
         editor-pane (JEditorPane.)
         scroll-pane (JScrollPane. editor-pane)
         docd (DefaultStyledDocument.)
         doc-listener (get-listener editor-pane)]
        (.setDocument editor-pane docd)
        (.setLayout frame (java.awt.GridLayout. 1 1))
        (.add frame scroll-pane)
        (.addDocumentListener docd doc-listener)
     (EventQueue/invokeLater
      #(do
         (.setSize frame 1000 1000)
         (.setVisible frame true)
         (cleanup-onclose frame)))
     {:frame frame :editor editor-pane :doc docd :listener doc-listener}))

