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

(ns 
  #^{:author "Eric Thorsen"
       :doc "Helper functions to nav around symbols within clojure strings or
Documents for editor support.  Functions unify strings and Documents so the same
functions can be called for either case."}
  org.enclojure.ide.analyze.symbol-nav
  (:require [clojure.set :as set]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.navigator.token-nav :as token-nav]
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (javax.swing.text Element Document)
    (java.util.logging Level Logger)))

; setup logging
(logger/ensure-logger)

;Make it uniform to work with strings or Documents
(defmulti unify-doc-str class)

(defmethod unify-doc-str java.lang.String
  [s]
  {:char-fn #(.charAt s %)
   :substr (fn ([i] (subs s i)) ([i l] (subs s i (+ i l))))
   :length #(count s)
   })

(defmethod unify-doc-str javax.swing.text.Document
  [d]
  {:char-fn #(.charAt (.getText d % 1) 0)
   :substr (fn ([offset len]
                 (.getText d offset len))
             ([offset]
               (.getText d offset)))
   :length #(.getLength d)
   })

(defn find-boundary
  [get-char-fn nav-func length start-offset]
      (loop [offset start-offset]
        (if (and (>=  offset 0) (> length offset)
                (let [c #^Character (get-char-fn offset)]
                  (or (parser/clojure-id-part? (char c))
                    (= (char c) \/))))
                    (recur (nav-func offset))
              offset)))

(defn find-boundary-fn
  [get-char-fn nav-func length start-offset predicate]
    (loop [offset start-offset]
        (if (and (>=  offset 0) (> length offset))
            (let [c #^Character (get-char-fn offset)]
              (if (not (predicate c))
                (recur (nav-func offset))
                offset)))))
          
(defn find-boundary-str [#^CharSequence s nav-func offset]
  (find-boundary #(.charAt s %1) nav-func (count s) offset))

(def brace-start
  {\{ \}
   \[ \]
   \( \)
   })

(def brace-end
  (zipmap (vals brace-start) (keys brace-start)))

(defn match-brace
  "given a document or string, an offset within the string and a max-position
in which to search, look for the first starting brace and attempt to find a matching
ending brace.  Returns the a list of expected ending brace chars if not balanced.
nil if all is good."
  ([document offset max-length]
    (let [{:keys [char-fn]} (unify-doc-str document)]
        (when-let [lstart (find-boundary-fn char-fn dec max-length offset brace-start)]
          (loop [offset (inc lstart) matches (list (brace-start (char-fn lstart)))]
            (if-let [c (and (> max-length offset)
                         (pos? (count matches))
                         (char-fn offset))]
              (do
                (logger/info "Start {} offset {} c={} {}" lstart offset (char-fn offset) matches)
                (recur (inc offset)
                    (cond
                        (brace-start c) (conj matches (brace-start c))
                        (brace-end c) (if (= c (first matches))
                                        (pop matches)
                                        (throw (Exception. "Brace mismatch ")))
                    :else matches)))
            (if (pos? (count matches)) matches nil))))))
  ([document offset]
    (let [{:keys [length]} (unify-doc-str document)]
      (match-brace document offset (length)))))

(defn do-find-pattern-boundary
  "Given a document, caret position and a nav-func (dec or inc)
 find the boundary of an identifier."
  [document caretOffset nav-func]
  (let [{:keys [char-fn length]} (unify-doc-str document)]
    (find-boundary char-fn nav-func (length) caretOffset)))

(defn find-start-boundary
  "Given a document, caret position and a nav-func (dec or inc)
 find the boundary of an identifier."
  [document caretOffset]
  (inc
    (do-find-pattern-boundary document caretOffset dec)))

(defn find-end-boundary
  "Given a document, caret position and a nav-func (dec or inc)
 find the boundary of an identifier."
  [document caretOffset]
    (do-find-pattern-boundary document caretOffset inc))

(defn get-identifier-bounds
  "Given a document or string, and a position within, return the
  start and end boundaries of the identifier at that position."
  [document offset]
  (let [s (find-start-boundary document offset)
        e (find-end-boundary document offset)]
    (when (and s e (> e s))
      {:start s :end e})))

(defn get-identifier-at
  "Given a document or string, and a position within, return the
  identifier at that position."
  [document offset]
  (let [{:keys [start end] :as bounds} (get-identifier-bounds document offset)]
    (when bounds
        ((:substr (unify-doc-str document)) start (- end start)))))

(defn resolve-identifier-at
  [document offset]
  (let [namesp (token-nav/get-namespace document)
        id (get-identifier-at document offset)]
    (when (and namesp id)
      (if-let [ns-local (find-ns (symbol namesp))]
                (ns-resolve ns-local
                    (symbol
                        (if (= \. (last id)) ; In case it is a ctor, strip off the trailing .
                            (subs id 0 (dec (count id)))
                        id)))
        (logger/warn "Could not locate namespace " namesp " perhaps it is not loaded?")
        ))))

(defn get-row-start-from-line
  [doc line]
 "Return start offset of the line
    * @param lineIndex line index starting from 0
    * @return start position of the line or -1 if lineIndex was invalid
    */"
  (let [#^Element line-root
            (-> (.getParagraphElement doc 0) .getParentElement)]
    (when (and (>= line 0)
            (< line (.getElementCount line-root)))
            (-> (.getElement line-root line) .getStartOffset))))

