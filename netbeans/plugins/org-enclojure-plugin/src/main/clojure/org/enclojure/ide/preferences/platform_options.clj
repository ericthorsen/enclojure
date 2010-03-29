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
(ns org.enclojure.ide.preferences.platform-options  
    (:require
          [org.enclojure.ide.settings.utils :as pref-utils]
          [org.enclojure.ide.nb.editor.utils :as utils]
          [org.enclojure.ui.controls :as controls]
          [org.enclojure.commons.c-slf4j :as logger]
      )
(:import (javax.swing Icon ImageIcon)
  (java.util.logging Level)
  (java.util  UUID)
  (java.util.zip ZipInputStream ZipEntry)  
  (org.openide.util NbBundle Utilities)
  (org.netbeans.api.project.libraries LibraryManager Library)
  (org.openide.filesystems FileObject FileStateInvalidException
      FileUtil JarFileSystem URLMapper)
  (java.beans PropertyChangeSupport PropertyChangeListener PropertyChangeEvent
    PropertyVetoException)
  (org.enclojure.ide.preferences EnclojureOptionsCategory)
  (org.enclojure.ide.preferences EnclojurePreferencesPanel)
  (javax.swing JFileChooser DefaultListModel JOptionPane JComboBox DefaultComboBoxModel)
  (javax.swing.filechooser FileFilter FileView)
  (javax.swing.event ListSelectionListener ListSelectionEvent)
  (java.awt.event KeyEvent)
  (java.awt EventQueue)
  (java.net URL)
  (java.io File FileOutputStream BufferedInputStream)
  ))

; setup logging
(logger/ensure-logger)

(def -meta-map- (ref {}))
(def -system-folder-for-platforms- "ClojurePlatforms")
(def -default-platform- "Clojure-1.1.0")

(defstruct platform :name :classpaths :default :key)

(def -clojure-default-platform-name- -default-platform-)
(def #^{:private true} -prefs-category- "platforms")

(def -platform-name-comp-
  (proxy [java.util.Comparator] []
    (compare [x y]
      (compare (:name x) (:name y)))))

(defn create-library 
  "Create a library in the netbeans library manager."
  [name classpaths]
  (.createLibrary (LibraryManager/getDefault)
    "j2se"
    name
    {"classpath"
     (reduce
       #(conj %1
          (URL. (str "jar:file:"
                  (.replace
                    (.getFile
                      (.toURL (java.io.File. %2))) " " "%20") "!/")))
       [] classpaths)}))

(defn proper-libname
  [name]
  (when name
    (.replace name " " "-")))

(defn ensure-libs [platforms]
  (let [lookup
        (reduce #(conj %1 (.getDisplayName %2)) #{}
              (.getLibraries (LibraryManager/getDefault)))]
    (doseq [{:keys [name classpaths]} platforms]
      (let [libname (proper-libname name)]
      (when-not (lookup libname)
        ; make sure there are classpaths before trying to create a lib
        (when (pos? (count classpaths))
            (create-library libname classpaths)))))))

(defn faster-copy
  [istream ostream buf-size]
  (let [bi (BufferedInputStream. istream)
        buff (make-array Byte/TYPE buf-size)]
    (loop [bytes (.read bi buff 0 buf-size)]
      (when (pos? bytes)
        (.write ostream buff 0 bytes)
        (when (= bytes buf-size)
          (recur (.read bi buff 0 buf-size)))))))

(defn unzip-to
  [inzip-stream dest-path]
  (let [#^ZipInputStream istream (if (instance? ZipInputStream inzip-stream)
                                   inzip-stream (ZipInputStream. inzip-stream))]
    (loop [e (.getNextEntry istream) lib-names []]
      (if e
        (let [full-name (str dest-path File/separator (.getName e))]
          (with-open [fout (FileOutputStream. full-name)]
            (faster-copy istream fout 8096)
            (recur (.getNextEntry istream) (conj lib-names full-name))))
        lib-names))))

(defn get-defined-platforms
  "Looks in config file of the app (in NB case the layer file)
in the -system-folder-for-platforms- path for all defined clojure platforms
and creates entries in the local preferences path."
  []
  (let [platforms (FileUtil/getConfigFile -system-folder-for-platforms-)
        base-path (str (pref-utils/get-pref-file-base)
                    File/separator -system-folder-for-platforms-)]
    (when platforms
      (loop [ps (.getChildren platforms) pforms []]
        (if-let [p (first ps)]
            (let [pname (.getNameExt p)
                  dest (File. base-path pname)]
                (.mkdirs dest)
                (with-open [is (.getInputStream p)]
                    (let [class-paths (unzip-to is dest)]
                      (recur (rest ps) (conj pforms
                                         (struct platform pname
                                           class-paths 
                                           (= pname -default-platform-)
                                           (hash pname)))))))
          pforms)))))

