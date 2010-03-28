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
        :doc "org.enclojure.ide.analyze.parser-comp"}
    org.enclojure.ide.analyze.parser-comp)
;
;  (:import (org.codehaus.jparsec Indentation OperatorTable Parser Parsers
;             Scanners Terminals Token Tokens)
;    (org.codehaus.jparsec.functors Binary Map Map2 Map3 Map4 Map5 Unary Maps
;      Pair Tuples Tuple3 Tuple4 Tuple5)
;    (org.codehaus.jparsec.pattern  CharPredicate CharPredicates Pattern Patterns)
;    (org.codehaus.jparsec.misc Mapper)
;    (org.codehaus.jparsec.error ParseErrorDetails Location ParserException)
;    (java.util List ArrayList Collection)
;  ))
;
;(def  OPERATORS (into-array ["+", "-", "*", "/", ">", "<", ">=", "<="]))
;(def KEYWORDS (into-array ["interface", "class", "enum",
;                           "private", "public", "protected"]))
;(def TERMINALS (Terminals/caseSensitive OPERATORS KEYWORDS))
;
;(defn adjacent
;  ([#^Parser parser #^String name]
;    (let [find-match (fn [#^java.util.List tokens]
;                    (let [p (loop [offset (.index (.get tokens 0))
;                                  tokens tokens]
;                              (when-let [token (first tokens)]
;                                (if (not= offset (.index token))
;                                    (Parsers/expect name)
;                                (recur (+ offset (.length token))
;                                    (rest tokens)))))]
;                            (or p (Parsers/always))))
;        parse-map (proxy [Parser][]
;                    (map [#^java.util.List tokens]
;                      (if (pos? (count tokens))
;                        (find-match tokens)
;                      (Parsers/always))))]
;        (-> (.next parser parse-map)
;            .atomic .source .token)))
;  ([#^String operator]
;    (adjacent
;      (reduce #(conj %1 (.token TERMINALS #^String %2)) [] operator) operator)))
;

    