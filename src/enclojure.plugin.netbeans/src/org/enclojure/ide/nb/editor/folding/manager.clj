(comment
;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;    The use and distribution terms for this software are covered by the
;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;    which can be found in the file epl-v10.html at the root of this distribution.
;    By using this software in any fashion, you are agreeing to be bound by
;    the terms of this license.
;    You must not remove this notice, or any other, from this software.
;
;    Author: Eric Thorsen
)

(ns org.enclojure.ide.nb.editor.folding.manager
    (:use org.enclojure.ide.nb.actions.token-navigator
    org.enclojure.ide.navigator.token-nav
    clojure.inspector clojure.set)
  (:require [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    [org.enclojure.ide.analyze.symbol-meta :as symbol-meta]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.nb.editor.completion.file-mapping :as file-mapping])
  (:import
    (java.util.logging Level)
    (java.awt BorderLayout EventQueue)
    (java.awt.event ActionEvent ActionListener)
    (javax.swing.tree TreeModel DefaultTreeCellRenderer)
    (javax.swing.table TableModel AbstractTableModel)
    (javax.swing JPanel JTree JTable JScrollPane JFrame JToolBar JButton SwingUtilities)
    (java.util ArrayList List Stack)
    (javax.swing.event DocumentEvent)
    (javax.swing.text BadLocationException Document)
    (java.io StringReader File)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token TokenHierarchy)
    (org.netbeans.editor BaseDocument)
    (org.netbeans.modules.editor NbEditorUtilities)
    (org.openide.windows TopComponent)
    (org.openide.cookies EditorCookie)
    (org.openide.util RequestProcessor)
    (org.openide.util ImageUtilities)
    (org.openide.loaders DataObject)
    (org.netbeans.api.editor.fold Fold FoldType)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence)
    (org.netbeans.editor BaseDocument)
    (org.netbeans.api.editor.fold FoldUtilities)
    (org.netbeans.spi.editor.fold FoldHierarchyTransaction FoldManager FoldOperation)
    (org.openide.util Exceptions)
    (org.openide.filesystems FileUtil)
    (clojure.lang LineNumberingPushbackReader)))

; setup logging
(logger/ensure-logger)

