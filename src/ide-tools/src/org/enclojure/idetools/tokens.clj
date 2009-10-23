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
        :doc "Protocol for org.enclojure.idetools.tokens"}
		org.enclojure.idetools.tokens
  (:import (Example ClojureSym)))

(def -TOKENS- (atom {}))

(def -token-meta-
  {:language "Clojure"
   :mime-type "text/x-clojure"
   ;:token-table -TOKEN-TYPES-MAP-
   })
(def -token-ids- (atom -1))

(defn- next-id []
  (swap! -token-ids- inc))

(defn make-token
  [str-tok typek tag]
  (with-meta
    {:type typek :token str-tok :lextag tag :ID (next-id)}
    -token-meta-))

(defmacro register-token
  [tname id str-tok typek tag]
  `(let [tname# ~tname
         id# ~id
         str-tok# ~str-tok
         typek# ~typek
         tag# ~tag]
     (~@(list 'def ~tname)
        {:type typek# :token str-tok# :lextag tag# :ID id#})))
       ;~@(list 'swap! '-TOKENS- 'assoc tname#) ~tname#)))


(defmacro make-token-set
  [str-tok tag token-set]  
  (let [syms# (vec (map str token-set))]
    `(with-meta
       {:type :token-set :token ~str-tok :lextag ~tag
        :ID (next-id)
        :token-set (zipmap (map symbol ~syms#)
                     ~token-set)
        } -token-meta-)))

(def cLEFT-PAREN (make-token :char "(" :list-start))
(def cRIGHT-PAREN (make-token :char ")" :list-end))

(def cLEFT-CURLY (make-token :char "{" :map-start))
(def cRIGHT-CURLY (make-token :char "}" :map-end))

(def cLEFT-SQUARE (make-token :char "[" :vec-start))
(def cRIGHT-SQUARE (make-token :char "]" :vec-end))

(def cSHARP (make-token :char "#" :sharp))
(def cUP (make-token :char "^" :hat))
(def cSHARPUP (make-token :string "#^" :sharp-hat))
(def cTILDA (make-token :char "~" :splice))
(def cAT (make-token :char "@" :deref))
(def cTILDAAT (make-token :string "~@" :list-splice))
(def cQUOTE (make-token :list "(def " :def))
(def cBACKQUOTE (make-token :char "`" :back-quote))

;Comments
(def cLINE-COMMENT (make-token :string "line comment" :line-comment))

(def cSTRING-LITERAL (make-token :string "string literal" :string-literal))
(def cWRONG-STRING-LITERAL (make-token :string "wrong string literal" :wrong-string-literal))

(def cINTEGER-LITERAL (make-token :int "integer literal" :int-literal))
(def cLONG-LITERAL (make-token :long "long literal" :long-literal))
(def cBIG-INT-LITERAL (make-token :bigint "big integer literal" :bigint-literal))
(def cFLOAT-LITERAL (make-token :float "float literal" :float-literal))
(def cDOUBLE-LITERAL (make-token :double "double literal" :double-literal))
(def cBIG-DECIMAL-LITERAL (make-token :big-decimal "big decimal literal" :big-decimal-literal))
(def cRATIO (make-token :ratio "ratio literal" :ratio-literal))

(def cCHAR-LITERAL (make-token :char "character literal" :char-literal))
(def cNIL (make-token :nil "nil" :nil))

(def cTRUE (make-token :boolean "true" :boolean-literal))
(def cFALSE (make-token :boolean "false"  :boolean-literal))

(def cCOLON-SYMBOL (make-token :char "key" :colon-symbol))

; Symbol parts
(def symATOM (make-token :symbol "atom" :atom))
(def symDOT (make-token :symbol "dot" :dot))
(def symNS-SEP (make-token :symbol "ns-sep" :ns-sep))
(def symIMPLICIT-ARG (make-token :symbol "implicit function argument" :implicit-arg))

; Control characters
(def cEOL (make-token :char "end of line" :EOL))
(def cEOF (make-token :char "end of file" :EOF))
(def cWHITESPACE  (make-token :char "white-space" :white-space))
(def cCOMMA (make-token :char "," :comma))
(def cBAD-CHARACTER (make-token :char (Object.) :bad-char))

;----------------- Composites ----------------------------
(def cBOOLEAN-LITERAL (make-token-set "boolean literals" :boolean-literals
                        #{cTRUE, cFALSE}))

(def cREADABLE-TEXT (make-token-set "readble-text" :readable-text
                      #{cSTRING-LITERAL, cLINE-COMMENT, cWRONG-STRING-LITERAL}))

(def cLITERALS (make-token-set "literals" :literals
                 #{cSTRING-LITERAL, cWRONG-STRING-LITERAL
                   , cINTEGER-LITERAL, cLONG-LITERAL, cBIG-INT-LITERAL
                   , cFLOAT-LITERAL, cDOUBLE-LITERAL, cBIG-DECIMAL-LITERAL
                   , cRATIO, cCHAR-LITERAL ,cTRUE, cFALSE}))


(def cCOMMENTS (make-token-set "comments" :comments #{cLINE-COMMENT}))

; Useful token sets
(def cWHITESPACE-SET (make-token-set "white-space" :white-space
                       #{cEOL, cEOF, cWHITESPACE, cCOMMA}))

(def symS (make-token-set  "symbols" :symbols 
            #{symATOM,  symDOT, symNS-SEP, symIMPLICIT-ARG}))

(def cATOMS (make-token-set "atoms" :atoms
              #{symATOM,  symDOT, symNS-SEP}))

(def cSEPARATORS (make-token-set "separators" :separators
                   #{symDOT, symNS-SEP}))

(def cIDENTIFIERS (make-token-set  "identifiers" :identifiers #{symATOM}))
(def cSTRINGS (make-token-set "string literals" :string-literals
                #{cSTRING-LITERAL, cWRONG-STRING-LITERAL}))


