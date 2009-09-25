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

(ns org.enclojure.ide.repl.repl-manager-test
  (use clojure.test org.enclojure.ide.repl.repl-manager)
  (:import (java.io File)
  )
  )


(testing "bad-classpath?"
  (let [clojure-jar (File/createTempFile "clojure" "jar")
        contrib-jar (File/createTempFile "clojure-contrib" "jar")
        clojure-jar-path (.getCanonicalPath clojure-jar)
        contrib-jar-path (.getCanonicalPath contrib-jar)]
    (is (= (bad-classpath? "") {:clojure [nil nil], :contrib [nil nil]})
    (is (= (bad-classpath? (str clojure-jar-path File/pathSeparator
                             contrib-jar-path) nil)))
    (is (= (bad-classpath? clojure-jar-path)
          {:clojure [nil nil], :contrib [clojure-jar-path true]})))))

    


