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

(ns org.enclojure.ide.livecode.data
  (:require 
    [org.enclojure.ide.livecode.changes :as changes]
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import
    (javax.swing.tree TreeModel TreePath)
    ))

; setup logging
(logger/ensure-logger)

; reference to a map keyed my namespace.
; each value contains a reference to the namespaces symbol data
(def -data-store-
  (ref
    (reduce #(assoc %1 (ns-name %2) (ref {})) {} (all-ns))))

(def -type-map- ; for display purposes
  {'ns :namespace
   'in-ns :namespace
   'defn :func
   'defmacro :func
   'def :field
   'defmulti :defmulti
   'defmethod :defmethod
   nil :unbound})

(defmulti get-ns class)
(defmethod get-ns clojure.lang.Namespace [n] n)
(defmethod get-ns clojure.lang.Symbol [n] (find-ns n))
(defmethod get-ns String [n] (get-ns (symbol n)))

(defn update-store [updated-ns-data]
  (let [stale-refs (select-keys @-data-store-
                        (keys updated-ns-data))]
    (dosync
      (doseq [[k r] stale-refs]
        (alter r assoc k (updated-ns-data k))))))

(defn build-sym-data
  "Given a single namespace, build the map of symbol data for use in the livecode browser"
  [ns]
  (reduce
    (fn [m [k sym]]
      (let [meta (meta sym)
            symbol-type
            (cond
              (not (.isBound sym)) nil
             (:macro m) 'defmacro
              (fn? (.get sym)) 'defn
              (= (:tag meta) clojure.lang.MultiFn) 'defmulti
              :else 'def)]
        (assoc m k
           (assoc meta :symbol-type symbol-type
             :type (-type-map- symbol-type)))))
   ;{(ns-name ns) {:ns (ns-name ns) :symbol-type 'ns :type :namespace}}
    {}
        (ns-interns (get-ns ns))))

(defn update-changed-ns []
  (update-store
    (reduce #(assoc %1 %2 (build-sym-data %2))
      {}
        (keys (changes/update-hashes-return-changes)))))

; ------------------------------------------------------------------
; Tree support stuff
; ------------------------------------------------------------------
(defn check-children-loaded [node]
    (let [{:keys [children get-child-data-fn]} node]
        (if-not (pos? (count @children))
            (dosync (alter children
                    (fn [_] (get-child-data-fn))))
          @children)))

(defmulti match-type (fn [c & args] (class c)))
(defmethod match-type clojure.lang.PersistentVector
  [col & args] clojure.lang.PersistentVector)
(defmethod match-type :default [col ky] (ky col))

(defmulti get-child (fn [node i] 
                      (match-type node :symbol-type)))

(defmethod get-child 'ns
  [nsnode i]  
  (nth (check-children-loaded nsnode) i))

(defmethod get-child clojure.lang.PersistentVector
  [nsnode i]
  (nth nsnode i))

(defmulti is-leaf? (fn [node i] (:symbol-type node)))
(defmethod is-leaf? 'ns [nsnode i] false)
(defmethod is-leaf? :default [nsnode i] true)

(defmulti get-child-count class)
(defmethod get-child-count clojure.lang.PersistentVector
  [col] (count col))
(defmethod get-child-count :default
  [m]
  (if (= 'ns (:symbol-type m))
    (count (check-children-loaded m))
    0))

(defn ns-data-to-tree [ns-symbol-map sort-fn]
  (let [top-level
        (reduce (fn [v [nsname symbol-ref]]
                   (conj v {:symbol-type 'ns
                            :name nsname
                            ; Change this to a remote call and we are done.
                             :get-child-data-fn #(vals (build-sym-data nsname))
                             :children symbol-ref}))
          [] ns-symbol-map)]
    top-level))

(defn ns-tree-model [ns-symbols sort-fn]
  (let [tree (ns-data-to-tree ns-symbols sort-fn)]
    (proxy [TreeModel] []
      (getRoot []
        tree)
      (addTreeModelListener [treeModelListener])
      (getChild [parent index]
        (get-child parent index))
      (getChildCount [parent]
        (get-child-count parent))
      (isLeaf [node]
        (is-leaf? node))
      (valueForPathChanged [path newValue])
      (getIndexOfChild [parent child] -1)
      (removeTreeModelListener [treeModelListener]))))
