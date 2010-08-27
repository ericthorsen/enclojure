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

(ns org.enclojure.ide.navigator.views.navigator-panel
  (:use
    clojure.inspector
    clojure.set
    )
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    [org.enclojure.ide.nb.editor.completion.symbol-caching :as symbol-caching]
    [org.enclojure.ide.nb.editor.utils :as editor-utils]
    )
  (:import (java.util.logging Level)
    (java.awt.event MouseAdapter ActionListener)
    (java.awt.font TextAttribute)
    (javax.swing.tree TreeModel TreePath)
    (javax.swing.event TreeSelectionListener)
    (javax.imageio ImageIO)
    (java.text AttributedString)
    (java.awt BorderLayout EventQueue Toolkit)
    (javax.swing.tree TreeModel DefaultTreeCellRenderer TreeCellRenderer)
    (javax.swing JLabel JPanel JTree JTable JScrollPane 
            JFrame JToolBar JButton SwingUtilities JViewport ImageIcon)
    (org.enclojure.ide.navigator.views CljNavigatorViewPanel)))

; setup logging
(logger/ensure-logger)

(def sample-ns-record
  {
   :root "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/build/cluster/modules/org-enclojure-ide-nb-editor.jar"
   :source "#<JarFileEntry org/enclojure/ide/nb/editor/utils.clj>"
   :ext "clj"
   :lib "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/build/cluster/modules/org-enclojure-ide-nb-editor.jar"
   :name "org/enclojure/ide/nb/editor/utils.clj"
   :symbols
   {
    'ensure-clojure-lib
    [
      {
       :form '(defn ensure-clojure-lib [lib-name] "Makes sure there is a external library called lib-name pointing to the internal clojure.jar" (when-not (some (fn* [p1__2310] (.contains (.getDisplayName p1__2310) lib-name)) (.getLibraries (LibraryManager/getDefault))) (create-external-clojure-lib lib-name))),
       :name 'ensure-clojure-lib,
       :arglist "[arguments]"
       :source-file "/Users/nsinghal/Work/scratch.clj"
       :static true,
       :lang :clojure,
       :line-end 50,
       :type :func,
       :line 46,
       :symbol-type 'defn
       }
     ]
    'create-external-clojure-lib
    [
      {:form '(defn create-external-clojure-lib [lib-name] "Creates an external library called lib-name using the internal clojure.jar installed with the module.\nThis is a temporary solution.  Users of the plugin should point to the desired clojure.jar\nthey want for their projects.  This will be a preference" (create-library (LibraryManager/getDefault) "j2se" lib-name (locate-clojure-jar)))
       :name 'create-external-clojure-lib
       :static true
       :lang :clojure
       :line-end 44
       :type :macro
       :line 40
       :symbol-type 'defn}
     ]
    'create-library
    [
      {:form '(defn create-library [lib-mgr type name jar-file] (.createLibrary lib-mgr type name {"classpath" [(URL. (str "jar:file:" (.replace (.getFile (.toURL jar-file)) " " "%20") "!/"))]}))
       :name 'create-library
       :static true
       :lang :clojure
       :line-end 38
       :type :multimethod
;       :line 34
       :symbol-type 'defn}
     ]
    'locate-clojure-jar
    [
      {:form '(defn locate-clojure-jar [] "returns a File object pointing to the installed clojure.lib with the Module" (.locate (org.openide.modules.InstalledFileLocator/getDefault) "modules/ext/clojure.jar" nil false))
       :name 'locate-clojure-jar
       :static true
       :lang :clojure
       :line-end 32
       :type :var
       :line 29
       :symbol-type 'defn}
     ]
    'org.enclojure.ide.nb.editor.utils
    [
      {:import '((org.openide.modules InstalledFileLocator) (java.io File) (java.net URL URLEncoder) (org.netbeans.api.project.libraries LibraryManager Library))
       :form '(ns org.enclojure.ide.nb.editor.utils (:use org.enclojure.repl.repl-manager org.enclojure.repl.repl-manager-ui org.enclojure.ide.common.classpath-utils) (:import (org.ope
                                                                                                                                                                                 nide.modules InstalledFileLocator) (java.io File) (java.net URL URLEncoder) (org.netbeans.api.project.libraries LibraryManager Library)))
       :name 'org.enclojure.ide.nb.editor.utils
       :static true
       :lang :clojure
       :line-end 27
       :type :namespace
       :use '(org.enclojure.repl.repl-manager org.enclojure.repl.repl-manager-ui org.enclojure.ide.common.classpath-utils)
       :line 18
       :symbol-type 'ns}
     ]
    'comment
    [
      {:form '(comment)
       :name 'comment
       :static true
       :lang :clojure
       :line-end 16
       :type :comment
       :line 2
       :symbol-type 'comment}
     ]
    }
   })

(def f "/Users/ericthorsen/dev/enclojure-nb-clojure-plugin/org.enclojure.ide.nb.clojure_plugin_suite/org.enclojure.ide.nb.editor/src/org/enclojure/ide/navigator/navigator_panel.clj")

(def -icons-
    {   'ns "namespace.png"
        'defmacro "macro.png"
        'defn "function.png"
        'defn- "function-private.png"
        'def "var.png"
        'comment "comment.png"
        'defmulti "multimethod.png"
        'defmethod "method.png"
        })

(defn load-image [str-name]
  (let [url (.getResource (.getContextClassLoader (Thread/currentThread))
                    str-name)]
    (ImageIcon.              
        (ImageIO/read #^java.net.URL url))))

; This must already live somewhere in java???
(def -image-cache- (atom {}))

(defn load-icon [n]
    (let [img-key (str "org/enclojure/ide/resources/" n)]
        (or (@-image-cache- img-key)
          (let [img (load-image #^java.net.URL img-key)]
            (swap! -image-cache- assoc img-key img)
            img))))

(defn swap-icon [renderer sym]
  (try
    (let [icon (load-icon (sym -icons-))]
        (.setLeafIcon renderer icon)
        (.setOpenIcon renderer icon)
        (.setClosedIcon renderer icon))
    (catch Throwable t
      (logger/error (.getMessage t))))
  renderer)

(defn get-cell-renderer-proxy []
  (proxy [DefaultTreeCellRenderer][]
    (getTreeCellRendererComponent
      [tree value selected expanded leaf row has-focus]
      (let [{:keys [name form arglists symbol-type disp-value] :as data}
            (if (vector? value) (first value) value)]
        (swap-icon this (if (contains? -icons- symbol-type) symbol-type 'comment))
        (proxy-super getTreeCellRendererComponent tree value selected expanded leaf row has-focus)
        ))))

(def -attrib-styles-
  {:symbol-type "color:#990000;font-style:italic;"
   :name nil
   :arglists "color:#555555;"
   :disp-value "color:blue;"
   })

(defn wrap-style [k v]
  (if-let [s (-attrib-styles- k)]
    (str "<span style=\"" s "\">" v "</span>")
    v))

(defn get-tree-proxy []
  (proxy [JTree] []
    (convertValueToText [value selected expanded leaf row hasFocus]
      (let [{:keys [name form arglists symbol-type disp-value]}
            (if (vector? value) (first value) value)]
        (str
          "<html>"
          (wrap-style :name name)
          (when disp-value
            (str " " (wrap-style :disp-value  disp-value)))
          (when arglists
            (str " " (wrap-style :arglists arglists)))
          " : "
          (wrap-style :symbol-type symbol-type)
          "</html>"
;          "<html>("
;          (wrap-style :symbol-type symbol-type)
;          " "
;          (wrap-style :name name)
;          (when disp-value
;            (str " " (wrap-style :disp-value  disp-value)))
;          (when arglists
;            (str " " (wrap-style :arglists arglists)))
;          ")</html>"
          )))))

(defn get-tree-selection-listener []
  (proxy [TreeSelectionListener] []
    (valueChanged [e]
      (when (.isAddedPath e)
        (let [tree-path (.getPath e)
              obj (.getPathComponent tree-path (dec (.getPathCount tree-path)))]
          (javax.swing.JOptionPane/showMessageDialog nil (str (first obj))))))))


(defn get-mouse-listener [tree open-file-fn]
  (proxy [MouseAdapter] []
    (mousePressed [e]
      (let [tree-path (.getPathForLocation tree (.getX e) (.getY e))
            obj (when tree-path
                  (.getPathComponent tree-path (dec (.getPathCount tree-path))))]
        (when (and (= (.getClickCount e) 2) obj)
          (let [{:keys [source-file start]}
                (if (vector? obj) (first obj) obj)]
            (open-file-fn source-file start)))))))
          ;(javax.swing.JOptionPane/showMessageDialog nil (:source-file (first (second obj)))))))))

(defmulti make-node
  (fn [[k v] sort-fn]
    (set (map :symbol-type v))))

(defmethod make-node #{'defmulti 'defmethod}
  [[k v] sort-fn]
  (let [parent (filter #(= 'defmulti (:symbol-type %1)) v)
        children (sort-by sort-fn (filter #(= 'defmethod (:symbol-type %1)) v))]
        [(first parent) (vec children)]))

(defmethod make-node :default
  [[k v] sort-fn]
  (first v))

(defn get-sort-keyfn [kw]
  (fn [n]
    (let [r (kw (if (vector? n) (first n) n))]
      (if (seq? r) (first r) r))))

(def -default-sort-
  (get-sort-keyfn :name))

(defn symbols-to-tree [symbols sort-fn]
  (loop [symbols symbols
         root []
         leaves []]
    (if-let [[n [f & fs] :as s] (first symbols)]
      (let [r (when (= :namespace (:type f)) f)]
        (recur (rest symbols)
            (or r root)
            (if r leaves (conj leaves (make-node s sort-fn)))))
      [root (sort-by sort-fn leaves)])))

(defn tree-model-3 [symbols sort-fn]
  (logger/info "In tree-model " (apply str (interpose "," (keys symbols))))
  (let [tree (symbols-to-tree symbols sort-fn)]
    (proxy [TreeModel] []
      (getRoot []
;        (logger/info "getRoot " (class tree))
        tree)
      (addTreeModelListener [treeModelListener])
      (getChild [parent index]
;        (logger/info "getChild type " (class parent) " i=" index)
        (when (vector? parent)
          (let [item (nth (seq (sort-by sort-fn
                            (fnext parent))) index)]
;            (logger/info "item is " item)
            item)))
      (getChildCount [parent]
        (let [cnt (if (vector? parent)
                    (count (fnext parent))
                        0)]
;        (logger/info "getChildCount type " cnt)
          cnt))
      (isLeaf [node]
 ;       (logger/info "isLeaf type " (class node)
 ;         " " (not (vector? node)))
        (not (vector? node)))
      (valueForPathChanged [path newValue])
      (getIndexOfChild [parent child] -1)
      (removeTreeModelListener [treeModelListener]))))

(defn expand-all [#^JTree tree #^TreePath parent expand?]
  (let [node (.getLastPathComponent parent)]
    (when (>= (.getChildCount node) 0)
      (loop [children (enumeration-seq (.children node))]
        (if-let [e (first children)]
          (do
            (expand-all tree
              (.pathByAddingChild parent
                (.nextElement e)) expand?)
            (recur (rest children))))))
    (if expand?
      (.expandPath tree parent)
      (.collapsePath tree parent))))

(defn expand-collapse-all [tree-state]
  (let [{:keys [tree expanded?]} @tree-state]
    (let [root (-> tree .getModel .getRoot)
          new-state (not expanded?)
          parent (TreePath. root)]
      (expand-all tree parent new-state)
      (dosync (alter tree-state
                assoc :expanded? new-state)))
    tree-state))

(defn add-action-listener-for-sort [component jtree data sort-fn]
  (proxy [ActionListener][]
    (actionPerformed [action-event]
      (.setModel jtree (tree-model-3 @data sort-fn)))))

; This is gross
(def --nav-panel-fn-- (atom {}))
(def --symbol-cache-fn-- (atom nil))

(defn create-navigator-tree [jpanel open-file-fn]
  (logger/info "Navigator create tree {} !!!!!!!!!!!" jpanel)
  (let [data-ref (atom {})
        jtree (get-tree-proxy)
        mypanel (CljNavigatorViewPanel.)
        alpha-sort-btn (.jToggleButtonAlphaSort mypanel)
        source-sort-btn (.jToggleButtonSortPos mypanel)
        expand-all-btn (.jToggleButtonExpandAll mypanel)]
    (logger/info "Navigator create tree calling the do section")
    (do
        (.addActionListener alpha-sort-btn
          (add-action-listener-for-sort alpha-sort-btn jtree
            data-ref (get-sort-keyfn :name)))
        (.addActionListener source-sort-btn
          (add-action-listener-for-sort source-sort-btn jtree
            data-ref (get-sort-keyfn :start)))
        (.setCellRenderer jtree (get-cell-renderer-proxy))
        (.addMouseListener jtree (get-mouse-listener jtree open-file-fn))
        (.setModel jtree (tree-model-3 @data-ref -default-sort-))
        (.setViewport (.jScrollPane mypanel)
          (doto (JViewport.) (.setView jtree))))
    (doto jpanel
      (.add
        mypanel
        BorderLayout/CENTER))
        (fn [data]
          (logger/info "Context changed {}" data)
          (when data
            (swap! data-ref (fn [_] data))
            (.setModel jtree (tree-model-3 data -default-sort-))))))

(defn navigator
  [title data]
  (let [jpanel (JFrame. title)
        jtree (get-tree-proxy)]
    (doto jpanel
      (.add
        (JScrollPane.
          (do
            (.setCellRenderer jtree (get-cell-renderer-proxy))
            (.addMouseListener jtree (get-mouse-listener jtree))
            (.setModel jtree (tree-model-3 data -default-sort-))
            ;(.addTreeSelectionListener jtree (get-tree-selection-listener))
            jtree))
        BorderLayout/CENTER)
      (.setSize 400 600)
      (.setVisible true))))

;(defn setup-action-listeners [tree-panel data]

(defn navigator2
  [title data]
  (let [jpanel (JFrame. title)
        mypanel (CljNavigatorViewPanel.)
        jtree (get-tree-proxy)
        alpha-sort-btn (.jToggleButtonAlphaSort mypanel)
        source-sort-btn (.jToggleButtonSortPos mypanel)
        expand-all-btn (.jToggleButtonExpandAll mypanel)]
      (do
        (.addActionListener alpha-sort-btn
          (add-action-listener-for-sort alpha-sort-btn jtree
            data (get-sort-keyfn :name)))
        (.addActionListener source-sort-btn
          (add-action-listener-for-sort source-sort-btn jtree
            data (get-sort-keyfn :start)))
        (.setCellRenderer jtree (get-cell-renderer-proxy))
        (.addMouseListener jtree (get-mouse-listener jtree))
        (.setModel jtree (tree-model-3 data -default-sort-))
        (.setViewport (.jScrollPane mypanel)
          (doto (JViewport.) (.setView jtree))))
    (doto jpanel
      (.add
        mypanel
        BorderLayout/CENTER)
      (.setSize 400 600)
      (.setVisible true))
  {:tree jtree :nav-panel mypanel}))

(defn new-context [jpanel context]
  (logger/info "Nav got {} {}" (class jpanel) (hash jpanel))
    (let [nav-panel-fn (or (@--nav-panel-fn-- (hash jpanel))
        (swap! --nav-panel-fn--
          (fn [m] (assoc m (hash jpanel)
                    (create-navigator-tree jpanel editor-utils/open-editor-file)))))]
        (logger/info "Nav panel is now {}" nav-panel-fn)
    (when context
      (let [file (.getPrimaryFile (first context))
	    _ (logger/info "Nav got file {}" file)
	    _ (logger/info "Nav looking for file {}" (.getPath file))
	    syms (symbol-caching/get-nav-data-for file)
	    _ (logger/info "Nav got symbol count {}" (count syms))]
	(nav-panel-fn syms)))))

;  (let [nav-panel-fn (or (@--nav-panel-fn-- jpanel)
;			 (first (vals
;				 (swap! --nav-panel-fn--
;					(fn [_] {jpanel
;						 (create-navigator-tree
;						  jpanel editor-utils/open-editor-file)})))))]
 ;   (logger/info "Nav got new context {} str {}" (class (first context)) (str context))
  ;  (logger/info "Nav nav-panel-fn {}" nav-panel-fn)
   ; (when context
    ;  (let [file (.getPrimaryFile (first context))
;	    _ (logger/info "Nav got file {}" file)
;	    _ (logger/info "Nav looking for file {}" (.getPath file))
;	    syms (symbol-caching/get-nav-data-for file)
;	    _ (logger/info "Nav got symbol count {}" (count syms))]
;	(nav-panel-fn syms)))))


;(defn test-nav []
;  (navigator "Testing navigator"
;    (get-nav-data-for (:file (utils/get-current-editor-data)))))
;
;(defn test-nav2 []
;  (navigator2 "Testing navigator"
;    (get-nav-data-for (:file (utils/get-current-editor-data)))))
