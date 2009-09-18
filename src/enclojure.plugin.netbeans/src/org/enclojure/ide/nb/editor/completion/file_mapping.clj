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

(ns org.enclojure.ide.nb.editor.completion.file-mapping
  (:use clojure.set)
  (:import (org.netbeans.spi.editor.completion CompletionResultSet
             CompletionItem CompletionProvider CompletionDocumentation
             CompletionTask) 
    (java.util Collection)
    (java.util.logging Level)
    (javax.swing JToolTip)
    (java.io StringWriter PrintWriter)
    (javax.swing.text Document)
    (java.awt.event ActionEvent KeyEvent KeyListener)
    (java.util.jar JarFile$JarFileEntry JarFile JarEntry)
    )
  (:require
    [org.enclojure.ide.navigator.token-nav :as token-nav]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    [org.enclojure.ide.analyze.symbol-meta :as symbol-meta]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.nb.actions.token-navigator :as token-navigator]
    ))

; setup logging
(logger/ensure-logger)

(defn- publish-stack-trace [throwable]
  (let [root-cause
            (loop [cause throwable]
                (if-let [cause (.getCause cause)]
                    (recur cause) cause))]
    (binding [*out* (StringWriter.)]
      (.printStackTrace root-cause (PrintWriter. *out*))
      (when (not= root-cause throwable)
         (.printStackTrace throwable (PrintWriter. *out*)))
      (logger/error (str *out*)))))

