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
(ns org.enclojure.ide.repl.factory-test
 (:use org.enclojure.ide.repl.factory
   org.enclojure.ide.repl.DefReplWindowFactory
   clojure.test)
 (:require
   [org.enclojure.ide.repl.repl-manager :as repl-manager]
   [org.enclojure.ide.repl.repl-panel :as repl-panel]
   [org.enclojure.commons.c-slf4j :as logger]
   [org.enclojure.ide.repl.repl-data :as repl-data])
 (:import (org.enclojure.repl IReplWindow IReplWindowFactory IRepl)
    (java.io File OutputStreamWriter FileOutputStream)
        (javax.swing JFrame JScrollPane WindowConstants)
        (java.awt EventQueue)
        (java.awt.event WindowAdapter WindowEvent)
    (org.enclojure.ide.repl DefReplWindowFactory)))

; setup logging
(logger/ensure-logger)

(defn cleanup-onclose
  "Add a window listener to a JFrame to shutdown the repl-servers
and exit the app on close."
  [jframe]
  (.setDefaultCloseOperation jframe WindowConstants/DISPOSE_ON_CLOSE)
  (.addWindowListener jframe
    (proxy [WindowAdapter][]
      (windowClosed [e]        
        (repl-manager/stop-repl-servers)        
        (System/exit 0))
        )))

;------------- Simple helper functions for creating REPLs ------------------
(defn create-inproc-repl
  []
  (let [irepl (atom nil)]
    (EventQueue/invokeAndWait
      #(swap! irepl (fn [_] (create-in-proc-repl))))
    @irepl))

;---------- Interactive tests for experimenting with REPLs ------------------
(defn test-create-in-proc-repl
  "Sample function to create an in-proc REPL"
  []  
  (let [irepl (create-inproc-repl)
        frame (JFrame. "Inproc REPL Frame")]
    (EventQueue/invokeAndWait
        #(swap! irepl (fn [_] (create-in-proc-repl))))
    (.setLayout frame (java.awt.GridLayout. 1 1))
    (.add frame (-> irepl .getReplPanel))
    (EventQueue/invokeLater
      #(do
         (.setSize frame 1000 1000)
         (.setVisible frame true)
         (cleanup-onclose frame)))))

(defn test-connect-external-repl
  "This funtion assumes there is already a process running and will connect
using the create-unmanaged-external-repl function"
  [server-port]
  (let [irepl (atom nil)
        frame (JFrame. "Unmanaged external REPL Frame")]
    (EventQueue/invokeAndWait
        #(swap! irepl
           (fn [_] (create-unmanaged-external-repl
                     {:repl-id (format "Repl-%s" server-port)
                      :port (Integer/parseInt server-port)
                      :host "127.0.0.1"}))))
    (.setLayout frame (java.awt.GridLayout. 1 1))
    (.add frame (-> @irepl .getReplPanel))
    (EventQueue/invokeLater
      #(do
         (.setSize frame 1000 1000)
         (.setVisible frame true)
         (cleanup-onclose frame)))))
         
  
(defn test-create-managed-repls
  "Sample function that creates 3 managed JVM processes and connects a
ReplPanel to each one"
  []
    (let [irepl (atom nil)
          frame (JFrame. "Managed external REPL Frame")]
    (EventQueue/invokeAndWait
        #(swap! irepl 
           (fn [_]
             (reduce (fn [v id]
                       (conj v (create-managed-external-repl
                                 {:repl-id (format "Repl-%s" id)}))) []
               (range 3)))))
    (.setLayout frame (java.awt.GridLayout. 3 1))    
    (doseq [repl @irepl]
      (.add frame (-> repl .getReplPanel)))
    (EventQueue/invokeLater
      #(do
         (.setSize frame 1000 1000)
         (.setVisible frame true)
         (cleanup-onclose frame)))))

;---------- Automated tests for REPLs ------------------

(deftest test-repl-startup
  (testing "Testing repl startup functions"
    (is (create-inproc-repl)

    
         (cleanup-onclose frame)))))



