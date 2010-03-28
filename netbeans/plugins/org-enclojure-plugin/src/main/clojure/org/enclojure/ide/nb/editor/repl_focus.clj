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
(ns org.enclojure.ide.nb.editor.repl-focus
  (:use org.enclojure.ide.repl.repl-manager    
    org.enclojure.ide.nb.actions.token-navigator
    org.enclojure.ide.navigator.token-nav)  
  (:import
    (org.openide.windows TopComponent)
    (org.openide.cookies EditorCookie)
    (org.netbeans.api.project Project ProjectInformation)
    (org.enclojure.ide.nb.editor ReplTopComponent)))

(def last-activated-repl (atom nil))

(defn get-project-name [#^Project p]
  (ReplTopComponent/GetProjectName p))
  ;(-> p .getLookup (.lookup (class ProjectInformation)) .getDisplayName))

(defn all-repls []
  (remove nil? (map #(:repl-tc %) (all-repl-configs))))

(defn find-first-visible-repl []
  (let [repls (all-repls)]
    (loop [repls repls]
      (when-let [repl (first repls)]
        (if (.isVisible repl)
          repl
          (recur (next repls)))))))

(defn find-project-repl [#^Project p]
  (let [project-name (get-project-name p)
        local? (nil? project-name)
        {:keys [repl-tc]} (get-repl-config (if local? (ReplTopComponent/IDE_REPL) project-name))]
    repl-tc))

(defn find-active-repl [#^Project p]
  (some #(if (instance? clojure.lang.IFn %) (%) %)
    [
    (:repl-tc (get-repl-config @last-activated-repl))
    find-first-visible-repl
    (find-project-repl p)
    (first (all-repls))
     ]))

(defn set-caret-visibility [repl-name repl-pane activated?]
  (let [editor-pane (current-editor-pane)]
    (if activated?
      (do
        (swap! last-activated-repl (fn[_] repl-name))
        (when editor-pane
          (-> editor-pane .getCaret (.setVisible false))))
      (-> repl-pane .getCaret (.setVisible false)))))