(defmacro #^{:private true}
    with-exception-handling [& body]
    `(try
      ~@body
       (catch Throwable t#
        (publish-stack-trace t#))))

(def -universal-libs-
  ["clojure.core"])

;(def -java-default-classes-
(def -completion-cache- (atom {}))
(def -parsed-file-cache- (atom {}))

(defmulti file-key class)

(defmethod file-key :default
  [file]
  ;(when-not file
  ;  (throw (Exception. "file-key passed a nil value.")))
  (logger/warn "default dispatch value for file-key arg={}" file)
  (when file
    (.getPath file)))

(defmethod file-key java.io.File
  [file] (.getPath file))

(defmethod file-key java.lang.String
  [file] file)

(defn from-cache [file]
  (when-let [c (@-completion-cache- (file-key file))]
    c))

(defn clear-caches []
  (swap! -completion-cache- {}))

(defn set-conj [s i]
  (conj (or s #{}) i))

(defn to-start-with-match [lst]
  (map #(re-pattern (str "^" %1)) lst))

(defn get-resources-by-lib 
  "Given a list of java classes, organize them by jar.
The given predicate is called with the fully qualified class name and is used as a filter."
  [java-class-list pred?]
  (reduce
    (fn [m cls]
      (if (pred? cls)
        (update-in m
          [(:lib (symbol-caching/from-symbol-cache cls))]
          set-conj cls) m))
    {}
    java-class-list))

(defn regex-for-lib-loading
"Given a list of classes, build a regex for matching against resources in a jar file."
  [package-set]
      (conj (to-start-with-match
                 (map #(.replace %1 "." "/")
                        package-set)) #"clj$"))

(defn ensure-classes
"Given a list of java classes, organize them by jar, and process them for completion.
The given predicate is called with the fully qualified class name.  Default checks to
see if there are symbols already loaded for the class"
  ([java-class-list pred?]
    (logger/debug "ensuring classes {}" (doall java-class-list))
    (let [package-map (get-resources-by-lib java-class-list pred?)]
      (when-let [nolib-items (package-map nil)]
            (logger/error "Completion cache problem. Following items had no library reference:")
            (logger/error (print-str nolib-items)))
      (when-let [libs (dissoc package-map nil)]
        (logger/debug "processing {} libs." (count (keys libs)))
            (dorun
                (pmap (fn [[jar-name package-set]]
                        (let [reg-ex-patterns
                              (regex-for-lib-loading package-set)]
                          (logger/debug "Processing jar {} with patterns {}"
                            jar-name reg-ex-patterns)
                          (try
                            (symbol-caching/process-jar
                                (java.io.File. jar-name)
                              (symbol-caching/get-regex-any-matcher-pred
                                reg-ex-patterns) nil)
                            (catch Throwable t
                              (publish-stack-trace t)))))
                  libs)))
        package-map))
  ([java-class-list] (ensure-classes java-class-list
                       #(not (symbol-caching/symbols-from-symbol-cache %1)))))

(defn all-java-instance-attr [classes]
  (apply concat
    (map (fn [cls]
           (apply concat
             (vals (symbol-meta/instance-items-public
                 (symbol-caching/from-symbol-cache (str cls))))))
            classes)))

(defn all-ns-publics [namespaces]
  (apply concat
    (map (fn [namesp]
           (apply concat
                (vals (symbol-meta/static-items-public
                    (symbol-caching/from-symbol-cache (str namesp))))))
                namespaces)))


(defn log-completion-info [ci]
  (logger/debug "\nCompletion-info :{}"
    (apply str (map #(when-let [f (%1 ci)]
                       (str "\n\t" %1 " count = "  (count (f))))
                 [:packages :classes :namespaces :hippy-words
                  :forms :java-instance :all-statics]))))

(defn adapt-for-completion-item [col]
    (reduce #(conj %1 {:name %2}) [] col))

(defn get-unqualified-name
  ([n]
  (let [inx (.lastIndexOf n ".")]
    (if (pos? inx)
      (subs n (inc inx)) n))))

(defn get-unqualified-names [col]
    (vec (map get-unqualified-name col)))

(defn get-ns-aliases 
  "given a set of namespace declarations, build a map from their alias to the full name"
  [namespaces]
  (reduce (fn [m nsd]
            (apply assoc m
               (if (vector? nsd)
                  [(str (fnext (drop-while #(not= :as %1) nsd)))
                   (str (first nsd))]
                      [nsd nsd])))
    {} namespaces))

(defn get-java-aliases
  "given a set of fully qualified java classes, build a map from the class name the full name"
  [java-classes]
  (reduce (fn [v cls]
            (assoc v (last (.split cls "[.]+"))
              cls))
    {} java-classes))


(defn get-default-completion-info
  "This function returns a default set of completion data when there is not file present"
  ([]
    (let [java-packages ["java.lang" "clojure.lang"]
            java-classes (vec (filter #(re-find #"^java\.lang\.[A-Z]+" %1)
                                    (keys (symbol-caching/get-symbol-cache))))
            ns-use-refer -universal-libs-
            ns-require  []
            hippy-words #{}]
      {
     :java-packages java-packages
     :java-classes java-classes
     :ns-use-refer ns-use-refer
     :ns-require ns-require
     :hippy-words hippy-words
     :unqualified-to-qualified-map
       (merge (get-java-aliases java-classes)
                            (get-ns-aliases
                              (concat ns-use-refer ns-require)))
     })))

;-------------------------------------------------------------------------------
; Main function for updating the completion info for a modified/just loaded file.
;-------------------------------------------------------------------------------
(defn refresh-completion-info
  "This function gets called on load and save of a file to cache the data to use
to generate responses to completion queries"
  ([filek]     
    (let [file (file-key filek)  
          forms
          (with-open [f (java.io.FileInputStream. file)]
            (symbol-caching/analyze-clj-update-cache
                      {:source file :name filek :ext "clj"} f file))
            java-packages (symbol-meta/get-java-packages-in-ns forms)
            java-classes (vec (concat
                                (filter #(re-find #"^java\.lang\.[A-Z]+" %1)
                                    (keys (symbol-caching/get-symbol-cache)))
                                    (symbol-meta/get-java-classes-in-ns forms)))
            ns-use-refer (vec (concat -universal-libs-
                                (symbol-meta/get-list-in-ns forms :use)
                                (symbol-meta/get-list-in-ns forms :refer)))
            ns-require  (symbol-meta/get-list-in-ns forms :require)
            hippy-words (with-open [f (java.io.FileReader. file)]
                            (parser/get-unique-words f))]
      {
     :file file
     :this-ns (symbol-caching/-file-cache- file)
     :java-packages java-packages
     :java-classes java-classes
     :ns-use-refer ns-use-refer
     :ns-require ns-require     
     :hippy-words hippy-words
     :unqualified-to-qualified-map
       (merge (get-java-aliases java-classes)
                            (get-ns-aliases
                              (concat ns-use-refer ns-require)))
     })))

(defn get-statics-for-ns-cls
  "Search for all the statics of a fully qualifer name"
  [qn]  
    (vec
      (apply concat
        (vals (symbol-meta/static-items-public
                (symbol-caching/from-symbol-cache (str qn)))))))

(defn get-qualified-name-from-alias [completion-info ns-alias-or-classname]
  (when-let [qn-map (:unqualified-to-qualified-map completion-info)]
    (qn-map ns-alias-or-classname)))

(defn find-ns-class-symbol-data 
  "Given a completion-info object and an alias, java class name or
fully qualified namespace or java class name, see if there are symbols loaded"
  [completion-info ns-alias-or-classname]
  (or (symbol-caching/from-symbol-cache ns-alias-or-classname)
    (symbol-caching/from-symbol-cache
        (get-qualified-name-from-alias completion-info ns-alias-or-classname))))

(defn get-all-java-instance-items
  "Gets the completion set of java instance fields and methods."
  [completion-info]
  (vec (filter #(not= (:name %1) "<init>")
         (all-java-instance-attr (:java-classes completion-info)))))

(defn get-java-class-instance-items
  "Gets the completion set for one java instance fields and methods."
  [completion-info java-class]
  (vec (filter #(not= (:name %1) "<init>")
         (apply concat
             (vals (symbol-meta/instance-items-public
                     (find-ns-class-symbol-data completion-info java-class)))))))

(defn get-static-funcs-fields 
  "Given a java class or namespace alias, return all the public / statics"
  [completion-info ns-alias-or-classname]
  (let [qn (or 
             ((:unqualified-to-qualified-map completion-info) ns-alias-or-classname)
             ns-alias-or-classname)]
    (get-statics-for-ns-cls qn)))

(defn get-funcall-items
  "Return all the symbols available for completion outside of the namespace search
, statics/qualified search and instance searching."
  [completion-info]
  (let [{:keys [unqualified-to-qualified-map hippy-words]} completion-info]
    (vec 
      (concat (all-ns-publics (:ns-use-refer completion-info))
        (adapt-for-completion-item
          (set
            (concat (keys unqualified-to-qualified-map)
              (vals unqualified-to-qualified-map) hippy-words)))))))

(defn get-namespaces-classes-items
  "Return all namespaces, and java classes available for searching"
  [completion-info]
  (vec (adapt-for-completion-item
         (keys (symbol-caching/get-symbol-cache)))))

(defn get-namespaces
  "Return all namespaces available for searching globally"
  ([completion-info]
  (vec (adapt-for-completion-item
         (map first
            (filter #(let [i (fnext %)]
                    (and (:symbols i) (not (:access i))))
         (symbol-caching/get-symbol-cache))))))
  ([] (get-namespaces nil)))

;(defn get-java-packages
;  "Return all java packages available for searching globally"
;  ([completion-info]
;  (let [candidates
;         (map first
;            (filter #(let [i (fnext %)]
;                    (not (and (:symbols i) (not (:access i)))))
;         (symbol-caching/get-symbol-cache)))]
;    (vec (adapt-for-completion-item
;           (filter
;             #(and %1
;                (not (.contains %1 java.io.File/separator)))
;                (reduce #(conj %1
;                    (when-let [i (.lastIndexOf %2 ".")]
;                      (subs %2 0 i))) #{} candidates))))))
;  ([] (get-java-packages nil)))

(defn get-java-packages
  "Return all java packages available for searching globally"
  ([completion-info]
    (vec (adapt-for-completion-item
           (keys (symbol-caching/get-java-class-symbol-cache-)))))
  ([] (get-java-packages nil)))

(defn get-java-classes-for-package
  "Return all java classes for the given package"
  ([package]
    (vec (adapt-for-completion-item
           ((symbol-caching/get-java-class-symbol-cache-) package)))))

;(defn do-refresh-completion-cache-data
;  "updates the completion info for the file"
;  [file]
;  (with-exception-handling
;    (let [completion-info
;          (if file (refresh-completion-info (file-key file)) 
;              (get-default-completion-info))]
;         (let [new-data (ensure-classes (:java-classes completion-info))]
;           (when file
;               (swap! -completion-cache- assoc (file-key file) completion-info))
;           n
; @-completion-cache- )
      
(defn refresh-completion-cache-data
  "updates the completion info for the file"
  [file]
  (with-exception-handling
    (let [completion-info
          (if file (refresh-completion-info (file-key file))
               (get-default-completion-info))]
          (let [new-data (ensure-classes (:java-classes completion-info))]
            (swap! -completion-cache- assoc (file-key file) completion-info))))
  @-completion-cache-)

(defn ensure-completion-info [file]
  (let [fkey (file-key file)]
    (if-let [data-from-cache
             (if (and fkey (pos? (count fkey)))
                (try
                    (from-cache fkey)
                (catch Throwable t
                  (publish-stack-trace t)))
			(get-default-completion-info))]
		data-from-cache)))
             
(defn refresh-current-completion-info []
  @(refresh-completion-cache-data
    (editor-utils/from-doc-to-file
        (token-navigator/current-editor-pane-doc))))

(defn get-completion-info
  ([f]
    (from-cache f))
  ([] (get-completion-info
        (editor-utils/from-doc-to-file
            (token-navigator/current-editor-pane-doc)))))

(defn check-completion
  ([f]
    (let [ci (refresh-completion-info f)]
      (filter #(not (symbol-caching/symbols-from-symbol-cache %1))
        (:java-classes ci))))
  ([] (check-completion
        (editor-utils/from-doc-to-file
            (token-navigator/current-editor-pane-doc)))))

(def f "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/completion/file_mapping.clj")
(def f2 "/Users/ericthor/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/completion/completion_task.clj")