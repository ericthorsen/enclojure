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
        :doc "org.enclojure.idetools.repl.test-repl-manager-ui"}
    org.enclojure.idetools.repl.test-repl-manager-ui
        (:require [org.enclojure.idetools.tokens :as tokens]
          [org.enclojure.idetools.token-set :as token-set])
  (:use clojure.test)
  (:import (org.enclojure.idetools.repl ReplManagerUI)))

(deftest actions
  (testing "add repl button click")
  (testing "delete repl button click on running repl")
  (testing "delete repl button click on not-running repl")
  (testing "duplicate repl button click")
  (testing "selection changed on repl-list")
  ; Need a ListSelectionListener on the table.
  (testing "right-click context menu on repl-list")
  (testing "context start repl - single seletion on repl-list")
  (testing "context start repl - multiple seletion on repl-list")
  (testing "context stop repl - single seletion on repl-list")
  (testing "context stop repl - multiple seletion on repl-list")
  (testing "context reset repl - single seletion on repl-list")
  (testing "context reset repl - multiple seletion on repl-list")
  (testing "clojure platform selection changed")
  (testing "add jvm arg clicked")
  (testing "delete jvm arg clicked when no selection")
  (testing "delete jvm arg clicked with one selection")
  (testing "delete jvm arg clicked with multiple selection")
  (testing "add clojure startup attribute")
  (testing "delete clojure startup attribute clicked when no selection")
  (testing "delete clojure startup attribute with one selection")
  (testing "delete clojure startup attribute with multiple selection")
  (testing "Project search patterns clicked")
  (testing "Project lib search patterns clicked")
  (testing "Use Project checkbox in Project model check/unchecked")
  (testing "add classpath clicked")
  (testing "delete classpath clicked when no selection")
  (testing "delete classpath clicked with one selection")
  (testing "delete classpath clicked with multiple selection")
  (testing "classpath move-up clicked")
  (testing "classpath move-down clicked")
  )

