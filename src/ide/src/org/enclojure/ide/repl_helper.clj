(comment
;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;    The use and distribution terms for this software are covered by the
;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;    which can be found in the file epl-v10.html at the root of this distribution.
;    By using this software in any fashion, you are agreeing to be bound by
;    the terms of this license.
;    You must not remove this notice, or any other, from this software.
;
;    Author: Eric Thorsen
)

(ns org.enclojure.ide.repl-helper)

(defn do-require [in-namespace to-require]
  (let [nsname (str (find-ns to-require))
        as (subs nsname 0 (.lastIndexOf nsname "."))]
    (binding [*ns* (find-ns in-namespace)]
       (require [(find-ns to-require):as (symbol as)]))))


