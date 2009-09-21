
(comment
  ; Interface used to de-couple the repl history from the IDE.
  ; The getHistoryLogFile returns a path (String) where the log file should live.
  ; showHistory [path] is called when the user clicks on the 'Show History' action.
  ; NOTE:  Could/should add clear and make the history implementation opaque.
  )

(gen-interface
  :name org.enclojure.repl.IReplHistorySupport
  :implements [java.lang.Object]
  :methods
  [[showHistory [java.lang.String] java.lang.Void]
   [getHistoryLogFile [java.lang.String] java.lang.String]
   ]
   )

   



