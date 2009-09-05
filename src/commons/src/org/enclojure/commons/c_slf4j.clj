(comment
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Eric Thorsen
)
(ns
   #^{:author "Eric Thorsen",
       :doc "Logging macros around slf4j.
These macros attempt to put as little additional scafolding as possible on the
sslf4j lib.  It all cases the calls should be the same as direct calls to the
slf4j lib convenience functions."}
 org.enclojure.commons.c-slf4j
 (:import (org.slf4j Logger LoggerFactory))
 )

(defmacro ensure-logger []
   (let [nsym# (str (ns-name *ns*))]
    `(~@(list 'defonce (with-meta '--logger-- {:private true}))
           (org.slf4j.LoggerFactory/getLogger ~nsym#))))

(defmacro log
  "macro to call log passing in a level.  Users should use the convenience macros:
debug, error, trace, warn and info.
NOTE: the def-logging-fn must be called in your file in order to use these macros.
It creates a logger using the namespace bound to *ns* at compile time."
  ([level msg]    
    `(~@(list '. '--logger--) (~level #^String ~msg)))
  ([level fmt obj]    
    `(~@(list '. '--logger--) (~level #^String ~fmt ~obj)))
  ([level fmt obj1 obj2]    
    `(~@(list '. '--logger--) (~level #^String ~fmt ~obj1 ~obj2)))
  ([level fmt obj1 obj2 & objs]    
    `(~@(list '. '--logger--)
      (~level #^String ~fmt ~obj1 ~obj2))))

(defmacro log-throwable
  ([level msg throwable]
    (ensure-logger)
    `(~@(list '. '--logger--)
       (~level #^String ~msg #^Throwable ~throwable))))

(defmacro make-level-macros
  [level]
  (let [t# (symbol (str level "-throwable"))]
      `(do         
         (defmacro ~level
             ([msg#]
                (let [s# (quote ~level)]
                    `(log ~s# ~msg#)))
             ([msg# obj#]
                (let [s# (quote ~level)]
                    `(log ~s# ~msg# ~obj#)))
             ([msg# obj1# obj2#]
                (let [s# (quote ~level)]
                    `(log ~s# ~msg# ~obj1# ~obj2#)))
             ([msg# obj1# obj2# & objs#]
                    (let [s# (quote ~level)]
                    `(log ~s# ~msg# ~obj1# ~obj2# ~objs#))))
        (defmacro ~t#
             ([msg# throwable#]
               (let [s# (quote ~level)]
                 `(log-throwable ~s# ~msg# ~throwable#)))))))


(make-level-macros debug)
(make-level-macros error)
(make-level-macros trace)
(make-level-macros warn)
(make-level-macros info)
;
;(defmacro log
; "Logs a message, either directly or via an agent. Also see the level-specific
; convenience macros."
; ([level message]
;   `(log ~level ~message nil))
; ([level message throwable]
;   `(log ~level ~message ~throwable ~(str *ns*)))
; ([level message throwable log-ns]
;   `(let [log# (impl-get-log ~log-ns)]
;     (if (impl-enabled? log# ~level)
;       (if (and @*allow-direct-logging*
;                (not (clojure.lang.LockingTransaction/isRunning)))
;         (impl-write! log# ~level ~message ~throwable)
;         (send-off *logging-agent*
;           agent-write! log# ~level ~message ~throwable))))))
;
;
;(defmacro enabled?
; "Returns true if the specific logging level is enabled.  Use of this function
; should only be necessary if one needs to execute alternate code paths beyond
; whether the log should be written to."
; ([level]
;   `(enabled? ~level ~(str *ns*)))
; ([level log-ns]
;   `(impl-enabled? (impl-get-log ~log-ns) ~level)))
;
;
;(defmacro spy
; "Evaluates expr and outputs the form and its result to the debug log; returns
; the result of expr."
; [expr]
; `(let [a# ~expr] (log :debug (str '~expr " => " a#)) a#))
;
;
;(defn log-stream
; "Creates a PrintStream that will output to the log. End-users should not need
; to invoke this function."
; [level log-ns]
; (java.io.PrintStream.
;   (proxy [java.io.ByteArrayOutputStream] []
;     (flush []
;       (proxy-super flush)
;       (let [s (.trim (.toString #^java.io.ByteArrayOutputStream this))]
;         (proxy-super reset)
;         (if (> (.length s) 0)
;           (log level s nil log-ns)))))
;   true))
;
;
;(def #^{:doc
; "A ref used by log-capture! to maintain a reference to the original System.out
; and System.err streams."
; :private true}
; *old-std-streams* (ref nil))
;
;
;(defn log-capture!
; "Captures System.out and System.err, redirecting all writes of those streams
; to :info and :error logging, respectively. The specified log-ns value will
; be used to namespace all redirected logging. NOTE: this will not redirect
; output of *out* or *err*; for that, use with-logs."
; [log-ns]
; (dosync
;   (let [new-out (log-stream :info log-ns)
;         new-err (log-stream :error log-ns)]
;     ; don't overwrite the original values
;     (if (nil? @*old-std-streams*)
;       (ref-set *old-std-streams* {:out System/out :err System/err}))
;     (System/setOut new-out)
;     (System/setErr new-err))))
;
;
;(defn log-uncapture!
; "Restores System.out and System.err to their original values."
; []
; (dosync
;   (when-let [{old-out :out old-err :err} @*old-std-streams*]
;     (ref-set *old-std-streams* nil)
;     (System/setOut old-out)
;     (System/setErr old-err))))
;
;
;(defmacro with-logs
; "Evaluates exprs in a context in which *out* and *err* are bound to :info and
; :error logging, respectively. The specified log-ns value will be used to
; namespace all redirected logging."
; [log-ns & body]
; (if (and log-ns (seq body))
;   `(binding [*out* (java.io.OutputStreamWriter.
;                      (log-stream :info ~log-ns))
;              *err* (java.io.OutputStreamWriter.
;                      (log-stream :error ~log-ns))]
;     ~@body)))
;
;(defmacro trace
; "Logs a message at the trace level."
; ([message]
;   `(log :trace ~message))
; ([message throwable]
;   `(log :trace ~message ~throwable)))
;
;(defmacro debug
; "Logs a message at the debug level."
; ([message]
;   `(log :debug ~message))
; ([message throwable]
;   `(log :debug ~message ~throwable)))
;
;(defmacro info
; "Logs a message at the info level."
; ([message]
;   `(log :info ~message))
; ([message throwable]
;   `(log :info ~message ~throwable)))
;
;(defmacro warn
; "Logs a message at the warn level."
; ([message]
;   `(log :warn ~message))
; ([message throwable]
;   `(log :warn ~message ~throwable)))
;
;(defmacro error
; "Logs a message at the error level."
; ([message]
;   `(log :error ~message))
; ([message throwable]
;   `(log :error ~message ~throwable)))
;
;(defmacro fatal
; "Logs a message at the fatal level."
; ([message]
;   `(log :fatal ~message))
; ([message throwable]
;   `(log :fatal ~message ~throwable)))
;
;
