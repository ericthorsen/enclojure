(comment
 ;
 ;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
 ;    The use and distribution terms for this software are covered by the
 ;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 ;    which can be found in the file epl-v10.html at the root of this distribution.
 ;    By using this software in any fashion, you are agreeing to be bound by
 ;    the terms of this license.
 ;    You must not remove this notice, or any other, from this software.
 ;
 ;    Author: Narayan Singhal
 )

(ns org.enclojure.ide.debugger.jdi-eval
  (:use org.enclojure.ide.repl.repl-manager)
  (:import
    (com.sun.jdi ThreadReference StackFrame LocalVariable Value ClassType)
    (org.netbeans.api.debugger DebuggerEngine DebuggerManager)
    (org.netbeans.api.debugger.jpda JPDADebugger CallStackFrame)
    ))

(defn get-current-thread-frame []
  (let [#^DebuggerManager dbg-manager (DebuggerManager/getDebuggerManager)
        #^DebuggerEngine dbg-engine (.getCurrentEngine dbg-manager)
        ;#^DebuggerEngine dbg-engine (first (:dbg-engines (get-repl-config repl-name)))
        #^JPDADebugger jdpa-debugger (.lookupFirst dbg-engine nil JPDADebugger)
        #^CallStackFrame csf (.getCurrentCallStackFrame jdpa-debugger)
        #^StackFrame stack-frame (.getStackFrame csf)
        vm (first (.connectedVirtualMachines (com.sun.jdi.Bootstrap/virtualMachineManager)))
        current-thread-name (.getName (.getCurrentThread jdpa-debugger))
        #^ThreadReference thread-reference (first (filter #(= (.name %) current-thread-name) (.allThreads vm)))]
    {:stack-frame stack-frame :thread-reference thread-reference}))

(defn get-var [#^StackFrame stack-frame var-name]
  (let [#^LocalVariable var-proxy (.visibleVariableByName stack-frame var-name)
        #^Value var-instance (.getValue stack-frame var-proxy)
        #^ClassType var-type (.type var-instance)]
    {:var-instance var-instance :var-type var-type :var-type-name (.name var-type)}))

;(def result (get-var-value "Hello World" "z" "toString"))
(defn get-var-value [var-name method-name & args]
  (let [{:keys [stack-frame thread-reference]} (get-current-thread-frame)
        {:keys [var-instance var-type var-type-name]} (get-var stack-frame var-name)
        method (first (.methodsByName var-type method-name))
        arg-list (if args (.toArray args) (java.util.ArrayList.))
        method-result (.invokeMethod var-instance thread-reference method arg-list 0)]
    method-result))

(defn get-value [var-name]
  (let [method-name "toString"
        {:keys [stack-frame thread-reference]} (get-current-thread-frame)
        {:keys [var-instance var-type var-type-name]} (get-var stack-frame var-name)
        method (first (filter #(nil? (first (seq (.argumentTypeNames %)))) (.methodsByName var-type method-name)))
        arg-list (java.util.ArrayList.)
        method-result (.invokeMethod var-instance thread-reference method arg-list 0)]
    (.value method-result)))

