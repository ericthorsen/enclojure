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
;*    Author: Eric Thorsen, Narayan Singhal
)

(ns org.enclojure.ide.repl.repl-panel
 (:refer-clojure :exclude (with-bindings))
 (:use org.enclojure.ide.repl.repl-manager
       org.enclojure.ide.repl.repl-history
       org.enclojure.repl.main
       )
 (:require [org.enclojure.ide.repl.repl-history-browse :as repl-history-browse]
            [org.enclojure.commons.meta-utils :as meta-utils]
            [org.enclojure.commons.c-slf4j :as logger]            
   )
 (:import (java.util.logging Logger Level)
      (org.enclojure.ide.repl ReplPanel)
      (java.io File OutputStreamWriter FileOutputStream)
      (java.awt.event KeyEvent)
      (java.awt EventQueue)))

(def #^{:private true} EOF (Object.))
; setup logging
(logger/ensure-logger)
(.setLevel (Logger/getLogger (-> *ns* ns-name str)) Level/FINEST)


(defn bind-editor-pane 
  "given a repl-panel and editor pane and a result function, wire them up"
  [panel pane result-fn]
  (let [awt-fn #(EventQueue/invokeAndWait
                  (fn [] 
                    (logger/info "Rcved {}\n" %)
                    (.resultReceived panel pane %)))]
        (start-io-thread
          #(awt-fn (result-fn))
          (fn [cause]
            (awt-fn "\nRepl is disconnected\n"))))
        panel)

(defn bind-repl-panel
  "Bind the ReplPanel to the repl function and the result function"
  [panel repl-fn result-fn]
  (.setReplFunction panel repl-fn)
  (bind-editor-pane panel (._replEditorPane panel) result-fn))

(defn bind-process-panel
  "Bind the err and out tab of the repl to the output and error of the process"
  [panel out-fn err-fn]
  (bind-editor-pane panel (.jEditorPaneOut panel) out-fn)
  (bind-editor-pane panel (._replErrorPane panel) err-fn)
  panel)

(defn create-repl-editor-pane [panel]
(let [editor-pane
          (proxy [javax.swing.JEditorPane] []
            (cut []
              (let [sel-start (.getSelectionStart this)
                    sel-end (.getSelectionEnd this)
                    prompt-pos (._promptPos panel)]
                (when (and (not= sel-start sel-end) (>= sel-end sel-start prompt-pos))
                  (proxy-super cut))))
            (copy []
              (proxy-super copy))
            (paste []
              (let [sel-start (.getSelectionStart this)
                    sel-end (.getSelectionEnd this)
                    ;caret-pos (.getCaretPosition this)
                    prompt-pos (._promptPos panel)
                    doc-length (.getLength (.getDocument this))]
                (when-not (and (< sel-start prompt-pos) (> sel-end prompt-pos))
                  (when (< sel-start prompt-pos)
                    (.setCaretPosition this doc-length))
                  (proxy-super paste)))))]
                  (doto editor-pane
                    (.setContentType "text/x-clojurerepl")
                    (.setName "Repl editor pane")
                    (.setDoubleBuffered true))))

;----------------------------------------------------------------------------
; Functions for managing repl history
;----------------------------------------------------------------------------
(defn clear-history [repl-id]
  (let [history-ref (new-history-ref)]
    (dosync (alter history-ref assoc :repl-id repl-id))
    (update-repl repl-id :history-ref history-ref)
    (repl-history-browse/clear-history repl-id)
    history-ref))

(defn load-history [repl-id]
  (let [history-ref (new-history-ref
                        (repl-history-browse/get-history repl-id))]
    (dosync (alter history-ref assoc :repl-id repl-id))
    (update-repl repl-id :history-ref history-ref)    
    history-ref))

(defn get-history-ref [repl-id]
  ;{:pre [(not (nil? repl-id))] } ;:post [(vector? %)]}
  (when-not (:history-ref (get-repl-config repl-id))
    (load-history repl-id))
  (:history-ref (get-repl-config repl-id)))

(defn history-event [history-ref repl-pane prompt-pos key-event]
  (dosync
    (alter history-ref
      #(org.enclojure.ide.repl.repl-history/nav-history %1 %2)
      key-event))
  (let [form (org.enclojure.ide.repl.repl-history/get-current-item @history-ref)
        d (.getDocument repl-pane)]
    (when form
      (try
        (.remove d prompt-pos (- (.getLength d) prompt-pos))
        (.insertString d (.getLength d) form nil)
        (catch Exception e
          (logger/error  (.getMessage e)))))))

