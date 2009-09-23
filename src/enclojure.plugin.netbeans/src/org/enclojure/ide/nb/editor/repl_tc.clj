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

(ns org.enclojure.ide.nb.editor.repl-tc
  (:use org.enclojure.repl.main
    org.enclojure.ide.repl.repl-panel
    org.enclojure.ide.repl.repl-manager
    org.enclojure.ide.common.classpath-utils
    org.enclojure.ide.debugger.jdi
    org.enclojure.ide.nb.editor.repl-focus)
  (:require
    [org.enclojure.ide.preferences.enclojure-options-category
        :as enclojure-options-category]
    [org.enclojure.ide.preferences.platform-options
        :as platform-options]
    [org.enclojure.ide.repl.repl-history-browse :as repl-history-browse]
    [org.enclojure.ide.nb.editor.utils :as utils]
    [org.enclojure.ide.settings.utils :as pref-utils]
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (org.enclojure.ide.repl ReplPanel)
    (org.enclojure.ide.nb.editor ReplTopComponent)
    (org.netbeans.api.project Project ProjectInformation)
    (org.openide.filesystems FileUtil)
    (java.awt EventQueue Component)
    (java.util.logging Level Logger)
    (java.io File)
    (org.enclojure.repl IReplHistorySupport)    
    (org.openide NotifyDescriptor$Confirmation DialogDisplayer NotifyDescriptor)
    ))

; setup logging
(logger/ensure-logger)

(defn verify-classpath
  [classpath]
  (if
    (bad-classpath? classpath)
    (.notify (DialogDisplayer/getDefault)
      (NotifyDescriptor$Confirmation.
        "There did not appear to be both valid clojure and clojure-contrib jars present in the classpath.  These are both required to start a repl.  You can add them as libraries to the project or to your platform under the preferences if this is a stand-alone repl.
Are you sure you want to continue?"
        "Possible problem with classpath for the REPL"
        NotifyDescriptor/YES_NO_OPTION
        NotifyDescriptor/ERROR_MESSAGE
          )) 0))
      
; setup the interface for the repl-history support functions.
(def -repl-history-support-
  (proxy [org.enclojure.repl.IReplHistorySupport][]
    (showHistory [repl-id]
      (let [log-file (File. (.getHistoryLogFile this repl-id))]
        (when (.exists log-file)
            (utils/open-editor-file-at-line 
              (.getCanonicalPath log-file) 1 true))))
   (getHistoryLogFile [repl-id]
     (pref-utils/get-pref-file-path (str repl-id "-command-history.clj")))
     ))

