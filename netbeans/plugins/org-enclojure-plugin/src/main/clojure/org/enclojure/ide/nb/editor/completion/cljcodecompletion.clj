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
;*    Author: Paul Wade
;*******************************************************************************
)
(ns org.enclojure.ide.nb.editor.completion.cljcodecompletion (:import (java.awt.event ActionEvent)
(javax.swing JEditorPane)
(org.netbeans.api.lexer TokenHierarchy TokenSequence Token)
(org.openide.windows TopComponent)
(org.openide.cookies EditorCookie)
(javax.swing.text Document)
(javax.swing.text JTextComponent)
(java.util.jar JarInputStream)
(java.util ArrayList))
(:require [org.enclojure.ide.common.classpath-utils :as classpath-utils] [org.enclojure.ide.navigator.token-nav :as token-nav]))


(defn removecharacters [fullclsstring]
  (let [x (.substring fullclsstring (+ (.indexOf fullclsstring "(") 1))]
    (.substring x 0 (- (.length x) 1) )))

(defn get-classname [clsstr]
  (let [dotpos (.lastIndexOf clsstr ".")]
    (if (> dotpos -1)
      (.substring clsstr (+ dotpos 1)))))

(defn get-last-part-of-namespace [str]
  (let [pos (.lastIndexOf  str ".")] (.substring str (+ pos 1))))

(defn get-java-class [clsstring fullclsstring]
  (if (.contains fullclsstring "java") (str clsstring) nil ))


(defn get-static-java-class [clsstring]
  (if (.contains clsstring "static") (str clsstring) nil))

(defn add-static-keyword [clsstring]
  (if (.contains clsstring "static") (str clsstring "-->static") (str clsstring) ))

(defn get-full-java-class [clsstring fullclsstring]
  (if (.contains fullclsstring "java") (removecharacters(str fullclsstring)) nil ))


(defn get-names
  "get the names from a namespace"
  [ns]
  (reduce (fn [v [sym cls]]
            (  if-let [cname  (get-java-class (name sym) (str cls))]
              (conj v cname)
              v))
    [] (ns-imports ns)))

(defn get-names1
  "get the names from a namespace"
  [ns]
  (remove nil? (map (fn [[sym cls]] (get-java-class (name sym) (str cls)))
    (ns-imports ns))))

