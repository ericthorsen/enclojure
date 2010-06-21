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

(ns org.enclojure.ide.common.classpath-utils
  (:require
    [org.enclojure.ide.repl.classpaths :as classpaths]
    [org.enclojure.commons.c-slf4j :as logger]
    [clojure.contrib.except :as except]
    )
  (:import (org.netbeans.api.java.classpath ClassPath GlobalPathRegistry
             GlobalPathRegistryEvent GlobalPathRegistryListener)
    (org.netbeans.modules.java.classpath ClassPathAccessor)
    (java.lang ExceptionInInitializerError)
    (org.netbeans.api.java.platform JavaPlatform)
    (org.netbeans.api.java.queries SourceForBinaryQuery)
    (org.netbeans.api.project Project ProjectUtils SourceGroup)
    ;(org.netbeans.api.project.ui OpenProjects)
    (org.netbeans.api.java.classpath
            ClassPath
            ClassPath$PathConversionMode
            GlobalPathRegistryEvent
            GlobalPathRegistry
            GlobalPathRegistryListener)
    (org.netbeans.spi.java.classpath.support ClassPathSupport)
    (org.netbeans.api.java.platform JavaPlatformManager)
    (org.netbeans.api.java.project JavaProjectConstants)
    (org.openide.filesystems FileObject FileStateInvalidException
      FileUtil JarFileSystem URLMapper)
    (java.io File FileWriter IOException StringReader StringWriter
      PrintStream PrintWriter OutputStream ByteArrayOutputStream)
    (java.net JarURLConnection URL URI)
    ;(com.sun.jdi VirtualMachine VirtualMachineManager ReferenceType ClassType)
    ))

; setup logging
(logger/ensure-logger)

(def *url-file-cache* (ref {}))

(defn flatten-paths [paths]
   "Helper function that takes a sequence of paths and returns a single string with portable separators"
   (apply str (interpose (. java.io.File pathSeparator) paths)))


(defn file-from-str [#^String str]
"Creates a File object from a path or URL string"
    (java.io.File. #^String (if (.startsWith str "file:/")
                                (.toURI (java.net.URL. str))
                                str)))

(defn source-classpath-for-java-platforms []
"Returns the classpaths for the installed Java platforms"
  (map #(.getSourceFolders %)
    (.. JavaPlatformManager getDefault getInstalledPlatforms)))

(defn classpath-for-source []
  (.getPaths
  (GlobalPathRegistry/getDefault) "classpath/source"))

(defn check-explicit-path [n]
"Creates a File object from 'n'.  If the file exists, returns a FileObject"
  (let [f (java.io.File. n)]
    (when (.exists f)
      (.. FileUtil (toFileObject f)))))

(defn find-resource [n]
"Attempts to locate a resource within the projects sources"
    (first
      (set (filter identity
             (map #(.findResource % n)
               (classpath-for-source))))))

(defn clear-file-cache []
  (let [cpy @*url-file-cache*]
    (sync nil
      (ref-set *url-file-cache* {}))
    (dorun #(let [f (java.io.File. %)]
              (when (.exists f)
                (.delete f)))
      (vals cpy))))

(defn- resource-to-temp-file [file-object]
  (let [temp-file (.. java.io.File (createTempFile (.getName file-object)
                                                   (str "." (.getExt file-object))))
        writer (new FileWriter temp-file)
        reader (.getInputStream file-object)]
    (if reader
      (do
        (loop [c (. reader (read))]
          (when (not= -1 c)
            (. writer (write c))
            (recur (. reader (read)))))
        (. reader (close)))
      (new IOException (str (.getPath file-object) " : resource not found")))
    (. writer (close))
    temp-file))

(defn create-temp-file-for-resource [file-object]
  (let [k (.getURL file-object)]
      (let [f (resource-to-temp-file file-object)]
        (.toURL (.toURI f)))))

(defn resource-name-from-full-path [fp]
  "Given an explicit path, locates the resource portion based on the open projects source roots"
  (let [fpath (file-from-str fp)]
    (loop [pcks (classpath-for-source)]
      (when-let [p (first pcks)]
        (let [fo (FileUtil/toFileObject fpath)]
        (if (.contains p fo)
          (.getResourceName p fo)
          (recur (next pcks))))))))

