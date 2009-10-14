
(ns org.enclojure.ide.nb.actions.action-handler
  (:use
    org.enclojure.ide.nb.actions.token-navigator
    org.enclojure.ide.navigator.token-nav
    org.enclojure.ide.repl.repl-manager
    org.enclojure.ide.repl.repl-panel
    org.enclojure.ide.nb.editor.repl-win
    org.enclojure.ide.nb.editor.repl-focus
    )
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.repl.repl-panel :as repl-panel]
    [org.enclojure.ide.analyze.symbol-nav :as symbol-nav]
    [org.enclojure.ide.nb.editor.completion.file-mapping :as file-mapping]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    [org.enclojure.commons.meta-utils :as meta-utils]
    [org.enclojure.ide.settings.utils :as pref-utils]
    )
  (:import
    (org.openide.loaders DataObject MultiDataObject)
    (org.openide.cookies EditorCookie)
    (org.openide.nodes Node)
    (org.openide.windows TopComponent)
    (org.openide.util NbBundle)
    (org.enclojure.ide.nb.editor ReplTopComponent)
    (clojure.lang Compiler)
    (java.io File PushbackReader FileReader OutputStreamWriter FileOutputStream)
    (java.util.logging Logger Level)
    (java.util.logging Level)    
    (javax.swing.text Document)
    (javax.swing.JEditorPane)
    ))

; setup logging
(logger/ensure-logger)

(def *settings* (ref {:refer-ns-after-file-load true
                      :refer-pretty-printer-after-ns-change true}))

(defn update-settings [m]
  (sync nil
    (alter *settings* #(merge % m))))

(def logger (. Logger (getLogger "org.enclojure.edit")))

;Clojure "require" fucntion has a bug which causes it to not evaluate the new
;file:
;http://code.google.com/p/clojure/issues/detail?id=38
;So if name__init.class exists in the "classes" directory and name.clj is updated
;later than the name.clj is not getting re-loaded. Clojure is suppose to load
;whichever is newer
(defn require-namespace
  "loads the file 'file-name'.  If :refer-ns-after-load is true in *settings*
the resulting file's ns is refered on successful load"
  ([repl nsname]
    (let [cmd (str "(require :reload-all '" nsname ")")]
      (.ExecuteExpr repl cmd nil))))


(defn get-file-context
  "attempts to determine the project, namespace and get the ns node for a given
source file referenced by the passed in node(s) and also extract the selected
text or top level form"
  [nodes]
  (let [pane (current-editor-pane nodes)
        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)
        expr (.getSelectedText pane)
        expr (if (and expr (pos? (.length expr)))
               expr
               (get-top-form-text pane))
        nsnode (get-namespace-node pane)
        nsname (get-namespace pane)]
    {:pane pane :project p :expr expr :nsnode nsnode :nsname nsname}))

(defn eval-expr-action
  [nodes]
  (let [{:keys [project expr nsnode nsname]}
        (get-file-context nodes)]
    (logger/info "eval expression in ns {}  type: {}" nsname (class nsname))
    (execute-expr project expr nsnode evaluate-in-repl)))

(defn paste-eval-expr-action
  [nodes]
  (let [{:keys [project expr nsnode nsname]} (get-file-context nodes)]
    (logger/info "paste-eval expression in ns {}" nsname)
    (execute-expr project expr nsnode put-expr-in-repl-and-repl)))

(defn load-file-action [nodes]
  (let [ec (editor-cookie nodes)
        _ (when ec (.saveDocument ec))
        pane (current-editor-pane nodes)
        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)]
    (when-let [repl-tc (find-active-repl p)]
      (let [{:keys [external local]} (get-repl-config (.ReplName repl-tc))
            nsnode (get-namespace-node pane)
            nsname (get-namespace pane)]
      (execute-expr p
        (repl-panel/load-with-debug
            (.getText pane) nsname) nsnode)))))

(defn load-namespace-action
  "loads the file 'file-name'.  If :refer-ns-after-load is true in *settings*
the resulting file's ns is refered on successful load"
  [nodes]
  (let [pane (current-editor-pane nodes)
        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)
        nsname (get-namespace pane)
        nsnode (get-namespace-node pane)
        exp nsnode]        
    (execute-expr p exp nil)))

