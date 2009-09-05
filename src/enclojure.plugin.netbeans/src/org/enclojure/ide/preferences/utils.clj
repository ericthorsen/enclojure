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
(ns org.enclojure.ide.preferences.utils
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import
    (org.openide.util NbPreferences)
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
  (let [env (into {} (System/getenv)) home (if (env "HOME") (env "HOME") (env "HOMEPATH"))]
        (str home (File/separator) ".enclojure-prefs")))

(defn get-pref-file-path 
  "Given a config category, returns a path for storing/retrieving config data for the given category"
  [config-category]
  (let [env (into {} (System/getenv)) home (if (env "HOME") (env "HOME") (env "HOMEPATH"))
        base-path (File. (str home (File/separator) ".enclojure-prefs"))
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

(defn p-node [cls]
  (NbPreferences/forModule cls))

(defn fetch-for
  ([node] (apply hash-map
            (apply concat
              (map #(vector (keyword %) 
                      (. node (get % nil))) 
                (. node (keys))))))
  ([node default-settings]
    (merge default-settings (fetch-for node))))

(defn fetch [cls]
  (fetch-for (p-node cls)))

(defn put [n k v]
  (. n (put (if (keyword? k) 
              (name k)
              (str k)) (str v))))

(defn store-for
  ([node data]
    (doseq [k (keys data)]
      (put node k (data k)))))

(defn store [cls data]
  (let [n (p-node cls)]
    (store-for n data)))

(defn clear 
  ([cls]
    (let [n (p-node cls)]
      (. (p-node cls) (clear))))
  ([cls default-data]
    (clear cls)
    (store cls default-data)))

