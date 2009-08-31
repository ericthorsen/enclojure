(comment
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;*    which can be found in the file epl-v10.html at the root of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*
;*    Author: Narayan Singhal
)

(ns org.enclojure.commons.core)


(defn first-valid
  "Takes a list of functions and/or expressions and returns the value of the 1st
non nil result"
  [& args]
  (some #(if (instance? clojure.lang.IFn %) (%) %) args))


