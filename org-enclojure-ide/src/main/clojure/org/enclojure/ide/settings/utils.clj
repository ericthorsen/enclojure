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
(ns org.enclojure.ide.settings.utils
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import
    (java.io File OutputStreamWriter FileOutputStream)
    (java.util.logging Level)
    (java.util.prefs Preferences)
    ))

; setup logging
(logger/ensure-logger)

;------------------------------------------------------
; Using clojure data structures on disk
;------------------------------------------------------
(defn get-pref-file-base
  "Given a config category, returns a path for storing/retrieving config data for the given category"
  []
  (str (System/getProperty "netbeans.user") (File/separator) "config" (File/separator) "enclojure-prefs"))

(defn get-pref-file-path 
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

(defn put-prefs 
  "Given a config category and a set of prefs
    (can be any readable clojure data structure), store it on disk.  Retrieve with
   get-prefs"
  [config-category prefs]
  (with-open [out (OutputStreamWriter. 
                    (FileOutputStream. (File. (get-pref-file-path config-category))))]
        (binding [*out* out]
          (prn prefs))))

(defn get-prefs 
  "Given a config category read the int the preferences"
  [config-category]
  (let [p (get-pref-file-path config-category)]
    (when (.exists (java.io.File. p))
        (read-string (slurp p)))))
