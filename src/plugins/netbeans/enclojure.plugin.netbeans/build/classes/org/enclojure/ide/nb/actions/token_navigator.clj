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

(ns org.enclojure.ide.nb.actions.token-navigator
  (:use
    org.enclojure.ide.navigator.token-nav
    )
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import
    (javax.swing JEditorPane)
    (java.util.logging Level)
    (java.awt EventQueue)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
    (org.openide.windows TopComponent)
    (org.openide.cookies EditorCookie)
    ))

; setup logging
(logger/ensure-logger)

(defn editor-cookie [nodes]
  (when (= (alength nodes) 1)
    (. (aget nodes 0) (getCookie (identity EditorCookie)))))

(defn current-editor-pane
  "Get the current JEditorPane."
  ([nodes]
    (when-let [ec (editor-cookie nodes)]
      (aget (.getOpenedPanes ec) 0)))
  ([]
    (current-editor-pane (-> (TopComponent/getRegistry) .getActivatedNodes))))

(defn current-editor-pane-awt []
  "Get the current JEditorPane in AWT thread"
  (let [pane (atom nil)]
    (EventQueue/invokeAndWait
      #(swap! pane (fn [_] (current-editor-pane))))
    @pane))

(defn current-editor-pane-doc []
  "Get the current doc"
    (.getDocument (current-editor-pane-awt)))