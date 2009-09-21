(comment
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Eric Thorsen
)

(ns org.enclojure.commons.meta-utils)

(defmacro defrt
  "wraps the initialization of a def in a (when (not *compile-files*)) form"
  [name & body]
  `(def ~name
     (when (not *compile-files*)
       ~@body)))

;(defn load-string-with-dbg 
;  "Load a string using the source-path and file name for debug info."
;  [str-data source-path file]
;  (clojure.lang.Compiler/load
;    #^java.io.Reader (java.io.StringReader. str-data)
;        #^String source-path #^String file))

(defn root-resource
  "Returns the root directory path for a lib"
  [lib]
  (str (-> lib (.replace \- \_) (.replace \. java.io.File/separatorChar))))

(defn root-directory
  "Returns the root resource path for a lib"
  [lib]
  (let [d (root-resource lib)]
    (subs d 0 (.lastIndexOf d "/"))))

(defn source-path-from-ns
  "return a clj file from the clojure namespace"
  [ns]
  (str (root-resource ns) ".clj"))

(defn file-from-ns
  "return a clj file from the clojure namespace"
  [ns]
  (let [parts (vec (.split ns "[.]"))]
    (-> (str (parts (dec (count parts))) ".clj")
    (.replace \- \_ ) )))

(defn ns-from-file
  "return a namespace name from a clojure file"
  [file]
  (let [endinx (.lastIndexOf file ".clj")]
    (-> (if (pos? endinx) (subs file 0 endinx) file)
      (.replace \_ \-) (.replace java.io.File/separatorChar \.))))

(defn classname-from-file 
  "Given a class file name, returns the proper java class name"
  [file]
    (let [endinx (.lastIndexOf file ".class")]
    (-> (if (pos? endinx) (subs file 0 endinx) file)
      (.replace \/ \.))))

(defn package-name-from-class
  "Given a class file name, returns the proper java class name"
  [clsname]
    (let [cname (classname-from-file clsname)
          endinx (.lastIndexOf cname ".")]
        (if (pos? endinx) (subs cname 0 endinx) cname)))

