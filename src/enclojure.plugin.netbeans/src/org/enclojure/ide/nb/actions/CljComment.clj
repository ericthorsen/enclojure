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
;*    Author: Narayan Singhal ,  Eric Thorsen
;*******************************************************************************
)

(ns org.enclojure.ide.nb.actions.CljComment
  (:import
    (javax.swing.text Document)
    (javax.swing JEditorPane)    
    (org.netbeans.editor BaseDocument)
    (org.openide.cookies EditorCookie)
    (org.openide.nodes Node)
    (org.openide.util Exceptions HelpCtx NbBundle)
    (org.openide.util.actions CallableSystemAction)
    (org.openide.windows TopComponent)))
    
(defn split-lines [str]
   (let [grps (re-seq #"(.*\n)|(.+$)" str)]
      (map #(first %) grps)))

(defn toggle-comments [txt]
   (let [lines (split-lines txt)
         commented? (every? #(= (first (.trim %)) \;) lines)]
      (apply str (map #(if commented?
                          (.replaceFirst % ";" "")
                          (str ";" %))
                    lines))))

(defn doc-toggle-comments [#^JEditorPane pane]
  (let [#^Document doc (.. pane getDocument)
        n0 (.. pane getSelectionStart)
        n2 (.. pane getSelectionEnd)
        n1 (if (zero? n0) n0
             (loop [n (dec n0)];move back to first new line, decrement first in case we are on a new line char
               (if (= (.charAt (. doc (getText n 1)) 0) \newline)
                 (inc n)
                 (recur (dec n)))))
        len (- n2 n1)]
       (let [txt (. doc (getText n1 len))
             changed-txt (if (zero? (. txt length))
                           ";" (toggle-comments txt))]
          (.atomicLock (cast BaseDocument doc))
          (try
             (. doc (remove n1 len))
             (. doc (insertString n1 changed-txt nil))
             (. pane (select n1 (+ n1 (. changed-txt length))))
             (catch Exception ex
                (.atomicUndo (cast BaseDocument doc)))
             (finally
                (.atomicUnlock (cast BaseDocument doc)))))))

(defn perform-action [ec]
          (when ec
            (let [panes (.getOpenedPanes ec)]
               (when (pos? (count panes))
                  (try
                     (doc-toggle-comments (aget panes 0))
                     (catch Exception ex
                        (.. Exceptions (printStackTrace ex))))))))