(defn put-history-item [history-ref form]
  (let [history-list (:history-list @history-ref)
        n (count history-list)]
    (dosync
        (alter history-ref
            #(add-history-item %1 %2) form))
    (logger/debug  "b4 = " n " after " (count (:history-list @history-ref)))
    {:added (not= n (count (:history-list @history-ref)))
     :history @history-ref}))
  

;----------------------------------------------------------------------------
; Functions for sending data to repl-queue and key processing
;----------------------------------------------------------------------------
(defn empty-string? [string]
  (nil? (seq (filter #(not (java.lang.Character/isWhitespace %)) string))))

(defn build-expr
  [print-pretty? nsnode expr]
  (let [wrapped-expr (str "(do " expr ")")]
    (if nsnode
      (format "(binding [*ns* *ns*] %s (eval '%s))"
        nsnode wrapped-expr)
      (format "(eval '%s)" wrapped-expr))))

(defn evaluate-in-repl
  ([repl-id expr ns-node]
    (let [{:keys [repl-fn repl-panel] print-pretty
           'clojure.contrib.pprint/*print-pretty*} (get-repl-config repl-id)          
          expr (build-expr print-pretty ns-node expr)]
      (logger/debug  "\neval expr:{}\n" expr)
      (.resultReceived repl-panel (._replEditorPane repl-panel) "\n")
      (if repl-fn
        (repl-fn (str expr "\n"))
        (.resultReceived repl-panel (._replEditorPane repl-panel) "Repl is disconnected\n"))))
  ([repl-id expr]
    (evaluate-in-repl repl-id expr nil)))

(defn evaluate-in-repl2
  ([repl-id expr ns-node]
    (let [{:keys [repl-fn repl-panel] print-pretty 
           'clojure.contrib.pprint/*print-pretty*} (get-repl-config repl-id)
          expr (if ns-node
                 (if print-pretty
                   (str "(binding [*ns* *ns*]" ns-node
                            "(eval (clojure.contrib.pprint/pprint (do " 
                                (pr-str expr) "\n))))")
                   (str "(binding [*ns* *ns*]" ns-node  "(eval '(do "
                        (pr-str expr) "\n)))"))
                 (if print-pretty
                   (str "(eval '(clojure.contrib.pprint/pprint (do " 
                     (pr-str expr) "\n)))")
                 (str " " (pr-str expr) " \n")))
          ]
      (logger/debug  "\neval expr:\n{}" (pr-str expr))
      (.resultReceived repl-panel (._replEditorPane repl-panel) "\n")
      (if repl-fn
        (repl-fn expr)
        (.resultReceived repl-panel (._replEditorPane repl-panel) "Repl is disconnected\n"))))
  ([repl-id expr]
    (evaluate-in-repl repl-id expr nil)))

(defn set-print-pretty
  [repl-id repl-pane new-val]
  (let [bv (boolean new-val)]
    (logger/debug  "set pretty print to {}" bv)
  (update-repl repl-id 'clojure.contrib.pprint/*print-pretty* bv)
  (evaluate-in-repl repl-id
    (str "(set! clojure.contrib.pprint/*print-pretty* " bv ")"))
  (.setSelected (.prettyPrintToggleButton repl-pane) bv)))
  

(defn set-print-stack-trace-on-error
  [repl-id repl-pane new-val]
  (let [bv (boolean new-val)]
    (logger/info  "set print stack trace to " bv)
  (update-repl repl-id 'org.enclojure.repl.main/*print-stack-trace-on-error* bv)
  (evaluate-in-repl repl-id
    (str "(set! org.enclojure.repl.main/*print-stack-trace-on-error* " bv ")"))
    (.setSelected (.printStackTraceToggleButton repl-pane) bv)
  (.setSelected (.stackTraceOnErrorMenu repl-pane) bv)))


(defn check-repl-form?
  ([expr repl-id]
    (when-not (empty-string? expr)
      (try
        (with-in-str (str "(do\n" expr "\n)") (read *in* false EOF))
        true
        (catch Throwable t
          (if repl-id
            (do
              (evaluate-in-repl repl-id (str "(prn \"" (clojure.main/repl-exception t) "\")"))
              false)
            (not (is-eof-ex? t)))))))
  ([expr]
    (check-repl-form? expr nil)))

(defn load-with-debug [text ns]
  (if (and ns (check-repl-form? text))
    (pr-str (list 'org.enclojure.repl.main/load-string-with-dbg
              text
              (meta-utils/source-path-from-ns ns)
              (meta-utils/file-from-ns ns)))
    text))

(defn check-repl-forms?
  "reads the set of forms that are in expr (could be just 1)
and returns a seq of forms or nil if there are any bad forms"
  ([expr repl-id]
    (when-not (empty-string? expr)
      (try        
        (with-in-str expr 
          (loop [forms []]
            (let [form (read *in* false EOF)]
              (if (and (not= form EOF) (list? form))
                (recur (conj forms form))
                forms))))
        (catch Throwable t
          (if repl-id
            (do
              (evaluate-in-repl repl-id (str "(prn \"" (clojure.main/repl-exception t) "\")"))
              false)
            (not (is-eof-ex? t)))))))
  ([expr]
    (check-repl-forms? expr nil)))


(defn send-to-repl
  "Given a repl id an expression and an optional nsnode, send the expression to the repl
and store it in repl-history if it has not been seen before"
  ([repl-id expr nsnode]
  (try
    (evaluate-in-repl repl-id expr nsnode)
    (catch Throwable t
      (logger/error-throwable (.getMessage t) t))
    (finally
      (let [{added :added}
                (put-history-item (get-history-ref repl-id) expr)]
        (when added
            (repl-history-browse/log-command repl-id expr))))))
  ([repl-id expr] (send-to-repl repl-id expr nil)))

(defn put-expr-in-repl-and-repl
  "First inserts the epxression into the repl-pane so the the passed expression
participates in the history lofic of the repl.  Useful for when you are evaling
expressions in a file and you want to maintain history for this"
  ([repl-id expr ns-node]
    (let [{:keys [repl-fn repl-panel] print-pretty 
           'clojure.contrib.pprint/*print-pretty*} (get-repl-config repl-id)]
      (.resultReceived repl-panel (._replEditorPane repl-panel) expr)
      (send-to-repl repl-id expr ns-node)))
  ([repl-id expr]
    (put-expr-in-repl-and-repl repl-id expr nil)))

(defn caret-at-prompt [repl-pane prompt-pos]
  (let [d (.getDocument repl-pane)
        caret-pos (.getCaretPosition repl-pane)]
    (and (> caret-pos prompt-pos)
      (not (.contains
             (.getText d prompt-pos (- caret-pos prompt-pos))
             "\n")))))

(defn set-caret-at-prompt [repl-pane prompt-pos key-event]
  (let [d (.getDocument repl-pane)
        caret-pos (.getCaretPosition repl-pane)]
    (when (> caret-pos prompt-pos)
      (when (not (.contains
              (.getText d prompt-pos (- caret-pos prompt-pos))
              "\n"))
        (.setCaretPosition repl-pane prompt-pos)
        (.consume key-event)))))

(defn show-repl-history [repl-id repl-panel]
  (let [history-ref (get-history-ref repl-id)]
    (repl-history-browse/show-history repl-id)))

(defn process-key-input
  ([repl-id repl-pane prompt-pos #^java.awt.event.KeyEvent key-event]
    (let [is-ctrl (.isControlDown key-event)
          code (.getKeyCode key-event)
          keychar (.getKeyChar key-event)
          #^javax.swing.text.Document d (.getDocument repl-pane)
          caret-pos (.getCaretPosition repl-pane)
          sel-start (.getSelectionStart repl-pane)
          sel-end (.getSelectionEnd repl-pane)]
      (if is-ctrl
        (let [history-ref (get-history-ref repl-id)
              do-history-fn #(history-event history-ref repl-pane prompt-pos key-event)
              ;select-pane-fn #(-> jpane .jTabbedPane1 .setSelectedComponent %)
              func ({;\R #(select-pane-fn (.jScrollPaneRepl jpane))
                     ;\E #(select-pane-fn (.jScrollPaneErr jpane))
                     ;\O #(select-pane-fn (.jScrollPaneOut jpane))
                     KeyEvent/VK_J #(evaluate-in-repl repl-id "(.printStackTrace *e *out*)" nil)
                     KeyEvent/VK_UP do-history-fn
                     KeyEvent/VK_DOWN do-history-fn
                     KeyEvent/VK_LEFT #(set-caret-at-prompt repl-pane prompt-pos key-event)
                     KeyEvent/VK_ENTER #(when (>= caret-pos prompt-pos)
                                          (.insertString d caret-pos "\n" nil))
                     KeyEvent/VK_A #(repl-history-browse/show-history "REPL History"
                                          (@history-ref :history-list))} code)]
          (when func
            (func)))
        (cond
          ;//??Note this should be configurable - if change here -change the
          ;setAccelerator also in the ReplPanel.java
;          (and (. key-event) (= code KeyEvent/VK_J))
;          (evaluate-in-repl repl-id "(.printStackTrace *e *out*)" nil)

          (and (or (.isControlDown key-event) (.isMetaDown key-event)) (= code KeyEvent/VK_F))
          (.consume key-event)

          (and (#{10 13 KeyEvent/VK_DELETE KeyEvent/VK_BACK_SPACE} code)
            (or (< sel-start prompt-pos) (= sel-start sel-end prompt-pos)))
          (.consume key-event)

          (and (not (.isShiftDown key-event)) (or (= code 10) (= code 13)))
          (when (< prompt-pos (.getLength d))
            (when-let [expr (.getText d  prompt-pos
                              (- (.getLength d) prompt-pos))]
              (when (check-repl-form? expr)
                (send-to-repl repl-id expr)
                (.consume key-event))))

;          (and (= code KeyEvent/VK_BACK_SPACE)
;            (or (< sel-start prompt-pos) (= sel-start sel-end prompt-pos)))
;          (.consume key-event)

          (= code KeyEvent/VK_HOME)
          (when (caret-at-prompt repl-pane prompt-pos)
            (if (.isShiftDown key-event)
              (.moveCaretPosition repl-pane prompt-pos)
              (.setCaretPosition repl-pane prompt-pos))
            (.consume key-event)))
        ))))

