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
(ns org.enclojure.ide.nb.editor.clj-language-support
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (java.util HashSet Set)
    (java.util.logging Logger)
    (javax.swing.text Document)
    (org.netbeans.api.languages ASTItem ASTNode ASTToken ParserManager SyntaxContext)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence)))

; setup logging
(logger/ensure-logger)
(def *nav-window-text-width* 70)
(def *folding-tooltip-text-width* 120)

(defn get-keywords [doc]
   (let [kw (new HashSet)]
      (doseq [item (keys (ns-publics (find-ns 'clojure)))]
         (. kw (add (str item))))
      kw))

(defn get-matching-atoms [#^ASTNode node  #^String filter]
   (let [atoms (new HashSet)]
      (if (. #^String (. node (getNT)) (equalsIgnoreCase "atomic_symbol"))
         (let [#^ASTToken t (or (. node (getTokenType "keyword"))
                               (. node (getTokenType "symbol")))
               trimmed-atom  (or (and t (.. t (getIdentifier) (trim))) "")]
            (when (and (pos? (count trimmed-atom))
                     (or (. node (getTokenType "keyword"))
                        (. node (getTokenType "symbol")))
                     (not (. atoms (contains trimmed-atom)))
                     (or (nil? filter)
                        (. trimmed-atom (startsWith filter))))
               (. atoms (add trimmed-atom)))
                 atoms)
            (doseq [item (seq (. node (getChildren)))]
               (when (instance? ASTNode item)
                  (dorun (for [a (seq (get-matching-atoms item filter))]
                            (. atoms (add a)))))))
      atoms))

(defn get-matching-AST-atoms [#^Document doc #^String filter]
   (try
      (get-matching-atoms (. (.. ParserManager (get doc)) (getAST)) filter)
      (catch Exception e
        (logger/error-throwable
           (str "Exception in get-matching-AST-atoms: " (. e (getMessage)))
          e))))

(defn get-grammar-piece [type #^Document doc nOffset]
  (try
    (let [#^ASTNode n (. (.. ParserManager (get doc)) (getAST))]
      (when n
        (let [#^ASTNode statement (. n (findNode type  nOffset))]
          (when statement
            (. statement (getAsText))))))
    (catch Exception e
      (logger/error-throwable
        (str "Exception in get-grammar-piece" (. e (getMessage)))
        e))))

(defn get-current-atom [#^Document doc nOffset]
  (get-grammar-piece "atomic_symbol" doc nOffset))

(defn get-last-sexpression [#^Document doc nOffset]
  (get-grammar-piece "Statement" doc nOffset))

(defn get-ast-text-wo-meta [#^ASTNode the-node]
   (let [func (fn func [node]
                 (let [#^String n (. node (getNT))
                       text
                       (cond (. n (equalsIgnoreCase "metadata")) ""
                          (. n (equalsIgnoreCase "atomic_symbol")) (. node (getAsText))
                          :else
                          (apply str
                             (map (fn [item]
                                     (if (instance? ASTNode item)
                                        (func item)
                                        (if (instance? ASTToken item)
                                           (. item (getIdentifier)))))
                                #^java.util.Enumeration (. node (getChildren)))))]
                    text))]
      (func the-node)))


(defn get-ast-meta [#^ASTNode the-node]
   (let [func (fn func [node]
                 (let [#^String n (. node (getNT))
                       text
                       (cond (. n (equalsIgnoreCase "metadata")) ""
                          (. n (equalsIgnoreCase "atomic_symbol")) (. node (getAsText))
                          :else
                          (apply str
                             (map (fn [item]
                                     (if (instance? ASTNode item)
                                        (func item)
                                        (if (instance? ASTToken item)
                                           (. item (getIdentifier)))))
                                #^java.util.Enumeration (. node (getChildren)))))]
                    text))]
      (func the-node)))

(defn trim-expr [s]
   "trims string so that there are at most 1 space"
   (apply str (first s)
      (map (fn [a b]
              (if (and (not= b \newline)
                     (not= b \tab)
                     (not (and (= a \space) (= b \space))))
                 b))
         s (next s))))

(defn get-meta-fold-display [#^SyntaxContext c]
      (let [leaf (.. c (getASTPath) (getLeaf))
            node (. leaf (findNode "metadata" (. c (getOffset))))]
   "meta data"))


 (defn get-fold-display [#^SyntaxContext c]
      (let [text (trim-expr (get-ast-text-wo-meta (.. c (getASTPath) (getLeaf))))]
         (if (> (count text) *folding-tooltip-text-width*)
            (str (subs text 0 (min *folding-tooltip-text-width* (count text))) "...")
         text)))

 (defn get-nav-display [#^SyntaxContext c]
      (let [text (trim-expr (get-ast-text-wo-meta (.. c (getASTPath) (getLeaf))))]
         (if (> (count text) *nav-window-text-width*)
            (str (subs text 0 (min *nav-window-text-width* (count text))) "...")
         text)))
