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
(ns org.enclojure.ide.repl.factory-test
 (:use org.enclojure.ide.repl.factory
   org.enclojure.ide.repl.DefReplWindowFactory)
 (:require
   [org.enclojure.ide.repl.repl-manager :as repl-manager]
   [org.enclojure.ide.repl.repl-panel :as repl-panel]
   [org.enclojure.ide.repl.repl-data :as repl-data])
 (:import (org.enclojure.repl IReplWindow IReplWindowFactory IRepl)
    (java.io File OutputStreamWriter FileOutputStream)
        (javax.swing JFrame JScrollPane)
        (java.awt EventQueue)
    (org.enclojure.ide.repl DefReplWindowFactory)))

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

(defn- get-pref-file-base
  "Given a config category, returns a path for storing/retrieving config data for the given category"
  []
  (let [env (into {} (System/getenv))
        home (or (env "USERPROFILE") (env "user.home") (env "netbeans.user")
               (env "HOME") (env "HOMEPATH"))]
        (str home (File/separator) ".netbeans" (File/separator) "enclojure-prefs")))

(defn- get-pref-file-path
  "Given a config category, returns a path for storing/retrieving config data for the given category"
  [config-category]
  (let [base-path (File. (get-pref-file-base))
        pfile (File. base-path config-category)]
    (when-not (.exists pfile)
      (.mkdirs base-path)
      (.createNewFile pfile)
      (with-open [out (OutputStreamWriter. (FileOutputStream. pfile))]
        (binding [*out* out]
          (prn {}))))
    (.getCanonicalPath pfile)))

(defn test-cp
  []
  (apply str
    (interpose java.io.File/pathSeparator
      (map #(format "%s%s" base-lib-path %1) libs))))

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
                (get-pref-file-path
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
              (DefReplWindowFactory.))]
    (.setResetReplFn
            (.getReplPanel irepl)
            #(do (repl-manager/stop-internal-repl repl-id) (test-repl)))
      (repl-panel/evaluate-in-repl repl-id
        (str (repl-manager/get-settings-set-expression repl-id)))
        (-> irepl .getReplWindow .open)
        (-> irepl .getReplWindow .makeActive)))


(defn test-create-in-proc-repl
  []
  (let [latch (java.util.concurrent.CountDownLatch. 1)
        irepl (atom nil)
        frame (JFrame. "Inproc REPL Frame")
        ]
    (EventQueue/invokeAndWait
        #(swap! irepl (fn [_] (create-in-proc-repl))))
    (.setLayout frame (java.awt.GridLayout. 1 1))
    (.add frame (-> @irepl .getReplPanel))
    (EventQueue/invokeLater
      #(do
         (.setSize frame 1000 1000)
         (.setVisible frame true)))
    (.await latch)))

(defn test-create-external-server-and-connect-repl
  []
  )

(defn test-create-managed-repls
  []
    (let [latch (java.util.concurrent.CountDownLatch. 1)
        irepl (atom nil)
        frame (JFrame. "Managed external REPL Frame")
        ]
    (EventQueue/invokeAndWait
        #(swap! irepl 
           (fn [_]
             (reduce (fn [v id]
                       (conj v (create-managed-external-repl
                                 {:repl-id (format "Repl-%s" id)}))) []
               (range 3)))))
    (.setLayout frame (java.awt.GridLayout. 3 1))
    (doseq [repl @irepl]
      (.add frame (-> repl .getReplPanel)))
    (EventQueue/invokeLater
      #(do
         (.setSize frame 1000 1000)
         (.setVisible frame true)))
    (.await latch)))
