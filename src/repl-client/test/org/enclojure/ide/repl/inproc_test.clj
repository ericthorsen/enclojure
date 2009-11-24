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

(ns #^{ :author "Eric Thorsen",
        :doc "Protocol for org.enclojure.ide.repl.inproc-test"}
		org.enclojure.ide.repl.inproc-test
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

(defn create-inproc-repl
  []
  (let [irepl (atom nil)]
    (EventQueue/invokeAndWait
      #(swap! irepl (fn [_] (create-in-proc-repl))))
    @irepl))
  
(defn repl-fixture
  [f]
  (let [repl (create-inproc-repl)]
    (f repl)
    (repl-manager/stop-repl-servers)))

(use-fixtures :once repl-fixture)



