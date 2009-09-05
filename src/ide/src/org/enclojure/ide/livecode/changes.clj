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

(ns org.enclojure.ide.livecode.changes
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  )

; setup logging
(logger/ensure-logger)

(def -prev-ns-hashes- (atom {}))

(defn clear-cache []
  (swap! -prev-ns-hashes- (fn [_] {})))

(defn get-val-for [v]
  (hash (if-let [{tag :tag} (meta v)]
    (if (.isBound v)
      (if (= tag clojure.lang.MultiFn)
        (methods (.get v)) (.get v))
      0))))

(defn all-ns-no-clojure []
  (let [cc (find-ns 'clojure.core)]
    (filter #(not= cc %1) (all-ns))))

(defn ns-hash
  "Generate a hash for a namespace using the values of all the interned
vars to determine if anything has changed on the namespace. For multimethods,
it looks at the dispatch map."
  [ns]
  (reduce #(bit-xor %1 (-> %2 fnext get-val-for hash))
    0 (ns-interns ns)))

(defn get-ns-hashes
  "Returns a map of namespace names to hashes excluding the clojure.core namespace.
Loading code in clojure changes the state of the clojure.core namespace which is
why it is omitted."
  []
  (reduce #(assoc %1 (ns-name %2) (ns-hash %2))
    {}
    (all-ns-no-clojure)))

(defn changed-namespaces
  "Given a map of old hashes and new hashes, return all the differing hashes based
on the union of keys from both maps.  Detects changes from one map in another."
  [new-hashes previous-hashes]
  (if previous-hashes
    (apply hash-map
        (reduce #(conj %1 %2 (new-hashes %2))
          []
            (filter #(not= (new-hashes %1) (previous-hashes %1))
                (clojure.set/union (keys new-hashes) (keys previous-hashes)))))
    new-hashes))

(defn update-hashes-return-changes []
  (let [curr-hashes @-prev-ns-hashes-
        new-hashes (get-ns-hashes)
        delta (changed-namespaces new-hashes curr-hashes)]
    (swap! -prev-ns-hashes- (fn [_] new-hashes))
        delta))

(defn reset-hashes []
    (swap! -prev-ns-hashes- (fn [_] (get-ns-hashes))))

