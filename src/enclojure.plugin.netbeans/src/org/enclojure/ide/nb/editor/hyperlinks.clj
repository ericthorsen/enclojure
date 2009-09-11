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

(ns org.enclojure.ide.nb.editor.hyperlinks
  (:require [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.nb.editor.utils :as utils]
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.commons.meta-utils :as meta-utils]
    )
  (:import
    (java.util.logging Level)
    (org.openide.util RequestProcessor)
    (java.lang.ref Reference WeakReference)
    (javax.swing.text  PlainDocument StringContent)
    (javax.swing.text Document JTextComponent StyledDocument)
    (org.netbeans.api.editor EditorRegistry)
    (org.netbeans.api.html.lexer.HTMLTokenId)
    (org.netbeans.api.lexer Token TokenHierarchy TokenSequence)
    (org.openide.text NbDocument)
    (org.netbeans.lib.editor.hyperlink.spi HyperlinkProvider)
   ))

; setup logging
(logger/ensure-logger)

(defn get-clj-source-and-line [s]
  (when-let [tag (re-find #"\(.*\.clj\:[0-9]+\)" s)] 
    (let [tags (.split tag ":")          
          file (subs (first tags) 1)
          line (apply str (butlast (fnext tags)))
          ; Now find the namespace
          start-ns (+ (.indexOf s " at ") 5)
          end-ns (.indexOf s (str "." (subs file 0 (.indexOf file ".clj")) "$"))
          ns-prefix (subs s start-ns end-ns)
          link-offset (.indexOf s (str "at " ns-prefix))
          link-start (if (>= link-offset 0) link-offset 0)
          ]
      {:line (Integer/parseInt line)
       :link-text tag
       :file file 
       :link-start link-start
       :link-end (dec (- (count s) link-start))
       :ns-prefix (subs s start-ns end-ns)
       :start-ns start-ns
       :end-ns end-ns})))
  

(defn do-get-hyperlink-provider []
  (let [last-ref (ref {})]
  (proxy [org.netbeans.lib.editor.hyperlink.spi.HyperlinkProvider] []
    (isHyperlinkPoint
    [#^Document d offset]
    (logger/info "is-hyperlink {}" offset)
      (boolean
        (let [lineno (NbDocument/findLineNumber d offset)
                line-offset (NbDocument/findLineOffset d lineno)
                next-line-offset (NbDocument/findLineOffset d (inc lineno))]
          (logger/info "is-hyperlink [{}]"
            (apply str (interpose " " [lineno line-offset next-line-offset])))
            (when-let [link-text (when (< line-offset next-line-offset)
                                   (.getText d line-offset
                                   (- next-line-offset line-offset)))]
              (logger/info "is-hyperlink got text: {}" link-text)
                (when-let [{:keys [line link-text file ns-prefix
                                   link-start link-end] :as tag-data}
                            (get-clj-source-and-line link-text)]
                  (let [full-path (str (meta-utils/root-resource ns-prefix) "/" file)]
                    (when-let [f (classpath-utils/find-resource full-path)]
                        (logger/info "updating tag-data {}" tag-data)
                        (dosync (alter last-ref
                            (fn [_]
                              (assoc tag-data :doc d :doc-offset offset
                                :link-start (+ link-start line-offset)
                                :link-end (+ link-end line-offset)
                                :full-path full-path :file-resource f))))
                      true)))))))
    (getHyperlinkSpan [#^Document doc offset]
       (logger/info "getHyperlinkSpan - offset {} last-ref-offset:{} " offset
         (if @last-ref (:doc-offset @last-ref) ""))
      (when (and @last-ref (= offset (:doc-offset @last-ref)))
        (let [a (make-array Integer/TYPE 2)]
          (aset a 0 (:link-start @last-ref))
          (aset a 1 (:link-end @last-ref))
          a)))
    (performClickAction
      [#^Document doc offset]
          (logger/info "perform click~~~~~~~~~~~ {}" (:ns-prefix @last-ref))
            (when @last-ref
               (utils/open-editor-file-at-line (:file-resource @last-ref) 
                 (:line @last-ref)))))))

  (defn get-hyperlink-provider
    [] (do-get-hyperlink-provider))

(defn get-hyperlink-provider-func []
  (get-hyperlink-provider))

(defn get-repl-doc []
  (.getDocument (org.netbeans.editor.Utilities/getFocusedComponent)))