(defn is-shipped-platform? [platform]
    (let [shipped-platforms-map
            (reduce (fn [m e]
                      (assoc m (:name e) e)) {} (get-defined-platforms))]
      (shipped-platforms-map (:name platform))))

(defn new-platform
  "This functions sets the key for the platform struct which should be used
as the identity of the platform."
  [n cp default]
  (struct platform n cp default 
    (str (UUID/randomUUID))))

(def
   #^{:doc "sequence of platforms"
      :prefs-category (str *ns*)}
   *clojure-platforms* (ref nil))

(defn validate-platforms
  "Checks for duplicate platform names, missing keys and makes sure there is only
1 default platform set"
  [platforms]
  (let [c (count platforms)]
  (cond
    (not= c
      (count (reduce (fn [s {n :name}]
                       (conj s n)) #{} platforms)))
    (throw (Exception. (str (format "[%d %d]" c
                              (count (reduce (fn [s {n :name}]
                                (conj s n)) #{} platforms))) "There are duplicate platform names. They must be unique"
             platforms)))
    (some (fn [{n :name}]
            (or (nil? n) (= "" n))) platforms)
;    (filter (fn [{n :name}]
;            (and n (not= "" n))) platforms)
        (throw (Exception. (str "Platform names cannot be blank."
                    platforms)))
    (not= c (count (filter :key platforms)))
            (throw (Exception. (str "Platform key cannot be nil."
                    platforms)))
    :else true)))

(set-validator! *clojure-platforms* validate-platforms)

(defn save-preferences []
  ; make sure the current platform is saved before flushing to disk.
  (logger/info "---------- Preferences being saved : count {} data {}"
        (count @*clojure-platforms*) @*clojure-platforms*)
    (pref-utils/put-prefs -prefs-category- 
(vec (filter (fn [{n :name}]
                    (and n (not= "" n)))
        @*clojure-platforms*))))
;      (sort -platform-name-comp-
;            @*clojure-platforms*)))

(defn next-new-platform-name
  "Given a seed name, return a unique platform name for adding a new platform"
  [seed-name]
  (loop [newname seed-name i 0]
    (if (some #(= (:name %) newname)
          @*clojure-platforms*)
      (recur (str seed-name (inc i)) (inc i))
      newname)))

(defn do-add-platform
  "creates a new platform object with a unique name and key and adds it to the
platforms list"
  []
  (let [new-platform
        (assoc (new-platform
                  (next-new-platform-name "New Clojure Platform") [] false)
          :index (count @*clojure-platforms*))
        {:keys [name]} new-platform]
      (dosync
        (alter *clojure-platforms*
            conj new-platform))
    (save-preferences)
        new-platform))

(defn ensure-default-platform-is-set
  "If there are no platforms marked as default, sets the first one to be the default."
  [platforms]
  (if (some :default platforms) platforms
    (apply vector
        (assoc (first platforms) :default true)
            (rest platforms))))

(defn platform-and-index
  "Given a seq of maps (platforms), return the index and the map that matches key"
  (
    ;{:pre [(not (nil? k))]}
    [platforms k]
  (loop [platforms platforms i 0]
    (when-first [p platforms]
      (if (= k (:key p))
        {:index i :platform p}
        (recur (rest platforms) (inc i))))))
  ([k] (platform-and-index @*clojure-platforms* k)))


(defn get-default-platform
  "Returns the default platform"
  []
  (ensure-default-platform-is-set @*clojure-platforms*)
  (first (filter :default @*clojure-platforms*)))


(defn do-remove-platform
  "Removes the given platform from the seq using the :key and saves the settings"
  [{k :key :as platform}]
  (dosync
    (let [{index :index p :platform} (platform-and-index
                           @*clojure-platforms*
                                (:key platform))
          ccount (count @*clojure-platforms* )]
      (logger/info "-----------do-remove-platform k={} i={} p={}" k index p)
      (when index
        (alter *clojure-platforms*
          #(let [[x xs] (split-at index %)]
             (logger/info "-----------split left {} right {}" x xs)
             (ensure-default-platform-is-set
               (apply vector (concat x (rest xs)))))))))
        (save-preferences))

(defn update-platform
  "Updates the given platform using the :key to look up the platform in the seq"
  [{k :key :as platform}]
  (logger/info "update-platform looking for key {} from {}" k platform)
  (let [{index :index p :platform}
        (platform-and-index @*clojure-platforms* k)]
  (logger/info "update-platform {} {}" (or index "nil!") p)
    (when (not= (:key platform) (:key p))
      (throw (Exception. (format "keys not= %s %s passed:%s found:%s"
                           k (:key p) platform p))))
  (when index
    (dosync
        (alter *clojure-platforms*
             #(let [[x xs] (split-at index %)]
                (ensure-default-platform-is-set
                    (apply vector 
                      (concat x [(merge p platform)] (rest xs)))))))
    (logger/info "update-platform: after trans {}" (@*clojure-platforms* index)))))


(defn update-default-platform
  "uses the passed platform map as the default.  Only one platform can have this"
  [{ck :key :as default}]
  (dosync
    (let [curr-default (get-default-platform)]
      (when (not= ck (:key curr-default))
        (update-platform (assoc default :default true))
        (update-platform (assoc curr-default :default false))))))

(def
   #^{:doc "hash-map for mapping keys to their associated ui-field-editor-maps"}
   *edit-map*
   (controls/build-settings-wrappers
    org.enclojure.ide.preferences.EnclojurePreferencesPanel
     (map #(apply controls/make-field-binding %)
       (partition 2
         [
        'platformList  []
        'platformNameTextField ""
        'setAsDefaultCheckBoxGuy false
        'errorLabel ""
        'classPathList []]))))

(defn get-selection-listener
  [func]
  (proxy [ListSelectionListener] []
    (valueChanged [event]
      (func this event))))

(defn- get-vec-from-list-model
  [model]
  (reduce #(conj %1 (.elementAt model %2))
        [] (range (.getSize model))))

(defn set-platform
  "This function takes a reference to the dialog and a single map
defining a clojure platform and populates the dialog"
  [dlg platform]
  (let [{:keys [name classpaths default]} platform]
    (controls/set-val *edit-map* dlg "platformNameTextField" name)
    (.setSelected (.setAsDefaultCheckBoxGuy dlg) (boolean default))
    (.setModel (.classPathList dlg)
        (controls/get-list-model classpaths))))

(defn get-platform
  "This function takes a reference to the dialog looks up the key to the platform
using the selectedIndex in the platforms list and returns a map with the key
and the data from the ui-fields"
  [dlg]
  (let [inx (.getSelectedIndex (.platformList dlg))
        selected-index (if (pos? inx) inx 0)
        pkey (if (> (count @*clojure-platforms*) selected-index)
               (:key (nth @*clojure-platforms* selected-index))
               (throw (Exception. (str "Unable to find platform key at index "
                        selected-index))))]
    (assoc
        (struct platform
            (.getText (.platformNameTextField dlg))
            (get-vec-from-list-model (controls/get-val *edit-map* dlg "classPathList"))
            (.isSelected (.setAsDefaultCheckBoxGuy dlg))
          pkey)
        :index (dec selected-index))))

(defn pop-dialog
  [dlg platforms def-edit]
  (let [def-platform (or (platform-and-index platforms def-edit)
                       {:index 0 :platform (first platforms)})]
    (logger/info "POP - platform and... {}" def-platform )
    (.setModel (.platformList dlg)
      (controls/get-list-model (map :name platforms)))
    (.setSelectedIndex (.platformList dlg) (:index def-platform))
    (set-platform dlg (:platform def-platform))))

(defn add-platform
  [pane action-event]
  (logger/info "add-platform event")
  (let [{:keys [key name]} (do-add-platform)]
    (logger/info "plaforms after add: count={} vec {}"
      (count  @*clojure-platforms* ) @*clojure-platforms*)
    (pop-dialog pane @*clojure-platforms* key)))

(defn remove-platform
  [pane action-event]
  (logger/info "remove-platform event")
    (if (= 1 (-> (.platformList pane) .getModel .getSize))
      (JOptionPane/showMessageDialog pane
        (NbBundle/getMessage EnclojurePreferencesPanel "Platform_settings_At_One_Platform_Msg")
        "Alert"
        JOptionPane/ERROR_MESSAGE)
      (let [selected (.getSelectedIndex (.platformList pane))
            to-remove (@*clojure-platforms* selected)]
        (if (is-shipped-platform? to-remove)
          (JOptionPane/showMessageDialog pane
            "This is one of the pre-packaged platforms and cannot be removed."
            "Alert"
            JOptionPane/PLAIN_MESSAGE)
            (when (= JOptionPane/YES_OPTION (JOptionPane/showConfirmDialog
                    pane "Are you sure you want to remove platform?"))
                (let [selected (.getSelectedIndex (.platformList pane))]
                  (do-remove-platform (@*clojure-platforms* selected))
                  (pop-dialog pane @*clojure-platforms*
                    (:key (@*clojure-platforms* (if (zero? selected) 1 (dec selected)))))
            (logger/info "remove-platform after do-remove count {}"
              (count @*clojure-platforms*))))))))

(defn- get-file-filter
  "return an implementation of a file filter using the predicate function for the accept call."
  [match-fn]
  (proxy [javax.swing.filechooser.FileFilter] []
    (accept [#^ java.io.File file]
      (boolean (match-fn file)))
    (getDescription [] ".jar files")))

(defn choose-classpaths [parent]
  (let [chooser (JFileChooser. #^String (.get (System/getProperties) "user.home"))]
    (.setMultiSelectionEnabled chooser true)
    (.setFileSelectionMode chooser JFileChooser/FILES_AND_DIRECTORIES)
    (.setApproveButtonText chooser "Select")
    (.setFileFilter chooser
      (get-file-filter
        #(or (.isDirectory %1)
               (re-find #".jar$" (.getName %1)))))
    (when (= JFileChooser/APPROVE_OPTION
          (.showOpenDialog chooser parent))
      (apply vector (map str (.getSelectedFiles chooser))))))

(defn addnew-classpath
  [pane action-event]
    (logger/info "add-cp")
  (let [nentries (choose-classpaths pane)
        curr-entries (get-vec-from-list-model
                       (controls/get-val *edit-map* pane "classPathList"))]
    (when nentries
      (controls/set-val *edit-map* pane "classPathList"
        (controls/get-list-model
          (apply vector (set (concat nentries curr-entries))))))))

(defn validate-classpaths
  "takes a sequence of classpaths and attempts to determine if clojure is present.
This is only doing a text search on the names...should do something more."
  [classpaths]
  (some #(.contains % "clojure") classpaths))

(defn validate-platform [{:keys [name classpaths]}]
    (cond (zero? (count classpaths)) "Must provide a classpath with a clojure system"
      (not (validate-classpaths classpaths)) "Does not appear to be a classpath with clojure?"))

(defn remove-classpath
  [pane action-event]
  (logger/info "rm-cp")
  (let [cpl (.classPathList pane)
        model (.getModel cpl)]
  (doseq [i (.getSelectedValues cpl)]
    (.removeElement model i))))

(defn platform-list-changed
  [pane #^ListSelectionEvent event]
  (let [s-inx (.getFirstIndex event)
        e-inx (.getLastIndex event)
        is-adjusting? (.getValueIsAdjusting event)
        selected (.getSelectedIndex (.platformList pane))]
  (logger/info "platform-list-changed s={} e={} adjusting?={} selected={}"
        s-inx e-inx is-adjusting? selected)
    (when (and (not= s-inx e-inx)
             (not is-adjusting?)
             (>= selected 0))
      (let [inx-to-update (if (= s-inx selected) e-inx s-inx)]
        ; possible that a deletion has occured.
        (when (> (count @*clojure-platforms*) inx-to-update)
          (let [platform (assoc
                       (get-platform pane)
                       :key (:key (@*clojure-platforms* inx-to-update)))]
            ; Make sure the current platform is still in the list...may be deleted.
            (when (some #(= (:name platform) (:name %)) @*clojure-platforms*)
                (logger/info "platform-list-changed...calling update for inx {} key={}"
                  inx-to-update (:key platform))
                (logger/info "platform-list-changed...platform={}" platform)
                (update-platform platform))))
            (let [newp  (@*clojure-platforms* selected)]
                 (logger/info "platform-list-changed...setting to {}" newp)
                (set-platform pane newp))
        ))))

(defn classpath-list-changed
  "Only enable the set as default checkbox if there are classpaths set"
  [pane event]
  (logger/info "classpath-list-changed: {}" 
    (get-vec-from-list-model (controls/get-val *edit-map* pane "classPathList")))
;  (logger/info "classpath-list-changed: name {} old {} new {}"
;    (.getPropertyName event)
;    (.getOldValue event)
;    (.getNewValue event))

  (.setEnabled (.removeClasspathButton pane)
    (pos? (count (.getSelectedValues (.classPathList pane)))))
  (let [platform (get-platform pane)]
      (logger/info "classpath-list-changed: got {}" platform)
   (if-let [msg (validate-platform platform)]
     (do
        (.setText (.errorLabel pane) (str "Platform invalid! " msg))
        (.setEnabled (.setAsDefaultCheckBoxGuy pane) false))
     (do
        (.setText (.errorLabel pane) "")
        (.setEnabled (.setAsDefaultCheckBoxGuy pane) true)))
  ; If it changed, update the platform and store it to disk...
  (update-platform platform)
  (save-preferences)))

(defn platform-key-typed
  [pane #^java.awt.event.KeyEvent event]
    (logger/info "key typed {}" event)
  (if (= -clojure-default-platform-name-
        (.getText (.platformNameTextField pane)))
    (.consume event)
    (do
        (.setElementAt (.getModel (.platformList pane))
          (str
        (.getText (.platformNameTextField pane))
        (.getKeyChar event))
      (.getSelectedIndex (.platformList pane)))
      (update-platform (get-platform pane))
      (save-preferences))))


(defn platform-changed
  [pane event]
  (logger/info "platform-changed old:{} new:{}" (.getOldValue event)
        (.getNewValue event)))

(defn set-as-default
  [pane event]
  (logger/info "set-as-default? {}" event)
  (when (.isSelected (.setAsDefaultCheckBoxGuy pane))
    (let [selected (.getSelectedIndex (.platformList pane))]
        (update-default-platform
          (assoc (get-platform pane)
            :key (:key (@*clojure-platforms* selected)))))))


;(defn load-preferences []
;    (let [c (pref-utils/get-prefs -prefs-category-)
;          new-init? (zero? (count c))
;          start-vals (if new-init?
;                       ;[(assoc (get-embedded-platform) :default true :index 0)]
;                       (get-defined-platforms)
;                       c)]
;      (dosync
;            (alter *clojure-platforms*
;                (fn [_] start-vals)))
;      (when new-init?
;          (save-preferences))))

(defn ensure-shipped-platforms
  [current-platforms]
  (let [shipped-platforms-map
            (reduce (fn [m e]
                      (assoc m (:name e) e)) {} (get-defined-platforms))
        current-platforms-map
            (reduce (fn [m e]
                      (assoc m (:name e) e)) {} current-platforms)
        missing (clojure.set/difference
                  (set (keys shipped-platforms-map))
                  (set (keys current-platforms-map)))]
    (vec (filter #(pos? (count (:name %)))
           (if (pos? (count missing))              
                (reduce (fn [v k]
                          (conj v (shipped-platforms-map k)))
                    current-platforms missing)
              current-platforms)))))

(defn load-preferences []
    (let [current-platforms
          (vec (filter (fn [{n :name}]
                    (and n (not= "" n)))
            (pref-utils/get-prefs -prefs-category-)))
          start-vals (ensure-shipped-platforms current-platforms)]
      (dosync
            (alter *clojure-platforms*
                (fn [_]
                  (ensure-default-platform-is-set start-vals))))
      (when (not= current-platforms start-vals)
        (logger/info "Added shipped platforms...saving them...")
        (save-preferences)))
  @*clojure-platforms*)

(defn get-clojure-default-lib []
  (load-preferences)
  (let [defp (get-default-platform)]
    (ensure-libs [defp])
    (proper-libname (:name defp))))

(defn load-settings
  [pane]
  (logger/info "calling load-settings for platforms ")
  (load-preferences)
  (pop-dialog pane @*clojure-platforms* (:key (get-default-platform))))

(defn save-settings
  [pane]
  (logger/info "calling save-settings for platforms {}" @*clojure-platforms*)
  (update-platform (get-platform pane))
  (save-preferences)
  (ensure-libs @*clojure-platforms*))

(def *events-map*
  {"removePlatformButtonActionPerformed" remove-platform
   "addPlatformButtonActionPerformed" add-platform
   "platformListValueChanged" platform-list-changed
   "addClasspathButtonActionPerformed" addnew-classpath
   "removeClasspathButtonActionPerformed" remove-classpath
   "classPathListValueChanged" classpath-list-changed
   "platformNameTextFieldKeyTyped" platform-key-typed
   "setAsDefaultCheckBoxGuyItemStateChanged" set-as-default
   "classPathListPropertyChange" classpath-list-changed
   "platformListPropertyChange" platform-changed
   })

(defn disp-hack
  [fnname & args]
  (when-let [func (*events-map* fnname)]
    (apply func args)))
