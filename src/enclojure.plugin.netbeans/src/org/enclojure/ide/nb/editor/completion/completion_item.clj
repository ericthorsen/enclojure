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

(ns org.enclojure.ide.nb.editor.completion.completion-item
    (:use org.enclojure.commons.meta-utils
        org.enclojure.commons.logging)
  (:import (org.netbeans.spi.editor.completion CompletionResultSet
             CompletionItem CompletionProvider CompletionDocumentation
             CompletionTask)
    (java.util.logging Level)
    (org.netbeans.api.editor.completion Completion)
    (org.netbeans.spi.editor.completion.support CompletionUtilities)
    (org.openide.util ImageUtilities)
    (java.net URLEncoder)
    (java.util Collection)
    (java.io StringReader)
    (javax.swing JToolTip ImageIcon)
    (javax.swing.text JTextComponent)
    (java.awt.event ActionEvent KeyEvent KeyListener InputEvent ComponentEvent)
    (java.awt Color Graphics Font AWTEvent))
  (:require
    [org.enclojure.ide.common.classpath-utils :as classpath-utils]
    [org.enclojure.ide.navigator.token-nav :as token-nav]))

(defrt #^{:private true} log (get-ns-logfn))

(defn encode-html [s]
  (let [reader (StringReader. s)]
  (loop [c (.read reader) buf (StringBuffer.)]
    (let [cc (char c)]
      (if (= -1 c)
         (str buf)
        (recur (.read reader)
          (.append buf
            (if (or (> c 127) (#{\" \< \> \&} cc))
                (str "&#" c ";")
              cc))))))))

(defn accept-and-close [item search-info]
  (.hideAll (Completion/get)))

(def icons {'ns "namespace.png"
            'defmacro "macro.png"
            'defn "function.png"
            'defn- "function-private.png"
            'def "var.png"
            'comment "comment.png"
            'defmulti "multimethod.png"
            'defmethod "method.png"
            })

(defn load-icon [s]
  (let [i (icons s (icons 'def))]
  (ImageUtilities/loadImage (str "org/enclojure/ide/nb/editor/resources/" i) true)))

(defn split-tags [tag]
  [tag tag])

(defn format-tag [name arglists]
  (str name "\t\t\t" arglists))


(defn insert-text-generic [#^JTextComponent component 
                           input-item
                           search-info
                           new-string]
  (let [{:keys [start end length]} input-item]
    (.remove (.getDocument component) start length)
    (.insertString (.getDocument component) start 
      (str (:prepend-text search-info)
                     (:search-scope search-info)
                     (:search-delim search-info) new-string) nil)))

(defn insert-text-inplace [#^JTextComponent component item search-info]
  (let [final-text (str (:prepend-text search-info)
                    ; (:search-scope search-info)
                    ; (:search-delim search-info)
                     (:name item))
        start (get-in search-info [:input :start])
        end (get-in search-info [:input :end])
        inlen (get-in search-info [:input :length])
        cpos (+ (get-in search-info [:input :start])
               (count final-text))]
                (.remove (.getDocument component) start inlen)
            (.insertString (.getDocument component) start (str final-text "") nil)))


(defn get-final-text [item search-info]
  (str (:prepend-text search-info) (:search-scope search-info)
                         (:search-delim search-info)
                         (print-str (:name item))))

(defn get-completion-item [item search-info]
  (let [{:keys [name arglists symbol-type] :as tag} item
        text (print-str name)
        args (if arglists (print-str arglists) "")
        {instant-sub :instant-sub} search-info
        final-text (get-final-text item search-info)];make sure we are not talking to a symbol
  (proxy [org.netbeans.spi.editor.completion.CompletionItem] []
    (createDocumentationTask [])
    (createToolTipTask [])
    (defaultAction [#^JTextComponent component]      
            ;final-text (get-final-text item search-info)
            (insert-text-inplace component item search-info))
    (getInsertPrefix [] text)
    (getPreferredWidth [#^Graphics g #^Font font]
     ; (log Level/INFO "count is !!!!!!! " (count (.trim full-tag)) " for:" full-tag ":")
         (CompletionUtilities/getPreferredWidth
           (encode-html text)
           (encode-html args)
           g font))
    (getSortPriority [] 1)
    (getSortText [] #^CharSequence (str text))
    (instantSubstitution [#^JTextComponent component] 
      (log Level/INFO "Instant baby!!!")
      (.defaultAction this component)
      (accept-and-close item search-info)
      true)
    (processKeyEvent [#^KeyEvent evt]
      (log Level/INFO "keyevent code " (.getKeyCode evt) " instant " instant-sub)
      (if instant-sub 
         (do
           (.consume evt)
           (.defaultAction this (:text-component search-info))
           (accept-and-close item search-info))
        (let [code (.getKeyCode evt)
                is-alt (.isAltDown evt)]
          (if (= code KeyEvent/VK_TAB)
                (do
                  (.consume evt)
                  (if (not is-alt)
                    (do                      
                        (.defaultAction this (:text-component search-info))
                        (accept-and-close item search-info))))))))
                ; Continued search
    (render [#^Graphics g #^Font defaultFont #^Color defaultColor
             #^Color backgroundColor width height selected]
      (CompletionUtilities/renderHtml
        (ImageIcon. (load-icon symbol-type))
            (encode-html text)
            (encode-html args)
        g defaultFont
                (if selected Color/white Color/blue)
                    width height selected)))))
