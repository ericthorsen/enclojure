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
(ns org.enclojure.ide.nb.clojure.project.create
  (:refer-clojure :exclude (replace))
  (:require 
    [org.enclojure.ide.preferences.platform-options :as platform-options]
    )
  (:use clojure.xml)
    (:import (java.util.zip ZipEntry ZipInputStream)
    (java.io ByteArrayInputStream LineNumberReader InputStreamReader PrintWriter
        ByteArrayOutputStream File FileInputStream FileNotFoundException
        FileOutputStream IOException InputStream OutputStream PrintStream)
        (org.openide.filesystems FileObject FileUtil)
        (org.enclojure.ide.nb.clojure.project ClojureTemplateWizardIterator)
      (javax.swing JFileChooser)))

;(def z (ZipInputStream. (FileInputStream. "/Users/ericthorsen/new-enclojure/src/enclojure/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/ClojureProjectTemplate.zip")))

(defn root-resource
  "Returns the root directory path for a lib"
  [lib]
  (str \/ (-> lib (.replace \- \_) (.replace \. \/))))

(defn root-directory
  "Returns the root resource path for a lib"
  [lib]
  (let [d (root-resource lib)]
    (subs d 0 (.lastIndexOf d "/"))))

(defn file-from-ns [lib]
  (let [rr (root-resource lib)]
    (.substring rr (inc (.lastIndexOf rr "/")))))

(defn- make-package-path
  "Creates the directories for the package name under path for a given fully-qualified
   class name.
        (make-package-path \"/Users/nsinghal/todo\" \"org.enclojure.nbmodule.SwitchEditorRepl\")
   creates the directory: /Users/nsinghal/todo/org/enclojure/nbmodule
   "
  [path cname]
  (let [name (.substring (str cname) 0 (.lastIndexOf (str cname) "."))
        file (java.io.File. path (. name replace \. (. java.io.File separatorChar)))]
    (.mkdirs file)))

(defn update-project-name [xml-str name]
  (loop [loc (clojure.zip/xml-zip xml-str)]
    (if (clojure.zip/end? loc)
      (clojure.zip/root loc)
      (recur
        (clojure.zip/next
          (if (= (:tag (clojure.zip/node loc)) :name)
            (clojure.zip/replace loc (merge (clojure.zip/node loc)
                                       {:content [name]}))
            loc))))))

(defn filter-project-file [fo istr name]
  (with-open [o (.getOutputStream fo)]
    (binding [*out* o]
      (emit (update-project-name (parse istr) name)))
    (.flush o)))

(defn filter-project-XML [fo istr name]
  (ClojureTemplateWizardIterator/filterProjectXML fo istr name))
  
(defn transform-string [text tags]
  (loop [tags tags ret-text text]
    (let [[k v :as tag] (first tags)]
      (if tag 
        (recur (next tags)
          (.replace ret-text k v))
        ret-text))))

(defn write-file [istream file-object]
  (with-open [out (.getOutputStream file-object)]
        (FileUtil/copy istream out)))

(defn clj-ns [p]
  (.substring p (inc (.lastIndexOf p (int \.)))))

(defn make-java-compatible [p]
  (.replace p "-" "_"))

(defn ns-to-path [pkg]   
  (.replace pkg "." "/"));java.io.File/separator))

(defn clj-file-for [pkg]
  (make-java-compatible (str (ns-to-path pkg)
    "/";java.io.File/separator
     (clj-ns pkg) ".clj")))

(defn transform-file [source target tag-map]
  (with-open [t (PrintWriter. (.getOutputStream target))]
    (let [s (LineNumberReader. (InputStreamReader. source))]    
      (loop [l (.readLine s)]
        (when l
          (.println t (transform-string l tag-map))
          (recur (.readLine s)))))
    (.flush t)))

