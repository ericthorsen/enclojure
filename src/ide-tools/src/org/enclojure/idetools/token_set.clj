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

(ns #^{ :author "Eric Thorsen",
        :doc "Protocol for org.enclojure.idetools.token-set"}
		org.enclojure.idetools.token-set	
        (:require [org.enclojure.idetools.tokens :as tokens]))


(def -TOKEN-TYPES-BY-ID-
  (when-not *compile-files*
    (apply vector (filter
             (fn [[k v]]
               (not= #{'-TOKEN-TYPES-BY-ID- '-TOKEN-TYPES-MAP- 'get-java-def} k))
                 (ns-publics (find-ns 'org.enclojure.idetools.tokens))))))


(def -TOKEN-TYPES-MAP-
  (when-not *compile-files*
      (reduce (fn [m i]
                (let [[k kv] (-TOKEN-TYPES-BY-ID- i)
                      v (when (.isBound kv) (.get kv))]
                    (if (and v (map? v))
                        (assoc m k (assoc v :ID i))
                    m))) {}
            (range (count -TOKEN-TYPES-BY-ID-)))))

(defn get-java-def
  []
  (let [fmt-str "\tfinal static IPersistentMap %s = (IPersistentMap)RT.var(\"org.enclojure.ide.tokens\",\"%s\").get();\n"
        tokens  (distinct
                  (keys (dissoc
                        (ns-publics (find-ns 'org.enclojure.idetools.tokens))
                            '-TOKEN-TYPES-)))
        java-tokens (map #(.replace (str %1) "-" "_")
                      tokens)]
    ;(println (count tokens) " " (count java-tokens))
    (apply str
      (map format (cycle [fmt-str]) java-tokens tokens))))