(defn get-full-names[ns]
 (vec(filter identity(reduce #(conj %1 (print-str (second %2))) [] (ns-imports (find-ns ns))))))

(defn get-clojure-names[ns]
  (if (not (nil? (find-ns ns)))
 (reduce #(conj %1 (name (first %2))) [] (ns-publics (find-ns ns)))))


(defn get-java-classes[ns]
  (distinct
    (map (fn [[sym cls ]]
           (print-str cls)
           )
      (ns-imports (find-ns ns)))))

(defn handle-jar-string[strJar]
  (if (and (not (= -1 (.indexOf strJar ".class"))) (= -1 (.indexOf strJar "$")))
    (.replaceAll (.substring strJar 0 (.lastIndexOf strJar ".")) "/" ".")))


(defn get-classes-for-jar[strJar]
  (with-open [jis (new java.util.jar.JarInputStream (new java.io.FileInputStream strJar))]
    (loop [ret []
           je (.getNextJarEntry jis)]
      (if je
        (let [jestr (handle-jar-string (.toString je))]
          (do (.closeEntry jis)
            (recur (if jestr (conj ret jestr) ret)
                   (.getNextJarEntry jis))))
        ret))))

(defn get-jar-entries [str-jar]
  (with-open [jis (new java.util.jar.JarInputStream (new java.io.FileInputStream str-jar))]
    (loop [ret []
           je (.getNextJarEntry jis)]
      (if je
        (recur (conj ret je) (.getNextJarEntry jis))
        ret))))


(defn get-java-classes-maplist[ns order]
  (distinct
    (map (fn [[sym cls ]]
           {:classname (name sym) :fullclassname (print-str cls)
            :isclojure false :isnamespaceorpkg true :ismethodorfunction
            false :display (print-str cls) :priority order :isconstructor false})
      (ns-imports (find-ns ns)))))

(defn get-java-classes-project-maplist[strJar order]
  (map (fn [eachcls]
           {:classname (get-classname eachcls) :fullclassname eachcls
            :isclojure false :isnamespaceorpkg true :ismethodorfunction
            false :display eachcls :priority order :isconstructor false})
    (try
    (get-classes-for-jar strJar)
      (catch Throwable t nil))
      ))


(defn get-all-java-classes []
  (distinct (reduce #(concat %1 (get-java-classes (symbol (str%2)))) [] (all-ns))))

(defn get-java-classes-from-project [Project order]
  (reduce #(concat %1 (get-java-classes-project-maplist %2  order))
    []
    (when Project
    (filter #(.endsWith % ".jar")
    (.split (classpath-utils/get-project-classpath Project) java.io.File/pathSeparator)))))


(defn get-all-java-classes-maplist [Project order]
  (distinct (concat
              (reduce #(concat %1 (get-java-classes-maplist (symbol (str%2)) order)) [] (all-ns))
              (get-java-classes-from-project Project order))))

(defn get-clojure-namespace [var1]
  (let [x (.indexOf var1 "#'")
        y (.indexOf var1 "/")]
        (.substring var1 (+ x 2) y )))


(defn get-all-clojure-namespaces-maplist[order]
  (map (fn [ns]
         {:classname (get-last-part-of-namespace (str ns)) :fullclassname (str ns)
          :isclojure true :isnamespaceorpkg true
          :ismethodorfunction false :display (str ns) :priority order :isconstructor false}) (all-ns)))

(defn get-clojure-symbols-maplist[ns order]
  (map (fn [sym]
         {:classname sym :fullclassname (str (str ns) "/" sym)
          :isclojure true :isnamespaceorpkg false :ismethodorfunction true
          :display sym :priority order :isconstructor false})(get-clojure-names ns)))


(defn get-class-string [lst]
  (when (list? lst)
  (map #(str (first lst) "." %1) (next lst))))

(defn alias-contained [ns symalias]
  (some #(when (= (key %) symalias) (symbol (str (val %)))) (ns-aliases ns)))

(defn get-ns-for-alias [symalias]
  (if-let [x (some #(alias-contained % symalias) (all-ns))] x symalias))

(defn get-clojure-symbols [ns]
  (reduce #(merge %1 {(name (key %2))(meta (val %2))}) {} (ns-publics ns)))

(defn get-clojure-symbols-all []
  (reduce #(merge %1 {(ns-name %2) (get-clojure-symbols %2)}) {} (all-ns)))


(defn get-clojure-names-with-users [ns]
  (reduce #(conj %1 (name (first %2))) [] (merge (ns-publics (find-ns ns))(ns-publics (find-ns 'user)))))

(defn get-clojure-names-with-users-ns [ns]
  (if (not (nil? (find-ns ns)))
  (reduce #(conj %1 {:symbolname (name (first %2)) :namespace (get-clojure-namespace (str (second %2)))})
        [] (merge (ns-publics (find-ns ns))(ns-publics (find-ns 'user))))))


(defn get-clojure-symbols-with-users-maplist [ns order]
  (map (fn [symmap] {:classname (:symbolname symmap)
                :fullclassname (str (:namespace symmap) "/" (:symbolname symmap))
          :isclojure true :isnamespaceorpkg false :ismethodorfunction true
          :display (:symbolname symmap) :priority order :isconstructor false})
         (get-clojure-names-with-users-ns ns)))

(defn get-clojure-symbols-with-users-maplist-nsstr [nsstr order]
  (map (fn [symmap] {:classname (:symbolname symmap)
                :fullclassname (str (:namespace symmap) "/" (:symbolname symmap))
          :isclojure true :isnamespaceorpkg false :ismethodorfunction true
          :display (:symbolname symmap) :priority order :isconstructor false})
         (get-clojure-names-with-users-ns (get-ns-for-alias (symbol nsstr)))))

(defn get-all-clojure-names []
  (distinct (remove nil? (reduce #(concat %1 (get-clojure-names (symbol (str %2)))) [] (all-ns)))))

(defn get-full-namespace [nspart]
  (filter identity (reduce #(conj %1 (if (.endsWith (str %2) nspart) (str %2) nil)) [] (all-ns))))

(defn get-clojure-functions-for-nspart [nspart]
  (reduce #(concat %1 (get-clojure-names (symbol %2))) [] (get-full-namespace nspart)))

(defn find-symbol
    "This function searches the symbol with the givem name in all namespaces and
     returns the first matching symbol object"
   [name]
    (loop [nss (all-ns)]
       (when-first [ns nss]
          (let [sym (first (filter #(= (str (key %)) name) (ns-publics ns)))]
             (if sym (val sym) (recur (next nss)))))))

(defn get-clojure-docs [f]
  (.replace (let [x (meta (find-symbol f))] (str (list (get x :arglists) (get x :doc)))) "\\n" "")
  )

(defn get-clojure-doc [f]
 (.replace (let [x (meta (find-symbol f))] (str (get x :doc))) "\n" "")
  )

(defn get-clojure-args [f]
 (.replace (let [x (meta (find-symbol f))] (str (get x :arglists))) "\n" "")
  )

(defn strip-class-prefix [x]
(let [pos  (.indexOf x "java.lang.Class.")
      len (.length "java.lang.Class.")  
      ]
(if (> pos -1)
(.  x (substring (+ pos len))))))

(defn strip-class-prefix1 [x cls]
(let [pos  (.indexOf x cls)
      len (.length cls)
      ]
(if (> pos -1)
(.  x (substring (+ pos len))))))

(defn strip-class-prefix2 [x cls]
(let [pos  (.indexOf x cls)
      len (.length cls)
      ]
(if (> pos -1)
(.  x (substring (+ pos len))))))


(defn getMethods [cls]
  (vec (filter identity (reduce #(conj %1  (strip-class-prefix (str %2))) [] (seq (.getMethods (class cls)))))))

(defn getMethods1 [cls]
  (vec (distinct (filter identity (reduce #(conj %1  (strip-class-prefix1 (str %2) (str (print-str cls) "."))) [] (seq (.getMethods cls)))))))

(defn getMethodsWithStatic [cls]
  (vec (distinct (filter identity (reduce #(conj %1  (strip-class-prefix1 (add-static-keyword (str %2)) (str (print-str cls) "."))) [] (seq (.getMethods cls)))))))

(defn get-methods-no-static [cls]
  (vec (distinct 
         (filter identity 
           (reduce #(conj %1 
                      (strip-class-prefix1  (str %2) (str (print-str cls) ".")))
             [] (filter #(not (.contains (str %) "static")) (seq (.getMethods cls))))))))

(defn isConstructor [cls]
  (if (= (class cls) java.lang.reflect.Constructor) true false))

(defn show-constructor [method cls]
  (if (= (class method) java.lang.reflect.Constructor)
  (let [methodstring (str method)
        clsstr (print-str cls)
        len (.length clsstr)
        pos (.indexOf methodstring clsstr)
        poscls (.lastIndexOf clsstr ".")
        clsname (.substring clsstr (+ poscls 1))
        lenclsname(.length clsname)]
    (if (and (> pos -1) (> poscls -1))
        (.substring methodstring (- (+ pos len) lenclsname))))))

(defn make-method-map [method cls order]
  (let [isConst (isConstructor method)
        methodstring (str method)
        mname (if isConst (show-constructor method cls) (strip-class-prefix1 methodstring (str (print-str cls) ".")))]
  {:classname mname :fullclassname methodstring :isclojure false :isnamespaceorpkg false 
   :ismethodorfunction true :display mname :priority order :isconstructor isConst}
    ))


(defn get-methods-only-no-static-maplist [cls order]
  (when cls
    (vec (filter (fn [eachmap] (not (nil? (:classname eachmap))))
         (distinct
         (filter identity
           (reduce #(conj %1
                      (make-method-map %2 cls order))
             [](filter #(not (.contains (str %) " static ")) (seq (.getMethods cls))))))))))

(defn get-methods-no-static-maplist [cls order]
  (vec (filter (fn [eachmap] (not (nil? (:classname eachmap))))
         (distinct
         (filter identity
           (reduce #(conj %1
                      (make-method-map %2 cls order))
             [] (concat (seq(.getConstructors cls)) 
                  (filter #(not (.contains (str %) " static ")) (seq (.getFields cls)))
                    (filter #(not (.contains (str %) " static ")) (seq (.getMethods cls))))))))))

(defn get-methods-no-static-maplist-by-filter [cls order fil]
(filter (fn [eachmap] (.startsWith (:display eachmap) fil))
  (get-methods-no-static-maplist cls order)))

(defn get-methods-static-maplist [cls order]
  (vec (filter (fn [eachmap] (not (nil? (:classname eachmap))))
         (distinct
         (filter identity
           (reduce #(conj %1
                      (make-method-map  %2 cls order))
             []  (concat 
                   (filter #(.contains (str %) " static ") (seq (.getMethods cls)))
                   (filter #(.contains (str %) " static ") (seq (.getFields cls)))
                   )))))))


(defn get-all-methods-no-static-maplist [Project order]
  (remove nil? (reduce #(into %1 (get-methods-only-no-static-maplist
                        (try
                          (Class/forName (:fullclassname %2))
                            (catch Throwable t nil))
                          order))
                                   [] (get-all-java-classes-maplist Project order))))

(defn getAllJavaMethods [ns]
  (vec (reduce #(distinct (concat %1  (getMethods1  (Class/forName (str (print-str %2)))))) [] (seq (get-full-names ns)))))


(defn getStaticJavaMethods [cls]
(filter identity (reduce #(conj %1  (strip-class-prefix1 (str (get-static-java-class (str %2))) (str (print-str cls) "."))) [] (seq (.getMethods cls)))))

(defn getAllJavaClasses []
  (filter identity (distinct (reduce #(concat %1  (get-full-names (symbol (str %2)))) [] (all-ns)))))


(defn getAllJavaClassesByFilter [fil]
   (filter #(.contains %1 fil) (distinct (reduce (fn [em ns] (concat em (get-full-names (symbol (str ns))))) [] (all-ns)))))


(defn getAllClojureNamespaces []
  (vec (reduce #(conj %1 (str %2)) [] (all-ns))))

(defn getAllClojureNamespacesByFilter [fil]
  (filter #(.startsWith %1 fil) (vec (reduce #(conj %1 (str %2)) [] (all-ns)))))



(defn getNode [#^JTextComponent pane position]
  (if (nil? position)
    (str "()")
  (let [currentNodeStr (.getText (.getDocument pane) (:start position)
                         (- (:end position) (:start position)))]
        currentNodeStr)))


(defn get-import-require [rlist]
  (map #(if (vector? %1) (str (first %1)) (str %1)) rlist)
  )

(defn get-import-maplist [ilist order]
  (map (fn [clsstr] {:classname (get-classname clsstr) :fullclassname clsstr
                     :isclojure false :isnamespaceorpkg true :ismethodorfunction false
                     :display clsstr :priority order :isconstructor false})
    (reduce #(concat %1 (get-class-string %2)) [] ilist)))

(defn get-import-require-maplist [rlist order]
  (map (fn [clsstr] {:classname (get-classname clsstr) :fullclassname clsstr
                     :isclojure true :isnamespaceorpkg true :ismethodorfunction false
                     :display clsstr :priority order :isconstructor false})
                     (get-import-require rlist)))

(defn get-node-maplist [nsstr keywordstr order]
    (let [nslist (read-string nsstr)
          ilist  (next (first (filter #(and (list? %)(keyword? (first %))(= (name (first %)) keywordstr)) nslist)))]
          (if (= keywordstr "import")
            (get-import-maplist ilist order)
            (get-import-require-maplist ilist order)
            )))

(defn get-node [nsstr keywordstr]
    (let [nslist (read-string nsstr)
          ilist  (next (first (filter #(and (list? %)(keyword? (first %))(= (name (first %)) keywordstr)) nslist)))]
          (if (= keywordstr "import")
          (reduce #(concat %1 (get-class-string %2)) [] ilist)
            (get-import-require ilist)
            )))


(defn get-class-from-list-cls [lstr]
  (let [llist (read-string lstr)
       cls (when llist (second llist))]
       cls))

(defn get-class-from-list [lstr]
  (let [llist (read-string lstr)
       clsstr (when llist (str (second llist)))]
       (when clsstr (str clsstr " "))))

(defn nodeMatches [node filterstr]
      (let [n (read-string node)]
        (when n
            (and (list? n)
              (or (symbol? (first n))(keyword? (first n)))
              (= filterstr (str (first n)))))))

(defn add-import-class [node replacestr]
  (let [nlist (read-string replacestr)
        pkgstr (str (first nlist))
        pos    (.indexOf node pkgstr)]
        (when (> pos -1) (+ pos (.length pkgstr) 1))))

(defn add-import  [pane ts offset hasPkg replacestr]
  (.move ts offset)
  (loop []
    (when (token-nav/move-next  token-nav/start-token? ts)
      (let [position (token-nav/get-enclosing-form ts (+ (.offset ts) (token-nav/count-leading-spaces ts) 1))]
        (if (not (nil? position))
          (if (nodeMatches (getNode pane position) ":import")
            (if (not hasPkg)
              (+ (:start position) 9)
              (+ (:start position) (add-import-class (getNode pane position) replacestr))))
          (recur))))))


(defn list-to-string [metamap  newlist]
  (let [newliststr (print-str newlist)]
    (when newliststr
      (reduce #(.replace %1 %2 (str "\n" %2)) newliststr metamap))))

(defn get-meta [lst]
(reduce (fn [init nspart] 
          (when (list? nspart) 
            (if (some #(list? %) nspart) 
              (get-meta nspart) (conj init (str nspart))))) [] lst))

(defn replace-ns-list [nslist oldilist newilist blnreplace]
  (if blnreplace
    (list-to-string (get-meta nslist)(replace {oldilist newilist} nslist))
    (list-to-string (get-meta nslist) 
      (remove nil? (conj nil (second (next nslist)) newilist (second nslist) (first nslist))))))

(defn handle-import-list [nsstr replacestr]
  (let [nslist (read-string nsstr)
        ilist (when nslist (first (filter #(and (list? %)
                                       (keyword? (first %))
                                          (= (name (first %)) "import"))
                                          nslist)))
        rpllist (read-string replacestr)
        rplitem (first rpllist)
        foundlst (when ilist (first (filter #(= (first %) rplitem) (next ilist))))
        ]
       (if ilist
         (if foundlst
           (replace-ns-list nslist ilist (replace {foundlst (conj (next foundlst) (get-class-from-list-cls replacestr) (first foundlst))}
             ilist) true)
           (replace-ns-list nslist ilist (conj (next ilist) rpllist (first ilist)) true))
         (replace-ns-list nslist ilist (conj nil rpllist :import) false))))



(defn handle-nsnode [nsstr replacestr]
  (let [nslist (read-string nsstr)
        ]
        (when nslist
         (handle-import-list nslist (some #(and (list? %)
                                            (keyword? (first %))
                                                (= (name (first %)) "import"))
                                                        nslist) replacestr)
            )))


(defn replace-import [nsstr replacestr]
  (let [nslist (read-string nsstr)
        replacelist (read-string replacestr)
        ilist (next (first (filter #(and (list? %)(keyword? (first %))(= (name (first %)) "import")) nslist)))
        ilistOrig (conj ilist :import)
        ilistNew (conj ilist replacelist :import)
        nslistnew (replace {ilistOrig ilistNew} nslist)
        nsstrnew (.replace nsstr (str ilistOrig) (str ilistNew))]
        nslistnew))


(defn replace-import-list[pane replacestr]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        ]
    (.moveStart ts)
    (loop []
      (when (token-nav/move-next token-nav/start-token? ts)
        (let [position (token-nav/get-enclosing-form ts (+ (.offset ts) 1))]
          (if (not (nil? position))
            (if (nodeMatches (getNode pane position) "ns")
              (replace-import (getNode pane position) replacestr)
              (recur))))))))
  
(defn add-import-list [pane replacestr hasPkg]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        ]
    (.moveStart ts)
    (loop []
      (when (< (.index ts) (.tokenCount ts))
      (when (token-nav/move-next token-nav/start-token? ts)
        (let [position (token-nav/get-enclosing-form ts (+ (.offset ts) (token-nav/count-leading-spaces ts) 1))]
          (if (not (nil? position))
            (if (nodeMatches (getNode pane position) "ns")
              (let [importstartpos (add-import pane ts (+ (:start position) 1) hasPkg replacestr)]
                (if importstartpos
                  (.insertString (.getDocument pane) importstartpos
                    (if hasPkg (get-class-from-list replacestr) replacestr) nil)
                  ))
              (recur)))))))))


(defn add-import-list-new [pane replacestr]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        ]
    (.moveStart ts)
    (loop []
      (when (< (.index ts) (.tokenCount ts))
      (when (token-nav/move-next token-nav/start-token? ts)
        (let [position (token-nav/get-enclosing-form ts (+ (.offset ts) (token-nav/count-leading-spaces ts) 1))]
          (if (not (nil? position))
            (if (nodeMatches (getNode pane position) "ns")
             (let [nodestr (getNode pane position)
                   hil (handle-import-list nodestr replacestr)]
              (do (.remove (.getDocument pane) (:start position) (- (:end position) (:start position)))
                    (.insertString (.getDocument pane) (:start position) hil nil))
               {:orignodestr nodestr :newnodestr hil}
               )
              (recur)))))))))

(defn get-ns-node  [#^JTextComponent pane keywordstr]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        ]
    (.moveStart ts)
    (loop []
      (when (token-nav/move-next  token-nav/start-token? ts)
        (let [position (token-nav/get-enclosing-form ts (+ (.offset ts) 1))]
          (if (not (nil? position))
            (if (nodeMatches (getNode pane position) "ns")
              (get-node (getNode pane position) keywordstr)
              (recur)
              )
            nil))))))

(defn get-ns-node-maplist  [#^JTextComponent pane keywordstr order]
  (let [#^TokenSequence ts (.tokenSequence (TokenHierarchy/get (.getDocument pane)))
        ]
    (.moveStart ts)
    (loop []
      (when (token-nav/move-next token-nav/start-token? ts)
        (let [position (token-nav/get-enclosing-form ts (+ (.offset ts) 1))]
          (if (not (nil? position))
            (if (nodeMatches (getNode pane position) "ns")
              (get-node-maplist (getNode pane position) keywordstr order)
              (recur)
              )
            nil))))))


(defn getAllJavaClassesWithNS [#^JTextComponent pane]
   (distinct (concat (getAllJavaClasses) (get-ns-node pane "import"))))


(defn getAllJavaClassesByFilterWithNS [#^JTextComponent pane fil]
  (filter #(.contains %1 fil)
    (distinct
      (concat (get-ns-node pane "import")
        (reduce (fn [em ns] (concat em (get-full-names (symbol (str ns)))))
          [] (all-ns))))))




(defn get-all-java-classes-with-ns [pane]
  (distinct (concat
              (get-ns-node pane "import")
              (filter #(.startsWith % "java.lang.")
              (reduce #(concat %1 (get-java-classes (symbol (str%2))))
                [] (all-ns))))))


(defn get-all-clojure-namespaces-within-nsnode[pane]
  (concat (get-ns-node pane "require") (get-ns-node pane "use")))

(defn remove-duplicates [coll]
  (remove nil?
    (reduce (fn [map1 map2]
                         (conj map1
                           (if
                             (and (= (:classname map2) (:classname (last map1)))
                               (= (:fullclassname map2) (:fullclassname (last map1))))
                             nil
                             map2) )) []
                 coll)))

(defn getAllClojureNamespacesByFilterWithNS [#^JTextComponent pane fil]
  (filter #(.startsWith %1 fil)
    (distinct (concat (get-ns-node pane "require") (get-ns-node pane "use")
                (reduce #(conj %1 (str %2))
                  [] (all-ns))))))


(defn get-all-namespaces-packages[Project pane order]
            (distinct (concat (get-all-clojure-namespaces-maplist order)
              (get-all-java-classes-maplist Project order)
              (get-ns-node-maplist pane "require" order)
              (get-ns-node-maplist pane "use" order)
              (get-ns-node-maplist pane "import" order)
                        )))

(defn get-all-namespaces-packages-by-filter[Project pane fil order]
      (filter (fn [eachmap] (or (.startsWith (:classname eachmap) fil)
                              (.startsWith (:fullclassname eachmap) fil)))
            (distinct (concat (get-all-clojure-namespaces-maplist order)
              (get-all-java-classes-maplist Project order)
              (get-ns-node-maplist pane "require" order)
              (get-ns-node-maplist pane "use" order)
              (get-ns-node-maplist pane "import" order)
                        ))))

(defn get-clojure-symbols-maplist-all[pane ns order]
    (concat
      (get-ns-node-maplist pane "require" order)
      (get-ns-node-maplist pane "use" order)
      (get-all-clojure-namespaces-maplist order)
      (get-clojure-symbols-with-users-maplist ns order)))


(defn get-clojure-symbols-maplist-by-filter[pane ns fil order]
  (filter (fn [eachmap] (.startsWith (:classname eachmap) fil))
    (concat
      (get-ns-node-maplist pane "require" order)
      (get-ns-node-maplist pane "use" order)
      (get-clojure-symbols-with-users-maplist ns order))))

(defn get-clojure-symbols-maplist-by-filter-contains[pane ns fil order]
  (filter (fn [eachmap] (.contains (:classname eachmap) fil))
    (concat
      (get-ns-node-maplist pane "require" order)
      (get-ns-node-maplist pane "use" order)
      (get-clojure-symbols-with-users-maplist ns order))))

(defn get-all-java-classes-maplist-all [Project pane order]
    (concat
      (get-ns-node-maplist pane "import" order)
      (get-all-java-classes-maplist Project order)))


(defn get-all-java-classes-maplist-by-filter [Project pane fil order]
  (filter (fn [eachmap] (or (.startsWith (:classname eachmap) fil)
                          (.startsWith (:fullclassname eachmap) fil)))
    (concat
      (get-ns-node-maplist pane "import" order)
      (get-all-java-classes-maplist Project order))))

(defn get-all-java-classes-maplist-by-filter-contains [Project pane fil order]
  (filter (fn [eachmap]   (.contains (:fullclassname eachmap) fil))
    (concat
      (get-ns-node-maplist pane "import" order)
      (get-all-java-classes-maplist Project order))))

(defn get-all-namespaces-packages-by-filter-contains[Project pane fil order]
  (filter (fn [eachmap]
            (.contains (:fullclassname eachmap) fil))
            (distinct (concat (get-all-clojure-namespaces-maplist order)
              (get-all-java-classes-maplist Project order)
              (get-ns-node-maplist pane "require" order)
              (get-ns-node-maplist pane "use" order)
              (get-ns-node-maplist pane "import" order)
              ))))

(defn get-symbols-classes-by-filter-contains[Project pane ns fil order]
  (concat
    (get-clojure-symbols-maplist-by-filter-contains pane ns fil order)
    (get-all-java-classes-maplist-by-filter-contains Project pane fil (+ order 1))))


(defn get-methods-static-maplist-by-filter [cls fil order]
  (filter (fn [eachmap] (.startsWith (:classname eachmap) fil))
            (get-methods-static-maplist cls order)))


  (defn get-results-for-scenario1[Project pane cls fil]
  (if (nil? cls)
    (remove-duplicates
    (sort-by :fullclassname
      (concat
      (get-all-namespaces-packages-by-filter Project pane fil 1)
      (get-all-namespaces-packages-by-filter-contains Project pane fil 2))))
   (concat
    (get-methods-no-static-maplist cls 1)
    (remove-duplicates
     (sort-by :fullclassname
    (concat
     (get-all-namespaces-packages-by-filter Project pane fil 2)
     (get-all-namespaces-packages-by-filter-contains Project pane fil 3)))))))

(defn get-results-for-scenario2 [Project pane ns fil]
  (remove-duplicates
  (sort-by :fullclassname
  (concat
    (get-clojure-symbols-maplist-by-filter pane ns fil 1)
    (get-all-java-classes-maplist-by-filter Project pane fil 2)
    (get-symbols-classes-by-filter-contains Project pane ns fil 3)))))

(defn get-results-for-scenario3 [cls nsstr]
  (if (nil? cls)
    (get-clojure-symbols-with-users-maplist-nsstr nsstr 1)
    (remove nil?
      (concat
        (get-methods-static-maplist cls 1)
        (get-clojure-symbols-with-users-maplist-nsstr nsstr 2)
        ))))

(defn get-results-for-scenario4 [Project]
   (get-all-methods-no-static-maplist Project 1))


(defn get-all-results-for-scenario1[Project pane cls fil]
  (if (nil? cls)
   (get-all-namespaces-packages Project pane 1)
    (get-methods-no-static-maplist cls 1)
     ))


(defn get-all-results-for-scenario2 [Project pane ns fil]
  (concat
    (get-clojure-symbols-maplist-all pane ns 1)
    (get-all-java-classes-maplist-all Project pane 2)))


(defn get-all-results-for-scenario3 [cls nsstr]
  (if (nil? cls)
    (get-clojure-symbols-with-users-maplist-nsstr nsstr 1)
    (remove nil? (get-methods-static-maplist cls 1))))

(defn get-all-results-for-scenario4 [Project]
   (get-all-methods-no-static-maplist Project 1))


