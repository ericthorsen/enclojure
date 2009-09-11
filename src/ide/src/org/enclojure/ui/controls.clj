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
(ns org.enclojure.ui.controls
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (javax.swing Icon ImageIcon DefaultListModel)
    (java.util.logging Level)
    (java.awt Toolkit)
    (java.util Date)
    (java.text DateFormat)
    ))

; setup logging
(logger/ensure-logger)

(defn center-component [c]
    (let [tk (Toolkit/getDefaultToolkit)
          screen-size (.getScreenSize tk)
          screenHeight  (.height screen-size)
          screenWidth   (.width screen-size)]
      (doto c
        (.setSize (/ screenWidth 2) (/ screenHeight  2))
        (.setLocation (/ screenWidth 4) (/ screenHeight 4)))))

(defn get-list-model [col]
  (let [lm (DefaultListModel.)]
    (doseq [i col]
      (.addElement lm i)) lm))

(defn to-int [v]
   "Helper to convert an argument to an int"
  (Integer/parseInt (str v)))

(defn to-bool [v]
   "helper to convert an argument to a boolean"
  (Boolean/parseBoolean (str v)))

(defstruct
   ui-field-editor-map :get :set :default-value :conversion-func)

(defmacro j-text-field [field-name default-value]
   "Macro to setup a simple data structure for :get :set :default-value
    and :conversion-func bound to a java variable 'field-name with 'default-value.
    All the functions expect the java object as the 1st argument to the functions."
  `(struct ui-field-editor-map
      (fn [p#] (.. p# ~field-name (getText)))
      (fn [p1# p2#] (.. p1# ~field-name (setText p2#)))
      ~default-value
      str))

(defmacro j-checkbox [field-name default-value]
  `(struct ui-field-editor-map
      (fn [p#] (.. p# ~field-name (isSelected)))
      (fn [p1# p2#] (.. p1# ~field-name (setSelected p2#)))
      ~default-value
      ~'to-bool))

(defmacro j-spinner [field-name default-value]
  `(struct ui-field-editor-map
     (fn [p#] (.. p# ~field-name (getModel) (getNumber) (intValue)))
     (fn [p1# p2#] (.. p1# ~field-name (getModel)
                         (setValue p2#)))
    ~default-value
    ~'to-int))

(defmacro j-list [field-name default-value]
   "Macro to setup a simple data structure for :get :set :default-value
    and :conversion-func bound to a java variable 'field-name with 'default-value.
    All the functions expect the java object as the 1st argument to the functions."
  `(struct ui-field-editor-map
      (fn [p#] (.. p# ~field-name (getModel)))
      (fn [p1# p2#] (.. p1# ~field-name (setModel p2#)))
      ~default-value
      get-list-model))

(defmulti convert-fn (fn [from-val to-type]
                           (let [from (class from-val)]
                             (cond (= from to-type) :default
                               (= java.lang.String to-type) [:any String]
                               :else [from to-type]))))

(defmethod convert-fn :default
  [from-val to-type] from-val)

(defmethod convert-fn [:any String]
  [from-val to-type] (str from-val))

(defmethod convert-fn [String Integer]
  [from-val to-type] 
    (Integer/parseInt from-val))

(defmethod convert-fn [String Date]
  [from-val to-type]
    (.parse
       (DateFormat/getInstance) #^String from-val))

(defmulti -get-val (fn [dlg fld & args] (.getType fld)))
(defmethod -get-val :default [& args])

(defmethod -get-val javax.swing.text.JTextComponent
  ([dlg fld def-val]
    (convert-fn (.getText (.get fld dlg))
      (class def-val)))
  ([dlg fld]
    (.getText (.get fld dlg))))

(defmethod -get-val javax.swing.JCheckBox
  ([dlg fld def-val]
    (convert-fn (.isSelected (.get fld dlg))
      (class def-val)))
  ([dlg fld]
    (.isSelected (.get fld dlg))))

(defmethod -get-val javax.swing.JList
  ([dlg fld & args]
    (.getModel (.get fld dlg))))

(defmulti -set-val (fn [dlg fld val] (.getType fld)))
(defmethod -set-val :default [& args])

(defmethod -set-val javax.swing.text.JTextComponent
  [dlg fld val]
  (.setText (.get fld dlg) 
    (convert-fn val String)))

(defmethod -set-val javax.swing.JCheckBox
  [dlg fld val]
  (logger/info "set-val javax.swing.JCheckBox {} {}" val fld)
  (.setSelected (.get fld dlg)
    (convert-fn val Boolean)))

(defmethod -set-val javax.swing.JList
  [dlg fld val]
  (.setModel (.get fld dlg) val))

(defn get-val
  [{ui-map :ui-map :as edit-map} dlg ui-field-name]
  (-get-val dlg (:field (ui-map ui-field-name))))

(defn set-val
  [{ui-map :ui-map :as edit-map} dlg ui-field-name val]
  (-set-val dlg (:field (ui-map ui-field-name)) val))

(defstruct field-binding :ui-name :def-val :name)

(defn make-field-binding
  ([ui-name def-val name]
    (struct field-binding ui-name def-val name))
  ([ui-name def-val]
    (make-field-binding ui-name def-val ui-name)))

(defn build-settings-wrappers
  "Given a java class and a list of ui-field-name ,default-value and data name
(see struct field binding), build a map of <ui-field-name> to a map of set/get/defval...
and a map of <data-field> to attribute.  This will enable simple getter/setter
via reflection and also plugs in easily to the preferences/utils stuff"
  [cls fields]
  (let [fnames (reduce (fn [m {:keys [ui-name] :as spec}]
                         (assoc  m (str ui-name) spec)) {} fields)
        ; Only include specs that I have matching field names for.
        ; I should probably return the missing fields or consider this an exception?
        flds (filter #(contains? fnames (.getName %1)) (:fields (bean cls)))]
    ;(logger/info " sd reducer " fnames)
    ;(logger/info " ddd flds " (count flds))
    (let [[ui-map data-map]
        (reduce (fn [[ui-map data-map] f]
                  (let [k (.getName f)
                        {:keys [ui-name name def-val] :as v} (fnames k)
                        fmap (merge v
                                {:field f
                                :get #(-get-val %1 f def-val)
                                :set #(-set-val %1 f %2)})]
                    [(assoc ui-map (str ui-name) fmap)
                     (assoc data-map name fmap)])) [{} {}] flds)]
      {:ui-map ui-map :data-map data-map})))

(defn populate-settings
  "Given an <edit-map> (built from build-settings-wrappers) set all the fields in
the <dialog> using the passed in data.  Missing fields in the data map will get
set to the default values in the edit-map"
  [{:keys [ui-map data-map] :as edit-map} dialog data]
    (doseq [entry data]
      (let [[k v] entry
            {:keys [get set def-val] :as field-map} (data-map k)]
        ; There may be settings in there that I do not know what to do with?
        (when field-map
            (set dialog (if (nil? v) def-val v))))))

(defn pull-settings
  "Given an <edit-map> (built from build-settings-wrappers) pull all the fields data
in the <dialog>. Returns a map of data using the names defined in the edit-map"
  [{:keys [ui-map data-map] :as edit-map} dialog]
  (reduce (fn [m [k {:keys [set get name]}]]
            (assoc m name (get dialog))) {} ui-map))

(defn get-default-settings [{:keys [ui-map data-map] :as edit-map}]
  (apply hash-map
    (reduce (fn [v [k {def-val :def-val}]]
              (conj v k def-val)) [] data-map)))