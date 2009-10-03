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
;*    Author: Eric Thorsen
;*******************************************************************************
)
(ns org.enclojure.ide.nb.editor.repl-factory
  (:require
        [org.enclojure.ide.nb.editor.utils :as utils]
        [org.enclojure.ide.settings.utils :as pref-utils])
  (:import
    (org.enclojure.repl IReplWindow IReplContext IReplTypeFactory)
    (org.enclojure.ide.repl ReplPanel)
    (org.enclojure.ide.nb.editor ReplTopComponent)
  ))

(defn get-repl-context
  [repl-id classpath attribs]
  (assert (string? repl-id))
  (assert (string? classpath))
  (assert (map? attribs))
  (let [repl-panel (ReplPanel. repl-id)]
    (proxy [IReplContext][]
        (getId [] repl-id)
        (getClassPath[] classpath)
        (getAttribs [] attribs)
        (getReplPanel [] repl-panel))))

(defn get-repl-window
  [repl-context]
  (assert (instance? IReplContext repl-context))
  (let [repl-id (.getId repl-context)
        repl-tc (ReplTopComponent.
                  (.getReplPanel repl-context))]
    (proxy [IReplWindow][]
        (open [] (.open repl-tc))
        (makeActive [] (.requestVisible repl-tc)
                       (.requestActive repl-tc)
          repl-tc)
        (getReplContext [] repl-context)
        (showHistory []
                (let [log-file (File. (.getHistoryLogFile this repl-id))]
                    (when (.exists log-file)
                        (utils/open-editor-file-at-line
                            (.getCanonicalPath log-file) 1 true))))
        (getHistoryLogFile []
            (pref-utils/get-pref-file-path
              (str repl-id "-command-history.clj")))
      )))





