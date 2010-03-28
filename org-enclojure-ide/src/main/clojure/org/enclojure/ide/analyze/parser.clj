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
        :doc "Protocol for org.enclojure.ide.analyze.parser"}
 org.enclojure.ide.analyze.parser  
;  (:import
;    (org.netbeans.modules.parsing.api Source Snapshot)
;    (org.netbeans.modules.parsing.spi Parser)
;    (javax.swing.text Document PlainDocument
;      StringContent AbstractDocument$Content)
;    (java.io File)
;    (org.netbeans.api.lexer TokenHierarchy)
;    (org.netbeans.modules.parsing.spi ParserFactory)
;    (org.openide.filesystems FileUtil FileObject)
    )

;(defmulti get-source class)
;
;(defmethod get-source Document
;  [document]
;  (Source/create document))
;
;(defmethod get-source File
;  [file]
;  (Source/create (FileUtil/createData file)))
;
;(defmethod get-source FileObject
;  [file]
;  (Source/create file))
;
;(def s (get-source
;         (java.io.File.
;            "/Users/ericthor/Dev/enclojure/src/ide/src/org/enclojure/ide/analyze/core.clj")))
;(def snap (.createSnapshot s))



