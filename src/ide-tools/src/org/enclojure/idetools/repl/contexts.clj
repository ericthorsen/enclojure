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
        :doc "Protocol for org.enclojure.idetools.form-def"}
		org.enclojure.idetools.repl.contexts)

(def -repl-contexts-
  (ref
    {:project-search-spec [""]
     :source-roots-search-spec [""]
     :libs-search-spec [""]
     :projects
     [{:name "" :path "" :web-root "" :source-roots [] :libs []}]
     :repls
     (ref
       [{:name "" :port -1 :host "" :status ""
         ; should these be startup attribs?
         :auto-start true :enable-logging true
         :jvm-addition-args [] :clojure-platform "platform-key"
         :startup-attributes {:attrib :value}
         :source-projects ["project"]
         :classpaths [""]
         :running-context {}
         }])}))