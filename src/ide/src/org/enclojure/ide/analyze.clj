(comment
;    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;    The use and distribution terms for this software are covered by the
;    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;    which can be found in the file epl-v10.html at the root of this distribution.
;    By using this software in any fashion, you are agreeing to be bound by
;    the terms of this license.
;    You must not remove this notice, or any other, from this software.
;
;    Author: Frank Failla
)

(ns org.enclojure.ide.analyze
  (:require [clojure.contrib.repl-utils :as ru]
    )
  (:import (java.io File LineNumberReader InputStreamReader PushbackReader)
    (java.lang.reflect Modifier Method Constructor)
    (clojure.lang RT Compiler Compiler$C)))

(defn find-ns-in [text]
  (let [s text
        nsi (.indexOf s "(ns ")
        end-ns (when (>= nsi 0)
               (apply min
                 (filter pos?
                   [(.indexOf s (int \() (inc nsi))
                    (.indexOf s (int \)) (inc nsi))])))]
    (when (and (every? #(>= % 0) [nsi end-ns])
            (> end-ns nsi))
            (.trim (.substring s (+ nsi 4) end-ns)))))

(defn find-ns-in-source [f]
  (let [jf (java.io.File. (if (.startsWith f "file:/")
                            (.toURI (java.net.URL. f))
                            f))]
    (when (.exists jf)
        (find-ns-in (slurp (.getCanonicalPath jf))))))

;(defn anal
;  "Uses the Clojure compiler to analyze the given s-expr.  Returns
;  a map with keys :class and :primitive? indicating what the compiler
;  concluded about the return value of the expression.  Returns nil if
;  not type info can be determined at compile-time.

  ;Example: (expression-info '(+ (int 5) (float 10)))
  ;Returns: {:class float, :primitive? true}"
  ;[expr]
  ;(Compiler/myeval (read-string (str expr))))

;(def e '(defn find-ns-in-source ([#^String f]
;     (let [jf (java.io.File. "fred")
;        {:keys [a b]} {:a 1 :b 2}]
;        (.close jf))) ([] (find-ns-in-source "bile"))))

; ae is a DefExpr
;(def ae (anal e))
; The DefExpr has a var that has the symbol find-ns-in-source and an init which contains the
; FnExpr
; Since there are 2 arities the FnExpr.methods has 2 FnMethods
;(def m1 (first (-> ae .init .methods)))
;(def m2 (second (-> ae .init .methods)))
; arguments are in a list .argLocals of LocalBindings (at least in this case)
; could not figure out there the type hint is in the above example.
;(def args-1 (.argLocals m1))
;(def args-2 (.argLocals m2))
; The LetExpr of the first fn. There is only 1 expr so just get first
;(def le (first (-> m1 .body .exprs)))
; BindingInit for the file guy
;(def bi (first (.bindingInits le)))
;(def bi-local-binding (.binding bi))
;(def bi-local-init (.init bi))
;(def lbody (.body le))
;=============================================\\
; Structured editing use case
;(defn find-blah
;  ([#^String f] ; at this point I know f is/should be a string
;    (let [jf (java.io.File. "fred")] ; at this point I know jf is a File and there are on 2 locals
;      (.close jf) ; the point at which the user types the . there are only 2 locals. A String and a File.
        ;   Of course the target could be anything so that does not help much
;)))

;(def ns-a (anal '(ns fred)))
; The above is a FnExpr...no idea if it is resolved???
;(def ns-fn (first (.methods ns-a)))
;(def ns-body (first (.exprs (.body ns-fn))))
;(def inv-expr (.fexpr ns-body))


;(defmulti p type)
;(defmethod p clojure.lang.Compiler$FnExpr [f]
;  )

;(defmethod p clojure.lang.Compiler$DefExpr [f])

;(defmethod p clojure.lang.Compiler$LocalBinding [f]
;  {:name (.name f) :sym (.sym f) :tag (.tag f) :idx (.idx f)})


