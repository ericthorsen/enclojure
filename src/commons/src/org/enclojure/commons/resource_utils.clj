(comment
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Eric Thorsen, Frank Failla
)

(ns org.enclojure.commons.resource-utils)

(defn load-resource
  "Load a resource from the current threads clas loader"
  [r]
  (.findResource (.getContextClassLoader (Thread/currentThread)) r))

(defn file-from-resource
  "Returns the path of a resource."
  [r]
  (when-let [rs (load-resource r)]
    (.getPath rs)))


