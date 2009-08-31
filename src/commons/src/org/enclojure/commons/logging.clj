(comment
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Frank Failla, Eric Thorsen
)

; this file is going ot be deprecated - new funcs are in trace.clj
 
(ns org.enclojure.commons.logging
  (:gen-class))

(defn log-to-fn [ns]
  (let [logger (java.util.logging.Logger/getLogger ns)]
       (fn ([lvl & msgs]
             (let [level-not-passed? (string? lvl)
                   level (if level-not-passed? java.util.logging.Level/INFO lvl)
                   msg-vec (if level-not-passed? (concat [lvl] msgs) msgs)]
             (.log logger level (apply str msg-vec)))))))

(defmacro get-ns-logfn
  "Called in a file to iniialize a log function with a Logger bound to the namespace
    of the called file"
  []
  (let [nsym# (str (ns-name *ns*))]
    `(org.enclojure.commons.logging/log-to-fn ~nsym#)))


;get a list of exceptions associated with exc
(defn get-exc-seq [exc]
  (loop [e exc acc []]
    (if e
      (recur (. e (getCause)) (conj acc e))
      acc)))

;concatenate the result func for each and its causes
(defn get-exc-info [exc func]
  (reduce str (map (fn [e]
		       (str (func e) "\n"))
		   (get-exc-seq exc))))

;get a formatted message for exc
(defn get-exc-msg [exc]
  (get-exc-info exc (memfn getMessage)))


(defn publish [logfn exc]
  (logfn java.util.logging.Level/SEVERE (get-exc-msg exc)))

(defmacro with-errorlogging [& body]
  (let [exc (gensym)]
    `(try
      ~@body
      (catch Exception ~exc
	     (publish ~exc)
	     (throw ~exc)))))

