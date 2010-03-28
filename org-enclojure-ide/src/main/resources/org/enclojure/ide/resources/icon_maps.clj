(comment
;*******************************************************************************
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    GNU General Public License, version 2
;*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
;*    exception (http://www.gnu.org/software/classpath/license.html)
;*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
;*    of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*******************************************************************************
;*    Author: Eric Thorsen
;*******************************************************************************
)
(ns org.enclojure.ide.resources.icon-maps)

(def icon-base-path "org/enclojure/ide/resources/")

(def icons
    (reduce #(assoc %1 (first %2) (str icon-base-path (fnext %2)))
      {}
        {   'ns "package.gif"
            'defmacro "fqn.gif"
            'defn "static.gif"
            'def "declaration_action.png"
            'comment "inherited.gif"
            'defmulti "filterHideInherited.png"
            'defmethod "filterHideStatic.png"
            }))



