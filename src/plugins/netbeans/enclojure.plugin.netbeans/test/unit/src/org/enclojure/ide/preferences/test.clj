(ns org.enclojure.ide.preferences.test
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:require [org.enclojure.ide.preferences.enclojure-options-category :as enclojure-options-category]
    [org.enclojure.ide.preferences.platform-options :as platform-options]
    )
  (:import (org.enclojure.ide.preferences  EnclojurePreferencesPanel EnclojureOptionsCategory)
    (org.uispec4j UISpecAdapter UISpecTestCase Window TabGroup)
    (javax.swing JFrame)
    (java.awt Component)
    ))

; setup logging
(logger/ensure-logger)

(def *options-swing* (atom nil))

(defn setup-test []
  (let [cat (EnclojureOptionsCategory.)
        controller (.create cat)
        option-pane (.getComponent controller nil)
        frame (JFrame.)]
    (.add frame option-pane)
    {:category cat :controller controller
     :option-pane option-pane
     :panel (org.uispec4j.Panel. option-pane)
     :tabbed-group (TabGroup. (.jTabbedPane1 option-pane))
     :repl-settings-pane (org.uispec4j.Panel.
                      (.getComponentAt (.jTabbedPane1 option-pane) 0))
     :platforms-pane (org.uispec4j.Panel.
                      (.getComponentAt (.jTabbedPane1 option-pane) 1))}))

(defn get-adaptor
  []
  (swap! *options-swing* (fn [_] (setup-test)))
  (proxy [UISpecAdapter] []
    (getMainWindow [] (:window @*options-swing*))))

(defn spec-test []
  (let [#^org.uispec4j.UISpecAdapter adaptor (get-adaptor)]
  (proxy [UISpecTestCase] []
    (setUp [] (.setAdapter this #^org.uispec4j.UISpecAdapter adaptor)))))


; save-preferences

; load-preferences

; Add-platform
    ; A unique item is added to the end of the platformList box and is the selected item.

; Remove-platform
    ; The currently selected platform is removed from the platformList box
    ; The removed platform is no longer part of the saved preferences
    ; The same selected index is highlighted in the platformList box
    ; The classpath list is updated with the newly selected platform
    ; The platform name text box is populated with the new selected platform.

