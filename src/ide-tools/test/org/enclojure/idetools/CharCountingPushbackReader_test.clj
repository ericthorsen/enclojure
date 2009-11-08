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
        :doc "Protocol for org.enclojure.idetools.CharCountingPushbackReader-test"}
 org.enclojure.idetools.CharCountingPushbackReader-test
   (:use clojure.test)
    (:import (java.io IOException LineNumberReader PushbackReader
             Reader LineNumberReader StringReader)
      (org.enclojure.idetools CharCountingPushbackReader)))


(def -in-buffer- (make-array Integer/TYPE 100))

(defn reader-test-init
  [in-str]
  (CharCountingPushbackReader. (StringReader. in-str)))

(def -inputs-
  (hash-map
  'empty-string ""
  'only-newlines-4 "\n\n\n\n"
  'tabs "\t\t\t"
  'buffer ""))


(deftest char-counting-reader-test
  (testing "positional functionality"
    (let [reader (reader-test-init "org.enclojure.idetools.CharCountingPushbackReader-test
   (:use clojure.test)
    (:import (java.io IOException LineNumberReader PushbackReader
             Reader LineNumberReader StringReader)
      (org.enclojure.idetools CharCountingPushbackReader)))")]
    (is (= 0 (.getPosition reader)))
    (is (= 1 (do (.read reader) (.getPosition reader))))
    (is (= 10 (.read reader -in-buffer- 0 10)))
    (is (= 1 (.getPosition reader)))
      )))

  
      