(defn transform-file2 [source target-ostr tag-map]
  (with-open [t (PrintWriter. target-ostr)]
    (let [s (LineNumberReader. (InputStreamReader. source))]
      (loop [l (.readLine s)]
        (when l
          (.println t (transform-string l tag-map))
          (recur (.readLine s)))))
    (.flush t)))


(defn file-tags [namespace]
  {"src/main/clojure/sample/" (str "src/main/clojure" (root-directory namespace))
   "src/main/clojure/sample/core.clj" (str "src/main/clojure" (root-resource namespace) ".clj")
   "src/main/clojure/sample" (str "src/main/clojure" (root-resource namespace))
   "src/test/clojure/sample/" (str "src/test/clojure" (root-directory namespace))
   "src/test/clojure/sample/core_test.clj" (str "src/test/clojure" (root-resource namespace) ".clj")
   "src/test/clojure/sample" (str "src/test/clojure" (root-resource namespace))
   })

(defn package-tags [pkg project-name]
   {
    ;"default." (subs pkg 0 (.lastIndexOf pkg "."))
    "default.core" pkg
    "sample.core" pkg
    "ClojureProjectTemplate" project-name
    "libs.Clojure.classpath"
        (str "libs." (platform-options/get-clojure-default-lib) ".classpath")
    })

(def *file-name-map* 
  {"src/main.clj" "~~CLJ-FILENAME~~.clj"
   "src/Main.java" "~~JAVA-CLASS~~.java"})

(defn file-mapping [file-name tag-map]
  (if-let [f (*file-name-map* file-name)]
    (transform-string f tag-map)
    file-name))

(defn to-map 
  "Return a map with all the items in the zip-stream keyed by name"
  {:test '(to-map (.getInputStream 
                    (org.enclojure.platform.jdi.sourcehelpers/find-resource 
                      "org/enclojure/enclojure/project/ClojureTemplateProject.zip")))}
  [zip-source]
  (with-open [istr (ZipInputStream. zip-source)]
    (loop [m {} istr istr]
      (if-let [entry (.getNextEntry istr)]
        (recur (assoc m (.getName entry) entry)
          istr)
        m))))


(defmulti process-entry (fn [e attribs-map]
                          (cond (.isDirectory e) :directory
                            (= "nbproject/project.xml" (.getName e)) :project-file
                            (= "src/" (.getName e)) :package
                            (.startsWith (.getName e) "src/") :source)
                            (.contains (.getName e) ".properties" :properties)))

(defmethod process-entry :default [e {:keys [root package istr]}]
  (write-file istr 
    (FileUtil/createData root (.getName e))))

(defmethod process-entry :package [e {:keys [root package istr]}])

(defmethod process-entry :directory [e {:keys [root package istr]}]
  (FileUtil/createFolder root (.getName e)))

(defmethod process-entry :project-file [e {:keys [root package istr]}]
  (filter-project-XML 
    (FileUtil/createData root (.getName e)))
    istr (.getName root))

(defmethod process-entry :source [e {:keys [root package istr java-class]}]
  (let [root-src (.getCanonicalPath 
                   (File. (str (.getPath (.. FileUtil (toFile root))) 
                            (. java.io.File separatorChar) "src")))
        package-path (root-directory package)
        tag-map (package-tags package)]    
    (make-package-path root-src package)                   
    (transform-file istr 
      (.. FileUtil 
        (createData 
          (File. 
            (apply str 
              (interpose 
                (. java.io.File separatorChar)
                [root-src package-path (file-mapping (.getName e) tag-map)]))))) 
      tag-map)))

(defn unzip-project 
  "unzips project template"  
  [source project-root package-name java-class] 
  (with-open [istr (ZipInputStream. source)]
    (loop [entry (.getNextEntry istr)]
      (when entry
        (process-entry entry 
          {:root project-root :package package-name :istr istr :java-class java-class})
        (recur (.getNextEntry istr))))))

