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
        :doc "Protocol for org.enclojure.ide.tokens"}
		org.enclojure.ide.tokens)

(declare -TOKEN-TYPES-)

(def -token-meta-
  {:language "Clojure"
     :mime-type "text/x-clojure"
     :token-table -TOKEN-TYPES-})

(defn make-token
  [str-tok typek tag]
  (with-meta
    {:type typek :token str-tok :lextag tag}
    -token-meta-))

(defmacro make-token-set
  [str-tok tag token-set]
  (let [syms (map clojure.core/symbol token-set)]
  `(with-meta
    {:type :token-set :token ~str-tok :lextag ~tag
     :token-set (reduce #(assoc %1 %2 (.get %2)) {} 
                  ~@syms)}
    -token-meta-)))

(def cLEFT_PAREN (make-token :char "(" :list-start))
(def cRIGHT_PAREN (make-token :char ")" :list-end))

(def cLEFT_CURLY (make-token :char "{" :map-start))
(def cRIGHT_CURLY (make-token :char "}" :map-end))

(def cLEFT_SQUARE (make-token :char "[" :vec-start))
(def cRIGHT_SQUARE (make-token :char "]" :vec-end))

(def cSHARP (make-token :char "#" :sharp))
(def cUP (make-token :char "^" :hat))
(def cSHARPUP (make-token :string "#^" :sharp-hat))
(def cTILDA (make-token :char "~" :splice))
(def cAT (make-token :char "@" :deref))
(def cTILDAAT (make-token :string "~@" :list-splice))
(def cQUOTE (make-token :list "(def " :def))
(def cBACKQUOTE (make-token :char "`" :back-quote))

;Comments
(def cLINE_COMMENT (make-token :string "line comment" :line-comment))

(def cSTRING_LITERAL (make-token :string "string literal" :string-literal))
(def cWRONG_STRING_LITERAL (make-token :string "wrong string literal" :wrong-string-literal))

(def cINTEGER_LITERAL (make-token :int "integer literal" :int-literal))
(def cLONG_LITERAL (make-token :long "long literal" :long-literal))
(def cBIG_INT_LITERAL (make-token :bigint "big integer literal" :bigint-literal))
(def cFLOAT_LITERAL (make-token :float "float literal" :float-literal))
(def cDOUBLE_LITERAL (make-token :double "double literal" :double-literal))
(def cBIG_DECIMAL_LITERAL (make-token :big-decimal "big decimal literal" :big-decimal-literal))
(def cRATIO (make-token :ratio "ratio literal" :ratio-literal))

(def cCHAR_LITERAL (make-token :char "character literal" :char-literal))
(def cNIL (make-token :nil "nil" :nil))

(def cTRUE (make-token :boolean "true" :boolean-literal))
(def cFALSE (make-token :boolean "false"  :boolean-literal))

(def cCOLON_SYMBOL (make-token :char "key" :colon-symbol))

; Symbol parts
(def symATOM (make-token :symbol "atom" :atom))
(def symDOT (make-token :symbol "dot" :dot))
(def symNS_SEP (make-token :symbol "ns-sep" :ns-sep))
(def symIMPLICIT_ARG (make-token :symbol "implicit function argument" :implicit-arg))

; Control characters
(def cEOL (make-token :char "end of line" :EOL))
(def cEOF (make-token :char "end of file" :EOF))
(def cWHITESPACE  (make-token :char "white-space" :white-space))
(def cCOMMA (make-token :char "," :comma))
(def cBAD_CHARACTER (make-token :char (Object.) :bad-char))


;----------------- Composites ----------------------------
(def cBOOLEAN_LITERAL (make-token-set "boolean literals" :boolean-literals
                        #{cTRUE, cFALSE}))
(def cLITERALS (make-token-set "literals" :literals
                 #{cSTRING_LITERAL, cWRONG_STRING_LITERAL
     cINTEGER_LITERAL, cLONG_LITERAL, cBIG_INT_LITERAL, cFLOAT_LITERAL
     cDOUBLE_LITERAL, cBIG_DECIMAL_LITERAL, cRATIO, cCHAR_LITERAL
     cTRUE, cFALSE, nil}))

(def cREADABLE_TEXT (make-token-set "readble-text" :readable-text
                      #{cSTRING_LITERAL, cLINE_COMMENT, cWRONG_STRING_LITERAL}))

(def cCOMMENTS (make-token-set "comments" :comments #{cLINE_COMMENT} -token-meta-))

; Useful token sets
(def cWHITESPACE_SET (make-token-set "white-space" :white-space
                       #{cEOL, cEOF, cWHITESPACE, cCOMMA}))

(def symS (make-token-set  "symbols" :symbols 
            #{symATOM,  symDOT, symNS_SEP, symIMPLICIT_ARG}))

(def cATOMS (make-token-set "atoms" :atoms
              #{symATOM,  symDOT, symNS_SEP}))

(def cSEPARATORS (make-token-set "separators" :separators
                   #{symDOT, symNS_SEP} -token-meta-))

(def cIDENTIFIERS (make-token-set  "identifiers" :identifiers
                    #{symATOM} -token-meta-))
(def cSTRINGS (make-token-set "string literals" :string-literals
                #{cSTRING_LITERAL, cWRONG_STRING_LITERAL}))

(declare -TOKEN-TYPES-)

(def -TOKEN-TYPES-
    (reduce (fn [m [k v]]
              (if (and (.isBound v)
                    (not= '-TOKEN-TYPES- k))                
                        (assoc m k (.get v)) m)) {}
      (ns-publics (find-ns 'org.enclojure.ide.tokens))))

(defn get-java-def
  []  
  (let [fmt-str "\tfinal static clojure.lang.Var %s = RT.var(\"org.enclojure.ide.tokens\",\"%s\");\n"
        tokens  (keys (dissoc
                        (ns-publics (find-ns 'org.enclojure.ide.tokens))
                            '-TOKEN-TYPES-))]
    (apply str
      (map format (cycle [fmt-str]) tokens tokens))))


