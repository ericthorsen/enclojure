;; clojure-compiler.clj -- beginnings of a clojure written in clojure
;;
;; Part of the Clojure project, thus falling under the CA described
;; at http://clojure.org/contributing
;;
;;  Copyright (c) Chris Houser. All rights reserved. The use and
;;  distribution terms for this software are covered by the Common Public
;;  License 1.0 (http://opensource.org/licenses/cpl.php) which can be found
;;  in the file CPL.TXT at the root of this distribution. By using this
;;  software in any fashion, you are agreeing to be bound by the terms of
;;  this license. You must not remove this notice, or any other, from this
;;  software.

(ns net.n01se.clojure-compiler
  (:import (clojure.lang RT LazySeq PersistentList Symbol Var
                         Namespace Keyword ISeq
                         Compiler$CompilerException
                         IPersistentList IPersistentVector
                         IPersistentMap IPersistentSet)
           (java.lang.reflect Modifier Method Field))
  (:use [clojure.contrib.cond :only (cond-let)]))

(defn old-analyze [f]
  (let [o (clojure.lang.Compiler/analyze clojure.lang.Compiler$C/STATEMENT f)]
    (reduce (fn [m f]
              (assoc m
                     (keyword (.getName f))
                     (try (.get f o) (catch Exception e '?))))
            {:class (symbol (.getSimpleName (class o)))
             :has-java-class (.hasJavaClass o)
             :java-class (when (.hasJavaClass o) (.getJavaClass o))}
            (.getFields (class o)))))

(defmacro err [& msgs]
  `(throw (Exception. (str ~@msgs))))

(defmacro err-arg [& msgs]
  `(throw (IllegalArgumentException. (str ~@msgs))))

(defn tag-of
  "Take a symbol and return it's tag (type hint) as a symbol"
  [o]
  (let [tag (:tag ^o)]
    (cond (symbol? tag) tag
          (string? tag) (Symbol/intern nil tag)
          :else nil)))

(def char-names
  {\- "_",
   \: "_COLON_",
   \+ "_PLUS_",
   \> "_GT_",
   \< "_LT_",
   \= "_EQ_",
   \~ "_TILDE_",
   \! "_BANG_",
   \@ "_CIRCA_",
   \# "_SHARP_",
   \$ "_DOLLARSIGN_",
   \% "_PERCENT_",
   \^ "_CARET_",
   \& "_AMPERSAND_",
   \* "_STAR_",
   \| "_BAR_",
   \{ "_LBRACE_",
   \} "_RBRACE_",
   \[ "_LBRACK_",
   \] "_RBRACK_",
   \/ "_SLASH_",
   \\ "_BSLASH_",
   \? "_QMARK_"})

(defn munge [string & [no-dot]]
  (let [char-names (if no-dot (assoc char-names \. "_DOT_") char-names)]
    (apply str (map #(get char-names % %) string))))

(defn namespace-for
  ([sym] (namespace-for *ns* sym))
  ([context-ns sym]
    (let [ns-sym (Symbol/create (namespace sym))]
      (or (.lookupAlias context-ns ns-sym) ; current ns' aliases?
          (Namespace/find ns-sym)))))      ; otherwise use Namespace's map

(defn maybe-class [form string-ok?]
  (condp instance? form
    Class form
    Symbol (when-not (namespace form)
             (if (or (some #{\.} (name form)) (= \[ (first (name form))))
               (RT/classForName (name form))
               (let [o (.getMapping *ns* form)]
                 (when (class? o)
                   o))))
    String (when string-ok?
             (RT/classForName form))
    nil))

(defmacro make-array-type-map [& classes]
  (reduce (fn [m classname]
            (let [c (Class/forName (str "java.lang." classname))
                  prim-type (.get (.getField c "TYPE") c)]
              (assoc m (str prim-type "s") (class (make-array prim-type 0)))))
          {} classes))

(def array-type
  (make-array-type-map Integer Long Float Double Character Short Byte Boolean))

(defn tag-to-class [tag]
  (cond-let [cls]
    (and (symbol? tag) (not (namespace tag)) (array-type (name tag))) cls
    (maybe-class tag true) cls
    :else (err "Unable to resolve classname: " tag)))

(defn primitive? [c]
  (and c (.isPrimitive c) (not= c Void/TYPE)))

(defstruct ast-simple :type :form :has-java-class :java-class)

(defn ast [type form class-fn & etc]
  (let [m (struct ast-simple
                  type form (boolean class-fn) (when class-fn (class-fn)))]
    (if etc
      (apply assoc m etc)
      m)))

(defn ast-get-java-class [o]
  (if (:has-java-class o)
    (:java-class o)
    (err "No Java class for " (:type o) ": " o)))

(defn ast-has-java-class [o]
  (:has-java-class o))

(defn ast-copy-java-class [o]
  (when (:has-java-class o)
    (constantly (:java-class o))))

(defn maybe-primitive-type [e]
  (when (#{:local :instance-field :static-field :method} (:type e))
    (when (ast-has-java-class e)
      (let [c (ast-get-java-class e)]
        (when (primitive? c)
          c)))))

(defn ast-get-primitive-type [ast-node]
  (maybe-primitive-type (:init ast-node)))

(def *line* -1)
(def *source* "[[unknown]]")
(def *name* nil)
(def *local-env* nil)
(def *loop-locals*)
(def *next-local-num* 0)
(def *method* nil)
(def *in-catch-finally* nil)
(def *constants*)
(def *vars*)
(def *keywords*)
(def *emit-context* :expression) ; could also be :statement, :return, or :eval
(def *reflector*
  {:get-field #(clojure.lang.Reflector/getField %1 %2 %3)
   :get-methods #(clojure.lang.Reflector/getMethods %1 %2 %3 %4)
   :arg-type-match #(clojure.lang.Reflector/paramArgTypeMatch %1 %2)})

(defn eval-or [x]
  (if (= :eval *emit-context*)
    :eval
    x))

(defn get-and-inc-local-num! []
  (let [n *next-local-num*]
    (when (> n @(:max-local *method*))
      (reset! (:max-local *method*) n))
    (set! *next-local-num* (inc n))
    n))

(defn register-constant! [o]
  (if-not (.isBound #'*constants*)
    -1
    (do
      (set! *constants* (conj *constants* o))
      (count *constants*))))

(defn register-var! [v]
  (when (.isBound #'*vars*)
    (when-not (*vars* v)
      (set! *vars* (assoc *vars* v (register-constant! v)))))
  v)

(defn close-over! [local-bind method]
  (when (and local-bind method)
    (if (nil? (@(:locals method) local-bind))
      (do
        (swap! (-> method :instance :closes) conj local-bind)
        (recur local-bind (:parent method)))
      (when *in-catch-finally*
        (swap! (:locals-in-catch-finally method) conj (:idx local-bind))))))

(defn reference-local! [sym]
  (when *local-env*
    (when-let [local-bind (*local-env* sym)]
      (close-over! local-bind *method*)
      local-bind)))

(defn register-local! [sym tag init-ast]
  (let [idx (get-and-inc-local-num!)
        ptype (maybe-primitive-type init-ast)
        get-java-class (if (and tag (ast-copy-java-class init-ast)
                                (primitive? (ast-get-java-class init-ast))
                                (not ptype))
                         nil
                         (if tag
                           #(tag-to-class tag)
                           (ast-copy-java-class init-ast)))]
    (when (and tag ptype)
      (throw (UnsupportedOperationException.
               "Can't type hint a local with a primitive initializer")))
    (let [lb (ast :local-binding sym get-java-class
                  :primitive-type ptype
                  :idx idx :tag tag :init init-ast :name (munge (name sym)))]
      (set! *local-env* (assoc *local-env* sym lb))
      (swap! (:locals *method*) conj lb)
      (swap! (:index-locals *method*) assoc idx lb)
      lb)))

(defn resolve-in [context-ns sym & flags]
  (let [{:keys [private-ok quiet intern-new no-class]} (set flags)
        throw? (not quiet)]
    (cond-let [obj]
      (namespace sym)
        (let [sym-ns (namespace-for context-ns sym)
              name-sym (Symbol/create (name sym))
              v (when sym-ns
                  (.findInternedVar sym-ns name-sym))]
          (cond
            (nil? sym-ns) (and throw? (err "No such namespace: " obj))
            (and intern-new (= sym-ns *ns*)) (.intern *ns* name-sym)
            (nil? v) (and throw? (err "No such Var: " sym))
            (and (not= (.ns v) *ns*)
                 (:private ^v)
                 (not private-ok)
                 throw?)
              (throw (IllegalStateException. (str "Var is not public: " sym)))
            :else v))
      (and (not no-class) (maybe-class sym false)) obj
      ({'ns #'ns, 'in-ns #'in-ns} sym) obj
      (.getMapping context-ns sym)     obj
      intern-new                       (.intern *ns* (Symbol/create (name sym)))
      *allow-unresolved-vars*          sym
      :else (and throw?
                 (err "Unable to resolve symbol '" sym "' in this context")))))

(defn resolve-sym
  ([sym] (resolve-in *ns* sym :quiet))
  ([sym private-ok?]
    (resolve-in *ns* sym :quiet (when private-ok? :private-ok))))

(defn lookup-var [sym intern-new?]
  (when-let [o (resolve-in *ns* sym
                           :quiet (when intern-new? :intern-new) :no-class)]
    (if-not (var? o)
      (err "Expecting Var, but " sym " is mapped to " o)
      (register-var! o))))

(defn inline-of! [op arity]
  (when-not (and (symbol? op) (reference-local! op))
    (when-let [v (if (symbol? op) (lookup-var op false) op)]
      (when (var? v)
        (when (and (not= *ns* (.ns v)) (:private ^v))
          (throw (IllegalStateException. (str "Var is not public: " op))))
        (when-let [inline (:inline ^v)]
          (when (get (:inline-arities ^v) arity true)
            inline))))))

(defn macro? [op]
  (when-let [v (if (var? op) op (lookup-var op false))]
    (when (:macro ^v)
      (if (and (:private ^v) (not= *ns* (.ns v)))
        (throw (IllegalStateException. (str "Var is not public: " op)))
        v))))

(declare analyze-seq)

; === analyze ===

;(analyze 'Integer/TYPE :get-field #(clojure.lang.Reflector/getField %1 %2 %3))

(defmulti analyze
  "Produce Clojure AST for Clojure forms."
  (fn [form] (class form)))

(defmethod analyze LazySeq [form]
  (if-let [form-seq (seq form)]
    (analyze form-seq)
    PersistentList/EMPTY))

(defmethod analyze nil [form]
  (ast :nil form (constantly nil) :val nil))

(defmethod analyze Boolean [form]
  (ast :boolean form (constantly Boolean) :val (boolean form)))

(defmethod analyze Symbol [sym]
  (let [tag (tag-of sym)
        static-field
          (when (and (namespace sym) (not (namespace-for sym)))
            (when-let [c (maybe-class (Symbol/create (namespace sym)) false)]
              (when ((:get-field *reflector*) c (name sym) true)
                (let [field (.getField c (name sym))]
                  (ast :static-field sym #(.getType field)
                       :field field :field-name (name sym)
                       :class c :line *line*)))))
        local-binding
          (when-not (namespace sym)
            (when-let [b (reference-local! sym)]
              (when (and tag (ast-get-primitive-type b))
                (throw (UnsupportedOperationException.
                         "Can't type hint a primitive local")))
              (ast :local sym (if tag
                                (tag-to-class tag)
                                (ast-copy-java-class b))
                   :local-binding b, :tag tag)))]
    (or
      static-field
      local-binding
      (let [o (resolve-sym sym)]
        (condp instance? o
          Var (if (macro? o)
                (err "Can't take value of a macro: " o)
                (let [tag2 (or tag (:tag ^o))]
                  (register-var! o)
                  (ast :var sym (when tag2 #(tag-to-class tag2))
                       :var o :tag tag2)))
          Class (ast :constant sym (constantly Class)
                     :val o :id (register-constant! o))
          Symbol (ast :unresolved-var sym nil)
          (err "Unable to resolve symbol '" sym "' in this context"))))))

(defmethod analyze Keyword [form] ; register-keyword
  (when (and (.isBound #'*keywords*) (not (*keywords* form)))
    (set! *keywords* (assoc *keywords* form (register-constant! form))))
  (ast :keyword form (constantly Keyword)))

(defmethod analyze String [form]
  (ast :string (.intern #^String form) (constantly String)))

(defn include-meta [ast-node]
  (let [form (:form ast-node)]
    (if-not ^form
      ast-node
      (ast :meta form (ast-copy-java-class form)
           :expr ast-node
           :meta (binding [*emit-context* (eval-or :expression)]
                   (analyze ^form))))))

(defn empty-expr [form]
  (when (empty? form)
    (include-meta
      (ast :empty form
           #(or (some (fn [c] (and (isa? c (class form)) c))
                      [IPersistentList IPersistentVector
                       IPersistentMap IPersistentSet])
                (throw (UnsupportedOperationException.
                         "Unknown Collection type")))))))

(defmethod analyze ISeq [form]
  (or (empty-expr form)
    (binding [*line* (or (:line ^form) *line*)]
;      (try
        (let [me (macroexpand-1 form)]
          (if-not (= me form)
            (analyze me)
            (let [op (first form)]
              (when-not op (err-arg "Can't call nil"))
              (let [inline (inline-of! op (dec (count form)))]
                (if inline
                  (analyze (apply inline (next form)))
                  (analyze-seq form))))))
;        (catch Compiler$CompilerException e
;          (throw e))
;        (catch Throwable e
;          (throw (Compiler$CompilerException. *source* *line* e)))))))
)))

(defmethod analyze IPersistentVector [form]
  (include-meta
    (ast :vector form (constantly IPersistentVector)
         :args (binding [*emit-context* (eval-or :expression)]
                 (into [] (map analyze form))))))

(defmethod analyze IPersistentMap [form]
  (include-meta
    (ast :map form (constantly IPersistentMap)
         :keyvals (binding [*emit-context* (eval-or :expression)]
                    (reduce (fn [acc [k v]]
                              (conj acc (analyze k) (analyze v)))
                            [] form)))))

(defmethod analyze IPersistentSet [form]
  (include-meta
    (ast :set form (constantly IPersistentSet)
         :keys (binding [*emit-context* (eval-or :expression)]
                 (into [] (map analyze form))))))

(defn constant-ast [form]
  (let [c (class form)]
    (ast :constant form
         (when (Modifier/isPublic (.getModifiers c)) (constantly c))
         :val form :id (register-constant! form))))

(defmethod analyze :default [form]
  (constant-ast form))

; === analyze-seq ===

(defmulti analyze-seq first)

(defmethod analyze-seq :default [form]
  ; default means no special form matches -- invoke expr
  (binding [*emit-context* (eval-or :expression)]
    (let [[fexpr & args] (map analyze form)
          tag (or (tag-of form) (when (= :var (type fexpr)) (:tag fexpr)))]
      (ast :invoke form (when tag #(tag-to-class tag))
           :source *source* :line *line*
           :tag tag :fexpr fexpr :args (into [] args)))))

(defmacro max-positional-arity [] 20)

(defn analyze-method [instance [m-name arg-vec & body :as form]]
  (when-let [a (first (remove symbol? arg-vec))]
    (err-arg "Formal method params must be Symbols: " a))
  (when-let [a (first (filter namespace arg-vec))]
    (err-arg "Can't use qualified name as param: " a))
  (let [[req-args [_ rest-arg too-many]] (split-with #(not= '& %) arg-vec)]
    (when (> (count req-args) (max-positional-arity))
      (err "Can't specify more than " (max-positional-arity) " arguments"))
    (when (or too-many (= rest-arg '&))
      (err-arg "Invalid parameter list"))
    (binding [*local-env* *local-env*
              *loop-locals* nil
              *next-local-num* 0
              *method* (ast :methoddef form nil
                            :name m-name
                            :parent *method*
                            :line *line*
                            :instance instance
                            :signature [m-name (count req-args)]
                            :max-local (atom 0)
                            :locals (atom #{})
                            :index-locals (atom {})
                            :locals-in-catch-finally (atom #{}))]
      (register-local! (:this-name instance) nil nil)
      (let [lbs (into [] (for [arg req-args]
                           (register-local! arg (tag-of arg) nil)))
            rest-lb (when rest-arg
                      (register-local! rest-arg 'clojure.lang.ISeq nil))]
        (set! *loop-locals* lbs)
        (assoc *method*
               :req-args lbs
               :rest-arg rest-lb
               :arg-locals (if rest-lb (conj lbs rest-lb) lbs)
               :body (binding [*emit-context* :return]
                       (analyze (cons 'do body))))))))

; (instance this-name? [Supers :as full.new.class.Name] [ctor-args]
;   (meth1 [] ...) ...)
(defmethod analyze-seq 'instance [[_ & args :as form]]
  (let [[this-name supers-vec ctor-args & methods]
          (if (vector? (first args)) (cons 'this args) args)
        [as? class-name] (nthnext supers-vec (- (count supers-vec) 2))
        supers-seq       (map tag-to-class
                              (if (= :as as?)
                                (drop-last 2 supers-vec)
                                supers-vec))
        class-name       (if (= :as as?) class-name 'gen.unique.class.name)
        [extend & infs]  (if (or (empty? supers-seq)
                                 (.isInterface (first supers-seq)))
                           (cons Object supers-seq)
                           supers-seq)
        simple-name      (str (if *name* (munge *name* :no-dot) "instance")
                              "__" (RT/nextID))
        full-name        (str (if *method*
                                (-> *method* :instance :name)
                                (munge (str (.name *ns*))))
                              "$" simple-name)
        instance (ast :instance form (if (next supers-seq)
                                       (err "need to gen general-super")
                                       (constantly (first supers-seq)))
                      :tag (tag-of form)
                      :once-only? (:once ^(first form))
                      :simple-name simple-name
                      :name full-name
                      :internal-name (.replace full-name \. \/)
                      ;:fntype (Type/getObjectType internal-name) ; emit-time?
                      :this-name (Symbol/intern (str this-name))
                      :line *line*
                      :closes (atom #{}))]
    (binding [*constants* [], *keywords* {}, *vars* {}]
      (loop [methods methods, method-map {}, variadic nil]
        (if methods
          (let [method (analyze-method instance (first methods))]
            (recur
              (next methods)
              (assoc method-map (:signature method) method)
              (cond (not (:variadic method)) variadic
                    variadic (err "Can't have more than 1 variadic overload")
                    :else method)))
          (do
            (when-let [{vm-name :name req-args :req-args} variadic]
              (when-not (= vm-name 'invoke)
                (err "Variadic is only supported for invoke methods"))
              (when (some (fn [[m-name m-args]]
                            (and (= m-name vm-name)
                                 (> m-args (count req-args))))
                          (keys method-map))
                (err "Can't have fixed arity function with more params "
                     "than variadic function")))
            (assoc instance
                 :methods method-map
                 :variadic variadic
                 :keywords *keywords*
                 :vars *vars*
                 :constants *constants*
                 :constants-id (RT/nextID))))))))


(defmethod analyze-seq 'do [[_ & forms :as form]]
  (let [middle-forms (drop-last forms)
        middle-exprs (binding [*emit-context* (eval-or :statement)]
                       (into [] (map analyze middle-forms)))
        last-expr (analyze (last forms))]
    (ast :do form (ast-copy-java-class last-expr)
         :exprs (conj middle-exprs last-expr))))

(defn analyze-let [[_ bindings & body :as form] loop?]
  (when-not (vector? bindings)
    (err-arg "Bad binding form, expected vector, got: " (second form)))
  (when-not (even? (count bindings))
    (err-arg "Bad binding form, expected symbol expression pairs"))
  (doseq [sym (take-nth 2 bindings)]
    (when-not (symbol? sym)
      (err-arg "Bad binding form, expected symbol, got:" sym))
    (when (namespace sym)
      (err "Can't let qualified name: " sym)))
  (if (or (= *emit-context* :eval)
          (and loop? (= *emit-context* :expression)))
    (analyze `(fn* [] ~@form))
    (binding [*local-env* *local-env*, *next-local-num* *next-local-num*]
      (let [inits (for [[sym init] (partition 2 bindings)]
                    (let [init-ast (binding [*emit-context* :expression
                                             *name* sym]
                                     (analyze init))
                          lb (register-local! sym (tag-of sym) init-ast)]
                      (when loop?
                        (set! *loop-locals* (conj *loop-locals* lb)))
                      {:binding lb, :init init-ast}))
            inits (into [] inits)
            body-ast (if loop?
                       (binding [*emit-context* :return
                                 *loop-locals* (into [] (map :binding inits))]
                         (analyze (cons 'do body)))
                       (analyze (cons 'do body)))] ; leave context alone
      (ast :let form (ast-copy-java-class body-ast)
           :loop? loop?, :binding-inits inits, :body body-ast)))))

(defmethod analyze-seq 'let*  [form]
  (analyze-let form false))

(defmethod analyze-seq 'loop* [form]
  (binding [*loop-locals* nil]
    (analyze-let form :loop)))

(defn subsumes [cs1 cs2]
  (if (= cs1 cs2)
    false
    (every? identity (map (fn [c1 c2]
                            (or (= c1 c2)
                                (and (not (.isPrimitive c1))
                                     (.isPrimitive c2))
                                (.isAssignableFrom c2 c1)))
                          cs1 cs2))))


(defn get-matching-params [mname arg-exprs minfos]
  ; cons target-expr type onto aclasses and :params:
  (let [jclass #(if (ast-has-java-class %) (ast-get-java-class %) Object)
        aclasses (map jclass arg-exprs)
        match? (:arg-type-match *reflector*)
        either-match?  #(or (match? %1 %2) (match? %2 %1))
        matching-minfos (filter #(every? identity
                                          (map either-match?
                                               (:params %) aclasses))
                                 minfos)]
    ; XXX isa?
    ; Now eliminate all methods that are subsumed by some other
    ; matching method, returning just the minimal set
    ;(doseq [m minfos] (prn (:params m)))
    ;(prn :arg aclasses)
    ;(println "----")
    ;(doseq [m matching-minfos] (prn (:params m)))
    (set (map :member
              (remove (fn [x]
                        (some
                          #(and (not= % x) (subsumes (:params x) (:params %)))
                          matching-minfos))
                      matching-minfos)))))

(defn instance-member [form obj mname args & [allow-field]]
  (let [minfos (for [c (vals (ns-imports *ns*))
                     m ((:get-methods *reflector*) c (count args) mname false)]
                 {:member m, :params (cons c (.getParameterTypes m))})
        finfos (when (and (empty? args) allow-field)
                 (for [c (vals (ns-imports *ns*))
                       :let [m ((:get-field *reflector*) c mname false)]
                       :when m]
                   {:member m :params [c]}))
        members (get-matching-params
                  mname (cons obj args) (concat minfos finfos))
        this-type-known (ast-has-java-class obj)]
    (when (and *warn-on-reflection*
               (or (not this-type-known) (> (count members) 1)))
      (.println *err*
        (format "Reflection warning %s line %d: '%s'%s, %d matches"
                *source* *line* mname
                (if this-type-known "" " with untyped 'this'")
                (count members))))
    (ast :instance-member form (when this-type-known
                                 (let [m (first members)]
                                   (cond (not m) nil
                                         (instance? Field m) #(.getType m)
                                         :else #(.getReturnType m))))
     :this-type-known this-type-known
     :allow-field (if this-type-known
                    (instance? Field (first members))
                    (boolean allow-field))
     :target obj, :mname mname, :args args, :members members
     :source *source*, :line *line*)))

(defn static-method [form cls mname args]
  (let [methods ((:get-methods *reflector*) cls (count args) mname true)]
    (when (empty? methods)
      (err-arg "No matching method: " mname))
    ;(prn methods)
    (let [members (get-matching-params
                    mname args (for [m methods]
                                 {:member m :params (vec (.getParameterTypes m))}))
          rtns (set (map #(.getReturnType %) members))]
      (when (and *warn-on-reflection* (not= 1 (count members)))
        (.println *err*
                  (format "Reflection warning %s line %d: '%s', %d matches"
                          *source* *line* mname (count members))))
      (ast :static-method form (when (seq rtns) (constantly (first rtns)))
           :class cls, :mname mname, :args args, :members members
           :source *source*, :line *line*))))

(defn static-field [form cls fname]
  (let [field (.getField cls fname)]
    (ast :static-field form (constantly (.getType field))
         :class cls, :mname fname
         :source *source*, :line *line*)))

(defmethod analyze-seq '. [[_ class-or-inst fld-meth-lst :as form]]
  (when (< (count form) 3)
    (err-arg "Malformed member expression, expecting (. target member ...)"))
  (let [cls (maybe-class class-or-inst false)
        obj (when-not cls
              (binding [*emit-context* (eval-or :expression)]
                (analyze class-or-inst)))] ; instance, in this case
    (if (and (== (count form) 3) (symbol? fld-meth-lst))
      (let [fld-meth-name (name fld-meth-lst)]
        (if cls
          (if (seq ((:get-methods *reflector*) cls 0 fld-meth-name true))
            (static-method form cls fld-meth-name [])
            (static-field form cls fld-meth-name))
          (instance-member form obj fld-meth-name [] :fld-ok)))
      (let [[sym & arg-seq] (if (seq? fld-meth-lst) fld-meth-lst (nnext form))
            args (binding [*emit-context* (eval-or :expression)]
                   (into [] (map analyze arg-seq)))]
        (when-not (symbol? sym)
          (err-arg "Malformed member expression"))
        (if cls
          (static-method form cls (name sym) args)
          (instance-member form obj (name sym) args))))))

(defmethod analyze-seq 'def [[_ sym & [init] :as form]]
  (when-not (<= 2 (count form) 3)
    (err "def takes 1 or 2 argumenets, not " (count form)))
  (when-not (symbol? sym)
    (err "Second argument to def must be a Symbol"))
  (let [v (lookup-var sym true)]
    (when-not v
      (err "Can't refer to qualified var that doesn't exist"))
    (when-not (= *ns* (.ns v))
      (if (namespace sym)
        (err "Can't create defs outside of current ns")
        (err "Name conflict, can't def " sym " because namespace: " *ns*
             " refers to " v)))
    (binding [*emit-context* (eval-or :expression)]
      (ast :def form (constantly clojure.lang.Var)
           :var v, :init (analyze init)
           :meta (analyze (with-meta sym (assoc ^sym :line *line*
                                                     :source *source*)))
           :init-provided (== 3 (count form))
           :source *source*, :line *line*))))

; (fn foo ([a] ....))
; (fn [a] ....)
; should be a regular macro, but this will do for now:
(defmethod analyze-seq 'fn* [[special & args]]
  (let [[this-name & etc] (if (symbol? (first args)) args (cons 'this args))
        methods (if (seq? (first etc)) etc (list etc))
        instance-body (map #(cons 'invoke %) methods)
        instance-form `(~(with-meta 'instance ^special) ~this-name
                           [clojure.lang.AFn] []
                           ~@instance-body)]
    (analyze instance-form)))

(defmethod analyze-seq 'quote [[_ const :as form]]
  (if (nil? const)
    (ast :nil form (constantly nil) :val nil)
    (constant-ast const)))

(defmethod analyze-seq 'try [[_ & forms :as form]]
  (if-not (= *emit-context* :return)
    (analyze `(fn* [] ~form))
    (let [ret-local (get-and-inc-local-num!)
          finally-local (get-and-inc-local-num!)
          parse-parts
            (fn [parts clause]
              (condp = (and (seq? clause) (first clause))
                'catch (let [[_ cls lcl & catch-body] clause]
                         (when (:final parts)
                           (err "finally must be last in try form"))
                         (or (symbol? lcl) (err-arg "Bad binding form: " lcl))
                         (when (namespace lcl)
                           (err "Can't bind qualified name: " lcl))
                         (binding [*next-local-num* *next-local-num*
                                   *in-catch-finally* true]
                           (prn :cls cls :lcl lcl :body catch-body)
                           (update-in
                             parts [:catches] conj
                             {:class (or (maybe-class cls false)
                                         (err-arg "Unable to resolve "
                                                  "classname: " cls))
                              :local-binding (register-local!
                                               lcl (and (symbol? cls) cls) nil)
                              :handler (analyze (cons 'do catch-body))})))
                'finally (do
                           (when (:final parts)
                             (err "only one finally allowed per try form"))
                           (binding [*in-catch-finally* true]
                             (assoc parts :final
                                    (analyze (cons 'do (rest clause))))))
                (do
                  (when (:final parts)
                    (err "finally must be last in try form"))
                  (when (seq (:catches parts))
                    (err "Only catch or finally clause can follow catch "
                         "in try expression"))
                  (update-in parts [:body] conj clause))))
          {:keys [body catches final]}
            (reduce parse-parts {:body [] :catches []} forms)
          try-expr (analyze (cons 'do body))]
      (ast :try form (ast-copy-java-class try-expr)
           :try-expr try-expr, :catches catches, :finally final
           :ret-local ret-local, :finally-local finally-local))))

(defmethod analyze-seq 'if* [[_ tst then else :as form]]
  (when (< (count form) 3) (err "Too few arguments to if"))
  (when (> (count form) 4) (err "Too many arguments to if"))
  (let [tst-ast (binding [*emit-context* (eval-or :expression)]
                  (analyze tst))
        then-ast (analyze then)
        else-ast (analyze else)]
    (ast :if form (when (and (ast-has-java-class then-ast)
                             (ast-has-java-class else-ast)
                             (or (= (ast-get-java-class then-ast)
                                    (ast-get-java-class else-ast))
                                 (nil? (ast-get-java-class then-ast))
                                 (nil? (ast-get-java-class else-ast))))
                    (if-let [cls (ast-get-java-class then-ast)]
                      (constantly cls)
                      (constantly (ast-get-java-class else-ast))))
         :source *source*,:line *line*)))

(defmethod analyze-seq 'var [[_ sym :as form]]
  (if-let [v (lookup-var sym false)]
    (ast :the-var form (constantly Var))
    (err "Unable to resolve var '" sym "' in this context")))

(defn testem []
  (let [f (str "/home/chouser/proj/clojure-compiler/src/"
               "net/n01se/clojure_compiler.clj")
        r (java.io.PushbackReader. (java.io.FileReader. f))]
    (doseq [form (repeatedly #(read r)) :while form]
      (print "====: ")
      (prn form)
      (prn (binding [*ns* (the-ns 'testem)] (analyze form))))))


