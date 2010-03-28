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
(ns org.enclojure.ide.navigator.parser
  (:require 
    [org.enclojure.ide.navigator.token-nav :as token-nav]
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (java.util.logging Level)
   (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
    (org.enclojure.ide.asm ClassReader ClassVisitor MethodVisitor)
    (org.enclojure.ide.asm.tree ClassNode)
    (java.io StringReader StringWriter PrintWriter Reader)
    (java.lang.StringBuilder)))

; setup logging
(logger/ensure-logger)

(def sigs
    (fn [fdecl]
        (if (seq? (first fdecl))
             (loop [ret [] fdecl fdecl]
               (if fdecl
                 (recur (conj ret (first (first fdecl))) (next fdecl))
                 (seq ret)))
             (list (first fdecl)))))

(defn- publish-stack-trace [throwable]
  (let [root-cause
            (loop [cause throwable]
                (if-let [cause (.getCause cause)]
                    (recur cause) cause))]
    (binding [*out* (StringWriter.)]
      (.printStackTrace root-cause (PrintWriter. *out*))
      (logger/error (str *out*)))))

(defmacro #^{:private true}
    with-exception-handling [& body]
    `(try
      ~@body
       (catch Throwable t#
         (publish-stack-trace t#))))

(defstruct form-key :ns :file :start-pos)
;-------------------------------------------------------------------
; hippy completion helpers
;-------------------------------------------------------------------
(def -clojure-start-syms- #{\! \$ \% \& \* \- \? \| \+ \:})
(def -clojure-part-syms- (conj -clojure-start-syms- \.))
(def -clojure-syms- (clojure.set/union -clojure-start-syms- -clojure-part-syms-))


(defn clojure-id-part? [#^Character c]
  (or (Character/isJavaIdentifierPart c)
          (-clojure-syms- c)))

(defn add-word [word-set new-word]
  (let [start-with-clj-sym? (-clojure-syms- (.charAt new-word 0))
        len (count new-word)]
    (if (and (not start-with-clj-sym?) (pos? len))
      (conj word-set new-word)
      (if (and start-with-clj-sym? (> len 1)
            (not= (.charAt new-word 0) \.))
        (conj word-set new-word (subs new-word 1))
        word-set))))


(defn get-unique-words
  "given a reader, attempt to pull valid clojure identifiers for use in hippy completion.
Returns a set of strings"
  [#^java.io.Reader reader]
     (loop [c (.read reader) word nil words #{} in-word? nil]
     (if (= -1 c)
       words; finished
       (let [cc (char c)
              valid? (clojure-id-part? cc)
              words (if (and (not valid?) in-word?) 
                        (conj words (apply str word))
                      words)
              word (cond (and valid? in-word?) (conj word cc)
                         (and valid? (not in-word?)) [cc]
                        :else nil)]
            (recur (.read reader) word words valid?)))))
;-------------------------------------------------------------------
; form reading from strings and docs
;-------------------------------------------------------------------
(defn get-doc-text-fn [#^javax.swing.text.Document document]
  (fn [start end]
      (.getText document start (- end start))))

(def #^{:doc "stuff for hippy completion...needs to be moved into the above cache"}
        -file-hippy-cache- (ref {}))

(defn clear-cache []
  (dosync (alter -file-hippy-cache- (fn [_] {}))))

(defn update-hippy-words
  "Given a java.io.File with a full path, attempt to reparse and update the code data"
  [file]
  (logger/info "update-hippy-words " file)
  (let [f (future (get-unique-words
                    (java.io.InputStreamReader.
                     (java.io.FileInputStream. file))))]
    (dosync
      (commute
        -file-hippy-cache-
        assoc (.getPath file) f))
    f))

(defn get-hippy-from-cache [file]
  (if-let [f (@-file-hippy-cache- (.getPath file))]
    f
    (update-hippy-words file)))

(defn get-string-text-fn [char-data]
  (partial subs char-data))

(defn read-form
  "Given a Document and a position, grab the text and 'read' it into a form.
returns {:form form :raw-text text (optionally) :comment-text }"
  [get-text-fn {:keys [start end] :as pos}]
  (when (and start end)
    (let [text (get-text-fn start (inc end))
         form (when text
                (try
                    (read-string text)
                  (catch Throwable t
                    (publish-stack-trace t))))]
    (merge {:form form :pos pos :raw-text text}
         (when (and form (= 'comment (first form)))
                {:comment-text text})))))

(defn get-top-level-form-data
  ([token-seq get-text-fn]
   (.moveStart token-seq)
      (doall (map
        #(merge %1
           (with-exception-handling
             (read-form get-text-fn (:pos %1))))
        (token-nav/get-top-level-forms token-seq)))))

(def small "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/completion/completion_provider.clj")
(def big "/Users/ericthorsen/Clojure/src/clj/clojure/core.clj")

