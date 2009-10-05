(in-ns 'org.enclojure.ide.repl.factory)

(import '(java.io File)
        '(javax.swing JFrame JScrollPane)
        '(java.awt EventQueue))

(require '(org.enclojure.ide.settings [utils :as utils])
         '(org.enclojure.ide.repl [repl-data :as repl-data]))

(def base-lib-path "/Users/ericthor/Dev/enclojure/src/repl-client/lib-artifacts/")

(def libs ["clojure-contrib.jar"
            "clojure.jar"
            "commons-exec.jar"
            "commons-io.jar"
            "commons-logging.jar"
            "repl-server.jar"
            "slf4j-api.jar"
            "slf4j-jdk14.jar"
            "swing-layout.jar"])

(def -history-viewers-
  (ref {}))

(defn test-cp
  []
  (apply str
    (interpose java.io.File/pathSeparator
      (map #(format "%s%s" base-lib-path %1) libs))))

(defn top-window
  [title repl-panel]
  (let [jpanel (JFrame. title)]
    (doto jpanel
      (.add
        (JScrollPane. repl-panel)))))

(defn get-log-viewer
  [repl-id]
  (if-let [viewer (@-history-viewers- repl-id)]
    (doto viewer
      (.setVisible true)
      (.requestFocusInWindow))
    (let [irepl (:irepl (repl-manager/get-repl-config repl-id))]
    (alter -history-viewers- assoc
      repl-id
      (-> irepl .getReplWindow .showHistory)))))

(def -repl-window-factory-
  (proxy [IReplWindowFactory][]
    (makeReplWindow [repl-panel repl-context]
      (let [repl-id (:repl-id repl-context)
            repl-tc (top-window repl-id repl-panel)]
        (proxy [IReplWindow][]
            (getComponent [] repl-tc)
            (open [] (.setVisible repl-tc true))
            (makeActive []  (.setSize repl-tc 1000 1000)
                            (.setVisible repl-tc true)
                repl-tc)
            (showHistory []
                    (let [log-file (.getHistoryLogFile this)]
                        (when (.exists (File. log-file))
                          (let [edit-win (javax.swing.JEditorPane.)
                                file-data (slurp log-file)
                                document (.getDocument edit-win)]
                            (.insertString document 0 file-data nil)
                            (let [w (top-window (str "History for " repl-id)
                                      edit-win)]
                                (.setSize w 1000 1000)
                                (.setVisible w true) w)))))
            (getHistoryLogFile []
                (utils/get-pref-file-path
                  (str repl-id "-command-history.clj")))
          )))))

(def *test-context* (assoc (merge repl-data/-repl-context-external-managed-validation-
                      repl-data/-default-repl-data-)
                     :classpath (System/getProperty "java.class.path")
                     :startup-expr ""))

(defn test-repl
  []
  (let [{repl-id :repl-id :as repl-context}
        (assoc (merge repl-data/-repl-context-external-managed-validation-
                      repl-data/-default-repl-data-)
                     :classpath (test-cp)
                     :startup-expr "")
        irepl
            (create-managed-external-repl
                   (assoc
                    (merge
                      repl-data/-repl-context-external-managed-validation-
                        repl-data/-default-repl-data-)
                     :classpath (test-cp)
                     :startup-expr "")
                -repl-window-factory-)]
    (.setResetReplFn
            (.getReplPanel irepl)
            #(do (repl-manager/stop-internal-repl repl-id) (test-repl)))
      (repl-panel/evaluate-in-repl repl-id
        (str (repl-manager/get-settings-set-expression repl-id)))
        (-> irepl .getReplWindow .open)
        (-> irepl .getReplWindow .makeActive)))

(defn start-external-managed-repl
  []
  (EventQueue/invokeLater #(test-repl)))

