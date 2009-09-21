(comment
;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;    The use and distribution terms for this software are covered by the
;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;    which can be found in the file epl-v10.html at the root of this distribution.
;    By using this software in any fashion, you are agreeing to be bound by
;    the terms of this license.
;    You must not remove this notice, or any other, from this software.
;
;    Author: Eric Thorsen
)

(ns org.enclojure.ide.nb.source.add-file
  (:require
    [org.enclojure.ui.controls :as controls]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.commons.meta-utils :as meta-utils]
    )
  (:import
    (java.io File)
    (java.util.logging Level)
    (javax.swing JComponent DefaultComboBoxModel)
    (javax.swing.event ChangeListener DocumentListener)
    (org.netbeans.api.project Project)
    (org.netbeans.spi.project.ui.templates.support Templates)
    (org.openide WizardDescriptor)
    (org.openide.loaders TemplateWizard DataObject DataFolder)
    (org.openide.filesystems FileUtil FileObject)
    (org.openide.cookies OpenCookie)
    (org.netbeans.api.project ProjectUtils)
    (org.netbeans.api.project ProjectInformation)
    (java.awt.event ActionListener KeyAdapter KeyEvent ItemListener ItemEvent)
    (org.netbeans.api.java.project JavaProjectConstants)
    ))

; setup logging
(logger/ensure-logger)

(def -properties-
  {:namespace "namespace"})

