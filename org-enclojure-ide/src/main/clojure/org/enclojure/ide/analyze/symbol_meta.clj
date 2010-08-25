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

(ns org.enclojure.ide.analyze.symbol-meta
  (:use [clojure.main :exclude (with-bindings)])
  (:import (java.util.logging Level))
  (:require
    [clojure.set :as set]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.commons.c-slf4j :as logger]
    ))
; setup logging
(logger/ensure-logger)
;(def sample-ns-record
;  {:symbols
; {rename-keys
;  ({:form
;    (defn
;     rename-keys
;     "Returns the map with the keys in kmap renamed to the vals in kmap"
;     [map kmap]
;     (reduce
;      (fn
;       [m [old new]]
;       (if (not= old new) (-> m (assoc new (m old)) (dissoc old)) m))
;      map
;      kmap)),
;    :name rename-keys,
;    :start 2233,
;    :end 2476,
;    :static true,
;    :lang :clojure,
;    :line-end 77,
;    :type :func,
;    :line 69,
;    :symbol-type defn}),
;
;union
;  ({:form
;    (defn
;     union
;     "Return a set that is the
;union of the input sets"
;
;([] #{})
;     ([s1] s1)
;     ([s1 s2]
;      (if
;       (< (count s1) (count s2))
;       (reduce conj s2 s1)
;       (reduce conj s1 s2)))
;     ([s1 s2 & sets]
;      (let
;       [bubbled-sets (bubble-max-key count (conj sets s2 s1))]
;       (reduce into (first bubbled-sets) (rest bubbled-sets))))),
;    :name union,
;    :start 703,
;    :end 1043,
;    :static true,
;    :lang :clojure,
;    :line-end 26,
;    :type :func,
;    :line 16,
;    :symbol-type defn}),
;  select
;  ({:form
;    (defn
;     select
;     "Returns a set of the elements for which pred is true"
;     [pred xset]
;     (reduce (fn [s k] (if (pred k) s (disj s k))) xset xset)),
;    :name select,
;    :start 1937,
;    :end 2097,
;    :static true,
;    :lang :clojure,
;    :line-end 62,
;    :type :func,
;    :line 57,
;    :symbol-type defn}),
;  project
;  ({:form
;    (defn
;     project
;     "Returns a rel of the elements of xrel with only the keys in ks"
;     [xrel ks]
;     (set (map (fn* [p1__1799] (select-keys p1__1799 ks)) xrel))),
;    :name proje
;ct,
;    :start 2097,
;    :end 2233,
;    :static true,
;    :lang :clojure,
;    :line-end 67,
;    :type :func,
;    :line 64,
;    :symbol-type defn}),
;  join
;  ({:form
;    (defn
;     join
;     "When passed 2 rels, returns the rel corresponding to the natural\n  join. When passed an additional keymap, joins on the corresponding\n  keys."
;     ([xrel yrel]
;      (if
;       (and (seq xrel) (seq yrel))
;       (let
;        [ks
;         (intersection
;          (set (keys (first xrel)))
;          (set (keys (first yrel))))
;         [r s]
;         (if (<= (count xrel) (count yrel)) [xrel yrel] [yrel xrel])
;         idx
;         (index r ks)]
;        (reduce
;         (fn
;          [ret x]
;          (let
;           [found (idx (select-keys x ks))]
;           (if
;            found
;            (reduce
;             (fn*
;              [p1__1801 p2__1802]
;              (conj p1__1801 (merge p2__1802 x)))
;             ret
;             found)
;            ret)))
;         #{}
;         s))
;       #{}))
;     ([xrel yrel km]
;
;    (let
;       [[r s k]
;        (if
;         (<= (count xrel) (count yrel))
;         [xrel yrel (map-invert km)]
;         [yrel xrel km])
;        idx
;        (index r (vals k))]
;       (reduce
;        (fn
;         [ret x]
;         (let
;          [found (idx (rename-keys (select-keys x (keys k)) k))]
;          (if
;           found
;           (reduce
;            (fn*
;             [p1__1803 p2__1804]
;             (conj p1__1803 (merge p2__1804 x)))
;            ret
;            found)
;           ret)))
;        #{}
;        s)))),
;    :name join,
;    :start 3046,
;    :end 4176,
;    :static true,
;    :lang :clojure,
;    :line-end 126,
;    :type :func,
;    :line 98,
;    :symbol-type defn}),
;  comment
;  ({:form
;    (comment
;     (refer 'set)
;     (def
;      xs
;      #{{:a 11, :b 1, :c 1, :d 4} {:a 3, :b 3, :c 3, :d 8, :f 42}
;        {:a 2, :b 12, :c 2, :d 6}})
;     (def
;      ys
;      #{{:a 12, :b 11, :c 12, :e 3} {:a 3, :b 3, :c 3, :e 7}
;        {:a 11, :b 11, :c 11, :e 5}})
;     (join xs ys)
;     (join xs (ren
;ame ys {:b :yb, :c :yc}) {:a :a})
;     (union #{:a :c :b} #{:c :d :e})
;     (difference #{:a :c :b} #{:c :d :e})
;     (intersection #{:a :c :b} #{:c :d :e})
;     (index ys [:b])),
;    :name comment,
;    :start 4176,
;    :end 4596,
;    :static true,
;    :lang :clojure,
;    :line-end 146,
;    :type :comment,
;    :line 128,
;    :symbol-type comment}),
;  intersection
;  ({:form
;    (defn
;     intersection
;     "Return a set that is the intersection of the input sets"
;     ([s1] s1)
;     ([s1 s2]
;      (if
;       (< (count s2) (count s1))
;       (recur s2 s1)
;       (reduce
;        (fn
;         [result item]
;         (if (contains? s2 item) result (disj result item)))
;        s1
;        s1)))
;     ([s1 s2 & sets]
;      (let
;       [bubbled-sets
;        (bubble-max-key
;         (fn* [p1__1798] (- (count p1__1798)))
;         (conj sets s2 s1))]
;       (reduce
;        intersection
;        (first bubbled-sets)
;        (rest bubbled-sets))))),
;    :name intersection,
;    :start 1043,
;    :end 1518,
;    :static tru
;e,
;    :lang :clojure,
;    :line-end 41,
;    :type :func,
;    :line 28,
;    :symbol-type defn}),
;  bubble-max-key
;  ({:form
;    (defn-
;     bubble-max-key
;     [k coll]
;     "Move a maximal element of coll according to fn k (which returns a number) \n   to the front of coll."
;     (let
;      [max (apply max-key k coll)]
;      (cons
;       max
;       (remove (fn* [p1__1797] (identical? max p1__1797)) coll)))),
;    :name bubble-max-key,
;    :start 480,
;    :end 703,
;    :static true,
;    :lang :clojure,
;    :line-end 14,
;    :line 10,
;    :symbol-type defn-}),
;  map-invert
;  ({:form
;    (defn
;     map-invert
;     "Returns the map with the vals mapped to the keys."
;     [m]
;     (reduce (fn [m [k v]] (assoc m v k)) {} m)),
;    :name map-invert,
;    :start 2921,
;    :end 3046,
;    :static true,
;    :lang :clojure,
;    :line-end 96,
;    :type :func,
;    :line 94,
;    :symbol-type defn}),
;  difference
;  ({:form
;    (defn
;     difference
;     "Return a set that is the first set without elements of
;the remaining sets"
;     ([s1] s1)
;     ([s1 s2]
;      (if
;       (< (count s1) (count s2))
;       (reduce
;        (fn
;         [result item]
;         (if (contains? s2 item) (disj result item) result))
;        s1
;        s1)
;       (reduce disj s1 s2)))
;     ([s1 s2 & sets] (reduce difference s1 (conj sets s2)))),
;    :name difference,
;    :start 1518,
;    :end 1937,
;    :static true,
;    :lang :clojure,
;    :line-end 55,
;    :type :func,
;    :line 43,
;    :symbol-type defn}),
;  rename
;  ({:form
;    (defn
;     rename
;     "Returns a rel of the maps in xrel with the keys in kmap renamed to the vals in kmap"
;     [xrel kmap]
;     (set (map (fn* [p1__1800] (rename-keys p1__1800 kmap)) xrel))),
;    :name rename,
;    :start 2476,
;    :end 2636,
;    :static true,
;    :lang :clojure,
;    :line-end 82,
;    :type :func,
;    :line 79,
;    :symbol-type defn}),
;  clojure.set
;  ({:form (ns clojure.set),
;    :name clojure.set,
;    :start 0,
;    :end 480,
;    :static true,
;    :lang :clojure,
;    :line-end 8,
;
;   :type :namespace,
;    :line 1,
;    :symbol-type ns}),
;  index
;  ({:form
;    (defn
;     index
;     "Returns a map of the distinct values of ks in the xrel mapped to a\n  set of the maps in xrel with the corresponding values of ks."
;     [xrel ks]
;     (reduce
;      (fn
;       [m x]
;       (let
;        [ik (select-keys x ks)]
;        (assoc m ik (conj (get m ik #{}) x))))
;      {}
;      xrel)),
;    :name index,
;    :start 2636,
;    :end 2921,
;    :static true,
;    :lang :clojure,
;    :line-end 92,
;    :type :func,
;    :line 84,
;    :symbol-type defn})},
; :root
; #<AbstractFileObject AbstractFileObject@18f697[root of /Users/ericthorsen/Clojure/clojure.jar[org.openide.filesystems.JarFileSystem@e5675b]]>,
; :jar #<JarFile java.util.jar.JarFile@2a1be1>,
; :source #<JarFileEntry clojure/set.clj>,
; :ext "clj",
; :lib "/Users/ericthorsen/Clojure/clojure.jar",
; :name "clojure/set.clj"}

(defn symbols-from-namespace
  "Returns all the symbols from a namespace or java class"
  [cache ns]
  (:symbols (cache ns)))

(defn java? [item] (= :java (:lang item)))
(defn clojure? [item] (= :clojure (:lang item)))
(defn static? [item] (:static item))
(defn public? [item] (not (:private item)))
(defn func? [item]  (= :func (:type item)))
(defn field? [item] (= :field (:type item)))
(defn macro? [item] (= :macro (:type item)))
(defn instance-item? [item] (and (java? item)
                              (not (static? item))))
(defn symbol-type? [item symtype]
  (= symtype (:symbol-type item)))

(defn- do-filter [ns-data filter-func]  
  (reduce  (fn [m [sym forms]]           
             (let [valid-funcs
                        (filter filter-func forms)]            
               (if (pos? (count valid-funcs))
                 (assoc m sym
                   (concat (m sym) valid-funcs)) m)))
                 {} (:symbols ns-data)))

;/Users/ericthorsen/dev/enclojure-clojure-lib/org.enclojure.ide/src/org/enclojure/ide/navigator/symbol_meta.clj
(defn all-forms
    "Returns a list of maps that contain all the form elections for a given namespace or class."
  [ns-data]
  (vec (apply concat (vals (:symbols ns-data)))))

(defn instance-items-public
  "only relevant for java classes.  Returns all instances methods and fields"
  [ns-data]
  (do-filter ns-data
    #(and (instance-item? %1) (public? %1))))

(defn instance-items-all
  "only relevant for java classes.  Returns all instances methods and fields"
  [ns-data]
  (do-filter ns-data instance-item?))

(defn static-items-public
  "returns class static methods and fields and all clojure defn and defs"
  [ns-data]
    (do-filter ns-data #(and
                           (static? %1)
                           (public? %1))))

(defn static-items-all
  "returns class static methods and fields and all clojure defn and defs and defmacros"
  [ns-data]
    (do-filter ns-data static?))

(defn get-namespace-node
  "returns namespace node."
  [ns-data]
    (do-filter ns-data #(and (static? %1)
                                   (or (symbol-type? %1 'ns)
                                     (symbol-type? %1 'in-ns)))))

(defn get-list-in-ns
  "Returns the list from the ns node matching the key (:import :use :require :refer)"
    [ns-data list-key]
  (let [nss (get-namespace-node ns-data)]
    (vec (some list-key (fnext (first nss))))))

(defn get-java-classes-in-ns
  "Returns the list java class imports"
    [ns-data]
  (vec (apply concat
    (for [package (get-list-in-ns ns-data :import)]
      (map #(str (first package) "." %1) (rest package))))))

 (defn get-java-packages-in-ns
  "Returns the list of java pacakges int the imports list"
    [ns-data]
  (vec (map (comp str first) (get-list-in-ns ns-data :import))))

 (defn get-ns-in-ns
  "Returns the list of all refered, required and/or used namespaces in this namespace"
    [ns-data]
   (vec (concat
        (get-list-in-ns ns-data :use)
        (get-list-in-ns ns-data :refer)
        (get-list-in-ns ns-data :require))))

(defn get-java-classes-and-ns-for
  "Returns the list of all refered, required and/or used namespaces plus all the classes in the :import"
    [ns-data]
  (vec (concat
    (get-java-classes-in-ns ns-data)
    (get-ns-in-ns ns-data))))


; Workflow for building the cache for a namespace:
; Need :statics :instance-members
; get all the java classes in the imports

; (. instance members of all the imported classes
;
; / show statics of the java classes, all namespace or alias publics
;
; xxx. should be handled with normal completion.
(defn ensure-cached-items
  "Makes sure all the java classes namespace symbols are loaded in the cache.
Since there is an expense to looking through a jar file, all the classes in a given
package are loaded. "
  [cache ns-and-classes])
