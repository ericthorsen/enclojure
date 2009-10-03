(comment
  ; Interface used to de-couple the repl history from the IDE.
  ; The getHistoryLogFile returns a path (String) where the log file should live.
  ; showHistory [path] is called when the user clicks on the 'Show History' action.
  ; NOTE:  Could/should add clear and make the history implementation opaque.
  )

(ns org.enclojure.ide.repl.interface-factory
  (:import
    (org.enclojure.ide.repl ReplPanel)))
    
(gen-interface
  :name org.enclojure.repl.IReplContext
  :implements [java.lang.Object]
  :methods
  [[getId [] java.lang.Object]
   [startupExpr [] java.lang.String]
   ])

(gen-interface
  :name org.enclojure.repl.IReplExternalContext
  :extends [org.enclojure.repl.IReplContext]
  :implements [java.lang.Object]
  :methods
  [[getHost [] java.lang.String]
   [getPort [] java.lang.Integer]]
   )

(gen-interface
  :name org.enclojure.repl.IReplManagedExternalContext
  :extends [org.enclojure.repl.IReplExternalContext]
  :implements [java.lang.Object]
  :methods
  [[getClassPath [java.lang.Object] java.lang.String]
   [additionalJVMArgs [] java.lang.String]]
   )

(gen-interface
  :name org.enclojure.repl.IReplWindow
  :implements [java.lang.Object]
  :methods
  [[open [] javax.swing.JComponent]
   [makeActive [] java.lang.Void]
   [showHistory [] java.lang.Void]
   [getHistoryLogFile [] java.lang.String]
   ]
   )

(gen-interface
  :name org.enclojure.repl.IReplPanel
  :implements [java.lang.Object]
  :methods
  [])

(gen-interface
  :name org.enclojure.repl.IRepl
  :implements [java.lang.Object]
  :methods
  [[getReplWindow [] org.enclojure.repl.IReplWindow]
   [getReplPanel [] org.enclojure.ide.repl.ReplPanel]
   [getReplContext [] org.enclojure.repl.IReplContext]
   ]
   )

(gen-interface
  :name org.enclojure.repl.IReplWindowFactory
  :implements [java.lang.Object]
  :methods
  [[makeReplWindow [org.enclojure.ide.repl.ReplPanel 
                    org.enclojure.repl.IReplContext] org.enclojure.repl.IReplWindow]]
   )
