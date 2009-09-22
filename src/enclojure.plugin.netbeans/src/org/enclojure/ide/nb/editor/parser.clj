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
  (:use org.enclojure.ide.ClojureLexer)
  (:require [org.enclojure.ide.nb.actions.token-navigator :as token-navigator]
            [org.enclojure.ide.navigator.token-nav :as token-nav]
            [org.enclojure.commons.c-slf4j :as logger]
            clojure.inspector clojure.set)
  (:import
    (java.util.logging Level)
    (java.io FileReader File)
    (org.openide.filesystems FileObject FileUtil)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence)))

; setup logging
(logger/ensure-logger)

(def -parsed-files-
 (ref {}))

(defn get-prop [data-object path]  
    (proxy [java.beans.PropertyChangeListener] []
      (propertyChange [event]
        (logger/info "Thread : {} DataObj: {} : doc changed :{}" (hash (Thread/currentThread))  (hash data-object) (bean event))
      (logger/info  "Thread : {} DataObj: {} : doc changed :{} path {}" (hash (Thread/currentThread)) (hash data-object) (hash data-object) path))))


(defn new-parser-data [file-object data-object]
  (let [data (agent {:fo file-object :data-obj data-object})
        full-path (.getCanonicalPath (FileUtil/toFile file-object))
        editor-cookie (.lookup (.getLookup data-object) org.openide.cookies.EditorCookie$Observable)
        d (when editor-cookie
            (.getDocument editor-cookie))
        db (when d (bean d))
        _ (.addPropertyChangeListener editor-cookie (get-prop data-object full-path))]
    (logger/info "Thread : {} {} : creating data for {}" (hash (Thread/currentThread)) (hash data-object))
    (logger/info "Thread : {} {} : Full: {}" (hash (Thread/currentThread)) (hash data-object)  full-path)
    (logger/info "{}" data-object)
    (logger/info "{}" (bean data-object))
    (logger/info "Doc: {} cookie: {}" db editor-cookie)
    data))
