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
  (:import
    (java.io File OutputStreamWriter FileOutputStream)
        (javax.swing JFrame JScrollPane WindowConstants JEditorPane)
        (java.awt EventQueue)
        (java.awt.event WindowAdapter WindowEvent)
        (javax.swing.event DocumentListener DocumentEvent)
        (javax.swing.text DefaultStyledDocument)))


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

(defn get-listener
  [d]
  (proxy [DocumentListener] []
    (changedUpdate [#^DocumentEvent e]
      (println "change " e " t:" (bean e) )
      )
    (insertUpdate [#^DocumentEvent e]
      (println "update " e " t:" (bean e))
      )
    (removeUpdate [#^DocumentEvent e]
      (println "remove " e " t:" (bean e))
      )))

(defn refresh-listener
  [{:keys [frame editor doc listener] :as m}]
  (.removeDocumentListener doc listener)
  (let [ret  (assoc m :listener (get-listener doc))]
    (.addDocumentListener doc (:listener ret))
    ret))

(defn editor
  []
   (let [frame (JFrame. "Inproc REPL Frame")
         editor-pane (JEditorPane.)
         scroll-pane (JScrollPane. editor-pane)
         docd (DefaultStyledDocument.)
         doc-listener (get-listener docd)]
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