(defn make-key-listener
  [func]
  (proxy [KeyAdapter][]
    (keyReleased [#^KeyEvent evt]
      (func evt))))

(defn get-doc-listener
  [call-back]
  (proxy [DocumentListener] []
    (changedUpdate [#^DocumentEvent e]
      (call-back :change e)
      )
    (removeUpdate [#^DocumentEvent e]
      (call-back :remove e)
      )
    (insertUpdate [#^DocumentEvent e]
      (call-back :insert e)
      )
    ))

(def p (atom nil))

(defn traverse
  "Takes a FileObject and recurses through returning all the files that satisfy pred"
  [root pred]
  (let [accumfn (fn accumfn [roots files]
                    (loop [c roots files files]
                        (if-let [file (first c)]
                          (let [nfiles (if (pred file)
                                         (conj files file) files)]
                            (recur (rest c)
                                (if (.isFolder file)
                                   (accumfn (.getChildren file) nfiles)
                                nfiles)))
                          files)))]
    (accumfn (.getChildren root) [])))

(defn get-source-root-and-ns-from-default
  "Returns a map with the {:root <selected root in the target file>
                           :ns <namespace prefix based on the target file>}
source roots is a map from diplay name to the root file object."
  [source-roots target-folder]
  (let [target-str (str target-folder)
        [disp-name root] 
            (first (filter (fn [[disp-name file-obj]]
                      (.startsWith target-str (str file-obj)))
                    source-roots))
        root-str (str root)]
    (logger/debug "dispname {} root {}" disp-name root)
    (when root
      {:root root
       :root-name disp-name       
       :ns (when (> (count target-str)
                   (count root-str))
              (subs target-str
                (+ 1 (.indexOf target-str root-str)
                  (count root-str))))})))

(defn get-project-data
  "Given a Project return {:name <project name>
                           :source-roots <map of display name to root FileObject>}"
  [project]
  (let [proj-info (ProjectUtils/getInformation project)
        source-roots (ProjectUtils/getSources project)]
    {:name (.getName proj-info)
     :source-roots
        (reduce #(assoc %1 (.getDisplayName %2) (.getRootFolder %2))
          {} (.getSourceGroups source-roots
                JavaProjectConstants/SOURCES_TYPE_JAVA))}))

(defn ns-from-root-file-object
  [source-root-str full-file-path]
  (meta-utils/ns-from-file
    (subs full-file-path
       (+ 1 (count source-root-str)))))

(defmulti get-canonical-path class)

(defmethod get-canonical-path java.io.File
  [f] (.getCanonicalPath f))

(defmethod get-canonical-path String
  [f] (.getCanonicalPath (java.io.File. f)))

(defmethod get-canonical-path org.openide.filesystems.FileObject
  [f] (get-canonical-path
        (FileUtil/toFile f)))

(defn get-packages-from-root-model
  [source-root]
  (logger/info "source root = {}" source-root)
  (let [root-str (get-canonical-path (.getPath source-root))]
    (DefaultComboBoxModel.
        (into-array
            (reduce (fn [v f]
                      (conj v 
                        (ns-from-root-file-object
                          root-str (get-canonical-path f))))
                            []
                (traverse source-root
                  #(and (.isFolder %1) (not (.contains (.getPath %) ".svn")))))))))

(defn select-default-location
  [location-ui source-roots maybe-null-target]
  (let [find-fn #(first (filter identity
                   (map (fn [[display-name file-obj] inx]
                          (when (.startsWith %1
                                  (.getPath file-obj)) inx))
                     source-roots (range (count source-roots)))))
        root (or (find-fn (str maybe-null-target))
                (find-fn "Source Packages") 0)]    
    (when (> (-> location-ui .getModel .getSize) root)
      (.setSelectedIndex location-ui root))
    ))

(defn find-inx ; this has got to be a built in function????
  [col pred]
  (loop [i (range (count col)) c col]
    (when-let [inx (first i)]
      (if (pred (first c))
        inx
        (recur (rest i) (rest c))))))

(defn update-created-file
  [proj-info file-pane]
  (boolean
    (let [root-name (.getSelectedItem (.sourceRootsComboBox file-pane))
          root (get-canonical-path (.getPath ((:source-roots proj-info) root-name)))
          pkg-dir (meta-utils/root-resource
                    (str (.getItem
                           (.getEditor
                             (.packagesComboBox file-pane)))))
          fname (meta-utils/file-from-ns (.getText (.filenameTextField file-pane)))
          full-path (str root
                        File/separator pkg-dir
                        File/separator fname)]
      (.setText (.createdFileTextField file-pane) 
        (get-canonical-path full-path)))))

(defn set-combo-box-to
  [combo val]
  (let [model (.getModel combo)]
    (loop [i (range (.getSize model))]
      (when-let [inx (first i)]
        (if (= val (.getElementAt model inx))
            (.setSelectedIndex combo inx)
           (recur (rest i)))))))

(defn check-namespace
  [strn]
  (every? #(and (not= % \_)
            (or (Character/isJavaIdentifierPart %)
            (#{\- \.} %))) strn))

(defn check-file-name
  [strn]
  (every? #(and (not= % \_)
            (or (Character/isJavaIdentifierPart %)
            (#{\-} %))) strn))

(defn validate-document
  [file-pane wizard-descriptor file-wizard proj-info]
  (fn [event]
    (let [d (.getDocument event)
          to-test (.getText d 0 (.getLength d))
          err-msg ; I hate this...
          (cond (= d (-> (.filenameTextField file-pane) .getDocument))
                    (when-not (check-file-name to-test)
                      "Invalid file name.")
                (= d (-> (.packagesComboBox file-pane)
                            .getEditor .getEditorComponent .getDocument))
                    (when-not (check-namespace to-test)
                      "Invalid namespace.")
            :else
            (throw (Exception. "Validation called but there is no source defined?")))]
        (if err-msg
        (do
          (.setValid wizard-descriptor false)
            (.putProperty wizard-descriptor
              WizardDescriptor/PROP_ERROR_MESSAGE
                err-msg))
         (do
            (update-created-file proj-info file-pane)
            (.setValid wizard-descriptor true)
            (.putProperty wizard-descriptor
                WizardDescriptor/PROP_ERROR_MESSAGE nil)))
      (zero? (count err-msg)))))
  
(defn init-edit-scheme
  [project-info file-pane wizard-descriptor file-wizard default-target-folder]
  (let [roots-array (into-array (keys (:source-roots project-info)))]
    (.setText (.projectTextField file-pane) (:name project-info))
    (.setModel
      (.sourceRootsComboBox file-pane)
        (DefaultComboBoxModel. roots-array))
  (let [{:keys [root ns root-name]}
         (get-source-root-and-ns-from-default
            (:source-roots project-info) default-target-folder)
        inx (or (find-inx (seq roots-array) #(= root-name %))
                    (find-inx (seq roots-array) #(= "Source Packages" %))
                    0)
        item-state-changed-fn
                  (fn []
                    (let [root-name (.getSelectedItem (.sourceRootsComboBox file-pane))]
                        (when-let [new-root ((:source-roots project-info) root-name)]
                            (.setModel (.packagesComboBox file-pane)
                                (get-packages-from-root-model new-root))
                                (update-created-file project-info file-pane))))]
    (.setSelectedIndex (.sourceRootsComboBox file-pane) inx)
    (item-state-changed-fn)
    (logger/info "ns-from-file {} Index {}"
      (when ns
        (meta-utils/ns-from-file ns)) inx)
    ; Wire up the event handlers first.
;    (.addKeyListener (.filenameTextField file-pane)
;      (proxy [KeyAdapter][]
;        (keyTyped [#^KeyEvent event]
;          (if-not (check-char event)
;            (.consume event)
;          (update-created-file file-pane project-info)))))
;    (.addActionListener (.getEditor (.packagesComboBox file-pane))
;      (proxy [ActionListener] []
;        (actionPerformed [event]
;          (update-created-file file-pane project-info)
;          )))
;    (.addDocumentListener (.getDocument (.filenameTextField file-pane))
;      (get-doc-listener (validate-document
;                          file-pane wizard-descriptor file-wizard project-info)))
;    (.addDocumentListener
;      (-> (.packagesComboBox file-pane)
;        .getEditor .getEditorComponent .getDocument)
;      (get-doc-listener (validate-document
;                          file-pane wizard-descriptor file-wizard project-info)))
    (.addItemListener (.packagesComboBox file-pane)
      (proxy [ItemListener][]
        (itemStateChanged [event]
          (update-created-file project-info file-pane))))
    (.addItemListener (.sourceRootsComboBox file-pane)
      (proxy [ItemListener][]
        (itemStateChanged [event]
          (item-state-changed-fn))))
    (set-combo-box-to (.packagesComboBox file-pane)
            (or (when ns (meta-utils/ns-from-file ns)) ""))
    (.setText (.filenameTextField file-pane) "new-ns")
    (update-created-file project-info file-pane)
    (validate-document file-pane wizard-descriptor
                    file-wizard project-info)
  )))

; On key type:
; make sure it is an allowable key (java package keys_
; update the namespace to reflect
; source root removed <from source root with file name> convert to namespace
; base file should reflect the
(defn setup-wizard
  [wizard-iterator wizard-descriptor]
  (let [file-wizard (.current wizard-iterator)
        file-pane (.getComponent file-wizard)
        project (Templates/getProject wizard-descriptor)
        project-info (get-project-data project)
        target-folder (Templates/getTargetFolder wizard-descriptor)]
    (swap! p (fn [_] {:p project-info :t target-folder}))
    (init-edit-scheme project-info file-pane wizard-descriptor file-wizard target-folder)
  ))

(defn create-file
    [wizard-iterator wizard-descriptor]
  (let [file-pane (.getComponent (.current wizard-iterator))
        project (Templates/getProject wizard-descriptor)
        ns-suffix (.getText (.filenameTextField file-pane))
        namesp (str (-> (.packagesComboBox file-pane) .getEditor .getItem)
             "." ns-suffix)
        file (.replace (str  ns-suffix ".clj") \- \_)
        full-path (.getText (.createdFileTextField file-pane))        
        base-path (subs full-path 0 (.indexOf full-path file))
        target-folder (FileUtil/createFolder (File. base-path))
        data-folder (DataFolder/findFolder target-folder)]
    (logger/debug "file {}" file)
    (doto wizard-descriptor
      (.setTargetName file)
      (.setTargetFolder data-folder))
    (let [t (.getTemplate wizard-descriptor)
          newf (.createFromTemplate t data-folder file
                 #^java.util.Map {"namespace" namesp})]
      (when-let [open-cookie
                 (.getCookie newf OpenCookie)]
        (.open open-cookie)))))
    

    


