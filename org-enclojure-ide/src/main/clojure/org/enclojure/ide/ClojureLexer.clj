(comment
;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;    The use and distribution terms for this software are covered by the
;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;    which can be found in the file epl-v10.html at the root of this distribution.
;    By using this software in any fashion, you are agreeing to be bound by
;    the terms of this license.
;    You must not remove this notice, or any other, from this software.
;
;    Author: Frank Failla
)

(ns org.enclojure.ide.ClojureLexer
  (:gen-class	
   :implements [org.netbeans.spi.lexer.Lexer]
   :state mstate
   :init ctor
   :constructors {[org.netbeans.spi.lexer.LexerRestartInfo] []}
   :methods [ #^{:static true} [language [java.util.Map] org.netbeans.api.lexer.Language] ])
  (:require 
    org.enclojure.ide.lexer org.enclojure.ide.LexerInputReader
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (java.util.logging Level)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
    (org.netbeans.spi.lexer TokenFactory LanguageProvider)))

; setup logging
(logger/ensure-logger)

(defn -ctor [info]  
  [[] {:factory (.tokenFactory info) 
       :input (.input info)
;       :rdr (org.enclojure.nb.LexerInputReader. (.input info))}])
       :rdr (proxy [java.io.PushbackReader] [(org.enclojure.ide.LexerInputReader. (.input info))]
	      (unread [ch] 
		      ;(proxy-super unread ch)
		      (.backup (.input info) 1)))}])

(defn -state [this] 
  nil)

(defn -release [this]  
  nil)

(defn make-tokenid [token ordinal category]
  (org.netbeans.spi.lexer.LanguageHierarchy/newId (.toLowerCase (name token)) ordinal category))

(def token-names
     [
      :char
      :symbol 
      :map-start
      :any 
      :map-end 
      :vec-end
      :list-start 
      :keyword 
      :deref 
      :list-end
      :meta
      :string
      :vec-start
      :number 
      :unquote
      :comment
      :quote
      :function
      :macro
      :ns-publics
      :ns-interns
      :ns-imports
      :compiler-special
      :error
      ])

(def token-ids 
     (into {} (map #(vector %1 (make-tokenid %1 %2 (name %1)))
		   token-names 
		   (range (count token-names)))))

(defn create-token [factory token id len]
  (when (pos? len)
    (let [txt (.name id)]
;      (logger/info (str "create-token called with token[" token "], id[" txt "], len[" len "]"))
      (.createToken factory id len))))

(defn recovery [rdr factory input]
  (logger/info "Calling recovery from ClojureLexer")
  (create-token factory 
		:error 
		(token-ids :error (token-ids :any))
		(.readLength input)))

(defn -nextToken [this]  
   (let [{:keys [rdr factory input]} (.mstate this)]
     (try
      (let [{:keys [type token]} (org.enclojure.ide.lexer/get-token rdr)]
	(create-token factory 
		      token 
		      (token-ids type (token-ids :any))
		      (.readLength input)))
      (catch Exception exc
        (logger/error-throwable
          (.getMessage exc) exc)
	(recovery rdr factory input)))))
    
(defn language-instance [mime-type]
  (when-not *compile-files*
    (.language (proxy [org.netbeans.spi.lexer.LanguageHierarchy] []
		 (mimeType [] mime-type)
		 (createTokenIds [] (vec (vals token-ids)))
		 (createTokenCategories [] {})
		 (createLexer [info] 
		;	      (sync nil (ref-set tokens nil))
			      (org.enclojure.ide.ClojureLexer. info))
		 (embedding [token path, attr] nil)))))

(defn -language [#^java.util.Map args]
  (let [mime-type (.get args "mime-type")]
    (if (and mime-type (.contains mime-type "text/x-clojure"))
        (language-instance mime-type)
      (throw (Exception. 
               (str "mime-type passed must contain 'text/x-clojure' in the string. Got "
                 mime-type))))))
