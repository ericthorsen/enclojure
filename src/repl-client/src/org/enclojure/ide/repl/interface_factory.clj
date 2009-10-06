(comment
  ; Interface used to de-couple the repl history from the IDE.
  ; The getHistoryLogFile returns a path (String) where the log file should live.
  ; showHistory [path] is called when the user clicks on the 'Show History' action.
  ; NOTE:  Could/should add clear and make the history implementation opaque.
  )

(ns #^{ :author "Eric Thorsen",
        :doc "Interface definitions for plugging into the Enclojure REPLs.
        There are 2 interfaces that need to be implemented for plugging
        in the Enclojure REPL framework into another windowing system:
        1. IReplWindow
            This is a container for ReplPanel.  
        See org.enclojure.ide.repl.DefReplWindowFactory/-makeReplWindow for 
        an example.
        2. IReplWindowFactory
        This is a factory that the Enclojure repl factory uses for creating repl
        windows.  
        See org.enclojure.ide.repl.DefReplWindowFactory for an example implementation.
        Also see  org.enclojure.ide.repl.factory and 
        org.enclojure.ide.repl.factory-test for more information on how these work.
        "
       }
  org.enclojure.ide.repl.interface-factory
  (:import
    (org.enclojure.ide.repl ReplPanel)))

(gen-interface
  :name org.enclojure.repl.IReplWindow
  :implements [java.lang.Object]
  :methods
  [[getComponent [] java.awt.Component]
   [open [] java.awt.Component]
   [makeActive [] java.awt.Component]
   [showHistory [] java.awt.Component]
   [getHistoryLogFile [] java.lang.String]
   ]
   )

(gen-interface
  :name org.enclojure.repl.IReplWindowFactory
  :implements [java.lang.Object]
  :methods
  [[makeReplWindow [org.enclojure.ide.repl.ReplPanel java.util.Map]
                    org.enclojure.repl.IReplWindow]]
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