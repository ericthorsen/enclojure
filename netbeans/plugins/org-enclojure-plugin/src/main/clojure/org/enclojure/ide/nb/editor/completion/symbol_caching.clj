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

(ns org.enclojure.ide.nb.editor.completion.symbol-caching
  (:use [clojure.main :exclude (with-bindings)])
  (:require [clojure.set :as set]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.analyze.symbol-meta :as symbol-meta]
    [org.enclojure.ide.nb.classpaths.resource-tracking :as resource-tracking]    
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.commons.meta-utils :as meta-utils]
    [org.enclojure.ide.analyze.files :as analyze.files]
    )
  (:import
    ; Java dependancies
    (java.util.logging  Level Logger)
    (java.io StringReader File StringWriter PrintWriter)
    (java.util.jar JarFile$JarFileEntry JarFile JarEntry)
    (java.net URL)
    (java.util Calendar)
    ; Enclojure dependancies for Asm (maybe I can piggy back off clojure or NB for this?
    (org.enclojure.ide.asm ClassReader ClassVisitor Type)
    (org.enclojure.ide.asm.tree ClassNode)
    ; Netbeans dependancies
    (org.netbeans.api.java.classpath ClassPath$Entry ClassPath GlobalPathRegistry)
    (org.openide.filesystems FileObject FileStateInvalidException
      FileUtil JarFileSystem URLMapper)
    ))

; setup logging
(logger/ensure-logger)
(.setLevel (Logger/getLogger "org.enclojure.ide.nb.editor.completion.symbol-caching")
  Level/WARNING)

(def -reader-queues- (ref {}))
(def -path-listener- (ref nil))

