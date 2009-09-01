
(ns org.enclojure.ide.nb.actions.action-handler
  (:use org.enclojure.ide.nb.actions.token-navigator
    org.enclojure.ide.navigator.token-nav
    org.enclojure.ide.repl.repl-manager
    org.enclojure.ide.repl.repl-panel
    org.enclojure.ide.nb.editor.repl-tc
    org.enclojure.ide.nb.editor.repl-focus)
  (:require [org.enclojure.commons.meta-utils :as meta-utils]
    [org.enclojure.commons.logging :as logging]
    [org.enclojure.ide.analyze.symbol-nav :as symbol-nav]
    [org.enclojure.ide.nb.editor.completion.file-mapping :as file-mapping]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.nb.editor.utils :as editor-utils])
  (:import 
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
    (javax.swing.JEditorPane)))

(def *settings* (ref {:refer-ns-after-file-load true
                      :refer-pretty-printer-after-ns-change true}))

(meta-utils/defrt #^{:private true} log (logging/get-ns-logfn))


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

(defn load-with-debug [text ns]
  (if (and ns (check-repl-form? text))
    (pr-str (list 'org.enclojure.repl.meta-utils/load-string-with-dbg
              text
              (meta-utils/source-path-from-ns ns)
              (meta-utils/file-from-ns ns)))
    text))

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
    (log Level/INFO "eval expression in ns " nsname " type:" (class nsname))
    (execute-expr project expr nsnode evaluate-in-repl)))

(defn paste-eval-expr-action
  [nodes]
  (let [{:keys [project expr nsnode nsname]} (get-file-context nodes)]
    (log Level/INFO "paste-eval expression in ns " nsname)
    (execute-expr project expr nsnode put-expr-in-repl-and-repl)))

;(defn eval-expr-action [nodes]
;  (let [pane (current-editor-pane nodes)
;        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)
;        expr (.getSelectedText pane)
;        expr (if (and expr (pos? (.length expr)))
;               expr
;               (get-top-form-text pane))
;        nsnode (get-namespace-node pane)
;        nsname (get-namespace pane)]
;    (execute-expr p expr nsnode)))

(defn load-file-action [nodes]
  (let [ec (editor-cookie nodes)
        _ (when ec (.saveDocument ec))
        pane (current-editor-pane nodes)
        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)
        repl-tc (find-active-repl p)
        {:keys [external local]} (get-repl-config (.ReplName repl-tc))
        nsnode (get-namespace-node pane)
        nsname (get-namespace pane)]
      (execute-expr p
        (load-with-debug
            (.getText pane) nsname) nsnode)))

(defn load-namespace-action
  "loads the file 'file-name'.  If :refer-ns-after-load is true in *settings*
the resulting file's ns is refered on successful load"
  [nodes]
  (let [pane (current-editor-pane nodes)
        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)
        nsname (get-namespace pane)
        nsnode (get-namespace-node pane)
        exp nsnode]
        ;exp (str "(do " nsnode "(require ['com.infolace.format :as 'format]))")]
    (execute-expr p exp nil)))

(defn require-file-ns-action
  "calls (require '[ns.of.file :as file]) in the repl."
  [nodes]
  (let [pane (current-editor-pane nodes)
        p (ReplTopComponent/GetProjectFromActivatedNodes nodes)
        nsname (get-namespace pane)
        alias (last (.split (str nsname) "\\."))]
        ;exp (str "(do " nsnode "(require ['com.infolace.format :as 'format]))")]
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
        {:keys [ns-use-refer unqualified-to-qualified-map]
            :as completion-info } (file-mapping/ensure-completion-info file)
        id (symbol-nav/get-identifier-at document
                  (.getCaretPosition editor-pane))]
    (when id
      (let [[alias sym] (.split (str id) "/")
            ns-list (if sym ;there was an alias
                      [(unqualified-to-qualified-map alias)]
                      ns-use-refer)]        
      (log Level/INFO " got id " id)
      (log Level/INFO " using " ns-list)
        (let [[file [sym & _]]
              (some #(let [{:keys [symbols source-file] :as ns-syms}
                                  (symbol-caching/from-symbol-cache (str %))]
                       (when-let [sym (symbols (symbol (or sym alias)))]
                         [source-file sym]))
                  ns-list)]
          (log Level/INFO "in file " file " found " sym)
          (when-let [full-path (classpath-utils/find-resource file)]
            (editor-utils/open-editor-file-at-line full-path
              (max (dec (:line sym)) 0)))
          )))))

(defn get-pref-file-path []
  (let [env (into {} (System/getenv)) home (if (env "HOME")
                                               (env "HOME") (env "HOMEPATH"))
        file-path (str home (File/separator)
                    ".enclojure-prefs" (File/separator) "adhoc-repl-configs.clj")]
    (when-not (.exists (File. file-path))
      (with-open [out (OutputStreamWriter. (FileOutputStream. (File. file-path)))]
        (binding [*out* out]
          (prn [""]))))
    file-path))

(defn save-standalone-repl-settings [classpath]
  (let [file-path (get-pref-file-path)]
    (with-open [out (OutputStreamWriter. (FileOutputStream. (File. file-path)))]
        (binding [*out* out]
          (prn [classpath])))))

(defn get-standalone-repl-settings []
  (let [file-path (get-pref-file-path)]
    (first (read-string (slurp file-path)))))
