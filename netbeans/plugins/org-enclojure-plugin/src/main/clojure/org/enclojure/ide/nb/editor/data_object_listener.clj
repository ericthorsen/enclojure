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

(ns org.enclojure.ide.nb.editor.data-object-listener
  (:use org.enclojure.ide.ClojureLexer)
  (:require [org.enclojure.ide.nb.actions.token-navigator :as token-navigator]
            [org.enclojure.ide.navigator.token-nav :as token-nav]
            [org.enclojure.ide.nb.editor.completion.file-mapping :as file-mapping]
            clojure.inspector clojure.set
            [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
            [org.enclojure.commons.c-slf4j :as logger])
  (:import
    (java.util.logging Level)
    (java.io FileReader File)
    (java.beans PropertyChangeEvent PropertyChangeListener)
    (org.openide.loaders MultiDataObject)
    (org.openide.filesystems FileObject FileUtil)))

; setup logging
(logger/ensure-logger)

(defn update-nav-data
  [data-object]
      (let [f (-> data-object .getPrimaryEntry .getFile)]
        (logger/info "data-object-listener updating file " f)
        (try
	  (file-mapping/refresh-completion-cache-data  (FileUtil/toFile f))
        (logger/info "data-object-listener completion info refreshed " f)
          ; This needs to be called explicitly here only the first time.
      ; On first load the navigator window gets called before the symbols are loaded
	  (symbol-caching/get-nav-data-for (FileUtil/toFile f))
                 (logger/info "data-object-listener symbol caching updated " f)
          (catch Throwable t
            (logger/error "Exception refreshing completion cache for file {}, error {}"
			  (str f) (.getMessage t))))))

(defmulti data-obj-event 
  (fn [event data-obj]
    (:propertyName event)))

(defmethod data-obj-event :default
   [{:keys [oldValue newValue]} data-object])

(defmethod data-obj-event "modified"
  [{:keys [oldValue newValue]} data-object]
  (when (and (= oldValue true) (= newValue false))
    (update-nav-data data-object)))

(defmethod data-obj-event "cookie"
  [{:keys [oldValue newValue]} data-object]
    (update-nav-data data-object))

(defn get-property-listener [obj]
  (proxy [PropertyChangeListener] []
    (propertyChange [#^PropertyChangeEvent e]
      (logger/info "data-object-listener {}" (bean e))
      (data-obj-event (bean e) obj))))
