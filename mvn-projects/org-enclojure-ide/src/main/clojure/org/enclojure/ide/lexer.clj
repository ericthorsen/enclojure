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

(ns org.enclojure.ide.lexer
  (:gen-class
   :methods [ #^{:static true} [getToken [java.io.Reader] clojure.lang.PersistentHashMap] ])
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  )

; setup logging
(logger/ensure-logger)

(def *token-stack* (atom []))

;get a list of exceptions associated with exc
(defn get-exc-seq [exc]
  (loop [e exc acc []]
    (if e
      (recur (. e (getCause)) (conj acc e))
      acc)))

;concatenate the result func for each and its causes
(defn get-exc-info [exc func]
  (reduce str (map (fn [e]
		       (str (func e) "\n"))
		   (get-exc-seq exc))))

;get a formatted message for exc
(defn get-exc-msg [exc]
  (get-exc-info exc (memfn getMessage)))

;publish exception
(defn publish [exc]
  (logger/error-throwable
    (.getMessage exc) exc))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def publics (ns-publics 'clojure.core))
(def interns (ns-interns 'clojure.core))
(def imports (ns-imports 'clojure.core))
(def has-args (into {} (filter #(:arglists (meta (val %))) publics)))
(def macros (into {} (filter #(:macro (meta (val %))) publics)))
(def functions (filter #(not (contains? macros (key %))) has-args))

(defn function? [s] (contains? functions (symbol s)))
(defn macro? [s] (contains? macros (symbol s)))
(defn ns-publics? [s] (contains? publics (symbol s)))
(defn ns-interns? [s] (contains? interns (symbol s)))
(defn ns-imports? [s] (contains? imports (symbol s)))
(defn compiler-special? [s] (contains? clojure.lang.Compiler/specials (symbol s)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def terminating-table
     {
      \" \"
      \; \;
      \' \'
      \@ \@
      \^ \^
      \` \`
      \~ \~
      \( \(
      \) \)
      \[ \[
      \] \]
      \{ \{
      \} \}
      \\ \\
      \% \%
      \# \#})

(defn terminating? [ch]
  (and (terminating-table ch) (not= \# ch)))

(def eof? neg?)
(def whitespace-chars #{\space \,})

(defn whitespace? [ch]
  (or (Character/isWhitespace ch)
      (whitespace-chars ch)))

(defn unread [rdr ch]
  (when (not= ch -1)
    (let []
      (.unread rdr ch))))

(def char-special-chars
     {\n \newline
      \space \space
      \t \tab
      \b \backspace
      \f \formfeed
      \r \return})

(def escape-chars
     {\t \tab
      \r \return
      \n \newline
      \\ \\
      \" \"
      \b \backspace
      \f \formfeed
      })

(defn read-unicode-char [rdr initch base len exact?]
  (let [uc (Character/digit initch base)
	_ (when (= -1 uc)
	    (throw (IllegalArgumentException. (str "Invalid digit: " initch))))
	i 1]
    (loop [i i
	   uc uc]
      (when (< len i)
	(let [ch (.read rdr)]
	  (if (or (eof? ch)
		  (whitespace? (char ch))
		  (terminating? (char ch)))
	    (do (unread rdr ch) (recur len uc))
	    (let [d (Character/digit ch base)
		  _ (when (= -1 d)
		      (throw (IllegalArgumentException. (str "Invalid digit: " initch))))]
	      (recur (inc i) (+ d (* uc base))))))))
    (when (and exact? (not (= len i)))
      (throw (IllegalArgumentException. (str "Invalid character length: " i ", should be: " len))))
    uc))

;(defn read-unicode-char-token [token offset len base])
;static private int readUnicodeChar(String token, int offset, int length, int base) throws Exception{
;	if(token.length() != offset + length)
;		throw new IllegalArgumentException("Invalid unicode character: \\" + token);
;	int uc = 0;
;	for(int i = offset; i < offset + length; ++i)
;		{
;		int d = Character.digit(token.charAt(i), base);
;		if(d == -1)
;			throw new IllegalArgumentException("Invalid digit: " + (char) d);
;		uc = uc * base + d;
;		}
;	return (char) uc;
;}

(defn read-unicode-char-token [token offset len base])
;  (logger/info "token {} offset {} len {}" token offset len)
;	(when (not= (.length token) (+ offset len))
;   		(throw (IllegalArgumentException.
;                        (str "Invalid unicode character: \\"  token))))
;	(loop [uc 0 i offset]
;            (if (< i (+ offset len))
;              (let [d (Character/digit (.charAt token i) base)]
;                (when (= -1 (int d))
;                  (throw (IllegalArgumentException.
;                           (str "Invalid digit: " (char d)))))
;                (recur (+ (* uc base) d) (inc i)))
;                (char uc))))

(defn read-char-token [rdr initch]
  (let [sb (StringBuilder.)]
    (.append sb (char initch))
    (loop [ch (.read rdr)]
      (if (or (eof? ch)
            (whitespace? (char ch))
            (terminating? (char ch)))
        (unread rdr ch)
        (do
          (.append sb (char ch))
          (recur (.read rdr)))))
    (str sb)))

(defn char-token [rdr backslash]
  (let [ch (.read rdr)
	sb (StringBuilder.)]
    (when-not (eof? ch)
      (let [tk (read-char-token rdr ch)]
	    (cond
	     (= 1 (.length tk)) (.append sb tk)
	     (= "newline" tk) (.append sb "\n")
	     (= "space" tk) (.append sb " ")
	     (= "tab" tk) (.append sb "\t")
             (= "backspace" tk) (.append sb "\b")
	     (= "formfeed" tk) (.append sb "\f")
             (= "return" tk) (.append sb "\r")
	     (.startsWith tk "u")
                (let [ch (read-unicode-char-token tk 1 4 16)]
                  (logger/info "char is {}" ch)
                    (if (and (>= ch 0xD800)
                            (<= ch 0xDFFF))  ;surrogate code unit?
                	  (throw (Exception. (str "Invalid character constant: \\u"  (Integer/toString (char ch) 16)))))
      		  (.append sb (char ch)))
    	 (.startsWith tk "o")
            (let [len (dec (.length tk))
	    			    _ (when (> 3 len)
	    				(throw (Exception. (str "Invalid octal escape sequence length: " len))))
	    			    uc (read-unicode-char-token tk 1 len 8)]
	    			(when (> uc 0377)
	    			  (throw (Exception. "Octal escape sequence must be in range [0, 377].")))
	    			(char uc)))))
    {:type :char :token (str sb)}))

(defn string-token [rdr double-quote]
  (let [sb (StringBuilder.)]
    (.append sb double-quote)
    (loop [ch (.read rdr)]
      (cond
       (eof? ch) nil
       (= (char ch) \\)
       (let [ch (.read rdr)]
	 (when-not (eof? ch)
	   (.append sb
		    (or (escape-chars (char ch))
			(cond
			 (= (char ch) \u)
			 (do (let [ch (.read rdr)]
			       (if (= -1 (Character/digit ch 16))
				 (throw (Exception. (str "Invalid unicode escape: \\u" (char ch))))
				 (read-unicode-char rdr ch 16 4 true))))
			 (Character/isDigit ch)
			 (let [ch (read-unicode-char rdr ch 8 3 false)]
			   (if (> ch 0377)
			     (throw (Exception. "Octal escape sequence must be in range [0, 377]."))
			     (char ch)))
             ;:else
			 ;(throw (Exception. (str "Unsupported escape character: \\" (char ch))))
     )))
	   (recur (.read rdr))))
       :else (do (.append sb (char ch))
		 (when-not (= (char ch) \")
		   (recur (.read rdr))))))
    {:type :string :token (str sb)}))

;will be used in the future :)
(def dispatch-table
  {\^ :meta
   \' :var
   \" :regex
   \( :fn
   \{ :set
   \= :eval
   \! :comment
   \< :unreadable
   \_ :discard})

(defn dispatch-token [rdr hash-char]
  {:type :comment :token hash-char})

(defn any-token [rdr ch] {:type :any :token (str ch)})

(defn comment-token [rdr ch]
  (loop [ret [ch]
         ch (.read rdr)]
    (if (or (eof? ch) (#{\return \newline} (char ch)))
       {:type :comment :token (apply str ret)}
       (recur (conj ret (char ch)) (.read rdr)))))

(defn make-delim-token [type]
  (fn delim-token [rdr ch]
    {:type type :token (str ch)})); :stack (swap! *token-stack* conj type)}))

(def read-table
     {
      \" string-token
      \; comment-token
      \' (make-delim-token :quote)
      \@ (make-delim-token :deref)
      \^ (make-delim-token :meta)
      \` any-token
      \~ (make-delim-token :unquote)
      \( (make-delim-token :list-start)
      \) (make-delim-token :list-end)
      \[ (make-delim-token :vec-start)
      \] (make-delim-token :vec-end)
      \{ (make-delim-token :map-start)
      \} (make-delim-token :map-end)
      \\ char-token
      \% any-token
      \# dispatch-token})

(defn get-word-type [s]
  (let [ch (.charAt s 0)]
    (cond
     (Character/isDigit ch)  {:type :number :token s}
     (and (#{\+ \-} ch) (> (count s) 1) (Character/isDigit (.charAt s 1)))  {:type :number :token s}
     (= ch \:) {:type :keyword :token s}
     (function? s) {:type :function :token s}
     (macro? s) {:type :macro :token s}
     (ns-publics? s) {:type :ns-publics :token s}
     (ns-interns? s) {:type :ns-interns :token s}
     (ns-imports? s) {:type :ns-imports :token s}
     (compiler-special? s) {:type :compiler-special :token s}
     :else {:type :symbol :token s})))

(defn word-token [rdr ch]
  (let [sb (StringBuilder.)]
    (.append sb ch)
    (loop [ch (.read rdr)]
      (if (or (eof? ch) (whitespace? (char ch)) (terminating? (char ch)))
	(do
	  (unread rdr ch)
	  (get-word-type (str sb)))
	(do
	  (.append sb (char ch))
	  (recur (.read rdr)))))))

(defn read-token [rdr ch]
  (let [f (read-table ch)]
    (if f
      (f rdr ch)
      (word-token rdr ch))))

(defn get-token [rdr]
  ;(binding [*token-stack* (swap! *token-stack* vector)]
  (let [ch (.read rdr)]
    (when-not (eof? ch)
      (let [ch (char ch)]
	(if (whitespace? ch)
	  (recur rdr)
	  (read-token rdr ch))))))

(defn- -getToken [this rdr]
  ;(binding [*token-stack* (swap! *token-stack* vector)]
    (get-token rdr))

(defn get-all-tokens [rdr]
  (loop [ret []
	 t (org.enclojure.ide.lexer/get-token rdr)]
    (if t
      (recur (conj ret t) (org.enclojure.ide.lexer/get-token rdr) )
      ret)))

(defn clean-token [t]
  (when (#{:symbol :keyword :macro} (:type t))
    ;I'm sure there is a regex for this
    (.replace (.replace (:token t) "\n" "") "\t" "")))

(def word-join-map
  {\. "\\."
   \- "-"
   \* "\\*"
   \: ":"
   \? "\\?"})

(defn word-parts [word]
  (let [split-points (clojure.set/intersection (set (keys word-join-map)) (set word))]
    (loop [words [word] split-points split-points]
      (if-let [point (first split-points)]
        (recur (reduce #(concat %1 (.split %2 (word-join-map point))) [] words)
          (next split-points))
        words))))

(defn strip-prefix [t]
  (loop [index 0 len (dec (count t))]
    (if (and (< index len)
          (word-join-map (.charAt t index)))
      (recur (inc index) len)
      (when t
        (subs t index)))))

(defn words [tokens]
  (loop [tokens tokens words []]
    (if-let [token (first tokens)]
      (let [t (clean-token token)
            no-prex (strip-prefix t)]
        (recur (next tokens)
          (if (pos? (count t))
            (concat words [t (when-not (= t no-prex) no-prex)]
              (word-parts t)) words)))
      (set words))))

(defn doit []
  (with-open [f (java.io.FileReader. "/Users/ericthor/foo.clj")]
    (get-all-tokens (clojure.lang.LineNumberingPushbackReader. f))))
