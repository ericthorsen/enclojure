(comment
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Frank Failla
)
(ns org.enclojure.commons.trace
   (:import (org.apache.log4j Logger LogManager PropertyConfigurator Level)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def *context*)

(defmacro with-context [ctx & body]
  `(if org.enclojure.commons.trace/*context*
     (binding [org.enclojure.commons.trace/*context*
               (conj org.enclojure.commons.trace/*context* {:context ~ctx :body '~body})]       
       ~@body)
     ~@body))

(defmacro defnc [name & body]
  `(let [f# (fn ~@body)]
     (defn ~name [& args#]
       (if org.enclojure.commons.trace/*context*
         (let [m# (meta (var ~name))]
           (binding [org.enclojure.commons.trace/*context*
                     (conj org.enclojure.commons.trace/*context*
                       (assoc m# :ns (.getName (:ns m#))) {:args args# :body '~body})]
             (try               
               (apply f# args#)
               (catch Throwable e#
                 (throw (Exception. (pr-str org.enclojure.commons.trace/*context*) e#))))))
         (apply f# args#)))))

(defn read-context [e]
  (read-string (.getMessage e)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; Levels
;ALL - The ALL has the lowest possible rank and is intended to turn on all logging.
;DEBUG - The DEBUG Level designates fine-grained informational events that are most useful to debug an application.
;ERROR - The ERROR level designates error events that might still allow the application to continue running.
;FATAL - The FATAL level designates very severe error events that will presumably lead the application to abort.
;INFO - The INFO level designates informational messages that highlight the progress of the application at coarse-grained level.
;OFF - The OFF has the highest possible rank and is intended to turn off logging.
;TRACE - The TRACE Level designates finer-grained informational events than the DEBUG
;WARN - The WARN level designates potentially harmful situations.

;Logger/getLogger - Retrieve a logger named according to the value of the name parameter.
;  If the named logger already exists, then the existing instance will be returned.
;  Otherwise, a new instance is created.
(defmacro get-logger []
  (let [nsym# (str (ns-name *ns*))]
    `(org.apache.log4j.Logger/getLogger ~nsym#)))

;define the log function
(defmacro log
 ([msg]
    `(log org.apache.log4j.Level/INFO ~msg))
  ([lvl msg]
    (let [nsym# (str (ns-name *ns*))]
       `(if (.isEnabledFor (org.apache.log4j.Logger/getLogger ~nsym#) ~lvl)
         (.log (org.apache.log4j.Logger/getLogger ~nsym#) ~lvl ~msg)))))

(defn get-all-exceptions [exc]
  (loop [e exc acc []]
    (if e
      (recur (.getCause e) (conj acc e))
      acc)))

(defn publish [exc]
  (let [msg (reduce str (map (fn [e] (str (.getMessage e) "\n")) (get-all-exceptions exc)))]
    (log Level/ERROR msg)))

;The exact default initialization algorithm is defined as follows:
;
;   1. Setting the log4j.defaultInitOverride system property to any other value 
;      then "false" will cause log4j to skip the default initialization procedure
;      (this procedure).
;   2. Set the resource string variable to the value of the log4j.configuration 
;      system property. The preferred way to specify the default initialization
;      file is through the log4j.configuration system property. In case the
;      system property log4j.configuration is not defined, then set the string
;      variable resource to its default value "log4j.properties".
;   3. Attempt to convert the resource variable to a URL.
;   4. If the resource variable cannot be converted to a URL, for example due to
;      a MalformedURLException, then search for the resource from the classpath
;      by calling org.apache.log4j.helpers.Loader.getResource(resource,
;      Logger.class) which returns a URL. Note that the string "log4j.properties"
;      constitutes a malformed URL. See Loader.getResource(java.lang.String) for
;      the list of searched locations.
;   5. If no URL could not be found, abort default initialization. Otherwise,
;      configure log4j from the URL. The PropertyConfigurator will be used to
;      parse the URL to configure log4j unless the URL ends with the ".xml"
;      extension, in which case the DOMConfigurator will be used. You can
;      optionaly specify a custom configurator. The value of the
;      log4j.configuratorClass system property is taken as the fully qualified
;      class name of your custom configurator. The custom configurator you
;      specify must implement the Configurator interface.


; PatternLayout
;c - Used to output the category of the logging event.
;C - Used to output the fully qualified class name of the caller issuing the logging request.
;d - Used to output the date of the logging event.
;F - Used to output the file name where the logging request was issued.
;l - Used to output location information of the caller which generated the logging event.
;L - Used to output the line number from where the logging request was issued.
;m - Used to output the application supplied message associated with the logging event.
;M - Used to output the method name where the logging request was issued.
;n - Outputs the platform dependent line separator character or characters.
;p - Used to output the priority of the logging event.
;r - Used to output the number of milliseconds elapsed from the construction of the layout until the creation of the logging event.
;t - Used to output the name of the thread that generated the logging event.
;x - Used to output the NDC (nested diagnostic context) associated with the thread that generated the logging event.
;X - Used to output the MDC (mapped diagnostic context) associated with the thread that generated the logging event.

(def -default-logprops- (array-map
      "log4j.rootCategory" "ALL, stdout"
      "log4j.appender.stdout" "org.apache.log4j.ConsoleAppender"
      "log4j.appender.stdout.layout" "org.apache.log4j.PatternLayout"
      "log4j.appender.stdout.layout.ConversionPattern" "%d %r [%t] %5p %c - %m%n"))

(def -rolling-logprops- (array-map
                         "log4j.rootCategory" "ALL, rolling"
                         "log4j.appender.rolling" "org.apache.log4j.RollingFileAppender"
                         "log4j.appender.rolling.File" "clojure-rolling.log"
                         "log4j.appender.rolling.MaxFileSize" "1024KB"
                         "log4j.appender.rolling.MaxBackupIndex" "10"
                         "log4j.appender.rolling.layout" " org.apache.log4j.PatternLayout"
                         "log4j.appender.rolling.layout.ConversionPattern" "%d %r [%t] %5p %c - %m%n"))

(def -package-rolling-logprops- (array-map
                         "log4j.rootCategory" "OFF"
;                         "log4j.category.<<PACKAGE>>" "ALL, rolling"
                         "log4j.appender.rolling" "org.apache.log4j.RollingFileAppender"
                         "log4j.appender.rolling.File" "clojure-rolling.log"
                         "log4j.appender.rolling.MaxFileSize" "1024KB"
                         "log4j.appender.rolling.MaxBackupIndex" "10"
                         "log4j.appender.rolling.layout" " org.apache.log4j.PatternLayout"
                         "log4j.appender.rolling.layout.ConversionPattern" "%d %r [%t] %5p %c - %m%n"))

(defn reset-props [props]
  (LogManager/resetConfiguration))

(defn set-props [props]
  (let [p (java.util.Properties.)]
    (doseq [[k v] props]
      (.put p k v))
    (PropertyConfigurator/configure p)
    p))

;configure the logger via a properties file or resource
(defn configure [file]
  (PropertyConfigurator/configure file))

(defn set-default-props []
  (set-props -default-logprops-))

(defn set-rolling-props [filename]
  (set-props (assoc -rolling-logprops- "log4j.appender.rolling.File" filename)))

(defmacro set-package-props [filename]
  (let [nsym# (str (ns-name *ns*))]
    `(set-props (assoc org.enclojure.commons.trace/-package-rolling-logprops-
                  "log4j.appender.rolling.File" ~filename
                  ~(str "log4j.category." nsym#) "ALL, rolling"))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
