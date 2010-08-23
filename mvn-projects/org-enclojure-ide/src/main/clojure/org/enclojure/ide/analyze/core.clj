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
(ns org.enclojure.ide.analyze.core
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (java.util.logging Level)
    (java.io StringWriter PrintWriter)
    ))

; setup logging
(logger/ensure-logger)

(defn- publish-stack-trace [throwable]
  (let [root-cause
            (loop [cause throwable]
                (if-let [cause (.getCause cause)]
                    (recur cause) cause))]
    (binding [*out* (StringWriter.)]
      (.printStackTrace root-cause (PrintWriter. *out*))
      (when (not= root-cause throwable)
         (.printStackTrace throwable (PrintWriter. *out*)))
      (logger/error (str *out*)))))

(defmacro #^{:private true}
    with-exception-handling [& body]
    `(try
      ~@body
       (catch Throwable t#
        (publish-stack-trace t#))))

;-------------------------------------------------------------------
; Helpers for handling various clojure forms as data.
;-------------------------------------------------------------------
(defn get-arglists
"Given a list (assumed to be a defn,defmacro, defmethod) and return
a vector of vectors of the arglists"
  ([f]
  (reduce #(conj %1 (first %2)) []
  (loop [t (drop 2 f)]
    (when-first [v t]
      (cond
        (vector? v) (list t)
        (list? v) t
        :else
        (recur (rest t))))))))

(defmulti form-parse (fn [form]
                       (when (seq? form)
                        (first form))))

(defn get-name [form]
  (let [n (fnext form)]
    (if (string? n) (symbol n) n)))

(defn get-def-attribs-for-clojure-form [form]
  [:symbol-type (first form)
   :static true :lang :clojure
   ;:name
   ; (let [n (first form)]
   ;   (if (string? n) (symbol n) n))
   ])

(defmethod form-parse :default [form]
  (conj
    (get-def-attribs-for-clojure-form form)
    :name
    (if (list? form)
        (let [n (first form)]
            (if (string? n) (symbol n) n))
      (str form))))

(defmethod form-parse 'ns [form]
  (conj
    (reduce #(conj %1 (first %2) (rest %2))
      (get-def-attribs-for-clojure-form form)
      ((comp next next) form))
    :type :namespace
    :name (get-name form)
    ))

(defmethod form-parse 'in-ns [form]
  (conj (get-def-attribs-for-clojure-form form)
    :type :namespace
    :name (get-name form)
    ))

(defmethod form-parse 'defn [form]
  (conj (get-def-attribs-for-clojure-form form)
    :type :func
    :name (get-name form)
    :arglists (get-arglists form)))

(defmethod form-parse 'defn- [form]
  (conj (get-def-attribs-for-clojure-form form)
    :type :func
    :name (get-name form)
    :arglists (get-arglists form)))

 (defmethod form-parse 'comment [form]
  (conj (get-def-attribs-for-clojure-form form)
    :type :comment :name 'comment))

(defmethod form-parse 'def [form]
  (conj (get-def-attribs-for-clojure-form form)
    :name (get-name form)
    :type :field))

(defmethod form-parse 'defmacro [form]
  (conj (get-def-attribs-for-clojure-form form)
    :type :func
    :name (get-name form)
    :arglists (get-arglists form)))

(defmethod form-parse 'defmulti [form]
  (conj (get-def-attribs-for-clojure-form form)
    :name (get-name form)
    :type :func :disp-fn (nth form 2)))

(defmethod form-parse 'defmethod [form]
  (conj (get-def-attribs-for-clojure-form form)
    :name (get-name form)
    :type :func :disp-value (nth form 2)
    :arglists (get-arglists form)))

