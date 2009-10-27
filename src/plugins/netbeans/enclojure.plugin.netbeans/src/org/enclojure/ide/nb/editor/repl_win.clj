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
;*    Author: Eric Thorsen, Narayan Singhal
;*******************************************************************************
)
(ns org.enclojure.ide.nb.editor.repl-win
  (:require
    [org.enclojure.ide.repl.repl-panel :as repl-panel]
    [org.enclojure.ide.repl.repl-manager :as repl-manager]
    [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.debugger.jdi :as jdi]
    [org.enclojure.ide.nb.editor.repl-focus :as repl-focus]
    [org.enclojure.ide.preferences.enclojure-options-category
        :as enclojure-options-category]
    [org.enclojure.ide.preferences.platform-options
        :as platform-options]
    [org.enclojure.commons.meta-utils :as meta-utils]
    [org.enclojure.ide.repl.repl-history-browse :as repl-history-browse]
    [org.enclojure.ide.nb.editor.utils :as utils]
    [org.enclojure.ide.settings.utils :as pref-utils]
    [org.enclojure.ide.repl.factory :as factory]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.nb.editor.repl-focus :as repl-focus]
    [org.enclojure.ide.debugger.jdi :as jdi]
    [org.enclojure.ide.nb.source.add-file :as add-file]
    [org.enclojure.ide.common.error-reporting :as error-reporting]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    )
  (:import (org.enclojure.ide.repl ReplPanel)
    (org.enclojure.repl IReplWindow IReplWindowFactory)
    (org.enclojure.ide.nb.editor ReplTopComponent)
    (org.netbeans.api.project Project ProjectInformation ProjectUtils Sources)
    (org.openide.filesystems FileUtil)
    (java.awt EventQueue Component)
    (java.util.logging Level Logger)
    (java.io File)    
    (org.openide NotifyDescriptor$Confirmation DialogDisplayer NotifyDescriptor)
    )
  )


; setup logging
(logger/ensure-logger)

(defn- verify-classpath
  [classpath]
  (if-let [{[clojure clojure-exists?] :clojure
            [clojure-contrib contrib-exists?] :contrib}
            (repl-manager/bad-classpath? classpath)]
    (.notify (DialogDisplayer/getDefault)
      (NotifyDescriptor$Confirmation.
        (str
        "There did not appear to be both valid clojure and clojure-contrib jars present in the classpath.  "
          "For clojure.jar => " (if clojure (format "\"%s\"" clojure) " no reference to a clojure jar found in classpath.")
            (when clojure (if clojure-exists? " possibly valid clojure file." " file not found."))
          "\nFor clojure-contrib.jar => " (if clojure-contrib
                                    (format "\"%s\"" clojure-contrib)
                                    " no reference to a clojure-contrib jar found in classpath.")
            (when clojure-contrib (if contrib-exists?
                                    " passibly valid clojure-contrib file." " file not found."))
          "\nThese are both required to start a repl."
          "\nFor a project REPL, you can add them as libraries to the project."
          "\nFor the stand-alone REPL, go to the Enclojure category in the Netbeans preferences and make sure they are both present in the selected platform."
          "\nAre you sure you want to continue?")
        "Possible problem with classpath for the REPL"
        NotifyDescriptor/YES_NO_OPTION
        NotifyDescriptor/ERROR_MESSAGE
          )) 0))

(def #^{:private true}
  -get-repl-window-factory-
  (proxy [IReplWindowFactory][]
    (makeReplWindow [repl-panel repl-context]
      (let [repl-id (:repl-id repl-context)
            repl-tc (ReplTopComponent. repl-id repl-panel)]
        (proxy [IReplWindow][]
            (getComponent [] repl-tc)
            (open [] (when-not
                       (.isOpened repl-tc)
                       (.open repl-tc)))
            (makeActive [] (.requestVisible repl-tc)
                           (.requestActive repl-tc)
              repl-tc)
            (showHistory []
                    (let [log-file (File. (.getHistoryLogFile this))]
                        (when (.exists log-file)
                            (utils/open-editor-file-at-line
                                (.getCanonicalPath log-file) 1 true))))
            (getHistoryLogFile []
                (pref-utils/get-pref-file-path
                  (str repl-id "-command-history.clj")))
          )))))


(defn- config-with-preferences
  ([settings-map]    
    (let [prefs (enclojure-options-category/load-preferences)
          jvm-args (when-let [args (prefs :jvm-additional-args)]
                     (let [vargs (.split args " ")]
                       (when (pos? (count vargs))
                         (apply vector vargs))))]
          (merge prefs
            (if jvm-args
              (assoc settings-map :jvm-additional-args jvm-args) settings-map))))
  ([]
    (config-with-preferences {})))

(defn get-default-java-exe
  "Get the full path of the java executable of the default platform"
  []
  (.getCanonicalPath
    (:launcher (classpath-utils/java-exec-properties-for-java-platform))))
