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

(ns org.enclojure.ide.nb.editor.parser
  (:use org.enclojure.commons.meta-utils
        org.enclojure.commons.logging
        org.enclojure.ide.ClojureLexer)
  (:require [org.enclojure.ide.nb.actions.token-navigator :as token-navigator]
            [org.enclojure.ide.navigator.token-nav :as token-nav]
            clojure.inspector clojure.set)
  (:import
    (java.util.logging Level)
    (java.io FileReader File)
    (org.openide.filesystems FileObject FileUtil)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence)))

(defrt #^{:private true} log (get-ns-logfn))

(defrt -parsed-files-
 (ref {}))

(defn get-prop [data-object path]  
    (proxy [java.beans.PropertyChangeListener] []
      (propertyChange [event]
        (log Level/INFO "Thread : " (hash (Thread/currentThread)) " DataObj: " (hash data-object)  " : doc changed " (bean event))
      (log Level/INFO  "Thread : " (hash (Thread/currentThread)) " DataObj: " (hash data-object) (hash data-object)  " : doc changed " path))))


(defn new-parser-data [file-object data-object]
  (let [data (agent {:fo file-object :data-obj data-object})
        full-path (.getCanonicalPath (FileUtil/toFile file-object))
        editor-cookie (.lookup (.getLookup data-object) org.openide.cookies.EditorCookie$Observable)
        d (when editor-cookie
            (.getDocument editor-cookie))
        db (when d (bean d))
        _ (.addPropertyChangeListener editor-cookie (get-prop data-object full-path))]
    (log Level/INFO "Thread : " (hash (Thread/currentThread)) (hash data-object) " : creating data for " (.getNameExt file-object))
    (log Level/INFO "Thread : " (hash (Thread/currentThread)) (hash data-object) " : Full: " full-path)
    (log Level/INFO data-object)
    (log Level/INFO (bean data-object))
    (log Level/INFO "Doc: " db " cookie: " editor-cookie)
    data))

 