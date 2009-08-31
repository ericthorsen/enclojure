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

(ns org.enclojure.commons.jprocess
    (import (org.apache.commons.exec Executor DefaultExecutor
                            CommandLine ExecuteResultHandler)))

;; FF - these are stubs to make it compile... need to define ALLL of this in order to make this 
;; helper functional
(defn on-complete [exit-value] nil)
(defn on-error [exception] nil)
(defn default-end-proc [] nil)
(defn kill-proc-fn [] nil)
(def std-out-stream-reader nil)
(defn shutdown-stream-readers-fn [] nil)
(def std-err-stream-reader nil)
(def start-proc-bind-streams nil)

(defn get-execute-result-handler [on-complete on-error]
    (proxy [ExecuteResultHandler] []
    (onProcessComplete [exit-value]
        (on-complete exit-value))
    (onProcessFailed [exception]
        (on-error exception))))

(defn start-proc
  "Starts a process using the apache-commons exec library.
    args needs to be a sequence of strings.
    Returns a map with:
    :process-builder ; reference to the Executor
    :process ; nil
    :cmd ; the array passed to the CommandLine
    :shutdown-fn ; nil commons library exec will shut down the process on exit of VM.
    :std-out-stream-reader ; nil
    :std-err-stream-reader ; nil"
  ([args {:keys [istr-for-stdout istr-for-stderr exit-proc-fn]
	  :or {istr-for-stdout *out* istr-for-stderr *err* exit-proc-fn default-end-proc}}]
  (let [cmd (CommandLine. "")
        executor (DefaultExecutor.)
        _ (.addArguments cmd (into-array args))
        result-handler (get-execute-result-handler
                            (fn [_])  exit-proc-fn)
        result (.execute executor cmd result-handler)]
    {:process nil
     :std-out-stream-reader std-out-stream-reader
     :std-err-stream-reader std-err-stream-reader
     :shutdown-stream-readers-fn shutdown-stream-readers-fn
     :kill-proc-fn kill-proc-fn
     :cmd cmd
     :cmd-as-string (apply str (interpose " " cmd))}))
  ([args] (start-proc-bind-streams args nil)))

