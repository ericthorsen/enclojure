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
(ns org.enclojure.ide.analyze.jar-helpers
  (:use org.enclojure.commons.meta-utils
    org.enclojure.commons.logging)
  (:require [org.enclojure.ide.navigator.token-nav :as token-nav])
  (:import (java.util.logging Level)
   (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
    (java.io StringReader FileInputStream)
    (java.lang.StringBuilder)
   ; (org.objectweb.asm ClassReader ClassVisitor)
   ; (org.objectweb.asm.tree ClassNode)
    ))

(defrt #^{:private true} log (get-ns-logfn))


;(defn get-jar-entries
;  "Given a URL, read in all the jar entries."
;  [jar-url entry-func agents]
;  (with-open [jar (java.util.jar.JarFile. (.getPath jar-url))]
;    (loop [ret []
;           entries (enumeration-seq (.entries jar))
;           next-agent (cycle agents)]
;      (if-let [je (first entries)]
;        (let [e (entry-func jar je)]
;          (recur (if e (conj ret e) ret) (rest entries) (rest agents)))
;        ret))))

(def f (java.net.URL. "file:/Users/ericthor/dev/third-party/Apache/commons-httpclient-3.1.jar"))

(def f2 (java.net.URL. "file:/Users/ericthor/dev/enclojure-clojure-lib/org.enclojure.ide/dist/org.enclojure.ide.jar"))

