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

(ns org.enclojure.ide.nb.classpaths.resource-tracking
  (:require
    [clojure.set :as set]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (org.netbeans.api.java.classpath ClassPath GlobalPathRegistry
             GlobalPathRegistryEvent GlobalPathRegistryListener)
    (clojure.asm Opcodes)    
    (java.util.logging Level)    
    (org.netbeans.api.java.classpath
            ClassPath
            ClassPath$Entry
            GlobalPathRegistryEvent
            GlobalPathRegistry
            GlobalPathRegistryListener)    
    (org.netbeans.spi.java.classpath.support ClassPathSupport)
    (org.openide.filesystems FileObject FileStateInvalidException
      FileUtil JarFileSystem URLMapper)
    (java.io File FileWriter IOException StringReader StringWriter
      PrintStream PrintWriter OutputStream ByteArrayOutputStream)))
    
; setup logging
(logger/ensure-logger)

(def -source-roots- (ref {}))

(defn add-source-roots 
  "Given a set of ClassPaths store a map from source roots to ClassPaths"
  [classpath]
  (let [new-entries
          (reduce
                #(assoc %1 (.getPath %2) classpath) {} (.getRoots classpath))]
  (dosync
    (commute
      -source-roots-
        merge new-entries))))

(declare get-full-file)

(defn get-relative-file 
  "From a full-path path, return the full path.
check-full? determines if you want the function to see if the caller already
passed a valid relative path"
  ([full-path check-full?]
  (let [p (drop-while #(not (.startsWith full-path %1))
                    (keys @-source-roots-))]
    (if (pos? (count p))
        (str (subs full-path (inc (count (first p)))))
    ;check and see is it might already be a full path
        (if check-full?
          (when (get-full-file full-path false)
            full-path)))))
  ([full-path] (get-relative-file full-path true)))
    

(defn get-full-file 
  "From a relative path, return the full path.
    check-rel? determines if you want the function to see if the caller already
passed a valid full path"
  ([relative-path check-rel?]
  (let [r (drop-while
                 #(not (.findResource %1 relative-path))
                    (set (vals @-source-roots-)))]    
    (if (pos? (count r))
        (str (first r) File/separator relative-path)
      ; Caller may have passed in a full file to begin with
      (if check-rel?
        (when (get-relative-file relative-path false)
            relative-path)))))
  ([relative-path] (get-full-file relative-path true)))

(defn resolve-source-file 
  "Given a valid existing source file with a relative or full path, return the full 
path of the file"
  [file]
  (if (.exists (File. file))
    file
    (when-let [root (first (drop-while #(not (.exists (File. (FileUtil/toFile %) file)))
                             (.getSourceRoots (GlobalPathRegistry/getDefault))))]
      (.getPath (File. (FileUtil/toFile root) file)))))

(defn seed-paths []
  (dorun (map
           add-source-roots
            (.getPaths (GlobalPathRegistry/getDefault) "classpath/source"))))

(def f "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/completion/file_mapping.clj")
(def fr "org/enclojure/ide/nb/editor/completion/file_mapping.clj")

(def bogus-full "/Users/ericthosrsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/completion/file_mapping.clj")
(def bogus-rel "org/enclojurse/ide/nb/editor/completion/file_mapping.clj")