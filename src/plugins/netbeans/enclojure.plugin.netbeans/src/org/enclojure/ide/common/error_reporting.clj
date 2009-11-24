
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
;*    Author: Eric Thorsen, Narayan Singhal
;*******************************************************************************
)
(ns org.enclojure.ide.common.error-reporting
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import
    (java.util.logging Level Logger)
    (org.openide NotifyDescriptor$Confirmation DialogDisplayer NotifyDescriptor
      NotifyDescriptor$Exception)
    )
  )


; setup logging
(logger/ensure-logger)

(defn report-error
  ([msg throwable]
    (.notifyLater (DialogDisplayer/getDefault)
      (NotifyDescriptor$Exception. throwable msg))))
  


