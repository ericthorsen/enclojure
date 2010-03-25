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

(ns org.enclojure.ide.nb.editor.completion.completion-provider
  (:import (org.netbeans.spi.editor.completion CompletionResultSet
             CompletionItem CompletionProvider CompletionDocumentation
             CompletionTask)
    (org.netbeans.api.editor.completion Completion)
    (org.netbeans.spi.editor.completion.support CompletionUtilities)
    (java.util.logging Level)
    (java.util Collection)
    (javax.swing JToolTip)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
    (java.awt.event ActionEvent))
  (:require
    [org.enclojure.ide.nb.editor.completion.completion-item :as completion-item]
    [org.enclojure.ide.nb.editor.completion.completion-task :as completion-task]
    [org.enclojure.commons.c-slf4j :as logger]
    ))

; setup logging
(logger/ensure-logger)

(defn create-task
  [query-type #^JTextComponent component]
  (completion-task/get-completion-task))
  
(defn get-auto-query-types
  [#^JTextComponent component #^String typed-text]
    (if (.contains "./" typed-text)
      CompletionProvider/COMPLETION_QUERY_TYPE  0))

(defn task-canceled [provider]
  (logger/warn "CompletionProvider task canceled???"))

;(defn token-sequence-from-chars [#^CharSequence char-data]
;    (.tokenSequence
;          (TokenHierarchy/create #^CharSequence char-data
;            (org.enclojure.ide.ClojureLexer/-language
;                    #^java.util.Map {"mime-type" "text/x-clojure"}))))

;(defn clean-token [#^CharSequence txt]
;  (apply str
;    (filter identity
;      (map #(let [c (int (.charAt txt %1))]
;              (when-not (or (= c 10) (= c 32) (= c 13)) (char c)))
;        (range (.length txt))))))
;
;(defn all-identifiers [ts]
;  (.moveStart ts)
;  (let [matchset #{"keyword" "symbol"}]
;    (loop [more? (.moveNext ts) syms #{}]
;      (if more?
;        (let [token (.token ts)
;                id (.id token)]
;           ;(println "got " (.name id) " cls " (class (.text token)))
;            (if (matchset (.name id))
;              (recur (.moveNext ts) (conj syms (clean-token (.text token))))
;             (recur (.moveNext ts) syms)))
;          syms))))
;
;(defn testthis [f]
;  (all-identifiers
;    (token-sequence-from-chars
;      (slurp f))))

(def small "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/completion/completion_provider.clj")
(def big "/Users/ericthorsen/Clojure/src/clj/clojure/core.clj")

              

