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
(ns org.enclojure.ide.repl.factory  
  (:require
    org.enclojure.ide.repl.interface-factory
    [org.enclojure.ide.repl.repl-panel :as repl-panel]
    [org.enclojure.ide.repl.repl-manager :as repl-manager]
    [org.enclojure.repl.main :as repl-main]
    )
  (:import (org.enclojure.repl IReplContext
             IReplExternalContext IReplManagedExternalContext
             IReplWindow IRepl IReplWindowFactory)
            (org.enclojure.ide.repl ReplPanel))
  )

(defn create-IRepl
  [#^IReplContext repl-context
   #^IReplWindowFactory repl-window-factory]
  (let [repl-id (.getId repl-context)
        repl-panel (org.enclojure.ide.repl.ReplPanel. repl-id)
        repl-win (.makeReplWindow repl-window-factory
                   repl-panel repl-context)]
  (proxy [org.enclojure.repl.IRepl][]
    (getReplWindow [] repl-win)
    (getReplContext [] repl-context)
    (getReplPanel [] repl-panel))))

(defn- assure-repl-panel
  "Given a repl-id, looks in the state of the repl-manager to see if there
already is a repl-window with this ID.  Returns nil if exists already,
otherwise creates a repl-top-componentwith a repl-panel and opens it if open-tc?"
  [#^IReplContext repl-context
   #^IReplWindowFactory repl-window-factory]
  (let [repl-id (.getId repl-context)]
      (if-let [irepl (repl-manager/get-repl-config repl-id)]
        irepl
        (let [irepl (create-IRepl repl-context repl-window-factory)]
          (config-with-preferences repl-id
            {:repl-id repl-id
             :repl-panel (.getReplPanel irepl)
             :repl-tc (.getReplWindow irepl)
             :irepl irepl})
          irepl))))

(defn- create-repl [irepl spawn-repl-fn]    
  (let [spawned-repl-keys (spawn-repl-fn)]
    ; store off all the keys retiurned by this function.
    (apply repl-manager/update-repl
      (-> irepl .getReplContext .getId) (apply concat spawned-repl-keys))
    (repl-panel/bind-repl-panel (.getReplPanel irepl)
      (:repl-fn spawned-repl-keys)
      (:result-fn spawned-repl-keys))
    (repl-panel/evaluate-in-repl repl-id
      (str (get-settings-set-expression repl-id)))
    repl-tc))

(defn create-in-proc-repl
  "Creates a repl that will run in-proc in the JVM of the app"
  [#^IReplContext repl-context
   #^IReplWindowFactory repl-window-factory]
  (let [irepl (assure-repl-panel repl-context repl-window-factory)]
    (create-repl irepl create-clojure-repl)))

(defn create-unmanaged-external-repl
  "Creates a repl that bind to an external already running JVM
using the host and port defined in the IReplUnmanagedExternalContext"
  [#^IReplUnmanagedExternalContext repl-context
   #^IReplWindowFactory repl-window-factory]
  (let [irepl (assure-repl-panel repl-context repl-window-factory)]
    (create-repl irepl
      #(repl-main/create-repl-client-with-back-channel
         (.getHost repl-context)
         (.getPort repl-context)))))

(defn create-managed-external-repl
  "Creates a repl that bind to an external process that is started by this function.
When the parent process shutsdown it will shutdown all external JVMs started by this
function as well."
  [#^IReplManagedExternalContext repl-context
   #^IReplWindowFactory repl-window-factory]
  (let [irepl (assure-repl-panel repl-context repl-window-factory)
        repl-id (.getId repl-context)
        repl-panel (.getReplPanel irepl)]
       (repl-manager/create-internal-repl repl-id
                    (.getClasspath repl-context)
                    (partial bind-process-panel repl-panel)
                    (partial bind-repl-panel repl-panel))))
