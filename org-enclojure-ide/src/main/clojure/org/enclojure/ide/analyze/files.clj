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

(ns org.enclojure.ide.analyze.files
  (:refer-clojure :exclude (with-bindings))
  (:use [clojure.main :exclude (with-bindings)])
  (:require [clojure.set :as set]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.analyze.core :as analyze.core]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.commons.meta-utils :as meta-utils]
    )
   (:import (java.util.logging Level)
     (org.enclojure.ide CharCountingPushbackReader)
     (java.io StringWriter PrintWriter InputStream)
     (clojure.lang LineNumberingPushbackReader)
     (clojure.asm Opcodes)
     (java.lang.reflect Method)
     (java.nio.charset Charset)
     (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
     (org.enclojure.ide.asm ClassReader ClassVisitor MethodVisitor Type)
     (org.enclojure.ide.asm.tree ClassNode FieldNode MethodNode)
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
      (logger/error (str *out*)))))

(defmacro #^{:private true}
    with-exception-handling [& body]
    `(try
      ~@body
       (catch Throwable t#
         (publish-stack-trace t#))))
;-------------------------------------------------------------------
; file analysis/parsing clojure sources and jar sniffing
;-------------------------------------------------------------------
(defmulti analyze-file
  (fn
    ([c] (class c))
    ([source type & args] type)))

(defmethod analyze-file :default
  [file type]
  (logger/warn "analyze-file default!!!!!!! file:" file " type:" type))

(defn slurp-stream [istream]
    (let [sb (StringBuilder.)]
      (loop [c (. istream (read))]
        (if (neg? c)
          (str sb)
          (do
            (. sb (append (char c)))
            (recur (. istream (read))))))))

;(defmethod form-parse 'load [form]
;  (conj (get-def-attribs-for-clojure-form form)
;    :type :func :disp-value (nth form 2)
;    :arglists (get-arglists form 3)))

(defn skip-to-next-form [#^CharCountingPushbackReader is]
  (let [v (.read #^CharCountingPushbackReader is)]
    (if (not= v -1)
      (let [c (char v)]
        (if (or (= c \newline)
            (= c \,)
            (= c \space))
            (recur #^CharCountingPushbackReader is)
         (.unread #^CharCountingPushbackReader is (int c))))
                       v)))
                       
(def #^{:private true} EOF (Object.))

(defn safe-hash-map [& keyvals]
  (reduce (fn [m [k v]]
            (assoc m k v)) {}
    (partition 2 keyvals)))

(defn readable-form?
  []
  (try
     (read *in* false EOF true)
     (catch Throwable t
       (logger/warn "Unreadable form found. {}" (str t)))))

(defn pull-forms
  [istream additional-attribs]
  (try
    (let [def-ns (atom nil)]
        (binding [*in* (org.enclojure.ide.CharCountingPushbackReader.
                       (java.io.InputStreamReader. #^InputStream istream
                         (.get (Charset/availableCharsets) "UTF-8")))]
          (let [pos-fn #(do (skip-to-next-form #^CharCountingPushbackReader *in*)
                           {:pos (.getPosition #^CharCountingPushbackReader *in*)
                            :line (.getLineNumber #^CharCountingPushbackReader *in*)})
                not-eos-fn #(let [c (.read #^CharCountingPushbackReader *in*)]
                                (when-not (= (int c) -1)
                                  (.unread #^CharCountingPushbackReader *in* c)
                                  c))]
            (loop [forms {} pos-info (pos-fn)]
              ; We need 2 pieces of info to keep looping: if eos and the set of parsed-forms
              (let [[eos parsed-forms]
                (if-let [form (readable-form?)]
                    (let [eos (not-eos-fn) ;look for true end of stream
                         parsed-form ;make sure what we read is a list
                        (if (and (not= form EOF) (list? form))
                            (try
                                (analyze.core/form-parse form)
                            (catch Throwable t
                              (logger/error
                                "pull-forms: could not parse form attribs= "
                                    additional-attribs))))
                    form-map (when parsed-form (apply safe-hash-map parsed-form))
                    is-ns? (= (:type form-map) :namespace)
                    names (if is-ns? (swap! def-ns
                                         (fn [_] (:name form-map)))
                                        @def-ns)
                    
                    form-entry
                        (when parsed-form
                          (let [mmeta (when (and (not is-ns?)
                                              (symbol? names)
                                              (find-ns names)
                                              (:name form-map))
                                        (ns-resolve names (:name form-map)))]
                            (merge additional-attribs form-map
                                (apply safe-hash-map
                                        :namespace names
                                        :form form
                                        :line (inc (:line pos-info))
                                        :line-end (.getLineNumber #^CharCountingPushbackReader *in*)
                                        :start (:pos pos-info)
                                        :end (.getPosition #^CharCountingPushbackReader *in*)
                                  (if meta [:doc (:doc (meta mmeta)) :meta (meta mmeta)] [])))))
                    sym-key (:name form-entry)
                    parsed-forms
                        (if parsed-form
                            (assoc forms sym-key
                                (conj (forms sym-key) form-entry)) forms)]
                        [eos parsed-forms])
                (do (logger/warn "Found unreadle form attribs: {}" (str additional-attribs))
                    [(not-eos-fn) forms]))]
                (if (not eos) parsed-forms
                    (recur parsed-forms (pos-fn))))))))
       (catch Throwable t
        (logger/warn-throwable
          (str "Exception for type " type " attrs " additional-attribs)
          t)
        (publish-stack-trace t))))

(defn test-pull-forms [file]
    (with-open [f (java.io.FileInputStream. #^String file)]
        (pull-forms f {:file file})))


(defmethod analyze-file "clj"
  [istream type additional-attribs]
  ;{:post [(every? #{:ext :lib :orgname :symbols :source-file} (keys %))]}
  ;(logger/warn "analyze-file clj")
  (merge additional-attribs
    {:symbols
        (pull-forms istream additional-attribs)}))


(defn oi []
  (org.enclojure.ide.CharCountingPushbackReader.
                       (java.io.InputStreamReader.
                         (java.io.FileInputStream.
                           "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/actions/action_handler.clj")
                         )))

(defn io []
  (LineNumberingPushbackReader.
                       (java.io.InputStreamReader.
                         (java.io.FileInputStream.
                           "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/actions/action_handler.clj")
                         )))

;(def f (File. "/Users/ericthor/Clojure/src/clj/clojure/core.clj"))
(defn p [in]
  (println "l: " (.getLineNumber in) " c:" (.getPosition in)))

(defmethod analyze-file javax.swing.text.AbstractDocument
  [document]
  (logger/warn "analyze-file AbstractDocument - " document)
  (parser/get-top-level-form-data
    (.tokenSequence (TokenHierarchy/get document))
    (parser/get-doc-text-fn document)))

(defn testins []
  (with-open [f (java.io.FileInputStream. "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/actions/action_handler.clj")]
    (analyze-file f "clj" {})))

;---------------------------------------------------------------
;Support for ASM parsing of class files
;---------------------------------------------------------------
(def -flags- (reduce bit-or ClassReader/SKIP_DEBUG
                [ClassReader/SKIP_CODE ClassReader/SKIP_FRAMES]))

(defn get-arglist-from-java-meth [m]
    (list (symbol (.getClassName (Type/getReturnType m)))
        (vec (map #(symbol (.getClassName %1))
            (Type/getArgumentTypes m)))))

(defn get-access-flags [opcode]
  (concat []
    (when (pos? (bit-and opcode Opcodes/ACC_PRIVATE))
        [:private true])
    (when (pos? (bit-and opcode Opcodes/ACC_STATIC))
        [:static true])))

(defn get-field-data [#^ClassNode cnode]
  (loop [fields (.fields cnode) flds {}]
    (if-let [f #^FieldNode (first fields)]
      (let [fname (.name f)
            access (.access f)
            entry
             (when (pos? (bit-and access (bit-or Opcodes/ACC_PUBLIC
                                       Opcodes/ACC_PROTECTED)))
                {   :name fname
                    :static (pos? (bit-and access Opcodes/ACC_STATIC))
                    :return-type (.getClassName (Type/getType (.desc f)))
                    :type :field
                    :lang :java
                 })]
               (recur (rest fields)
                 (if entry
                   (assoc flds fname (conj (flds fname) entry))
                 flds)))
      flds)))

(defn get-method-data [#^ClassNode cnode]
  (loop [methods (.methods cnode) funcs {}]
    (if-let [m #^MethodNode (first methods)]
      (let [funcname (.name m)
            access (.access m)
            entry
            (when (pos? (bit-and access (bit-or Opcodes/ACC_PUBLIC
                                       Opcodes/ACC_PROTECTED)))
                {:arglists (get-arglist-from-java-meth (.desc m))
                    :name funcname
                    :static (pos? (bit-and access Opcodes/ACC_STATIC))
                    :type :func
                    :lang :java
                    ;:return-type (symbol (.getClassName (Type/getReturnType m)))
                 })]
        (recur (rest methods)
          (if entry
              (assoc funcs funcname (conj (funcs funcname) entry))
            funcs)))
      funcs)))


(defn test-file2 []
  (let [fname
        "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/classpaths/executors.clj"]
    (with-open [f (java.io.FileInputStream. fname)]
        (analyze-file f "clj" {:source-file fname}))))

(defn test-file [#^String file]
    (with-open [f (java.io.FileInputStream. file)]
        (analyze-file f "clj" {:source-file file})))

(defmethod  analyze-file "class"
  ([#^InputStream istream type jar]
    ;{:post [(every? #{:package :super :source :access :symbols} (keys %))]}
 ;   (logger/warn "analyze-file class")
    (try
        (let [cnode (ClassNode.)
              reader (ClassReader. istream)]
          (.accept reader cnode -flags-)
          (when-not (pos? (bit-and (.access cnode) Opcodes/ACC_PRIVATE))
              {:package (meta-utils/package-name-from-class (.name cnode))
               :super (.superName cnode)
               :source (.sourceFile cnode)
               :access (.access cnode)
               :symbols (merge (get-method-data cnode)
                            (get-field-data cnode))}
          ))
      (catch Throwable t
        (publish-stack-trace t)))))
