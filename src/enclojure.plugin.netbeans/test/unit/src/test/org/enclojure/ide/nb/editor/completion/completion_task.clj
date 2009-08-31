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

(ns test.org.enclojure.ide.nb.editor.completion.completion-task
  (:use org.enclojure.commons.meta-utils
    org.enclojure.commons.logging)
  (:import (org.netbeans.spi.editor.completion CompletionResultSet
             CompletionItem CompletionProvider CompletionDocumentation
             CompletionTask)
    (org.netbeans.api.editor.completion Completion)
    (org.netbeans.spi.editor.completion.support CompletionUtilities)
    (java.util Collection)
    (java.util.logging Level)
    (javax.swing JToolTip)
    (javax.swing.text Document PlainDocument StringContent)
    (javax.swing.text JTextComponent)
    (java.awt.event ActionEvent KeyEvent KeyListener)
    (java.awt EventQueue Component)
    (org.openide.util Exceptions)
    (org.netbeans.modules.editor NbEditorUtilities)
    (org.netbeans.spi.editor.completion.support AsyncCompletionQuery AsyncCompletionTask))
  (:require 
    [org.enclojure.ide.nb.editor.completion.completion-task :as completion-task]
    [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.navigator.token-nav :as token-nav]
    [org.enclojure.ide.nb.editor.completion.completion-item :as completion-item]
    [org.enclojure.ide.navigator.parser :as parser]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    [org.enclojure.ide.nb.editor.completion.file-mapping :as file-mapping]
    [org.enclojure.ide.analyze.symbol-meta :as symbol-meta]
    ))

(defn create-doc [s]
  (PlainDocument.
    (doto (StringContent. (count s)) (.insertString 0 s))))

