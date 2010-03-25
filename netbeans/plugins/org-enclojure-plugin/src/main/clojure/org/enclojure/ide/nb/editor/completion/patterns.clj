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

(ns org.enclojure.ide.nb.editor.completion.patterns  
  (:import
    (java.util Collection)
    (java.util.logging Level)
    (javax.swing.text Document PlainDocument StringContent ))
  (:require [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.navigator.token-nav :as token-nav]
    [org.enclojure.ide.nb.editor.completion.completion-item :as completion-item]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    [org.enclojure.ide.nb.editor.completion.file-mapping :as file-mapping]
    [org.enclojure.ide.analyze.symbol-meta :as symbol-meta]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    ))

; setup logging
(logger/ensure-logger)

; What if I defined a set of known top level forms for dealing with incomplete
; expressions?
      ; ns, in-ns , def, defn, defmacro, import etc.
      ; I can use these for scoping (still feel like I'm reinventing something that is already written)
; need to also tread binding forms specially is I want to remember things
; about locals

; ns declarations
; (ns                           -> defining namespace
; (:use                         -> ns-lookup
; (:require                     -> ns-lookup
; (:refer                       -> ns-lookup
; (:import (                     -> java-package-lookup
; (:import (package.name         -> java-class-within-package-lookup
      ; For all of the above, I really just need the to backtrack until I find
      ; a matching call form "(<thing>" that is relevant



;(defmulti get-completion-input-context
;  [#^Document document caretOffset]
;  (let [search-offset (check-caret-pos document caretOffset)]))

;(defn encode-fred [s]
;  (let [reader (StringReader. s)]
;    ; At this point I know that reader is of type StringReader
;  (loop [c (.read reader) buf (StringBuffer.)]
;    ; If the user selects a java method of an object whose type I have locally,
;    ; I can auto select the var.  For example,
;    ; (.read ....look in the loca vars to see if there is a type with a method read and insert it
;    ; I also know that buf is a StringBuffer
;    (let [cc (char c)]
;      ; cc is a char
;      (if (= -1 c)
;         (str buf)
;        (recur (.read reader)
;          (.append buf ; same java method lookup
;            (if (or (> c 127) (#{\" \< \> \&} cc))
;                (str "&#" c ";")
;              cc))))))))

; (binding [reader (StringReader. s)]
; I could use a strategy by where I take eack binding form and determine what
;      if anything I can know about the types.  This is a place where the compiler
;      stuff might help me???
