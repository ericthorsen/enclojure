(ns org.enclojure.commons.trace-test
 (:require [org.enclojure.commons.trace :as trace]))

(defn test-loadprops []
  (let [r (.findResource (.getContextClassLoader (Thread/currentThread)) "org/enclojure/commons/log4j_test.properties")]  
    (trace/configure r)))

(defn test-log [msg]
  (trace/log (str "test default log [" msg "]"))
  (trace/log org.apache.log4j.Level/DEBUG (str "test DEBUG log [" msg "]"))
  (trace/log org.apache.log4j.Level/FATAL (str "test FATAL log [" msg "]"))
  (trace/log org.apache.log4j.Level/INFO (str "test INFO log [" msg "]"))
  (trace/log org.apache.log4j.Level/TRACE (str "test TRACE log [" msg "]"))
  (trace/log org.apache.log4j.Level/WARN (str "test WARN log [" msg "]"))
  (trace/log org.apache.log4j.Level/ALL (str "test ALL log [" msg "]"))
  (trace/log org.apache.log4j.Level/OFF (str "test OFF log [" msg "]"))
  (trace/log org.apache.log4j.Level/ERROR (str "test ERROR log [" msg "]")))

(def -test-warn-props- (array-map
      "log4j.rootLogger" "WARN, stdout"
      "log4j.appender.stdout" "org.apache.log4j.ConsoleAppender"
      "log4j.appender.stdout.layout" "org.apache.log4j.PatternLayout"
      "log4j.appender.stdout.layout.ConversionPattern" "%d %r [%t] %5p %c - %m%n"))

(defn test-setprops []
  (trace/set-props -test-warn-props-))

(defn test-all []
  (test-loadprops)
  (test-log "properties file set rooLogger to ALL")
  (test-setprops)
  (test-log "manual rootLogger set to WARN"))