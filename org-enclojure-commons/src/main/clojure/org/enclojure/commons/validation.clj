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

(ns org.enclojure.commons.validation)

(defn validator
  "Given a list of single argument predicates, return a single arg function that
returns true if all the results of applying each function to the arg are true"
  [& fns]
  (fn [a] (every? true? 
            ((apply juxt (complement nil?) fns) a))))

(defn nilable-validator
  "Convenience function for adding (or (nil? arg)) to the list of acceptable results
for a 'validator"
  [& fns]
  (fn [a] (or (nil? a) 
            ((apply validator fns) a))))

(defn validate
  "validator-meta is a mp of keys to validator functions.  For each map entry in
validator-meta lookup the value in data-map and call the validator func on it.
Short circuits as soon as there is one false"
  [data-map validator-meta]
  (assert (map? data-map))
  (assert (map? validator-meta))
  (loop [validator-meta validator-meta]
    (if-let [[kf f] (first validator-meta)]
      (if (f (data-map kf))
        (recur (rest validator-meta))
        nil)
      true)))

(defn get-validation
  "validator-meta is a mp of keys to validator functions.  For each map entry in
validator-meta lookup the value in data-map and call the validator func on it.
Returns a map of key [true|false] for each result"
  [data-map validator-meta]
  (assert (map? data-map))
  (assert (map? validator-meta))  
    (reduce (fn [m [k vfn]]              
              (assoc m k (vfn (data-map k))))
      {} validator-meta))

(defn validate-throw-on-fail
  "If the data map fails validation, throws an exception passing back the failed
keys and their passed in values"
  [data-map validator-meta]
  (let [vmap (get-validation data-map validator-meta)]
    (if (every? true? (vals vmap)) true
      (let [bad-keys (map first (filter (fn [[k v]] (not v)) vmap))]        
      (throw (IllegalArgumentException.
               (format "Validation failed for keys %s"
                 (reduce #(assoc %1 %2 (data-map %2))
                   {} bad-keys))))))))

