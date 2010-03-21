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
(ns org.enclojure.ide.repl.DefReplWindowFactory
    (:gen-class
    :implements [org.enclojure.repl.IReplWindowFactory])
    (:import (org.enclojure.repl IReplWindow IReplWindowFactory IRepl)
        (java.io File OutputStreamWriter FileOutputStream)
            (javax.swing JFrame JScrollPane JWindow JPanel)
            (java.awt EventQueue)))

(defn top-window
  [title repl-panel]
  (let [jpanel (JFrame. title)]
    (doto jpanel
      (.add repl-panel))))

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

(defn -makeReplWindow [this repl-panel repl-context]
      (let [repl-id (:repl-id repl-context)
            repl-tc (JPanel.)];(top-window repl-id repl-panel)]
        (.add repl-tc repl-panel)
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
          )))
