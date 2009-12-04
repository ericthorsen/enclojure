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
        :doc "Protocol for org.enclojure.protocols"}
 org.enclojure.protocols)

(defprotocol brace-matcher
  "Basic brace matching for Documents"
  (find-match ([document char-pairs]
               [document char-pairs start-offset]
               [document char-pairs start-offset limit])))

(def -PATH-TYPES- #{:source :compile :exec :debug :boot})

(defprotocol path-manager
  "protocol for managing source root paths and classpaths"
  (get-source-roots [this])
  (get-classpath-for [this cp-type]
        "get the classpath for one of:
        :source
        :boot
        :compile
        :exec
        :debug"))


