(comment
;*
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
(ns org.enclojure.ide.repl.classpaths
  (:import (java.io File FilenameFilter)
  ))


(def -jar-file-filter-
    (proxy [java.io.FilenameFilter][]
      (accept [dir name] (boolean (.endsWith name ".jar")))))

(defn jars-from-path
  "Helper to return a set a jars from a path.  Does not recur."
  [classpath]
  (reduce #(conj %1 (.getCanonicalPath %2))
    [] (.listFiles
         (File. classpath) -jar-file-filter-)))

(defn build-classpath-with-jars
  "Given a path, return a set of classpaths with the path + all jars contained within"
  [classpath]
  (conj (jars-from-path classpath) classpath))

(defn build-classpath-str
    "Takes a list of paths (items that can construct File objects)
and returns a classpath string with the paths and any jars contained within"
    [classpaths]
    (let [all-paths (reduce
                      #(concat %1
                         (build-classpath-with-jars %2)) [] classpaths)]
      ;attempts to keep the order while eliminating dups
      (loop [paths [] index #{} candidates all-paths]
        (if-let [p (first candidates)]
          (recur (if (index p)
                   paths (conj paths p))
            (conj index p) (rest candidates))
          (apply str (interpose File/pathSeparator paths))))))