(defn config-with-preferences
  ([repl-id settings-map]
    ; make sure the history browser func is set...this does not really belong here
    (swap! repl-history-browse/*repl-history-support-impl* 
      (fn [_] -repl-history-support-))
    (let [prefs (enclojure-options-category/load-preferences)
          jvm-args (when-let [args (prefs :jvm-additional-args)]
                     (let [vargs (.split args " ")]
                       (when (pos? (count vargs))
                         (apply vector vargs))))]
      (apply add-or-update-repl repl-id
        (reduce (fn [vec [k v]]
                  (conj vec k v)) []
          (merge
            prefs
            (if jvm-args
              (assoc settings-map :arguments jvm-args) settings-map))))))
  ([repl-id]
    (config-with-preferences repl-id {})))
  
(defn activate-repl-tc [repl-tc]
  (.requestVisible repl-tc)
  (.requestActive repl-tc)
  repl-tc)

(defn open-repl-tc [repl-id open-tc? repl-panel]
  (let [repl-tc (ReplTopComponent. repl-id repl-panel)]
    (when open-tc? (.open repl-tc))
    repl-tc))

(defn assure-repl-panel [repl-id open-tc?]
  (when-not (:repl-panel (get-repl-config repl-id))
    (let [repl-panel (ReplPanel. repl-id)
          repl-tc (open-repl-tc repl-id open-tc? repl-panel)]
      (config-with-preferences repl-id 
        {:repl-id repl-id :repl-panel repl-panel :repl-tc repl-tc}))))

(defn start-non-project-repl [repl-id java-args classpath]
  (assure-repl-panel repl-id true)
  (stop-internal-repl repl-id)
  ;(update-repl repl-id :arguments java-args)
  (let [{:keys [repl-panel repl-tc]} (config-with-preferences repl-id)
        classpath (str (classpath-for-repl) java.io.File/pathSeparator classpath)]
    (when
      (zero? (verify-classpath classpath))
        (.setResetReplFn repl-panel #(do
                                       (stop-internal-repl repl-id)
                                       (start-non-project-repl repl-id java-args classpath)))
        (create-internal-repl repl-id classpath
          (partial bind-process-panel repl-panel)
          (partial bind-repl-panel repl-panel))
        (activate-repl-tc repl-tc)
        (evaluate-in-repl repl-id
          (str (get-settings-set-expression repl-id))))))

(defn start-non-project-repl-action [action]
  (let [prefs (enclojure-options-category/get-stand-alone-settings)]
    (start-non-project-repl
      "Stand-alone REPL"
      (:jvm-additional-args prefs)
      (apply str
        (interpose java.io.File/pathSeparator
          (:classpaths (:platform  prefs)))))))

(defn create-repltc [repl-id open-tc? spawn-repl-fn]
  (assure-repl-panel repl-id open-tc?)
  (let [{:keys [repl-tc repl-panel]} (get-repl-config repl-id)
        spawned-repl-keys (spawn-repl-fn)]
        ;{:keys [repl-fn result-fn]} (spawn-repl-fn)]
    ; store off all the keys retiurned by this function.
    (apply update-repl repl-id (apply concat spawned-repl-keys))
    (bind-repl-panel repl-panel (:repl-fn spawned-repl-keys) (:result-fn spawned-repl-keys))
    (evaluate-in-repl repl-id
      (str (get-settings-set-expression repl-id)))
    repl-tc))

(defn connect-external-repl [repl-id host port]
  (create-repltc repl-id true
    #(create-repl-client-with-back-channel host port)))

(defn create-ide-repltc
  [repl-id]
  (create-repltc repl-id false create-clojure-repl))

(declare reset-repl)

(defn start-project-repl [#^Project p]
  (let [repl-id (get-project-name p)
        _ (assure-repl-panel repl-id true)
        {:keys [repl-panel repl-tc]} (config-with-preferences repl-id)
        classpath (get-repl-classpath p)
        ]
    (when
      (zero? (verify-classpath classpath))
        (.setResetReplFn repl-panel (partial reset-repl p))
        (create-internal-repl repl-id classpath
          (partial bind-process-panel repl-panel)
          (partial bind-repl-panel repl-panel))
        (activate-repl-tc repl-tc)
        (evaluate-in-repl repl-id
            (str (get-settings-set-expression repl-id))))))

(defn stop-project-repl [proj repl-tc-closing?]
  (let [repl-id (if (string? proj) proj (get-project-name proj))]
    (if (= repl-id ReplTopComponent/IDE_REPL)
      ((:repl-fn (get-repl-config repl-id)) ":CLOSE-REPL")
      (stop-internal-repl repl-id))
    (when repl-tc-closing? (unregister-repl repl-id))))

(defn start-stop-project-repl [#^Project p]
  (let [repl-id (get-project-name p)]
    (if (repl-connected? repl-id)
      (stop-project-repl p false)
      (start-project-repl p))))

(defn reset-repl [#^Project p]
  (stop-project-repl p false)
  (start-project-repl p))

(defn run-context-menu-name [#^Project p]
  (let [repl-id (get-project-name p)]
    (ReplTopComponent/getBundleProperty
      (if (repl-connected? repl-id)
        "CTL_RunProjectWithReplAction_Stop"
        "CTL_RunProjectWithReplAction")
      repl-id)))
  
;;;;  (let [classpath (apply str (interpose ":" (distinct (.split classpath ":"))))]; ;Remove the duplicate paths

;;;;;;;;;;;;;END: New code

(defn repl-focus [top-window]
  (EventQueue/invokeLater 
    #(-> top-window ._replPanel .GetEditorPane .requestFocusInWindow)))

;(defn execute-expr [#^Project p expr nsnode]
;  (let [repl-tc (find-active-repl p)]
;    (when repl-tc
;      (let [repl-id (.GetReplID repl-tc)]
;        (.requestVisible repl-tc)
;        (when (check-repl-form? expr repl-id)
;          (evaluate-in-repl repl-id expr nsnode))))))

(defn execute-expr 
  "Finds the active top-component and sends the expression to the passed in repl
func. The repl-function is used to paste the expression into the repl-window first
(such as when 'eval expression' when you want history or not)"
  ([#^Project p expr nsnode repl-func]
  (let [repl-tc (find-active-repl p)]
    (when repl-tc
      (let [repl-id (.GetReplID repl-tc)]
        (.requestVisible repl-tc)
        (when (check-repl-form? expr repl-id)
          (repl-func repl-id expr nsnode))))))
  ([#^Project p expr nsnode]
    (execute-expr p expr nsnode evaluate-in-repl)))

(defn repl-debugging? [repl-id]
  (and (repl-connected? repl-id) (:dbg-engines (get-repl-config repl-id))))

(defn start-attach-detach-debugger [#^Project p]
  (let [repl-id (get-project-name p)
        _ (when-not (repl-connected? repl-id) (start-stop-project-repl p))
        {:keys [dbg-engines repl-panel]} (get-repl-config repl-id)]
    (if dbg-engines
      (kill-dbg repl-id)
      (attach-dbg repl-id (._debugPort repl-panel)))))

(defn debug-context-menu-name [#^Project p]
  (let [repl-id (get-project-name p)]
    (ReplTopComponent/getBundleProperty
      (if (repl-debugging? repl-id)
        "CTL_DetachDebugProjectWithReplAction"
        (if (repl-connected? repl-id)
          "CTL_AttachDebugProjectWithReplAction"
          "CTL_RunDebugProjectWithReplAction"))
      repl-id)))

