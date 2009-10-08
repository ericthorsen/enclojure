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
   org.enclojure.ide.repl.DefReplWindowFactory)
 (:require
   [org.enclojure.ide.repl.repl-manager :as repl-manager]
   [org.enclojure.ide.repl.repl-panel :as repl-panel]
   [org.enclojure.ide.repl.repl-data :as repl-data])
 (:import (org.enclojure.repl IReplWindow IReplWindowFactory IRepl)
    (java.io File OutputStreamWriter FileOutputStream)
        (javax.swing JFrame JScrollPane)
        (java.awt EventQueue)
    (org.enclojure.ide.repl DefReplWindowFactory)))


(defn test-create-in-proc-repl
  "Sample function to create an in-proc REPL"
  []
  (let [latch (java.util.concurrent.CountDownLatch. 1)
        irepl (atom nil)
        frame (JFrame. "Inproc REPL Frame")
        ]
    (EventQueue/invokeAndWait
        #(swap! irepl (fn [_] (create-in-proc-repl))))
    (.setLayout frame (java.awt.GridLayout. 1 1))
    (.add frame (-> @irepl .getReplPanel))
    (EventQueue/invokeLater
      #(do
         (.setSize frame 1000 1000)
         (.setVisible frame true)))
    (.await latch)))

(defn test-connect-external-repl
  "This funtion assumes there is already a process running and will connect
using the create-unmanaged-external-repl function"
  [server-port]
  (let [latch (java.util.concurrent.CountDownLatch. 1)
        irepl (atom nil)
        frame (JFrame. "Unmanaged external REPL Frame")
        ]
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
         (.setVisible frame true)))
        (.await latch)))
  
(defn test-create-managed-repls
  "Sample function that creates 3 managed JVM processes and connects a
ReplPanel to each one"
  []
    (let [latch (java.util.concurrent.CountDownLatch. 1)
        irepl (atom nil)
        frame (JFrame. "Managed external REPL Frame")
        ]
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
         (.setVisible frame true)))
    (.await latch)))
