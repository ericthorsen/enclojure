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
    (com.sun.jdi VirtualMachine VirtualMachineManager ReferenceType
      ClassType)))

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

(defn get-all-classpaths-launch-string []
    (build-launcher-cp-string (get-all-classpaths)))

(defn get-source-files [#^Project p]
  (let [sources (ProjectUtils/getSources p)
        source-groups (.getSourceGroups sources JavaProjectConstants/SOURCES_TYPE_JAVA)]
    (loop [source-groups source-groups
           source-group (first source-groups)
           ret []]
      (if source-group
        (recur (next source-groups) (first source-groups) (conj ret (.getRootFolder source-group)))
        (distinct ret)))))

(defn get-classpath-from-source2 [source]
  (str (ClassPath/getClassPath source ClassPath/SOURCE)
    java.io.File/pathSeparator
    (ClassPath/getClassPath source ClassPath/EXECUTE)))

(defn get-classpath-from-source [source]
  (str (ClassPath/getClassPath source ClassPath/SOURCE)
    java.io.File/pathSeparator
    (.toString
        (ClassPath/getClassPath source ClassPath/EXECUTE)
      ClassPath$PathConversionMode/FAIL)))

(defn get-project-classpath [#^Project p]
  (when p
    (loop [sources (get-source-files p) ret ""]
      (if-let [source (first sources)]
        (recur (next sources)
          (str ret java.io.File/pathSeparator (get-classpath-from-source source)))
        (when ret (.substring ret 1))))))

(defn classpath-for-repl []
    (let [l (org.openide.modules.InstalledFileLocator/getDefault)]
                        (apply str (interpose java.io.File/pathSeparator
                                     (map #(.locate l % nil false)
                                       ["modules/ext/org.enclojure.repl-server.jar"                                        
                                        ])))))

(defn get-repl-classpath [#^Project p]
  (str (classpath-for-repl) java.io.File/pathSeparator (get-project-classpath p)))


        