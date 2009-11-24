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
        :doc "Protocol for org.enclojure.idetools.form-def"}
		org.enclojure.idetools.form-def)

(defn- all-true?
  [& fns]
  (fn [x]
    (every? true? ((apply juxt fns) x))))

(defn body
  [form]
  {:type :body :form form})

(defn libspec
  "Returns true if x is a libspec"
  [spec]
  (apply hash-map :type :libspec
    (cond (symbol? spec) [:na spec]
          (vector? spec)
            (let [[nsn as alias] spec]              
                [:ns nsn :alias alias])
            :else (throw (Exception. "Excpected symbol or vector for a :libspec")))))
    
(defn bind-form
  "Returns true if the lhs is a bindable form for use in a let, doseq, binding,etc."
  ([lhs rhs]
    (when (some true? ((juxt vector? map? symbol?) lhs)))
      {:type :bind-form :lhs lhs :rhs rhs}))

(defn let-form
  "checks to see if this is a valid let form."
    ([form]
      (assert (symbol? (first form)))
      (when (vector? (fnext form))
        (let [[_ forms body] form]
            {:type :let
            :bind-forms
            (reduce (fn [v [l r]]
                        (conj v (bind-form l r))) []
                    (partition 2 forms))
            :body body}))))

(defn reference-form
  "checks to see if this is a valid form for a use, refer, require clause"
  [[sym & references]]
  (assert (symbol? sym))  
    {:type (keyword sym)
     :libspecs (reduce #(conj %1 (libspec %2)) []
                            references)})

(defn import-spec
  [form]
  (when (list? form)
    (let [[package & classes]
            (if (and (seq? form) (= 'quote (first form)))
                        (second form) form)]
        {:type :import-spec
         :package package
         :classes (reduce #(conj %1
                            (symbol (str package "." %2))) [] classes)})))

(defn import-form
  [form]
  (when (list? form)
    (let [[_ & forms] form]
      {:type :import
       :imports (reduce #(conj %1 (import-spec %2)) [] forms)})))


(defn ns-form
  "This provides context but no relationship to position in the form"
  [form]
  (when (= (first form) 'ns)    
    (let [[nsname & forms] (rest form)
          procmap #(let [msym (first %)
                         pfunc (cond (#{:refer :use :require} msym)
                                        reference-form
                                    (= :import msym) import-form
                                :else
                                (throw
                                    (Exception. "Expected one of :import :use :require :refer")))]
                     (pfunc %))
          specs (reduce #(conj %1 (procmap %2)) [] forms)
          filter-keyfn (fn [k sel-key]
                         (reduce (fn [v m]
                                    (concat v (m sel-key))) []
                                        (filter #(= (:type %) k) specs)))]
      { :type :ns
        :name nsname
        :use (filter-keyfn :use :libspecs)
        :refer (filter-keyfn :refer :libspecs)
        :require (filter-keyfn :require :libspecs)
        :import (filter-keyfn :import :imports)})))

(defn in-ns-form
  [form]
  (assert (and (symbol? (first form))
            (= 'quote (first (fnext form)))))
  {:type :is-ns :ns (nth (fnext form) 1)})
  
(defn arglist-form
  [form]
  (assert (vector? form))
  {:type :arglist :arglist form})

;(defn defn-form
;  [form]
;  (assert (symbol? (first form)))
;    (let [fdecl (rest form)
;          m (if (string? (first fdecl))
;              {:doc (first fdecl)}
;              {})
;          fdecl (if (string? (first fdecl))
;                  (next fdecl)
;                  fdecl)
;          m (if (map? (first fdecl))
;              (conj m (first fdecl))
;              m)
;          fdecl (if (map? (first fdecl))
;                  (next fdecl)
;                  fdecl)
;          fdecl (if (vector? (first fdecl))
;                  (list fdecl)
;                  fdecl)
;          m (if (map? (last fdecl))
;              (conj m (last fdecl))
;              m)
;          fdecl (if (map? (last fdecl))
;                  (butlast fdecl)
;                  fdecl)
;          m (conj {:arglists (list 'quote (sigs fdecl))} m)]
;      (list 'def (with-meta name (conj (if (meta name) (meta name) {}) m))
;            (cons `fn fdecl))))

(def *forms-map*
  (hash-map
   'let let-form
   'use reference-form
   'refer reference-form
   'require reference-form
   'import import-form
   'ns ns-form
   'in-ns in-ns-form
   ))



   