(defmacro #^{:private true}
    with-exception-handling [& body]
    `(try
      ~@body
       (catch Throwable t#
        (Exceptions/printStackTrace t#))))

(defmacro with-read-lock [item & body]
  `(try
     (.readLock ~item)
    ~@body
     (catch Throwable t#
       (throw t#))
     (finally (.readUnlock ~item))))


(defmulti fold-text
  (fn [form-data] :symbol-type))

(defmethod fold-text 'comment [form-data]
  "comment ...")

(defmethod fold-text :default 
  [{:keys [symbol-type arglists name]}]
    (str "(" symbol-type " " name "" arglists ")"))

(defn add-folds-from-forms
  "Takes a new set of forms, adds folds to the hierarchy and stores them in a set"
  ([#^org.netbeans.spi.editor.fold.FoldOperation fold-operation
    folds
    new-forms
    #^FoldHierarchyTransaction tran]
    (let [basedoc (-> fold-operation .getHierarchy .getComponent .getDocument)
          max-length (.getLength basedoc)]
      (try
        (doseq [{:keys [start end symbol-type name argslist form] :as form-data} new-forms]
          (when (and start end)
            (let [end-is (min end max-length)
                  new-fold (.addToHierarchy fold-operation
                             (FoldType. (str symbol-type))
                             (fold-text form-data)
                             false start end-is 0 (- end-is start) nil tran)]
              (dosync
                (alter folds conj new-fold)))))
        (catch Throwable t
          (logger/error (.getMessage t))
          ;(Exceptions/printStackTrace t)
          )))))

(defn get-fold-data-for-file [ns-data]
  (map
    #(select-keys %1
       [:start :end :name :symbol-type])
    (symbol-meta/all-forms  ns-data)))

(defn reset-all-folds
  [{:keys [fold-operation base-document folds forms]}
   #^FoldHierarchyTransaction tran]
  (try
    (when-let [file (editor-utils/from-doc-to-file @base-document)]
        (when-let [top-level-forms (symbol-caching/reparse-file file)]
          ; This needs to be triggered on load and on changed of the namespace node.          
          (future (file-mapping/refresh-completion-cache-data (.getPath file)))
          (when top-level-forms
            (logger/info "Folder got forms, symbol count {}"
              (count (symbol-meta/all-forms top-level-forms)))
                (dosync
                    (alter forms
                        (fn [_] (symbol-meta/all-forms top-level-forms)))
                    (alter folds (fn [_] #{}))))
            (EventQueue/invokeLater
                #(add-folds-from-forms @fold-operation folds @forms tran))))
    (catch Throwable t
      (logger/error (.getMessage t))
      ;    (Exceptions/printStackTrace t)
      )))

(defn find-search-space [doc-event
   {:keys [fold-operation base-document folds forms]}
   #^FoldHierarchyTransaction tran]    
  (let [doc-offset (.getOffset doc-event)
        hierarchy (-> @fold-operation .getHierarchy)
        root (.getRootFold hierarchy)
        start-offset (FoldUtilities/findFoldStartIndex root doc-offset)
        end-offset (FoldUtilities/findFoldEndIndex root doc-offset)]
      {:start (if  (= -1 start-offset) 0
                (inc (.getEndOffset
                       (.get (FoldUtilities/childrenAsList root start-offset 1) 0))))
       :end   (if (= end-offset (.getFoldCount root)) (.getLength @base-document)
                (dec (.getStartOffset 
                       (.get (FoldUtilities/childrenAsList root end-offset 1) 0))))}))

; As soon as someone types, the form is invalidated.  With the exception of the ns form,
; I probably just need to update the hippy words here?
; For the namespace form, I need to see if there were any updates to referenced
; namespaces or imports.
(defn handle-modified-fold
  "this function handles edits to the fold hiearchy once it has already been initialized"
  [doc-event
   {:keys [fold-operation base-document folds forms] :as data}
   #^FoldHierarchyTransaction tran]
  (if-let [file (editor-utils/from-doc-to-file @base-document)]
    (let [token-seq (.tokenSequence (TokenHierarchy/get @base-document))
            change-len (.getLength doc-event)]
     (when-not (= 0 change-len)
       (let [fold (FoldUtilities/findOffsetFold
                    (.getHierarchy @fold-operation)
                    (.getOffset doc-event))
             {:keys [start end] } (find-search-space doc-event data tran)]
         (when-not fold
           (let [forms (get-top-level-forms token-seq start end)
                 candidate-text (.getText @base-document start (- end start))
                 completion-info (file-mapping/ensure-completion-info file)]
            (if (pos? (count forms))
              (EventQueue/invokeLater
                #(add-folds-from-forms @fold-operation folds forms tran))))))))
    (logger/warn "Fold  manager exists with no base document? Could be from a REPL?")))

(defn get-data-object-data [d]
  (if-let [#^DataObject dob (NbEditorUtilities/getDataObject d)]
    (.clojureAnalyzerData dob)
    (logger/warn "Unable to find DataObject from document in fold/manager.clj")))

(defn get-fold-manager-clj [init-data]
  (let [data (assoc init-data
               :folds (ref #{})
               :forms (ref []))]
    (proxy [org.netbeans.spi.editor.fold.FoldManager][]
      (getOperation [] @(:fold-operation data))
      (getDocument [] @(:base-document data))
      (init [#^FoldOperation op]
        (logger/info "new fold manager.........")
        )
      (initFolds [#^FoldHierarchyTransaction tran]
        (reset-all-folds data tran))
      (insertUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran]
        (with-exception-handling
            (handle-modified-fold doc-event data tran)))
      (removeUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran]
        (with-exception-handling
            (handle-modified-fold doc-event data tran)))
      (changedUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran]
        (with-exception-handling
            (handle-modified-fold doc-event data tran)))
      (removeEmptyNotify [#^Fold fold]
        (with-exception-handling
            (dosync (alter (:folds data) disj fold))))
      (removeDamagedNotify [#^Fold fold]
        (with-exception-handling
            (dosync (alter (:folds data) disj fold))))
      (expandNotify [#^Fold fold])
      (release []))))

(defn get-fold-manager-repl [data]
    (proxy [org.netbeans.spi.editor.fold.FoldManager][]
      (getOperation [] @(:fold-operation data))
      (getDocument [] @(:base-document data))
      (init [#^FoldOperation op])
      (initFolds [#^FoldHierarchyTransaction tran])
      (insertUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran])
      (removeUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran])
      (changedUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran])
      (removeEmptyNotify [#^Fold fold])
      (removeDamagedNotify [#^Fold fold])
      (expandNotify [#^Fold fold])
      (release [])))

  (defn get-fold-manager-proxy []
    (let [data {:fold-operation (atom nil)
                :base-document (atom nil)
                :forms (ref [])
                :folds (ref #{})}
          fproxy (atom nil)]
      (logger/info "get-fold-manager-proxy Thread : {} Getting fold proxy "
        (hash (Thread/currentThread)))
      (proxy [org.netbeans.spi.editor.fold.FoldManager][]
        (getOperation [] @(:fold-operation data))
        (getDocument [] @(:base-document data))
        (init [#^FoldOperation op]
          (with-exception-handling
            (dosync
                (swap! (:fold-operation data) (fn [_] op))
                (swap! (:base-document data)
                  (fn [_] (-> op .getHierarchy .getComponent .getDocument)))
                (logger/info "fold init - name : {}" (-> op .getHierarchy .getComponent .getName))
                (logger/info "fold init - doc: {}" @(:base-document data))
                (swap! fproxy
                  (fn [_]
                    (if (= "Repl editor pane"
                          (-> op .getHierarchy .getComponent .getName))
                      (get-fold-manager-repl data)
                      (get-fold-manager-clj data)))))
              (logger/info "Thread : {} Fold {} Doc {}"
                (hash (Thread/currentThread)) (hash this) (hash @(:base-document data)))
              (.init @fproxy op)))
        (initFolds [#^FoldHierarchyTransaction tran]
          (with-exception-handling
            (.initFolds @fproxy tran)))
        (insertUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran]
          (with-exception-handling
            (.insertUpdate @fproxy doc-event tran)))
        (removeUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran]
          (with-exception-handling
            (.removeUpdate @fproxy doc-event tran)))
        (changedUpdate [#^DocumentEvent doc-event #^FoldHierarchyTransaction tran]
          (with-exception-handling
            (.changedUpdate @fproxy doc-event tran)))
        (removeEmptyNotify [#^Fold fold]
          (with-exception-handling
            (.removeEmptyNotify @fproxy fold)))
        (removeDamagedNotify [#^Fold fold]
          (with-exception-handling
            (.removeDamagedNotify @fproxy fold)))
        (expandNotify [#^Fold fold]
          (with-exception-handling
            (.expandNotify @fproxy fold)))
        (release []
          (with-exception-handling
            (.release @fproxy))))))
