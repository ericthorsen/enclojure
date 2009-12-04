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

(ns org.enclojure.ide.nb.editor.completion.completion-task
  (:import (org.netbeans.spi.editor.completion CompletionResultSet
             CompletionItem CompletionProvider CompletionDocumentation
             CompletionTask)
    (org.netbeans.api.editor.completion Completion)
    (org.netbeans.spi.editor.completion.support CompletionUtilities)
    (java.util Collection)
    (java.util.logging Level)
    (javax.swing JToolTip)
    (javax.swing.text Document PlainDocument StringContent)
    (javax.swing.text JTextComponent)
    (java.awt.event ActionEvent KeyEvent KeyListener)
    (java.awt EventQueue Component)
    (org.openide.util Exceptions)
    (org.netbeans.modules.editor NbEditorUtilities)
    (org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
    (org.netbeans.spi.editor.completion.support AsyncCompletionQuery AsyncCompletionTask))
  (:require
    [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.navigator.token-nav :as token-nav]
    [org.enclojure.ide.nb.editor.completion.completion-item :as completion-item]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    [org.enclojure.ide.nb.editor.completion.file-mapping :as file-mapping]
    [org.enclojure.ide.analyze.symbol-meta :as symbol-meta]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    [org.enclojure.ide.analyze.symbol-nav :as symbol-nav]
    [org.enclojure.commons.c-slf4j :as logger]
    ))

; setup logging
(logger/ensure-logger)

;Types of queries
; clojure funcs and hippy completion (which includes pieces of namespaces and classes)
; fred
; (fred

; Instance methods
; (.fred
; (-> target .fred (if I knew something about target...
; (memfn 

; Find namespace or class globally
; fred.

; All statics for class/ns fred
; fred/
;
; ctors
; (Fred.\space
;

(defn namespaces-only?
  [{:keys [start length prev-char first-char 
           input special-fn first-lparen context-in] :as args} ci]
  (and special-fn
    (#{"ns" "in-ns" ":require" "require" ":use" "use" ":refer" "refer"} special-fn)))

(defn java-packages-only?
  [{:keys [start length prev-char first-char
           input special-fn first-lparen context-in] :as args} ci]
  (and special-fn
    (#{"import" ":import"} special-fn)))

(defn java-classes-for-package?
  [{:keys [start length prev-char first-char
           input special-fn first-lparen context-in] :as args} ci]
  (and (java-packages-only? args ci)
    first-lparen 
    (not= first-lparen special-fn)
    (not= first-lparen input)))

(defn all-instance-members?
  [{:keys [start length prev-char first-char input] :as args} ci]
  (and (= prev-char "(") (= first-char ".")))

(defn statics?
  [{:keys [start length prev-char first-char input] :as args} ci]
  (and (.contains input "/")
        (not (= first-char "/"))
        ( = (count (filter #(= \/ (char %1)) input)) 1)))

(defn class-instance-lookup?
  [{:keys [start length prev-char first-char input] :as args} ci]
  (when (> (count input) 2)
        (and (.contains input ".")
            (not (= first-char "."))
          (file-mapping/find-ns-class-symbol-data ci
            (subs input 0 (.lastIndexOf input "."))))))

(defn namespace-search?
  [{:keys [start length prev-char first-char input] :as args} ci]
    (and (.contains input ".")
        (not (= first-char "."))
      (not (class-instance-lookup? args ci))))

(defn query-type-dataset-fn
  [{:keys [start length prev-char first-char input] :as args} ci]
    (cond ; order matters here
      (namespaces-only? args ci) :namespaces-only
      (java-classes-for-package? args ci) :java-classes-for-package
      (java-packages-only? args ci) :java-packages-only
      (all-instance-members? args ci) :all-instance-members ;java instance members
      (statics? args ci) :statics ; anything that can be qualified (java class statics, clojure publics)
      (namespace-search? args ci) :namespaces-classes
      (class-instance-lookup? args ci) :class-instance-lookup
      (= prev-char "(")  :funcall ; any clojure functions in scope
      ;<java class name>. for looking up member functions
       :else :any))
      
(defmulti query-type-dataset
  (fn [attribs completion-info]
    (when (not completion-info)
      (throw (Exception. "completion-info nil! Cannot perform operation")))
    (query-type-dataset-fn attribs completion-info)))

(defn check-for-token-walking 
  "Any character followed by an uppercase charactor"
  [#^CharSequence intext]
  (let [c (count intext)]
    (when (> c 1)
      (let [last-char (char (.charAt intext (dec c)))
            prev-char (char (.charAt intext (- c 2)))]
        (if (and (Character/isLetterOrDigit prev-char)
              (Character/isUpperCase last-char))
          {:token-walk-prefix (subs intext 0 (dec c))
           :token-walk-char (str last-char)})))))

(defmethod query-type-dataset :all-instance-members
  [{:keys [input]} completion-info]
   (logger/warn ":all-instance-members- input {}" input)
   (logger/warn ":all-instance-members- info {}" completion-info)
     ;(file-mapping/log-completion-info completion-info))
  (merge (check-for-token-walking input)
  {:search-token   
   (cond 
     (.startsWith input "(.") (subs input 2)
     (.startsWith input ".") (subs input 1)
     :else input)
    :data-set (file-mapping/get-all-java-instance-items completion-info)
    :prepend-text "."}))

(defmethod query-type-dataset :statics
  [{:keys [input]} completion-info]
  (logger/warn "statics - input={}" input)
  (let [[search-scope search-token] (.split input "/")]
    (merge (check-for-token-walking input)
    {:search-token search-token
     :search-scope search-scope
     :search-delim "/"
     :prepend-text (str search-scope "/")
     :data-set (file-mapping/get-static-funcs-fields completion-info search-scope)})))

(defmethod query-type-dataset :class-instance-lookup
  [{:keys [input]} completion-info]
  (logger/warn ":class-instance-lookup")
  (let [[search-scope search-token] (.split input "[.]+")]
    (merge (check-for-token-walking input)
    {:search-token search-token
     :search-scope search-scope
     :search-delim "."
     :prepend-text "."
     :data-set (file-mapping/get-java-class-instance-items completion-info search-scope)})))

;"All hippy completion candidates
;   All clojure publics from refered and used namespaces
;   Java statics and required namespaces need qualification so they are omitted here"
(defmethod query-type-dataset :funcall  
  [{:keys [input]} completion-info]
    (logger/warn "funcall")
  (merge (check-for-token-walking input)
  {:search-token (if (pos? (count input)) input "")
   :data-set (file-mapping/get-funcall-items completion-info)}))

(defmethod query-type-dataset :namespaces-classes
  [{:keys [input]} completion-info]
  (logger/warn ":namespaces-classes")
  (merge (check-for-token-walking input)
  {:search-token input
   :data-set (file-mapping/get-namespaces-classes-items completion-info)}))

(defmethod query-type-dataset :namespaces-only
  [{:keys [input]} completion-info]
  (logger/warn ":namespaces-only")
  (merge (check-for-token-walking input)
  {:search-token input
   :data-set (file-mapping/get-namespaces completion-info)}))

(defmethod query-type-dataset :java-packages-only
  [{:keys [input]} completion-info]
  (logger/warn ":java-packages-only")
  (merge (check-for-token-walking input)
  {:search-token input
   :data-set (file-mapping/get-java-packages completion-info)}))

(defmethod query-type-dataset :java-classes-for-package
  [{:keys [input first-lparen] } completion-info]
  (logger/warn ":java-classes-for-package")
  (merge (check-for-token-walking input)
  {:search-token input
   :data-set (file-mapping/get-java-classes-for-package first-lparen)}))

(defmethod query-type-dataset :any
  [{:keys [input]} completion-info]
  (logger/warn "any")
  (merge (check-for-token-walking input)
  {:search-token input
   :data-set (file-mapping/get-funcall-items completion-info)}))

(defn check-caret-pos
  "If the caret is at a space, move back one for the search"
  [document caret-offset]
  (let [{:keys [char-fn]} (symbol-nav/unify-doc-str document)
        c (char-fn caret-offset)]
    (logger/debug "check-caret-pos c is {} {}" c (= \space (char c)))
    (if (#{\r \n \newline \space \)} c)
        (dec caret-offset) caret-offset)))

(defn get-basic-completion-input
    "given a doc or a string with a possibly partial form and a position, 
    attempt to find the token. returns a maps with:
    :start, :end, :length, :caret-offset, :search-offset,:first-char,:prev-char                           
    :input"                
  [document caret-offset]
  (let [search-offset (check-caret-pos document caret-offset)
        start (symbol-nav/find-start-boundary document search-offset)
        end (max (inc start) caret-offset);(symbol-nav/find-end-boundary document start)
        length (- end start)
        {:keys [substr]} (symbol-nav/unify-doc-str document)]
    (logger/info "get-basic-completion-input: caret {} search-offset {} s {} e {} l {}"
      caret-offset search-offset start end length)
          {:start start
           :end end
           :length length
           :caret-offset caret-offset
           :search-offset search-offset
           :first-char (substr start 1)
           :prev-char (when (>= (dec start) 0)
                            (substr (dec start) 1))
           :input (if (pos? length)
                    (substr start length)
                    "")}))
;*******************************************************************************
; Helper code for doing more context aware searching
;******************************************************************************* 
(def *specials*
  (set (map str
  ['ns
   'in-ns
    :use
    :require
    :import
    :refer
    'use
    'require
    'import
    'refer
    'def
    'defn
    'defmacro
    'defmulti
    'defmethod
    'defstruct
    'import
    ; These may be nested...probably true of some of the above as well...
    'let
    'loop
    'for
    'doseq])))

(defn find-cursor-context-prev
  "given a doc or a string with a possibly partial form, attempt to make sense
  out of it for completion purposes.  Returns a map with data intended to be
  passed to a function that knows how to filter the completion symbols
  for the given context.
   {:special-fn     ; the form recognized as one from the *specials* table
    :first-lparen   ; the text to the right of the 1st left paren
    :context-in     ; All the text from the form found to the cursor}
                           "
    [document search-offset]
  (let [{:keys [char-fn substr length]} (symbol-nav/unify-doc-str document)
        len (length)
        start-offset (check-caret-pos document search-offset)]
      (loop [offset start-offset context-in nil first-lparen nil rparen nil]
        (if (and (>=  offset 0) (> len offset))
            (let [c #^Character (char-fn offset)
                  rparen (or rparen (= (char c) \)))]
              (if (= (char c) \()
                (let [cidx (symbol-nav/find-boundary char-fn inc len (inc offset))
                      _ (logger/info "cidx " cidx " offset " offset)
                      cid (if (pos? cidx) (substr (inc offset) (- cidx offset 1)))
                      special-fn (*specials* cid)
                      first-lparen (or first-lparen 
                                     (when-not rparen cid))
                      context-in (or context-in
                                   (and special-fn
                                     (substr cidx (- start-offset cidx))))]
                  (logger/info (str "find-cursor-context: cidx " cidx " :cid " cid " cid-len " (count cid) " :offset " offset
                        " :lparen " first-lparen " :spec " special-fn
                        " :ctx-in " context-in))
                  (if special-fn ; need to make sure that the offset is within this
                                 ; special fn form.
                    (when (> cidx search-offset)
                        {:special-fn special-fn
                        :first-lparen first-lparen
                        :context-in context-in})
                    (recur (dec offset) context-in first-lparen rparen)))
           (recur (dec offset) context-in first-lparen rparen)))
              offset))))

(defn find-cursor-context
  "given a doc or a string with a possibly partial form, attempt to make sense
  out of it for completion purposes.  Returns a map with data intended to be
  passed to a function that knows how to filter the completion symbols
  for the given context.
   {:special-fn     ; the form recognized as one from the *specials* table
    :first-lparen   ; the text to the right of the 1st left paren
    :context-in     ; All the text from the form found to the cursor}
                           "
    [document search-offset]
  (let [{:keys [char-fn substr length]} (symbol-nav/unify-doc-str document)
        len (length)
        start-offset (check-caret-pos document search-offset)]
      (loop [offset start-offset context-in nil first-lparen nil rparen nil]
        (if (and (>=  offset 0) (> len offset))
            (let [c #^Character (char-fn offset)
                  rparen (or rparen (= (char c) \)))]
              (if (= (char c) \()
                (let [cidx (symbol-nav/find-boundary char-fn inc len (inc offset))
                      _ (logger/info "cidx " cidx " offset " offset)
                      cid (if (pos? cidx) (substr (inc offset) (- cidx offset 1)))
                      special-fn (*specials* cid)
                      first-lparen (or first-lparen
                                     (when-not rparen cid))
                      context-in (or context-in
                                   (and special-fn
                                     (substr cidx (- start-offset cidx))))]
                  (logger/info (str "find-cursor-context: "                                 
                                    " cidx " cidx
                                    " :cid " cid
                                    " cid-len " (count cid)
                                    " :offset " offset
                        " :lparen " first-lparen " :spec " special-fn
                        " :ctx-in " context-in))
                  (if special-fn
                    ; need to make sure that the start-offset is within this
                    ; special-fn form.  Otherwise it is not relevant.
                    (when (symbol-nav/match-brace document (inc offset) start-offset)
                        {:special-fn special-fn
                        :first-lparen first-lparen
                        :context-in context-in})
                  (recur (dec offset) context-in first-lparen rparen)))
           (recur (dec offset) context-in first-lparen rparen)))
              offset))))

  (defn tfp [s]
    (find-cursor-context s (count s) (dec (count s))))

;*******************************************************************************
; End region for :Helper code for doing more context aware searching
;*******************************************************************************
(defn get-completion-input [document caret-offset]
  (let [basic (get-basic-completion-input document caret-offset)]
    (if-let [more
        (try ; be paranoid until this is better debugged...
            (find-cursor-context document caret-offset)
        (catch Throwable e
            (logger/warn (str "Error when attempting to get more context for completion: "
                                 (.getMessage e)))))]
      (merge basic more)
    basic)))

 (defn prepare-query [#^JTextComponent component]
   (let [cinputs (get-completion-input
                             (.getDocument component)
                             (.getcaretPosition component))]
     (.setCaretPosition component
       (+ (:start cinputs) (:length cinputs)))
     cinputs))

(def -reg-escape-chars-
  #{\( \) \[ \] \\ \. \* \& \+ \-})

(defn regex-escape [s]
  (apply str
    (apply concat
        (map #(if-let [nc (-reg-escape-chars- %1)] [\\ nc] [%1]) s))))

;-----------------------improved regex search
(defn partition-with
  "Takes a seqable and splits it into pieces applying pred to each item.
Returns a vector of items"
  [col pred]
  (loop [col col seqs [] curr []]
    (if-let [c (first col)]
      (let [split? (pred c)
            s (if split?
                (if (pos? (count curr))
                  (conj seqs curr) seqs) seqs)
            c (if split? [c] (conj curr c))]
      (recur (rest col) s c))
      (if curr (conj seqs curr) seqs))))

(defn build-search-anchors2
  "Builds the incremental search portion of a regular expression"
  [upper-char-vec]
  (apply str
    (reduce
        (fn [v [c & cs]]
            (let [lc (regex-escape (str (Character/toLowerCase c)))
                  uc (regex-escape (str (Character/toUpperCase c)))
                  css (apply str cs)]
                (conj v (str "([a-z,A-Z]*[.-]+[" lc uc "]+"
                          css "|[a-z]*" uc css ")"))))
    [] upper-char-vec)))

(defn search-pattern2
  "Pattern for doing searches across camel case and -. separated tokens"
  [start-text]
  (when (> (count start-text) 1)
    (let [[prefix & patterns]
            (partition-with start-text
                          #(Character/isUpperCase %))
          p (str "^" (apply str prefix)
                      (build-search-anchors2 patterns))]
      (logger/info "RE search pattern:" p)
        (re-pattern p))))

(defn do-regex-search2
  "does a regular expression search for token walking. Returns the shortest group match
   as the replacement input string."
  [search-token forms]
  (when (and (not= "" search-token) (pos? (count search-token)))
    (when-let [pattern (search-pattern2 search-token)]
        (loop [forms (filter
                        (fn [{name :name}]
                          (and (not= (str name) "")  (pos? (count (str name)))))
                       forms)
               matches []]
            (if-let [{tag :name :as f} (first forms)]
                (recur (rest forms)
                    (let [grp (re-find pattern (str tag))]
                      (if grp (conj matches f) matches)))
              matches)))))

(defn do-search
  "worker function to do the actual search"  
  [search-token forms pred? info]
  (logger/debug "{} token {}" info search-token)
    (filter (fn [{name :name}]
              (when (and (not= (str name) "") (not= "" search-token)
                    (pos? (count search-token)) (pos? (count (str name))))
                   (pred? name search-token))) forms))

(defn do-search-strategy [search-token forms]
  (logger/info "do-search-strategy token:{}  len(search-token) {}" search-token ""
    (count search-token) " (count forms) " (count forms))
  (if (or (nil? search-token)
        (zero? (count (.trim search-token)))
            (= " " search-token))
             {:match :start-with :results forms}
    (let [criteria-match
          (vec (do-search search-token forms #(.startsWith (str %1) %2) "startswith"))]
      (if (pos? (count criteria-match))
        {:match :start-with :results criteria-match :search-str search-token}
       (when-let [matches (do-regex-search2 search-token forms)]
         {:match :token-walk :results matches
             :search-str search-token})))))

(defn remove-dups2 [forms]
    (loop [forms forms results [] lookup #{}]
      (if-let [f (first forms)]        
         (if (lookup (str (:name f) (:arglists f)))
           (recur (rest forms) results lookup)
           (recur (rest forms) (conj results f) 
             (conj lookup (str (:name f) (:arglists f)))))
        results)))

(defn remove-dups [forms]
    (loop [forms forms lookup {}]
      (if-let [f (first forms)]
        (let [args (:arglists f)
              nname (str (:name f))
              exists? (lookup nname)]
         (if (and exists? (:arglists exists?))
           (recur (rest forms) lookup)
           (recur (rest forms) 
             (assoc lookup nname f))))
        (vals lookup))))

(def -debug- (ref nil))

(defn get-completion-query [query-type text-component]
  (let [data {:query-type query-type :text-component text-component}]
  (proxy [org.netbeans.spi.editor.completion.support.AsyncCompletionQuery] []
    (query [#^CompletionResultSet resultset #^Document document caretOffset]              
      (try ; Always grab the current editor pane.  Could be invoked from a repl
      (let [{:keys [file doc]} (editor-utils/get-current-editor-data)]
        (let [completion-info 
                (if-let [c (file-mapping/ensure-completion-info file)]
                  c (file-mapping/get-default-completion-info))
              input (if (not= doc document) ; its the repl,keep it simple
                      (get-basic-completion-input document caretOffset)
                (get-completion-input document caretOffset)) ; file editing
              intext (.getText document (:start input) (:length input))
              dataset (query-type-dataset input completion-info)
              forms (:data-set dataset)
              {:keys [search-token search-scope search-delim]} dataset]
        (logger/info "input: {} search-token:{} nil?: {} scope: {}"
            input search-token (nil? search-token)  search-scope)
          ;first do a normal lookup:
        (let [{:keys [match results search-str]}
                (do-search-strategy search-token forms)
              filtered (remove-dups results)]
          (logger/info "Completion: count of initial forms is {}" (count forms))
          (logger/info "Completion: count of forms after search is {}" (count results))
          (logger/info "Completion: count of forms after filtering is {}" (count filtered))
          (when (= 1 (count filtered))
            (logger/info "one results is {}" (first filtered)))
          (dosync
            (alter -debug-
              (fn [_] (hash-map :caret caretOffset :file file :info completion-info :input input
                 :intext intext :dataset dataset :forms forms
                 :match match :results results :search-str search-str
                 :filtered filtered))))
        (doseq [form filtered]
            (.addItem resultset
              (completion-item/get-completion-item
                form
                (assoc (merge data (dissoc dataset :dataset))
                        :input input :intext intext :caret-offset caretOffset
                  ; Get rid of this tag if you want to disable the auto-select
                  ; when there is one item in the list
                        :instant-sub (= 1 (count filtered)))))))))
         (catch Throwable t
          (Exceptions/printStackTrace t))
        (finally
          (logger/info "Finishing completion task {}")
          (.finish resultset)))))))

(defn get-completion-task [query-type text-component]
  (AsyncCompletionTask. (get-completion-query query-type text-component) text-component))

(defn get-key-listener [data]
  (proxy [java.awt.event.KeyListener] []
    (keyTyped [#^KeyEvent evt])
    (keyPressed [#^KeyEvent evt])))
