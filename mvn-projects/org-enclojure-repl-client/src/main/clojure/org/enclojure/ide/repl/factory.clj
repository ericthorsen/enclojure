(comment
;*
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

(ns #^{:author "Eric Thorsen"
       :doc "This file defines the startup routines for the 3 types of repls:
            1. Inproc - Runs inside the current application.
            2. External unmanaged - There is a running repl server in a remote
                application that will be connected to via a socket.
            3. External managed - A child process will be started and conneted to
                via socket.  When the parent process ends so will any children
                started (hence, managed).

           The validation routines for the config data for each case defined above
           are in the org.enclojure.ide.repl.repl-data ns and are:
           -repl-context-validation-
           -repl-context-external-validation-
           -repl-context-external-managed-validation-
           respectively.  These maps are used by the org.enclojure.commons.validation
           routines to ensure the proper configiuration data is passed prior to
           attempting to create a REPL.
           You will also have to implement the org.enclojure.repl.IReplWindowFactory
           for creating parent windows for the REPLs.  This enables this framework
           to be pluggable into another windowing framework such as Netbeans."}
  org.enclojure.ide.repl.factory  
  (:require    
    [org.enclojure.ide.repl.repl-panel :as repl-panel]
    [org.enclojure.ide.repl.repl-manager :as repl-manager]
    [org.enclojure.commons.validation :as validation]
    [org.enclojure.ide.repl.repl-data :as repl-data]
    [org.enclojure.repl.main :as repl-main]
    [clojure.contrib.except :as contrib.except]
    )
  (:import (org.enclojure.repl IReplWindow IRepl IReplWindowFactory)
            (org.enclojure.ide.repl ReplPanel)
    (org.enclojure.ide.repl DefReplWindowFactory)))
     

(defn create-IRepl
  [repl-context
   #^IReplWindowFactory repl-window-factory]
  (let [repl-id (:repl-id repl-context)
        repl-panel (org.enclojure.ide.repl.ReplPanel. repl-id)
        repl-win (.makeReplWindow repl-window-factory
                   repl-panel repl-context)]
  (proxy [org.enclojure.repl.IRepl][]
    (getReplWindow [] repl-win)
    (getReplContext [] repl-context)
    (getReplPanel [] repl-panel))))


(defn assure-repl-panel
  "Given a repl-id, looks in the state of the repl-manager to see if there
already is a repl-window with this ID.  Returns nil if exists already,
otherwise creates a repl-top-componentwith a repl-panel and opens it if open-tc?"
  [repl-context
   #^IReplWindowFactory repl-window-factory]
  (let [repl-id (:repl-id repl-context)]
      (if-let [irepl (repl-manager/get-repl-config repl-id)]
        (:irepl irepl)
        (let [irepl (create-IRepl repl-context repl-window-factory)]
          (apply repl-manager/add-or-update-repl repl-id
             :repl-panel (.getReplPanel irepl)
             :repl-tc (-> irepl .getReplWindow .getComponent)
             :irepl irepl
            (reduce concat [] repl-context))
          irepl))))

(defn- create-repl [irepl spawn-repl-fn]    
  (let [spawned-repl-keys (spawn-repl-fn)
        {:keys [repl-id startup-expr]} (-> irepl .getReplContext)]
    ; store off all the keys retiurned by this function.
    (apply repl-manager/update-repl
      repl-id (apply concat spawned-repl-keys))
    (repl-panel/bind-repl-panel (.getReplPanel irepl)
      (:repl-fn spawned-repl-keys)
      (:result-fn spawned-repl-keys))
    (when startup-expr
        (repl-panel/evaluate-in-repl repl-id startup-expr))
    irepl))

(defn create-in-proc-repl
  "Creates a repl that will run in-proc in the JVM of the app"
  ([repl-context-arg
   #^IReplWindowFactory repl-window-factory]
  (let [repl-context (merge repl-data/-default-repl-data- repl-context-arg)]
    (validation/validate-throw-on-fail repl-context repl-data/-repl-context-validation-)
        (let [irepl (assure-repl-panel repl-context repl-window-factory)]
            (create-repl irepl repl-main/create-clojure-repl))))
  ([repl-context-arg]
    (create-in-proc-repl repl-context-arg (DefReplWindowFactory.)))
  ([] (create-in-proc-repl repl-data/-default-repl-data-)))

(defn create-unmanaged-external-repl
  "Creates a repl that bind to an external already running JVM
using the host and port defined in the IReplUnmanagedExternalContext"
  ([repl-context-arg
   #^IReplWindowFactory repl-window-factory]
  (let [repl-context (merge repl-data/-default-repl-data- repl-context-arg)]
    (validation/validate-throw-on-fail repl-context
        repl-data/-repl-context-external-validation-)
    (let [irepl (assure-repl-panel repl-context repl-window-factory)]        
        (create-repl irepl
          #(repl-main/create-repl-client-with-back-channel
             (:host repl-context)
             (:port repl-context)))
      (.setResetReplFn
            (.getReplPanel irepl)
            #(do (repl-manager/stop-internal-repl (:repl-id repl-context-arg))
               (create-unmanaged-external-repl repl-context-arg repl-window-factory)))
      irepl)))
  ([repl-context-arg]
    (create-unmanaged-external-repl repl-context-arg (DefReplWindowFactory.)))
  ([] (create-unmanaged-external-repl repl-data/-default-repl-data-)))

(defn create-managed-external-repl
  "Creates a repl that bind to an external process that is started by this function.
When the parent process shutsdown it will shutdown all external JVMs started by this
function as well. If not :classpath is set in the repl-context it will default to
using the claspath of the running JVM."
  ([repl-context-arg
   #^IReplWindowFactory repl-window-factory]
  (let [repl-context (assoc (merge repl-data/-default-repl-data- repl-context-arg)
                       :classpath
                        (or (:classpath repl-context-arg)
                            (System/getProperty "java.class.path")))]
  (validation/validate-throw-on-fail repl-context
            repl-data/-repl-context-external-managed-validation-)
  (let [irepl (assure-repl-panel repl-context repl-window-factory)]
    (let [repl-id (:repl-id repl-context)
          repl-panel (.getReplPanel irepl)]
       (repl-manager/create-internal-repl repl-id
                    (:classpath repl-context)
                    (partial repl-panel/bind-process-panel repl-panel)
                    (partial repl-panel/bind-repl-panel repl-panel)
                    (or (:working-dir repl-context) "."))
      (.setResetReplFn
            (.getReplPanel irepl)
            #(do (repl-manager/stop-internal-repl repl-id)
               (create-managed-external-repl repl-context-arg repl-window-factory)))
        irepl))))
  ([repl-context-arg]
    (create-managed-external-repl repl-context-arg (DefReplWindowFactory.)))
  ([] (create-managed-external-repl repl-data/-default-repl-data-)))
