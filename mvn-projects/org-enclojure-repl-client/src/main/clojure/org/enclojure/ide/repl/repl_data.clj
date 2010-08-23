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
        :doc "This defines validation maps to be used by the
            org.enclojure.commons.validation routines to ensure valid config data
            for the following use cases for REPLS:
            1. Inproc - Runs inside the current application.
            2. External unmanaged - There is a running repl server in a remote
                application that will be connected to via a socket.
            3. External managed - A child process will be started and conneted to
                via socket.  When the parent process ends so will any children
                started (hence, managed).
            "
    }
   org.enclojure.ide.repl.repl-data
  (:require [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.commons.validation :as validation]))

(def -default-repl-data-
  {
   :jvm-additional-args ["-server" "-Xmx512m" "-Xms128m"]
   :debug-port-arg "-Xrunjdwp:transport=dt_socket,server=y,suspend=n"   
   :java-main "org.enclojure.repl.launcher"
   :repl-id "Default Repl Id"
   :port 0
   :ack-port nil
   :java-exe "java"
   })

(def #^{:doc "Context required for an inproc repl"}
  -repl-context-validation-
    {:repl-id (validation/validator string?)
     :startup-expr (validation/nilable-validator string?)})
     

(def #^{:doc "Context required for an unmanaged external repl"}
  -repl-context-external-validation-
  (assoc -repl-context-validation-
     :port (validation/validator integer?)
     :host (validation/validator string?)
     :debug-port-arg (validation/nilable-validator string?)))


(def #^{:doc "Context required for an managed external repl"}
  -repl-context-external-managed-validation-
  (assoc -repl-context-validation-
    :port (validation/validator integer?)
    :jvm-additional-args (validation/nilable-validator vector?)
    :java-main (validation/validator string?)
    :classpath (validation/validator string?)
    :debug-port-arg (validation/nilable-validator string?)))

(defn bless-external-context
  [repl-context]
  (if (:bless-external-context? (meta repl-context))
    repl-context
    (with-meta
      repl-context
      (assoc (meta repl-context)
        :bless-external-context?
            (validation/validate-throw-on-fail repl-context
                    -repl-context-external-managed-validation-)))))



