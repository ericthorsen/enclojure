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
        :doc "Protocol for org.enclojure.idetools.parser-test"}
		org.enclojure.idetools.parser-test
  (:use clojure.test)
  (:require [org.enclojure.idetools.lexer-test :as lexer-test])
  (:import (org.enclojure.flex _Lexer ClojureSymbol)
    (Example ClojureSym ClojureParser)
    (java.io File FileReader FileInputStream StringReader)
    (java.beans Introspector BeanInfo PropertyDescriptor)
    (java.lang.reflect Field)
    (clojure.lang Compiler Compiler$C Compiler$Expr Reflector
      Compiler$FnExpr
      Compiler$LiteralExpr
      Compiler$AssignableExpr
      Compiler$NilExpr
      Compiler$BodyExpr
      )))


(def -stmt- clojure.lang.Compiler$C/STATEMENT)
(def -expr- clojure.lang.Compiler$C/EXPRESSION)
(def -ret- clojure.lang.Compiler$C/RETURN)
(def -eval- clojure.lang.Compiler$C/EVAL)

(def -lexer- (_Lexer. (StringReader. "")))

(defn parser-test
  [in-str]
  (.yyreset -lexer- (StringReader. in-str))
  (let [parser (ClojureParser. -lexer-)]
    (.parse parser)))

(defmulti get-field-value
  (fn [c & args] (class c)))

(defmethod get-field-value PropertyDescriptor 
  [pd instance]
  (let [m (.getReadMethod pd)]
    (when m
      (try
        (Reflector/invokeInstanceMethod instance (.getName m) (into-array Object []))
        (catch Throwable t
          (println "Exception: property get " (.getName m))
          (println t))))))

(defmethod get-field-value Field
  [field instance]  
      (try
        (.get field instance)
        (catch Throwable t
          (println "Exception: field get " field)
          (println t))))

(defmulti pull-fields class)

(defmethod pull-fields :default
  [expr]
    (reduce #(assoc %1 (keyword (.getName %2))
               (let [v (get-field-value %2 expr)]
                 (if (and v (isa? (class v) Compiler$Expr))
                   (pull-fields v) v)))
      { :this expr
        :class (symbol (.getSimpleName (class expr)))
        :has-java-class (.hasJavaClass expr)
        :java-class (when (.hasJavaClass expr) (.getJavaClass expr))}
      (.getFields (class expr))))

(defmethod pull-fields Compiler$FnExpr
  [expr]
    (reduce #(assoc %1
               (keyword (.getName %2))
               (try
                 (Reflector/invokeInstanceMethod
                   expr (.getName %2) (into-array Object []))
               (catch Throwable t
                (println "Exception: No go on get " (.getName %2))
                    (println t))))
      { :this expr
        :class (symbol (.getSimpleName (class expr)))
        :has-java-class (.hasJavaClass expr)
        :java-class (when (.hasJavaClass expr) (.getJavaClass expr))}
      (filter #(and (= (.getDeclaringClass %) (class expr))
                 (not (.startsWith (.getName %) "emit")))
                 (.getMethods (class expr)))))


(defn get-field-props
  [expr]
  (let [c (class expr)
        fields (.getFields c)
        bean-info (Introspector/getBeanInfo c)
        properties (vec (.getPropertyDescriptors bean-info))]
    (concat fields properties)))

(defn get-fields
  [expr]
  (let [c (class expr)
        fields (.getFields c)
        bean-info (Introspector/getBeanInfo c)
        properties (vec (.getPropertyDescriptors bean-info))]
    (reduce #(assoc %1 (keyword (.getName %2))
               (let [v (get-field-value %2 expr)]
                 (if (and v (isa? (class v) Compiler$Expr))
                   (get-fields v) v)))
      { :this expr
        :class (symbol (.getSimpleName (class expr)))
        :has-java-class (.hasJavaClass expr)
        :java-class (when (.hasJavaClass expr) (.getJavaClass expr))}
      (concat fields properties))))

(defn walk-analysis
  [expr]
  (println (vec (.getFields (class expr))))
  (reduce (fn [m f]
            (assoc m
              (keyword (.getName f))
              (when-let [v (try (.get f expr) (catch Exception e '?))]
                (if (isa? (class v) Compiler$Expr)
                  (walk-analysis v)
                  v))))
    {:this expr
     :class (symbol (.getSimpleName (class expr)))
     :has-java-class (.hasJavaClass expr)
     :java-class (when (.hasJavaClass expr) (.getJavaClass expr))}
    (.getFields (class expr))))

(defn analyze [f t]
  (let [o (clojure.lang.Compiler/analyze t f)]
    (walk-analysis o)))