(defn require-file-ns-action
  "calls (require '[ns.of.file :as file]) in the repl."
  [nodes]
  (let [pane (current-editor-pane nodes)
        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)
        nsname (get-namespace pane)
        alias (last (.split (str nsname) "\\."))]        
    (execute-expr p (str "(require '[" nsname " :as " (or alias nsname) "])") nil)))


(defn switch-repl-focus-action []
  (when-let [editor-pane (current-editor-pane)]
    (let [p (ReplTopComponent/GetProjectFromDocument (.getDocument editor-pane))
          repl-tc (find-active-repl p)]
      (when repl-tc
        (if (.IsReplHasFocus repl-tc)
          (.requestFocusInWindow editor-pane)
          (.RequestReplFocus repl-tc))))))

(defn goto-declaration-action
  "Attempt to location the definition of a symbol using the 
completion support system (static analysis) so that the code does not need to
be loaded"
  []
  (let [editor-pane (current-editor-pane)
        document (.getDocument editor-pane)
        file (editor-utils/from-doc-to-file document)
        {:keys [ns-use-refer unqualified-to-qualified-map this-ns]
            :as completion-info } (file-mapping/ensure-completion-info file)
        id (symbol-nav/get-identifier-at document
                  (.getCaretPosition editor-pane))]
    (when id
      (let [[alias sym] (.split (.trim (str id)) "/")
            ns-list
            (conj (if sym ;there was an alias
                      [(unqualified-to-qualified-map alias)]
                      ns-use-refer) this-ns)]
      (logger/info "goto-declaration-action: id=> {} alias=> {} sym=> {}"
                    id alias sym)
      (logger/info " using ns-list => {} (count ns-list) {}" ns-list (count ns-list))
        (when (or (pos? (count alias))
                (pos? (count sym)))
        (let [[file [sym & _]]
              (some #(let [{:keys [symbols source-file] :as ns-syms}
                                  (symbol-caching/from-symbol-cache (str %))]
                       (when-let [sym (symbols (symbol (or sym alias)))]
                         [source-file sym]))
                  ns-list)]
          (logger/info "in file {} found {}" file sym)
          (when file
            (when-let [full-path (if (.exists (File. file)) file
                                     (classpath-utils/find-resource file))]
                (editor-utils/open-editor-file-at-line full-path
                  (max (- (:line sym) 2) 0)))
              )))))))

(defn save-standalone-repl-settings [classpath]
  (let [file-path (pref-utils/get-pref-file-path "stand-alone-repl")]
    (with-open [out (OutputStreamWriter. (FileOutputStream. (File. file-path)))]
        (binding [*out* out]
          (prn [classpath])))))

(defn get-standalone-repl-settings []
  (let [file-path (pref-utils/get-pref-file-path "stand-alone-repl")]
    (first (read-string (slurp file-path)))))

(defn load-sources-in-repl
  "Given 1 or more selected nodes, attempts to find the relevant repl for the source
nodes and loads each of them in turn as text (so searching is done)"
  [nodes]
  (let [p (ReplTopComponent/GetProjectFromActivatedNodes nodes)]
    (when-let [repl-tc (find-active-repl p)]
      (let [{:keys [external local]} (get-repl-config (.ReplName repl-tc))]
        (doseq [node nodes]
          (let [data-obj (.lookup (.getLookup node) MultiDataObject)]
            (when-let [fo (-> data-obj .getPrimaryEntry .getFile)]
              ; I have the full file path now.  Need the resource/ns name
              (let [ns-f (classpath-utils/resource-name-from-full-path
                           (.getPath fo))
                    full-text (slurp (.getPath fo))]
                (logger/info "Resource {} full-path {}" ns-f (.getPath fo))
      (execute-expr p
        (repl-panel/load-with-debug full-text (meta-utils/ns-from-file ns-f)) nil
        )))))))))
