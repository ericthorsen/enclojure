(comment
  ; Interface used to de-couple the repl history from the IDE.
  ; The getHistoryLogFile returns a path (String) where the log file should live.
  ; showHistory [path] is called when the user clicks on the 'Show History' action.
  ; NOTE:  Could/should add clear and make the history implementation opaque.
  )

(ns org.enclojure.ide.repl.interface-factory
  (:import
    (org.enclojure.ide.repl ReplPanel)))

;This will go away
(gen-interface
  :name org.enclojure.repl.IReplHistorySupport
  :implements [java.lang.Object]
  :methods
  [[showHistory [java.lang.String] java.lang.Void]
   [getHistoryLogFile [java.lang.String] java.lang.String]
   ]
   )

(gen-interface
  :name org.enclojure.repl.IReplWindow
  :implements [java.lang.Object]
  :methods
  [[getComponent [] javax.swing.JComponent]
   [open [] javax.swing.JComponent]
   [makeActive [] java.awt.Component]
   [showHistory [] java.lang.Void]
   [getHistoryLogFile [] java.lang.String]
   ]
   )

(gen-interface
  :name org.enclojure.repl.IRepl
  :implements [java.lang.Object]
  :methods
  [[getReplWindow [] org.enclojure.repl.IReplWindow]
   [getReplPanel [] org.enclojure.ide.repl.ReplPanel]
   [getReplContext [] java.util.Map]
   ]
   )

(gen-interface
  :name org.enclojure.repl.IReplWindowFactory
  :implements [java.lang.Object]
  :methods
  [[makeReplWindow [org.enclojure.ide.repl.ReplPanel java.util.Map]
                    org.enclojure.repl.IReplWindow]]
   )