(defn- publish-stack-trace [throwable]
  (let [root-cause
            (loop [cause throwable]
                (if-let [cause (.getCause cause)]
                    (recur cause) cause))]
    (binding [*out* (StringWriter.)]
      (.printStackTrace #^Throwable root-cause (PrintWriter. *out*))
      (when (not= root-cause throwable)
         (.printStackTrace #^Throwable throwable (PrintWriter. *out*)))
      (logger/error (str *out*)))))

(defmacro #^{:private true}
    with-exception-handling [& body]
    `(try
      ~@body
       (catch Throwable t#
        (publish-stack-trace t#))))


;------------------------------------------------------------------------------
; validators
;------------------------------------------------------------------------------
(defn validate-symbol-cache
  "validator for the structure of the symbo cache"
  ([cache]
    ;(logger/info "validating " cache)
    (when (not= (count cache) (count (filter string? (keys cache))))
      (throw (Exception. "All keys in the symbol cache must be strings")))
    (let [r (reduce (fn [l [k {syms :symbols}]]
                      (when syms
                        (if (not= (count syms)
                              (count (filter string? (keys syms))))
                          (conj l k) l))) []
              cache)]
      (when (pos? (count r))
        (throw (Exception. (str "All symbol keys must be strings. Problems found in "
                             (interpose "," r)))))
      true)))

(defn validate-string-keys
  ([cache]
    (let [bad-keys (filter #(not (string? %)) (keys cache))]
    (if (pos? (count bad-keys))
              (throw (Exception. (apply str "All keys must be strings. Problems found in "
                             (interpose "," bad-keys))))
      true))))

;-----------------------------------------------------------------------------
; globals
;-----------------------------------------------------------------------------
(def #^{:doc "global map of all the parsed clj files.
            Key is the full path of the file."
        :private false}
        -symbol-data-cache- (ref {})); :validator validate-symbol-cache))

(defn get-symbol-cache [] @-symbol-data-cache-)

(def #^{:doc "global map of symbols keyed on the namespace."
        :private false}
        -ns-symbol-data-cache- (ref {}))

(defn get-ns-symbol-cache [] @-ns-symbol-data-cache-)

(def #^{:doc "global map of symbols keyed on the java-class."'
        :private false}
        -java-class-symbol-data-cache- (ref {}))

(defn get-java-class-symbol-cache- [] @-java-class-symbol-data-cache-)

(def #^{:doc "global map of all the parsed lib files.
            Key is the full path of the file.
            For clojure files the value is the namespace."
        :private false}
        -file-cache- (ref {} :validator validate-string-keys))


(defn clear-caches []
  (dosync
    (alter -symbol-data-cache- (fn [_] {}))
    (alter -java-class-symbol-data-cache- (fn [_] {}))
    (alter -file-cache- (fn [_] {}))))

(defn get-regex-any-matcher-pred [regex-lst]
  (fn [f] (some  #(re-find %1 f) regex-lst)))

(defn update-ns-symbol-cache
  [cache {:keys [symbols] :as symbol-map}]
  (if symbols
    (reduce (fn [m [k v]]
              (update-in m [(:namespace (first v))] assoc k v)) cache
                symbols)))

;(defmulti update-caches
;  (.fn [& args]
;    (class (first args))))
(defn- set-conj [s i]
  (conj (or s #{}) i))

(defn- classname-only
  [full-classname]
  (let [clsi (.lastIndexOf #^String full-classname  ".")]
    (if (pos? clsi) (subs full-classname (inc clsi)) full-classname)))

(def -sym-cache-val-keys-
  #{:super :package :symbols :access :orgname :lib :ext :source :source-file})

(defn- all-string-keys?
  [m]
  (= (count m) (count (filter string? (keys m)))))

(defn- expected-keys?
  [m]
  (every? -sym-cache-val-keys- (keys m)))

(defn update-caches 
  ([key new-forms]
    (let [{:keys [package]} new-forms]
      (when-not (expected-keys? new-forms)
        (throw (Exception. #^String
                 (apply str "New forms in single update had unexpected keys: "
                             (interpose "," (keys new-forms))))))
    (dosync
      (commute
            -symbol-data-cache-
        #(let [syms (or (%1 key) {})]
                (assoc %1 key (merge syms new-forms))))
      (when package
        (commute -java-class-symbol-data-cache-
          update-in [package]
            set-conj (classname-only key))))))
  ([new-forms]
    (when-not
        (all-string-keys? new-forms)
      (let [bad-keys (filter #(not (string? %)) (keys new-forms))]   
            (throw (Exception. #^String
                     (apply str "All keys in the symbol cache must be strings. Found "
                       (count bad-keys) " that were not strings:"
                       (interpose "," bad-keys))))))
    (dosync
      (commute -symbol-data-cache-
        (fn [cinx]
          (reduce (fn [m [k v]]
                    (let [syms (or (m k) {})]
                      (when-not (expected-keys? v)
                        (throw (Exception. #^String 
                                 (apply str "New forms in batch update for " k " had unexpected keys: "
                                    (interpose "," (filter #(not (-sym-cache-val-keys- %)) (keys v)))))))
                      (assoc m k (merge syms v))))
                    cinx new-forms)))
      (commute -java-class-symbol-data-cache-
        (fn [cinx]
            (reduce
                (fn [m [k {package :package}]]
                    (if package
                        (update-in m [package]
                          set-conj (classname-only k)) m))
                cinx new-forms))))))

;;                    
;          ;(merge-with merge m n))
;-----------------------------------------------------------------------------
; helper funcs
;-----------------------------------------------------------------------------
(defn is-clojure-compiled-class
  [class-name] (re-find #"\$.*__" class-name))

(defn strip-extensions [#^String file-name exts]
  (let [last-dot (.lastIndexOf #^String file-name ".")]
    (if (and (pos? last-dot)
          (exts (subs file-name (inc last-dot))))
      (subs file-name 0 last-dot)
      file-name)))

(defn key-from-file [filename]
  (.replace #^String (strip-extensions filename #{"clj" "class"}) "/" "."))

(defn was-file-processed? [lib]
  (@-file-cache- lib))

(defn cache-lookup-ns-from-file 
  "Takes a relative or full file path and looks in the cache to see if the file has been processed"
  [full-file-path]
  (let [relative (resource-tracking/get-relative-file full-file-path)
        fullpath (resource-tracking/get-full-file full-file-path)]
  (or (@-file-cache- fullpath) (@-file-cache- relative))))

(defn from-symbol-cache [ns-or-class]
  (@-symbol-data-cache- ns-or-class))

(defn symbols-from-symbol-cache [ns-or-class]
  (:symbols (from-symbol-cache ns-or-class)))

(defn all-java-instance-attr [full-file-path]
  (from-symbol-cache (cache-lookup-ns-from-file full-file-path)))

(defn cache-counts []
  (println
    "keys in symbol cache "
    (count @-symbol-data-cache-)
    "keys parsed data "
    (count (filter #(:symbols %1) (vals @-symbol-data-cache-)))
    " files " (count @-file-cache-)))

(defn file-obj-traverse
  "Takes a FileObject and recurses through returning all the files"
  ([#^FileObject root predicate]
    (let [accumfn (fn accumfn [roots files]
                (loop [c roots files files]
                    (if-let [file #^FileObject (first c)]                      
                        (recur (rest c)
                            (if (.isFolder #^FileObject file)
                              (accumfn (.getChildren #^FileObject file) files)
                              (if (predicate file)
                                (conj files file) files)))
                      files)))]
    (accumfn (.getChildren #^FileObject root) [])))
  ([#^FileObject root]
    (file-obj-traverse root identity)))

;------------------------------------------------------------
; Analysis of a given unit
;------------------------------------------------------------

(defmulti analyze
  (fn [& args] [(class (:source (first args)))
                  (:ext (first args))]))

(defmethod analyze :default [& args]
    (logger/warn "analyze default!!!!!!! {}" (apply str (interpose " " args))))

(defn analyze-clj-update-cache
  [{:keys [source name] :as args} istream file-key]
  (try ; At this level, I need to look inside the file for the 
    (let [k (meta-utils/ns-from-file file-key)]
      (logger/debug "analyze :clj  {}" source)
      (let [symbols (analyze.files/analyze-file istream "clj" {:source-file file-key})
            ns (ffirst (symbol-meta/get-namespace-node symbols))
            existing-forms (from-symbol-cache (str ns))
            new-symbols (merge existing-forms symbols)]
 ;       (logger/debug "analyze :clj  namespace is " ns)
        ; I have the file name, the namespace within the file and all the forms.
        ; Need to update the file cache as well as the symbol cache since
        ; the clojure soure may be in several different files.
        ; Need to dissoc any symbols in the existing forms based on this file        
       (dosync
         (commute
           -file-cache- 
                assoc file-key (str ns))
         (update-caches (str ns) new-symbols))
        (from-symbol-cache (str ns))))
    (catch Throwable t
            (publish-stack-trace t))))

(defn is-clj-data-in-cache? [filename ns]
  (and 
    (was-file-processed? filename)
    (from-symbol-cache ns)))

(defmethod analyze [java.util.jar.JarEntry "clj"]
  [{:keys [jar source name lib] :as args}]
;  (logger/warn "analyze [java.util.jar.JarEntry clj]")
  (try
    (let [k (meta-utils/ns-from-file (.getName #^JarEntry source))]
      (logger/debug "analyze :clj  looking up {} {}" k
            (if (symbols-from-symbol-cache k) "yes" "no"))
    (if (symbols-from-symbol-cache k)
      (from-symbol-cache k)
     (with-open [istream (.getInputStream #^JarFile jar #^JarEntry source)]
           (analyze-clj-update-cache args istream (.getName #^JarEntry source)))))
    (catch Throwable t
            (publish-stack-trace t))))

(defmethod analyze [java.io.File "clj"]
  [{:keys [source name force-update?] :as args}]
  (try
    (let [k (meta-utils/ns-from-file (.getName #^File source))]
      (logger/debug "analyze :clj  looking up {} {}" k
        (if (symbols-from-symbol-cache k) "yes" "no"))
    (if (or (and (not force-update?)
              (symbols-from-symbol-cache k)))
        (from-symbol-cache k)
        (with-open [istream (java.io.FileInputStream. #^File source)]
           (analyze-clj-update-cache args istream 
             (.getPath #^File source)))))
    (catch Throwable t
            (publish-stack-trace t))))

(defmethod analyze [org.openide.filesystems.FileObject "clj"]
  [args]
  (FileUtil/toFile (:source args)))

(defmethod analyze [java.util.jar.JarEntry "class"]
  [{:keys [jar source ext lib] :as args}]
  (try
    (let [k (meta-utils/classname-from-file (.getName #^JarEntry source))]
      ; If it is a clojure compiled class we will skip it
      (when-not (is-clojure-compiled-class k)
            (or (symbols-from-symbol-cache k)
            (logger/debug "analyze :class {}" source)
              (let [forms (with-open [istream (.getInputStream #^JarFile jar  #^JarEntry source)]
                                (analyze.files/analyze-file istream ext jar))]
                (when-not (expected-keys? forms)
                                    (throw (Exception. #^String
                                             (apply str "New forms during analyze class for " k " had unexpected keys: "
                                                (interpose "," (filter #(not (-sym-cache-val-keys- %)) (keys forms)))))))
                (dosync
                  (update-caches k
                    (assoc forms
                      :ext ext :lib lib :package (meta-utils/package-name-from-class k))))
                forms
                  ))))
    (catch Throwable t
        (publish-stack-trace t))))
;                    (commute
;                        -symbol-data-cache-
;                            assoc k
;                            (merge {:ext ext :lib lib :package (package-name-from-class k)}
;                              forms))


(defn reparse-file
  "Given a java.io.File with a full path, attempt to reparse and update the code data"
  [file]
  (logger/debug "reparse {}" file)
  (analyze {:source file :name (.getPath file)
            :force-update? true :ext "clj"}))

(defn test-analyze [fname]
  (analyze {:source (java.io.File. fname) :ext "clj" :force-update? true :name fname}))

(defn test-file []
  (analyze {:ext "clj"
          :source (java.io.File. "/Users/ericthorsen/dev/third-party/Clojure/src/clj/clojure/core.clj")
          :force-update? true
          :name "/Users/ericthorsen/dev/third-party/Clojure/src/clj/clojure/core.clj"}))

(def ff "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/completion/symbol_caching.clj")

(defn test-more []
  (analyze {:ext "clj"
          :source (java.io.File. ff)
          :force-update? true
          :name ff}))

;------------------------------------------------------------
; keys for the entries in the caches
;------------------------------------------------------------
(defn check-empty [#^String key-val & args]
  (when
    (or (nil? key-val)
        (and (string? key-val)
          (not (pos? (count (.trim key-val))))))
    (logger/error "null or empty key for {}" args))
  (str key-val))

(defmulti path-key
  (fn [& args] (class (first args))))

(defmethod path-key :default [path-data]
  (check-empty (.getPath path-data) path-data))

(defmethod path-key org.netbeans.api.java.classpath.ClassPath$Entry
  [#^ClassPath$Entry path-data]
  (check-empty (.getURL #^ClassPath$Entry path-data) path-data))

(defmethod path-key clojure.lang.IPersistentMap
  [path-data]
  (check-empty (:key path-data) path-data))

(defmethod path-key java.util.jar.JarEntry
  [#^JarEntry jar-entry data]
  (check-empty (.getName #^JarEntry jar-entry) jar-entry data))

;------------------------------------------------------------
; Processing entries for a jar
;------------------------------------------------------------

(defn get-ext [#^JarEntry entry]
  (let [ext (re-find #"\.[a-z]+$" (.getPath  entry))]
    (when ext (subs ext 1))))

(defn process-jar
  ([#^File jar-file parse-now-pred? ignore-pred? source-root n]
    (logger/debug "process jar {}" jar-file)
    (let [lib (.getPath jar-file)]
    (try
      (with-open [jr #^JarFile (JarFile. jar-file)]
        (logger/debug "Processing entries in jar {}" jar-file)
        (loop [entries  (take n (enumeration-seq (.entries jr))) classes {}]
          (if-let [entry #^JarEntry (first entries)]
            (let [ename (.getName entry)
                  ekey (key-from-file ename) ;(ns-from-file ename)
                  isdir? (.isDirectory entry)
                  path (when (and isdir? (.exists (File. ename)))
                                           (FileUtil/toFileObject
                                                (FileUtil/normalizeFile (File. ename))))
                  sufx (.lastIndexOf ename (int \.))
                  ext (when (not= -1 sufx) (subs ename (inc sufx)))]
              (if (parse-now-pred? ename)
                    (logger/debug "Parse now -> {} match {} :name {} :isdir? {} path {} sufx {} :ext {}"
                      ekey (parse-now-pred? ename) ename isdir? path sufx ext)
                (logger/debug "Store key only -> {} match {} :name {} :isdir? {} path {} sufx {} :ext {}"
                    ekey (parse-now-pred? ename) ename isdir? path sufx ext))
                (recur (rest entries)
                      (cond
                        path
;                        (merge classes
;                            (hash-map
;                                (reduce #(conj %1 %2
;                                           {:root source-root :jar jr :source entry
;                                            :ext ext :name (.getName entry) :lib lib})
;                                    (filter (#{"clj" "class"} (.getExt entry))
;                                            (file-obj-traverse path)))))
                        (merge classes
                          (reduce
                            #(let [en (.getName %2)
                                   ek (key-from-file en)]
                               (assoc %1 ek
                                   {:root source-root :jar jr :source entry
                                    :ext ext :name en :lib lib})) {}
                                    (filter (#{"clj" "class"} (get-ext entry))
                                      (file-obj-traverse path))))

                        (or isdir? (#{"clj" "class"} ext))
                            (let [more-args (when (= ext "class")
                                                [:package
                                                  (meta-utils/package-name-from-class ekey)])
                                  new-data (apply assoc
                                             (if (and parse-now-pred? (parse-now-pred? ename))
                                                (analyze {:root source-root :jar jr :source entry
                                                          :ext ext :lib lib :name (.getName entry)}) {})
                                             :ext ext :lib lib :orgname ename more-args)]
                                        (if (not (expected-keys? new-data))
                                            (logger/error
                                                 (apply str "New forms during process-jar for " ekey " had unexpected keys: "
                                                    (interpose "," (filter #(not (-sym-cache-val-keys- %)) (keys new-data)))))
                                         (assoc classes ekey new-data)))
                       :else classes))) classes)))
      ;(logger/debug "Completed processing entries in jar " jar-file)
       (catch Throwable t
        (publish-stack-trace t)))))
    ([jar-file parse-pred? ignore-pred?] (process-jar jar-file parse-pred? ignore-pred? nil Integer/MAX_VALUE))
    ([jar-file] (process-jar jar-file nil nil Integer/MAX_VALUE)))

(def testy "/Applications/NetBeans/NetBeans 6.5.app/Contents/Resources/NetBeans/ide10/modules/org-netbeans-modules-editor-completion.jar")
(def cljs "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/release/modules/ext/org.enclojure.repl.jar")
(def jclasses "/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Classes/classes.jar")

(defn ttt [#^String f]
  (process-jar
     (java.io.File. f)
     (get-regex-any-matcher-pred [#".clj$" #".class$" #"org.netbeans"]) nil))

  
(defn process-jar-update-cache
  ;"traverses a set of jars and calls analyze on them"
  [jar-file parse-pred? ignore-pred?]      
      (with-exception-handling
        ; Keep the results but make sure all the keys are in the file map so they get cached.        
        (let [new-data
              (process-jar jar-file parse-pred? ignore-pred?)
              filtered (select-keys new-data
                         (filter #(not (is-clojure-compiled-class %))
                           (keys new-data)))]
          (when (validate-symbol-cache new-data)
            (dosync
                (update-caches filtered))))))
;          (commute
;            -symbol-data-cache-
;                (fn [curr-state] ; Want the existing data to take precedence (why?)
;                  (merge filtered curr-state)))
          ;))))

;------------------------------------------------------------
; Processing for a set of paths
;------------------------------------------------------------
(defmulti process-path
  (fn ([& args ] (class (first args)))))

(defmethod process-path :default [path-data & args]
  (logger/debug "process-path default!!! {}" (class path-data))
  )

(defmethod process-path org.netbeans.api.java.classpath.ClassPath$Entry
  ;"traverses a set of jars and calls analyze on them"
  [classpath-entry]
  ;(logger/debug "process-path ClassPath$Entry" classpath-entry)
  (with-exception-handling
    (if classpath-entry
        (let [url (.getURL #^ClassPath$Entry classpath-entry)
              root (.getRoot #^ClassPath$Entry classpath-entry)]
          (if url
            (condp = (.getProtocol #^URL url)
              "jar" (process-path
                      (if root
                        (.getFileSystem root)
                      (FileUtil/toFileObject
                        (FileUtil/normalizeFile (File. (.getPath url)))))
                      classpath-entry)
              "file" (process-path (.getRoot #^ClassPath$Entry classpath-entry))))))))


(defmethod process-path org.openide.filesystems.JarFileSystem
  ;"traverses a set of jars and calls analyze on them"
  [#^JarFileSystem jar-file-system classpath-entry]
    (logger/debug "process-path JarFileSystem {}" (.getDisplayName #^JarFileSystem jar-file-system))
  (when-not (was-file-processed? (.getDisplayName #^JarFileSystem jar-file-system))
      (with-exception-handling
        ; Keep the results but make sure all the keys are in the file map so they get cached.
        (logger/debug "process-path JarFileSystem (not cached) {}" (.getDisplayName #^JarFileSystem jar-file-system))
        (let [new-data
              (process-jar (.getJarFile jar-file-system)     
                (get-regex-any-matcher-pred
                          [#"\.clj$" #"^java/lang/" #"^java/io" #"^java/text"])
                (get-regex-any-matcher-pred
                            [#"^clojure/core" #"^clojure/main" #"^clojure/set" #"^clojure/inspector"])
                    (.getRoot #^ClassPath$Entry classpath-entry) Integer/MAX_VALUE)
              filtered (select-keys new-data
                         (filter #(not (is-clojure-compiled-class %))
                           (keys new-data)))]
    (logger/info "!!!!!!!!!!!! process-path JarFileSystem " (.getDisplayName jar-file-system)
      " adding " (count (keys new-data)))
        (dosync
          (update-caches filtered)
          (commute
            -file-cache-
                assoc (.getDisplayName jar-file-system)
                    {:datetime (.getTime (Calendar/getInstance))}))))))


(defn analyze-class2
  [istream]
  ;(logger/debug "analyze-file class")
    (with-open [i istream]
    (let [cnode (org.enclojure.ide.navigator.CljClassVisitor.)]
        (.accept (ClassReader. #^java.io.InputStream istream) cnode analyze.files/-flags-) cnode)))
        ;(logger/debug "analyze-file class success!!!!!!!!!!")


(defn get-paths [type]
  (.getPaths (GlobalPathRegistry/getDefault) type))


(defn get-jars [type]
  (doall
    (filter #(="jar" (-> %1 .getURL .getProtocol))
        (for [path (.getPaths (GlobalPathRegistry/getDefault) type)
              entry (.entries path)] entry))))

(defn get-nav-data-for 
  "Get the symbol data for a given file."
  [file]
  (logger/info "Navigator loading {}" file)
  (:symbols
        (from-symbol-cache
                (cache-lookup-ns-from-file (.getPath file)))))

(def test-j (first (get-jars "classpath/compile")))

(defn testit []
    (with-open [jr (JarFile. "/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Classes/classes.jar")]
      (doall
        (reduce #(assoc %1 %2 {:name (.getName %2) :isdir? (.isDirectory %2)
                               :size (.getSize %2)}) {} (enumeration-seq (.entries jr))))))

(def libs (ref #{}))
(def class-names (ref #{}))


(defn classes [how-many]
    (with-open [jr (JarFile. "/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Classes/classes.jar")]
      (doall
        (map
          #(try
             (when (.endsWith (.getName %1) ".class")
                (with-open [s (.getInputStream jr %1)]
                    (analyze-class2 s)))
           (catch Throwable t
               (logger/error "error reading {}" (.getName %1))
             (publish-stack-trace t)))
            (take how-many
                (enumeration-seq (.entries jr)))))))

(defn testing [n]
  (process-jar (File. "/System/Library/Frameworks/JavaVM.framework/Versions/1.5.0/Classes/classes.jar")
    (get-regex-any-matcher-pred [#".clj$" #"^java.lang."]) nil))

(defn test-proc-path []
  (process-path (first (get-jars "classpath/boot"))))

(defn reload-compile-path-all []
  (doseq [p (get-jars "classpath/compile")]
    (process-path p)))

(defn reload-boot-path-all []
  (doseq [p (get-jars "classpath/boot")]
    (process-path p)))

(defn reset-all []
  (clear-caches)
  (logger/debug "loading boot path {}")
  (reload-boot-path-all)
  (logger/debug "loading compile path {}")
  (reload-compile-path-all))
  
(defn seeit [tt]
  (map #(println (:defs (fnext %1))) (filter #(.startsWith (first %1) "java.lang.") tt)))