(defn cp-str [#^ClassPath cp]
   (. cp (toString)))

(defn build-launcher-cp-string [classpaths]
   (if-not (empty? classpaths)
     (cp-str
      (ClassPathSupport/createProxyClassPath (into-array classpaths)))
     ""))

(defn move-file [src dest]
  "Deletes the destination file if exists and then move the source file to
   destination folder.
        e.g. (move-file \"/Users/nsinghal/todo/abc.jar\" \"/Users/jars\")
  "
  (let [srcf (new File src)
        destf (new File (str dest (. java.io.File separatorChar) (.getName srcf)))]
      (when (.exists destf) (.delete destf))
      (.renameTo srcf destf)))

(defn delete-directory [#^File path]
  "Removes recursively all files and folders from the given path and then delete
   this folder itslef.
        e.g. (delete-directory (new File \"/Users/nsinghal/todo\"))
  "
  (when (.exists path)
    (let [files (.listFiles path)]
      (doseq [file files]
        (if (.isDirectory file)
            (delete-directory file)
            (.delete file))))
    (.delete path)))

(defn make-package-path
  "Creates the directories for the package name under path for a given fully-qualified
   class name.
        (make-package-path \"/Users/nsinghal/todo\" \"org.enclojure.nbmodule.SwitchEditorRepl\")
   creates the directory: /Users/nsinghal/todo/org/enclojure/nbmodule
   "
  [path cname]
  (let [name (.substring (str cname) 0 (.lastIndexOf (str cname) "."))
        file (java.io.File. path (. name replace \. (. java.io.File separatorChar)))]
    (.mkdirs file)))

(defn make-unique-folder
  "Make unique folder under the system temp folder. First the base folder is
   created and then the temporaty folder.
        (make-unique-folder \"enclojure\" \"STATIC\")
  "
  [base prefix]
  (let [tmpdir (new File (str (.getProperty System "java.io.tmpdir")
                           (. java.io.File separatorChar) base))]
    (.mkdirs tmpdir)
    (let [tmpf (. File createTempFile prefix nil tmpdir) ;creates a temporary file
          tmp (.getAbsolutePath tmpf)]
      (.delete tmpf)
      (.mkdirs (new File tmp))
      tmp)))

(defn get-all-classpaths []
    (set (apply concat (map #(.getPaths (GlobalPathRegistry/getDefault) %)
                            ["classpath/source" "classpath/compile"]))))

(defn get-all-project-classpaths
  "Puts all the source roots first so clojure will be able to find the source files.
After that follows the compile dependant classpaths for each of the projects."
  []
  (let [[src compile]
        (map #(ClassPathSupport/createProxyClassPath
                (into-array (.getPaths (GlobalPathRegistry/getDefault) %)))
          ["classpath/source" "classpath/compile"])]
    (str src File/pathSeparator compile)))

(defn get-all-classpaths-launch-string []
    (build-launcher-cp-string (get-all-classpaths)))

(defmulti get-file-name-from class)

(defmethod get-file-name-from sun.net.www.protocol.file.FileURLConnection
  [connection]  
  (java.io.File. (-> connection .getURL .toURI)))
  
(defmethod get-file-name-from JarURLConnection
  [connection]
  (java.io.File. (-> connection .getJarFileURL .toURI)))
  

(defn file-from-jar-url
  "Given a jar URL, return a File object that refers to it"
  [jar-url]
  (except/throw-if-not
        (instance? java.net.URL jar-url)
    "Expected argument type of java.net.URL got %s" 
        (str (or (nil? jar-url) "nil" jar-url)))
  (when-let [jar-conn (.openConnection jar-url)]
    (get-file-name-from jar-conn)))

(defn get-source-roots
  "Looks at each of the SourceGroups of a project and returns the root folder
of all the JavaProjectConstants/SOURCES_TYPE_JAVA"
  [#^Project p]
  (let [sources (ProjectUtils/getSources p)
        source-groups (.getSourceGroups sources
                        JavaProjectConstants/SOURCES_TYPE_JAVA)]
    (loop [source-groups source-groups
           source-group (first source-groups)
           ret []]
      (if source-group
        (recur (next source-groups) (first source-groups)
          (conj ret (.getRootFolder source-group)))
        (distinct ret)))))


(defn get-paths-from-classpath
  "Given a ClassPath object, returns a vector of canonical paths as strings"
  [#^ClassPath cp]
  (reduce #(if (.exists %2)
             (conj %1 (.getCanonicalPath %2))
             %1)
          [] (filter identity 
               (map #(when-let [r (-> % .getRoot)]
                         (file-from-jar-url (.getURL r)))
                    (.entries cp)))))


(defn classpath-set-from-cp
  "Given a ClassPath object, builds a canonical classpath string for
use on a jvm startup."
  [#^ClassPath cp]
  (let [base-str (.toString cp ClassPath$PathConversionMode/FAIL)]
    (str base-str
      java.io.File/pathSeparator
      (classpaths/build-classpath-str
        (get-paths-from-classpath cp)))))

(defn build-classpath-set
  "Given a SourceGroup and ClassPath/<Type>
    ClassPath/BOOT
    ClassPath/COMPILE
    ClassPath/DEBUG
    ClassPath/EXECUTE
    ClassPath/SOURCE
    etc.
    returns a canonical classpath string for
use on a jvm startup."
  [source-group cp-type]
  (classpath-set-from-cp (ClassPath/getClassPath source-group cp-type)))

(defn get-classpath-from-source2 [source]
  (str (ClassPath/getClassPath source ClassPath/SOURCE)
    java.io.File/pathSeparator
    (ClassPath/getClassPath source ClassPath/EXECUTE)))

(defn get-classpath-from-source3 [source]
  (str (ClassPath/getClassPath source ClassPath/SOURCE)
    java.io.File/pathSeparator
    (.toString
        (ClassPath/getClassPath source ClassPath/EXECUTE)
      ClassPath$PathConversionMode/FAIL)))

(defn get-classpath-from-source [source]
  (str (build-classpath-set source ClassPath/SOURCE)
    java.io.File/pathSeparator
    (build-classpath-set source ClassPath/EXECUTE)))

(defn get-classpath-from-source [source]
  {:source-roots
    [(build-classpath-set source ClassPath/SOURCE)]
   :execute-paths
    [(build-classpath-set source ClassPath/EXECUTE)]
   :boot-paths
    [(build-classpath-set source ClassPath/BOOT)]})

(def proj (atom nil))

(defn do-get-project-classpath-str
  "For a given project we want a map of:
{:source-roots :execute-paths}"
  ([#^Project p]
    (logger/info "might be a sub-project call for " p)
    (when p
        (loop [sources (get-source-roots p) ret {}]
        (if-let [source (first sources)]
            (recur (next sources)
              (merge-with concat
                ret
                (get-classpath-from-source source)))
            ret)))))

(defn do-get-project-classpath
  "For a given project we want a map of:
{:source-roots :execute-paths}"
  ([#^Project p]
    (when p
        (loop [sources (get-source-roots p) 
               ret   {  :source-roots []
                        :execute-paths []
                        :boot-paths [] }]    
        (if-let [source (first sources)]
            (recur (next sources)
              (reduce (fn [m [k src]]
                        (update-in m [k]
                         conj (ClassPath/getClassPath source src)))
                    ret (map vector [:source-roots :execute-paths :boot-paths]
                          [ClassPath/SOURCE ClassPath/EXECUTE ClassPath/BOOT])))
            ret)))))

(defn get-project-classpath
  "This function recursively looks at dependancies and collects all the source roots
and execution requirements for the root project and all it's dependancies.  The
resulting classpath has all the source roots listed before any execution dependancies
in order to promote clojure finding the source and loading that before anything else."
  ([#^Project p]
  (when p
    (swap! proj (fn [_] p))
    (loop [sub-projects 
           (when-let [subpp (-> p .getLookup
                         (.lookup org.netbeans.spi.project.SubprojectProvider))]
             (logger/info "Found subproject..............")
             (.getSubprojects subpp))
           cps {}]
      (if-let [subp (first sub-projects)]
        (recur (rest sub-projects)
          (merge-with concat cps
            (do-get-project-classpath-str subp)))
        (let [final-set
              (merge-with concat
                (do-get-project-classpath-str p) cps)]
            (apply str
                (interpose java.io.File/pathSeparator
                  (concat
                    (set (:source-roots final-set))
                    (set (:execute-paths final-set))
                    (set (:boot-paths final-set)))))))))))

(defn classpath-for-java-platform
  ([platform]
    (classpath-set-from-cp
      (.getBootstrapLibraries platform)))
  ([] (JavaPlatform/getDefault)))

(defn java-exec-properties-for-java-platform
  "Given a JavaPlatform returns a map with:
  :java-launcher (File object with the fill path to the java launcher)
  :java-home (File object with the home path of the JavaPlatform.)
If not arg is passed, calls this function with the default Java Platform
setup in Netbeans"
  ([platform]
    (loop [paths (iterator-seq (-> platform .getInstallFolders .iterator))]
      (if-let [path (first paths)]
        (let [basepath (File. (.getPath path))
              java (first (filter #(.exists %1)
                            [(File. basepath (str "bin" File/separator "java"))
                             (File. basepath (str "bin" File/separator "java.exe"))]))]
          (if java {:launcher java
                    :java-home basepath
                    :libs (.getBootstrapLibraries platform)}
            (recur (rest paths))))
                nil)))
  ([] (java-exec-properties-for-java-platform
            (JavaPlatform/getDefault))))

(defn get-java-platform-for
  ([canonical-classpath-str]
    (assert (string? canonical-classpath-str))
  (let [platforms
            (map java-exec-properties-for-java-platform
                     (.getInstalledPlatforms (JavaPlatformManager/getDefault)))]
    (when-let [platform (first (filter #(.contains
                                        canonical-classpath-str
                                        (.getCanonicalPath (:java-home %)))
                                            platforms))]
      platform))))
  

(defn classpath-for-repl []
    (let [l (org.openide.modules.InstalledFileLocator/getDefault)
          repl-server-jar (.locate l
                            "modules/ext/org-enclojure-repl-server-1.0-SNAPSHOT.jar"
                            nil false)]
      repl-server-jar))

(defn get-repl-classpath [#^Project p]
  (let [cp (str (classpath-for-repl) java.io.File/pathSeparator (get-project-classpath p))]    
    cp))


;(comment

(def plats (.getInstalledPlatforms (JavaPlatformManager/getDefault)))
(def libs (map #(.getBootstrapLibraries %) plats))

;)