(def --skip-these-- #{".DS_Store"})

(defn unzip-project-files 
  "unzips project template"
 [source project-root package-name project-name]
  (with-open [istr (ZipInputStream. source)]
    (let [file-tags (file-tags package-name)]
    (loop [entry (.getNextEntry istr)]
      (when entry
        (let [temp-name (.getName entry)
              is-source (.startsWith temp-name "src/")
              ; Conversion of any file names are done here.
              entry-name (or (file-tags temp-name) temp-name)]
          (println "Project creation " entry-name)
          (if (.isDirectory entry) 
            (FileUtil/createFolder project-root entry-name)
            (cond (= "nbproject/project.xml" entry-name)
              (filter-project-XML 
                (FileUtil/createData project-root entry-name)
                istr (.getName project-root))
              :else
              ;(and package-name is-source)
                (transform-file istr 
                  (FileUtil/createData project-root entry-name)
                    (package-tags package-name project-name)))))
        (recur (.getNextEntry istr)))))))

(defn unzip-and-create-project-files
  "unzips project template"
 [source project-root package-name project-name]
  (with-open [istr (ZipInputStream. source)]
    (let [file-tags (file-tags package-name)]
;      (println file-tags)
        (loop [entry (.getNextEntry istr) files []]
 ;         (println (.getName entry))
          (if entry
            (let [temp-name (.getName entry)
                  is-source (.startsWith temp-name "src/")
                  ; Conversion of any file names are done here.
                  entry-name (or (file-tags temp-name) temp-name)
                  f (File. (str project-root) (str entry-name))]
              (println "Project creation " entry-name)
              (if (not (some #(.contains temp-name %) --skip-these--))
                (do
                  (if (.isDirectory entry)
                    (.mkdirs f)
                    (transform-file2 istr
                      (FileOutputStream. f)
                      (package-tags package-name project-name)))
              (recur (.getNextEntry istr) (conj files f)))
              (recur (.getNextEntry istr) files)))
            files)))))

(defn realize-files-in-nbs [files]
      (doseq [f files]
        (if (.isDirectory f)
            (FileUtil/createFolder f)
        (FileUtil/createData f)))
  files)

(defn unzip-create-and-reg-project
 [source project-root package-name project-name]
  (let [files (unzip-and-create-project-files
                    source project-root package-name project-name)]
    (realize-files-in-nbs files)))
  
(defn test-unzip []
  (let [z (FileInputStream. "/Users/ericthorsen/new-enclojure/src/enclojure/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/nb/editor/ClojureProjectTemplate.zip")
        f (when-let [f (File. "/Users/ericthorsen/aaaatesting2")]
             (.mkdirs f)
            (when (.exists f)
                   (org.openide.filesystems.FileUtil/createFolder #^java.io.File f)))
        p "org.mycompany.defpackage"]
    (print "can u see this?")
    (unzip-project-files z f p)))

(def --testzip--
  "/Users/ericthor/Dev/enclojure-work/enclojure/netbeans/plugins/org-enclojure-plugin/src/main/resources/org/enclojure/ide/templates/project/Clojure-1.2/Clojure-1.2.zip")

(defn test-unzip2 []
  (let [z (FileInputStream. --testzip--)
        f (when-let [f (File. "/Users/ericthor/nb-zip")]
             (.mkdirs f) f)
        proj-name "MyProject"
        p "org.mycompany.different.package"]
    (print "can u see this?")
    (unzip-and-create-project-files z f p proj-name)))

(defn select-location [panel path]
    (let [chooser (JFileChooser.)]
      (FileUtil/preventFileChooserSymlinkTraversal chooser nil)
      (doto chooser (.setDialogTitle "Select Project Location")
                (.setFileSelectionMode JFileChooser/DIRECTORIES_ONLY))
      (when-let [f (File. path)]
            (when  (.exists f)
                (.setSelectedFile chooser f)))
            (if (= JFileChooser/APPROVE_OPTION (.showOpenDialog chooser panel))
               (.getSelectedFile chooser))))
