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
(ns org.enclojure.ide.preferences.enclojure-options-category
  (:require
    [org.enclojure.ide.settings.utils :as pref-utils]
    [org.enclojure.ide.nb.editor.utils :as utils]
    [org.enclojure.ui.controls :as controls]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.preferences.platform-options :as platform-options]
    )
(:import (javax.swing Icon ImageIcon)
  (java.util.logging Level)
  (org.netbeans.spi.options OptionsCategory OptionsPanelController)
  (org.openide.util NbBundle Utilities)
  (java.beans PropertyChangeSupport PropertyChangeListener)
  (org.enclojure.ide.preferences EnclojureOptionsCategory)
  (org.enclojure.ide.preferences EnclojurePreferencesPanel)
  (javax.swing JLabel JFileChooser ListCellRenderer
    DefaultListCellRenderer JComboBox DefaultComboBoxModel)
  (javax.swing.filechooser FileFilter FileView)
  (javax.swing.event ListSelectionListener)
  (java.beans PropertyEditor PropertyEditorManager)
  ))

; setup logging
(logger/ensure-logger)

(def -settings-loaded?- (atom false))

(def
   #^{:doc "default binding for the current enclojure preferences"
      :prefs-category (str *ns*)}
   *repl-settings* (ref nil))

(def #^{:private true} -prefs-category- "repl-settings")

(def
   #^{:doc "hash-map for mapping keys to their associated ui-field-editor-maps"}
   *edit-map*  
   (controls/build-settings-wrappers
    org.enclojure.ide.preferences.EnclojurePreferencesPanel
     (map #(apply controls/make-field-binding %)
       (partition 3
         [
        'jvmAdditionArgsTextField "-Xms512m -Xmx512m" :jvm-additional-args
       ; 'printBaseTextField  10   'clojure.contrib.pprint/*print-base*
        'printCircleCheckBox false 'clojure.contrib.pprint/*print-circle*
        'printLengthTextField  40 'clojure.core/*print-length*
        'printLevelTextField  4 'clojure.core/*print-level*
        'printLinesTextField 25 'clojure.contrib.pprint/*print-lines*
        'printMiserWidthTextField 40 'clojure.contrib.pprint/*print-miser-width*
        ;'printRadixCheckBox false 'clojure.contrib.pprint/*print-radix*
        'printRightMarginTextField  78 'clojure.contrib.pprint/*print-right-margin*
        ';printSharedCheckBox false 'clojure.contrib.pprint/*print-shared*
        'printStackTraceCheckBox false 'org.enclojure.repl.main/*print-stack-trace-on-error*
        'printSuppressNamespaceCheckBox  false 'clojure.contrib.pprint/*print-suppress-namespaces*
        'usePrettyPrintCheckBox  true 'clojure.contrib.pprint/*print-pretty*
        'warnOnReflectionCheckBox  false 'clojure.core/*warn-on-reflection*
        'includeAllClasspathsCheckBox  true :include-all-project-classpaths
        'enableLogging  true :enable-logging
        'enableIDEReplCheckBox  false :enable-ide-repl
         ]
       ))))
    
(defn save-preferences []
    (pref-utils/put-prefs -prefs-category-
      @*repl-settings*))

(defn fresh-init
  []
  (let [platform-prefs (platform-options/load-preferences)]
        (assoc
          (controls/get-default-settings *edit-map*)
          :stand-alone-repl-platform
          (:key (first (filter :default @platform-options/*clojure-platforms*))))))

(defn load-preferences []
    (let [c (pref-utils/get-prefs -prefs-category-)
          new-init? (zero? (count c))
          start-vals (if new-init? (fresh-init) c)]
          (dosync
            (alter *repl-settings*
                (fn [_] start-vals)))
      (when new-init?
          (save-preferences))
      @*repl-settings*))

(defn set-standalone-platform [dlg prefs]
  (when-let [platform-key (prefs :stand-alone-repl-platform)]
    (when-let [{:keys [index platform]}
               (platform-options/platform-and-index platform-key)]
      (if (> (-> (.clojurePlatformsComboBox dlg) .getModel .getSize) index)
        (.setSelectedIndex (.clojurePlatformsComboBox dlg) index)))))

(defn get-standalone-platform [dlg]
  (let [selected (.getSelectedIndex (.clojurePlatformsComboBox dlg))]
    (when (and (>= selected 0)
            (>= (count @platform-options/*clojure-platforms*) selected))
      (:key (@platform-options/*clojure-platforms* selected)))))      

(defn load-settings [dlg]
  (platform-options/load-settings dlg)
  (let [prefs (load-preferences)]
    (controls/populate-settings *edit-map* dlg prefs)
    (set-standalone-platform dlg prefs)
    (swap! -settings-loaded?- (fn [_] true))))
              
(defn save-settings
  [dlg]
  (platform-options/save-settings dlg)
  (dosync
    (alter *repl-settings*
      (fn [_]
        (assoc
          (controls/pull-settings *edit-map* dlg)
            :stand-alone-repl-platform
            (get-standalone-platform dlg)))))
   (save-preferences))
   
(defn changed [changed pcs] 
  (sync nil
    (when @changed
      (alter changed (fn [_] false))
      (.firePropertyChange pcs (. OptionsPanelController PROP_CHANGED) false  true)))
  (.firePropertyChange pcs (. OptionsPanelController PROP_VALID) nil nil))

(defn map-cell-renderer
  [key-to-show]
  (proxy [DefaultListCellRenderer][]
    (getListCellRendererComponent
         [jist value index isSelected cellHasFocus]      
      (proxy-super getListCellRendererComponent
        jist value index isSelected cellHasFocus)
      (.setText this (key-to-show value))
      this)))

(defn update-plaforms-combobox
  [pane]
  (when (pos? (count @platform-options/*clojure-platforms*))
    (let [dm (DefaultComboBoxModel.)]
      (doseq [i @platform-options/*clojure-platforms*]
        (.addElement dm i))
      (.setModel (.clojurePlatformsComboBox pane) dm))))

(defn platform-list-listener
  [pane]
  (proxy [ListSelectionListener][]
    (valueChanged [event]
      (logger/info "list-listener.........")
      (update-plaforms-combobox pane))))

(defn create-enclojure-preferences-pane
  [this]
  (let [p (org.enclojure.ide.preferences.EnclojurePreferencesPanel/create
            #^org.netbeans.spi.options.OptionsPanelController this)]
    (.setRenderer (.clojurePlatformsComboBox p) (map-cell-renderer :name))
    (.addListSelectionListener (.platformList p) (platform-list-listener p))
    p))

(defn lazy-init-panel [panel pcs]
  (fn [this]
    (when-not @panel
      (sync nil
        (alter panel
          (fn[_]
            (create-enclojure-preferences-pane this)))
        (alter pcs (fn [_](new PropertyChangeSupport this)))))
    @panel))

(defn get-options-controller []
    (let [panel (ref nil)
          pcs (ref nil)
          changed (ref false)
          get-panel (lazy-init-panel panel pcs)]
  (proxy [org.netbeans.spi.options.OptionsPanelController] []
      (addPropertyChangeListener [#^java.beans.PropertyChangeListener l]
        (. @pcs addPropertyChangeListener l))
    (applyChanges []
      (logger/info "calling save-settings!!")
          (save-settings (get-panel this)))
    (cancel [])
    (createAdvanced [_])
    (getComponent [masterLookup]
        (get-panel this))
    (getHelpCtx [])
    (isValid [] true)
    (isChanged [] true)
    (removePropertyChangeListener [#^java.beans.PropertyChangeListener l]
        (. @pcs removeProperyChangeListener l))
    (update []
          (load-settings (get-panel this))))))

(defn get-stand-alone-settings
  []
  (platform-options/load-preferences)
  (let [prefs (load-preferences)
        platform (platform-options/platform-and-index
                   (:stand-alone-repl-platform prefs))]
    (merge platform (select-keys prefs [:jvm-additional-args
                                        :include-all-project-classpaths]))))
(defn tabbed-panel-changed
  [pane list-value-changed-event]
  ; If we are moving off the platform pane, make sure we are saving it...
  (when @-settings-loaded?-
    (save-settings pane)
    (update-plaforms-combobox pane))
  (logger/info "tabbed state-changed " list-value-changed-event))

(defn test-standalone
  []
  (let [frame (javax.swing.JFrame.)
        options (get-options-controller)
        pane (.getComponent options nil)]
    (doto frame
    (.add pane)
    (.setSize (java.awt.Dimension. 800 500))
    (.setVisible true))
    {:frame frame :options options :pane pane}))
  