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
 ;    Author: Eric Thorsen
 )

(ns org.enclojure.ide.debugger.jdi
  (:use org.enclojure.ide.repl.repl-manager)
  (:import (com.sun.jdi Bootstrap)
    (com.sun.jdi.connect AttachingConnector)
    (com.sun.tools.jdi LinkedHashMap)
    (org.netbeans.api.debugger.jpda AttachingDICookie)
    (org.netbeans.api.debugger DebuggerManager DebuggerInfo ActionsManager)))

(defstruct virtual-machine-manager :vmm :attaching-connectors :listening-connectors)

(def *vmm* (ref nil))

(defn refresh-vmm []
  "Grab the current virtual-machine-manager and it's connectors"
  (let [vmm (Bootstrap/virtualMachineManager)]
    (dosync
      (alter *vmm* (fn [_] (struct virtual-machine-manager
                             vmm
                             (.attachingConnectors vmm)
                             (.listeningConnectors vmm))))))
  @*vmm*)

(defn check-vmm []
  (or @*vmm* (refresh-vmm)))

(defn get-attach-dbg-args [connector port]
  (when connector
    (let [args (.defaultArguments connector)
          port-arg (.get args "port")]
      (.setValue port-arg port)
      args)))

(defn get-acookie [port]
   (let [#^AttachingConnector c (first (:attaching-connectors (check-vmm)))
         #^com.sun.tools.jdi.LinkedHashMap args (get-attach-dbg-args c port)]
      (AttachingDICookie/create c args)))

(defn get-dbi [port]
   (DebuggerInfo/create
     AttachingDICookie/ID
     (into-array  [(get-acookie port)])))

(defn attach-dbg [repl-name port]
  (let [dbg-engines (.startDebugging (DebuggerManager/getDebuggerManager) (get-dbi port))]
    (update-repl repl-name :dbg-engines dbg-engines)))
    
(defn kill-dbg [repl-name]
  (let [{:keys [dbg-engines]} (get-repl-config repl-name)
        actions-manager (.getActionsManager (first dbg-engines))]
    (.doAction actions-manager org.netbeans.api.debugger.ActionsManager/ACTION_KILL)))

(defn session-removed [session]
  (let [engine (.getCurrentEngine session)
        {:keys [repl-id]} (first (filter
                                      (fn [{:keys [dbg-engines]}]
                                        (and dbg-engines (.contains (seq dbg-engines) engine)))
                                      (all-repl-configs)))]
    (when repl-id
      (update-repl repl-id :dbg-engines nil))))

