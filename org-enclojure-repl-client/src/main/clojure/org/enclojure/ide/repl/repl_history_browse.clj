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

(ns org.enclojure.ide.repl.repl-history-browse
  (:require [org.enclojure.ui.controls :as controls]
    [org.enclojure.ide.repl.repl-manager :as repl-manager]
    [org.enclojure.commons.c-slf4j :as logger]   
    )
  (:import  
    (java.util.logging Level)
    (javax.swing JList ListModel JLabel JPanel JTree JTable JScrollPane
            JFrame JToolBar JButton SwingUtilities JViewport JTextPane
      JEditorPane ImageIcon ListCellRenderer)
    (javax.swing.event ListSelectionListener)
    (javax.swing JViewport)
    (javax.swing.text DefaultStyledDocument)
    (java.awt BorderLayout EventQueue Toolkit)
    (java.awt.event MouseListener MouseAdapter MouseEvent)
    (org.enclojure.ide.repl ReplHistory)
    (java.io StringWriter File OutputStreamWriter FileOutputStream
        BufferedReader FileReader)
    (java.util Vector)))

; setup logging
(logger/ensure-logger)

(def -form-prefix- ";{:first-seen ")

(defn -form-delimiter-
  []
  (str -form-prefix-
    (.getTime (java.util.Calendar/getInstance)) "};-------------------------------------"))

(defn get-repl-command-log-file
  "Helper function to return the File object for the repl-commands history"
  [repl-id]
  (let [irepl (repl-manager/get-IRepl repl-id)]
    (when irepl
        (File. (-> irepl .getReplWindow .getHistoryLogFile)))))

(defn log-command
  [repl-id form]
  (let [history-ref (:history-ref (repl-manager/get-repl-config repl-id))]
    (logger/info  "logging {}" form)
    (when-not ((:forms-set @history-ref) form)
      (dosync 
        (alter history-ref
            #(update-in %
                [:forms-set] conj form)))
        (with-open [out (OutputStreamWriter.
                    (FileOutputStream.
                      (get-repl-command-log-file repl-id) true))]
        (binding [*out* out]
          (println (-form-delimiter-) \newline (.trim form)))))))

(defn get-history
  [repl-id]
  (let [file (get-repl-command-log-file repl-id)]
    (when (.exists file)
        (with-open [f (BufferedReader.
                  (FileReader. (get-repl-command-log-file repl-id)))]
        (loop [line (.readLine f) lines [] forms []]
          (let [adder-fn #(if (pos? (count lines))
                            (conj forms (apply str ; have to reconstitute the \newlines
                                          (interpose "\n" lines))) forms)]
          (if line
            (if (.startsWith line -form-prefix-)
              (recur (.readLine f) [] (adder-fn))
              (recur (.readLine f) (conj lines (.trim line)) forms))
            (adder-fn))))))))

(defn clear-history
  [repl-id]
  (with-open [out (OutputStreamWriter.
                    (FileOutputStream.
                      (get-repl-command-log-file repl-id) false))]
        (binding [*out* out]
          (println ""))))

(defn get-cell-renderer []
  (proxy [JTextPane ListCellRenderer] []
    (getListCellRendererComponent
      [list value index selected? has-focus?]
        (.setText this value)
      (doto this
        (.setBackground
            (if selected? (.getSelectionBackground list)
              (.getBackground list)))
          (.setForeground
            (if selected? (.getSelectionForeground list)
              (.getForeground list)))
          (.setEnabled (.isEnabled list))
          (.setFont (.getFont list))
          (.setOpaque true))
      this)))

(defn on-mouse-click [#^MouseEvent e lst func]
    (when (= 2 (.getClickCount e))
      (func lst (.locationToIndex lst (.getPoint e)))))

(defn get-mouse-listener [lst func]
  (proxy [MouseAdapter] []
    (mouseClicked [#^MouseEvent e]
      (on-mouse-click e lst func))))

(def -editor-pane- (ref nil))
(def -styled-doc- (ref nil))

(defn build-history-text
  [history-list]
  (binding [*out* (StringWriter.)]
    (doall (map #(println % \newline) history-list))
    (.toString *out*)))

(defn create-repl-history-browse [jpanel select-fn history-list]
  (let [data-ref (atom {})
        repl-browser (ReplHistory.)        
        alpha-sort-btn (.alphaSortButton repl-browser)
        source-sort-btn (.sortTimeButton repl-browser)]
    (dosync
      (alter -editor-pane-
              (fn [_] (JEditorPane.)))
      (alter -styled-doc-
        (fn [_] (DefaultStyledDocument.))))
    (doto @-editor-pane-
      (.setContentType "text/x-clojurerepl")
      (.setName "Repl history")
      (.setDoubleBuffered true))
    (println (build-history-text history-list))
;    (.insertString @-styled-doc-
;      0 (build-history-text history-list) nil)
    (.insertString (.getDocument @-editor-pane-)
          0 (build-history-text history-list) nil)
    ;(.setDocument @-editor-pane- @-styled-doc-)
    (let [vp (JViewport.)]
      (.setView vp @-editor-pane-)
        (.setViewport
            (.jScrollPane repl-browser) vp))
    (doto jpanel
      (.add
        repl-browser
        BorderLayout/CENTER))))

(defn show-history-fn [title history-list]
  (let [jframe (JFrame. title)]
    (create-repl-history-browse
      jframe
      (fn [l i] (.close jframe)) history-list)
    (controls/center-component jframe)
    (.show jframe)))

(defn show-history [repl-id]
  (let [irepl (repl-manager/get-IRepl repl-id)]
    (.showHistory (.getReplWindow irepl))))

(defn test-browser []
  (let [jframe (JFrame. "Testing the repl history")]
    (create-repl-history-browse jframe 
      (fn [l i] (.setSelectedIndex l 0)))
    (controls/center-component jframe)
    (.show jframe)))