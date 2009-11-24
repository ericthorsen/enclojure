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
       :doc "Protocol for org.enclojure.protocols.logger"}
  org.enclojure.protocols.logger
  (:require [org.enclojure.protocols.helpers :as helpers]))

(defmulti debug helpers/classfn-arity*)
(defmulti error helpers/classfn-arity*)
(defmulti trace helpers/classfn-arity*)
(defmulti warn helpers/classfn-arity*)
(defmulti info helpers/classfn-arity*)

