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


(ns org.enclojure.ide.nb.editor.utils
    (:use org.enclojure.ide.repl.repl-manager
      org.enclojure.ide.common.classpath-utils)
    (:require [org.enclojure.ide.nb.actions.token-navigator :as token-navigator]
          [org.enclojure.ide.nb.classpaths.resource-tracking :as resource-tracking]
      [org.enclojure.ide.analyze.symbol-nav :as symbol-nav]
      )
    (:import (org.openide.modules InstalledFileLocator)
            (java.io File)
            (java.net URL URLEncoder)
            (org.netbeans.modules.editor NbEditorUtilities)
            (org.openide.filesystems FileUtil FileObject)
            (org.openide.cookies EditorCookie)
            (org.openide.windows TopComponent)
            (org.openide.util Mutex Mutex$Action)
            (org.netbeans.api.project.libraries LibraryManager Library)))

(defn locate-clojure-jar 
  ([jar-name]
  "returns a File object pointing to the installed clojure.lib with the Module"
        (.locate (org.openide.modules.InstalledFileLocator/getDefault)
            jar-name nil false))
  ([] (locate-clojure-jar "modules/ext/clojure-1.0.0.jar")))

(defn create-library [lib-mgr type name jar-file]
  (.createLibrary lib-mgr
                  type
                  name
                  {"classpath" [(URL. (str "jar:file:" (.replace (.getFile (.toURL jar-file)) " " "%20") "!/"))]}))

(defn create-external-clojure-lib [lib-name]
  "Creates an external library called lib-name using the internal clojure.jar installed with the module.
This is a temporary solution.  Users of the plugin should point to the desired clojure.jar
they want for their projects.  This will be a preference"
  (create-library (LibraryManager/getDefault) "j2se" lib-name (locate-clojure-jar)))

(defn ensure-clojure-lib [lib-name]
  "Makes sure there is a external library called lib-name pointing to the internal clojure.jar"
  (when-not (some #(.contains (.getDisplayName %) lib-name)
              (.getLibraries (LibraryManager/getDefault)))
    (create-external-clojure-lib lib-name)))

;----------------------------------------------------------
; Editor/document/file utilities
;----------------------------------------------------------
(defn from-doc-to-file
  "Given a document, return a java.io.File object"
  [base-document]
  (when-let [data-obj (NbEditorUtilities/getDataObject base-document)]
        (when-let [file (first (-> data-obj .files))]
           (FileUtil/toFile file))))

(defn get-current-editor-data []
  (when-let [ep (token-navigator/current-editor-pane-awt)]
    (let [d (.getDocument ep)]
    {:editor-pane ep
     :doc d
     :file (from-doc-to-file d)})))

(defn get-current-editor [e]
       (.readAccess Mutex/EVENT
         (proxy [Mutex$Action] []
           (run []
             (when-let [op (-> e .getOpenedPanes)]
               (first op))))))

(defn get-current-editor-cookie []
        (when-let [nodes (.getCurrentNodes (TopComponent/getRegistry))]
          (when (= (.length nodes) 1)
            (.getCookie (first nodes) EditorCookie))))

(defn get-current-editor []
   (when-let [e (get-current-editor-cookie)]
     (get-current-editor e)))

(defn get-file-object [file]
  (let [f (java.io.File. (resource-tracking/resolve-source-file file))]
    (let [fo (FileUtil/toFileObject f)]
      fo)))

;(defn open-editor-file
;  "Use this function to open the editor file.
;   e.g. (open-editor-file /Users/nsinghal/Work/scratch.clj 12)"
;  [file-path caret-pos]
;  (let [fileObject (get-file-object file-path)
;        dataObject (org.openide.loaders.DataObject/find fileObject)
;        editorCookie (.getCookie dataObject org.openide.cookies.EditorCookie)
;        _ (.open editorCookie)
;        pane (aget (.getOpenedPanes editorCookie) 0)]
;    (.setCaretPosition pane caret-pos)))

(defn open-editor-file-base
  "Use this function to open the editor file.
e.g. (open-editor-file /Users/nsinghal/Work/scratch.clj 12)
returns the editor-cookie used to open the file."
  ([file-path read-only?]
    (let [fileObject (if (instance? FileObject file-path) file-path
                       (get-file-object file-path))
          dataObject (org.openide.loaders.DataObject/find fileObject)
          editorCookie (.getCookie dataObject org.openide.cookies.EditorCookie)
          _ (.open editorCookie)
          pane (aget (.getOpenedPanes editorCookie) 0)]
      (when read-only?
        (.setEditable pane false))
      editorCookie))
  ([file-path]
    (open-editor-file-base file-path false)))

(defn open-editor-file
  "Use this function to open the editor file.
e.g. (open-editor-file /Users/nsinghal/Work/scratch.clj 12)
returns the editor-cookie used to open the file."
  ([file-path caret-pos read-only?]
    (let [editorCookie (open-editor-file-base file-path read-only?)
          pane (aget (.getOpenedPanes editorCookie) 0)]
      (.setCaretPosition pane caret-pos)
      editorCookie))
  ([file-path caret-pos]
    (open-editor-file file-path caret-pos false)))

(defn open-editor-file-at-line
  "Use this function to open the editor file.
   e.g. (open-editor-file /Users/nsinghal/Work/scratch.clj 12)"
  ([file-path line-no read-only?]
    (let [editorCookie (open-editor-file-base file-path read-only?)
          pane (aget (.getOpenedPanes editorCookie) 0)
        line-offset (symbol-nav/get-row-start-from-line (.getDocument pane) line-no)]
    (when (and line-offset (>= line-offset 0))
        (.setCaretPosition pane line-offset))
      pane))
  ([file-path line-no]
    (open-editor-file-at-line file-path line-no false)))

(defn file-from-doc
  "helper function to get the file related to a document"
  [document]
   (-> (NbEditorUtilities/getDataObject document)
                .getPrimaryEntry .getFile))