;========================================================================
; Embedded IDE REPL startup
;========================================================================
(defn create-ide-repl
  "Top level function for starting the embedded IDE Repl"
  [repl-id]
  (let [irepl
          (factory/create-in-proc-repl
            (or (repl-manager/get-repl-config repl-id)
              {:repl-id repl-id})
            -get-repl-window-factory-)]
    (-> irepl .getReplWindow .getComponent)))

;========================================================================
; External unmanaged REPL startup
;========================================================================
(defn connect-external-repl [repl-id host port]
  (let [irepl
        (factory/create-unmanaged-external-repl
          (assoc (or (repl-manager/get-repl-config repl-id)
                   {:repl-id repl-id})
            :host host :port port)
          -get-repl-window-factory-)]
    (try
      (-> irepl .getReplWindow .getComponent)
    (catch Exception e
      (error-reporting/report-error        
        (format "Error starting REPL %s using host %s with port %s. Make sure
the JVM you are connecting to has the Enclojure repl-server running and has
the clojure.jar and clojure-contrib.jars in the classpath. Also check your host
and port settings." repl-id) e)))))


;========================================================================
; External managed stand alone REPL startup (no project associations)
;========================================================================
(defn start-stand-alone-repl
  "Start a stand alone repl"
  [repl-id java-args classpath]
  (let [curr-config (repl-manager/get-repl-config repl-id)
        updated-config (merge (or curr-config {:repl-id repl-id})
                         (config-with-preferences))]
    (when curr-config ; Stop the repl if it is already running
      (repl-manager/stop-internal-repl repl-id))    
      (when
        (zero? (verify-classpath classpath))
        (let [irepl (factory/create-managed-external-repl
                      (assoc updated-config :classpath classpath
                        :java-exe (get-default-java-exe))
                      -get-repl-window-factory-)]
          (.setResetReplFn
            (.getReplPanel irepl)
            #(do (repl-manager/stop-internal-repl repl-id)
               (start-stand-alone-repl repl-id java-args classpath)))          
          (repl-panel/evaluate-in-repl repl-id
            (str (repl-manager/get-settings-set-expression repl-id)))
            (-> irepl .getReplWindow .open)
            (-> irepl .getReplWindow .makeActive)))))

;========================================================================
; Top level action for external managed stand alone REPL startup (no project associations)
;========================================================================
(defn start-stand-alone-repl-action
  "Top level function that grabs the preferences and starts a stand alone repl"
  [action]
  (try
    (let [prefs (enclojure-options-category/get-stand-alone-settings)]
      (start-stand-alone-repl
        "Stand-alone REPL"
        (:jvm-additional-args prefs)
        (apply str (classpath-utils/classpath-for-repl)
          java.io.File/pathSeparator
          (interpose java.io.File/pathSeparator
            (:classpaths (:platform  prefs))))))
    (catch Exception e
      (error-reporting/report-error        
        (str "Stand alone repl failed to start.  Make sure you have Clojure
and Clojure.contrib jars in assigned Clojure Platform for the standalone REPL.
See the Enclojure category under preferences to view your settings" (.getMessage e)) e))))


(declare reset-repl)
;========================================================================
; External managed project REPL startup 
;========================================================================
(defn start-project-repl [#^Project p]
  (let [repl-id (repl-focus/get-project-name p)
        classpath (classpath-utils/get-repl-classpath p)
        curr-config (repl-manager/get-repl-config repl-id)
        updated-config (merge (or curr-config {:repl-id repl-id})
                         (config-with-preferences))]
    (try
      (logger/info "For project {} Verifying classpath {} " p classpath)
      (logger/info "updated-config {} " updated-config)
      (when
        (zero? (verify-classpath classpath))
        (let [irepl (factory/create-managed-external-repl
                      (assoc updated-config :classpath classpath
                        :java-exe (get-default-java-exe))
                      -get-repl-window-factory-)]
          (.setResetReplFn (.getReplPanel irepl) (partial reset-repl p))
          (repl-panel/evaluate-in-repl repl-id
            (str (repl-manager/get-settings-set-expression repl-id)))
          (-> irepl .getReplWindow .open)
          (-> irepl .getReplWindow .makeActive)))
    (catch Exception e
      (error-reporting/report-error        
        (str (format "Project REPL for %s failed to start.  Make sure you have Clojure
and Clojure.contrib jars as libraries in your project." repl-id) 
          "\nThese are both required to start a repl."
          "\nFor a project REPL, you can add them as libraries to the project.\n"
          (.getMessage e))
          e)))))
        

(defn stop-project-repl [proj repl-tc-closing?]
  (let [repl-id (if (string? proj) proj 
                    (repl-focus/get-project-name proj))]
    (if (= repl-id ReplTopComponent/IDE_REPL)
      ((:repl-fn (repl-manager/get-repl-config repl-id)) ":CLOSE-REPL")
      (repl-manager/stop-internal-repl repl-id))
    (when repl-tc-closing?
      (repl-manager/unregister-repl repl-id))))

(defn start-stop-project-repl [#^Project p]
  (let [repl-id (repl-focus/get-project-name p)]
    (if (repl-manager/repl-connected? repl-id)
      (stop-project-repl p nil)
      (start-project-repl p))))

(defn reset-repl [#^Project p]
  (stop-project-repl p nil)
  (start-project-repl p))

;========================================================================
; Support functions
;========================================================================
(defn repl-running?
  [#^Project p]
  (boolean
    (repl-manager/repl-connected?
        (repl-focus/get-project-name p))))

(defn run-context-menu-name [#^Project p]
  (let [repl-id (repl-focus/get-project-name p)]
    (ReplTopComponent/getBundleProperty
      (if (repl-manager/repl-connected? repl-id)
        "CTL_RunProjectWithReplAction_Stop"
        "CTL_RunProjectWithReplAction")
      repl-id)))

(defn repl-focus [top-window]
  (EventQueue/invokeLater
    #(-> top-window ._replPanel .GetEditorPane .requestFocusInWindow)))

(defn execute-expr
  "Finds the active top-component and sends the expression to the passed in repl
func. The repl-function is used to paste the expression into the repl-window first
(such as when 'eval expression' when you want history or not)"
  ([#^Project p expr nsnode repl-func]
  (let [repl-tc (repl-focus/find-active-repl p)]
    (when repl-tc
      (let [repl-id (.GetReplID repl-tc)]
        (.requestVisible repl-tc)
        (when (repl-panel/check-repl-form? expr repl-id)
          (repl-func repl-id expr nsnode))))))
  ([#^Project p expr nsnode]
    (execute-expr p expr nsnode repl-panel/evaluate-in-repl)))

(defn repl-debugging? [repl-id]
  (and (repl-manager/repl-connected? repl-id)
    (:dbg-engines (repl-manager/get-repl-config repl-id))))

(defn start-attach-detach-debugger [#^Project p]
  (let [repl-id (repl-focus/get-project-name p)
        _ (when-not (repl-manager/repl-connected? repl-id)
                (start-stop-project-repl p))
        {:keys [dbg-engines repl-panel]} (repl-manager/get-repl-config repl-id)]
    (if dbg-engines
      (jdi/kill-dbg repl-id)
      (jdi/attach-dbg repl-id (._debugPort repl-panel)))))

(defn debug-context-menu-name [#^Project p]
  (let [repl-id (repl-focus/get-project-name p)]
    (ReplTopComponent/getBundleProperty
      (if (repl-debugging? repl-id)
        "CTL_DetachDebugProjectWithReplAction"
        (if (repl-manager/repl-connected? repl-id)
          "CTL_AttachDebugProjectWithReplAction"
          "CTL_RunDebugProjectWithReplAction"))
      repl-id)))

(defn get-source-group
  "Given a project and an index into a source-group, return the source group. If
the index is out of range returns nil"
  [#^Project p index]
  (when-let [{:keys [name source-roots]}
                (add-file/get-project-data p)]
    (let [source-groups (into [] (sort source-roots))]
        (when (and (< index (count source-groups))
                  (>= index 0))
          (let [ret (source-groups index)]
            (logger/info "Returning the {} item from {} {}"
              index (class source-groups) source-groups)
            {:name (first ret) :source-group (fnext ret)})))))        

(defn load-all-source-context-menu-name
  "Gets the context menu with the source group name"
  [#^Project p source-group-inx]
  (let [repl-id (repl-focus/get-project-name p)]
    (let [{:keys [name source-group]} (get-source-group p source-group-inx)]       
      (if name
       (format
            (ReplTopComponent/getBundleProperty
              "CTL_LoadAllSourcesAction" repl-id) name) ""))))
  
(defn check-enabled-for-load-all-sources?
  "If the source-group is in range and the REPL is running returns true"
  [#^Project p source-group-inx]
  (boolean (when-let [source-group (get-source-group p source-group-inx)]
    (repl-running? p))))
  
(defn loadall-source-for-project
  "If the source group is in range, loads all clojure source files into the REPL"
  [#^Project p source-group-inx]
  (when-let [{:keys [name source-group]} (get-source-group p source-group-inx)]
    (let [repl-id (repl-focus/get-project-name p)
          sources (symbol-caching/file-obj-traverse
                    source-group
                    #(= "clj" (.getExt %)))
          {:keys [external local]} (repl-manager/get-repl-config repl-id)]
      (logger/debug "Found {} source files to load" (count sources))
      (doseq [source sources]
         (let [ns-f (classpath-utils/resource-name-from-full-path
                           (.getPath source))
               full-text (slurp (.getPath source))]
        (logger/info "Loading Resource {} in REPL. Full-path {}" ns-f (.getPath source))
        (execute-expr p
            (repl-panel/load-with-debug full-text (meta-utils/ns-from-file ns-f)) nil))))))
          

