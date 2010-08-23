/*
(comment
 *
 *    Copyright (c) ThorTech, L.L.C.. All rights reserved.
 *    The use and distribution terms for this software are covered by the
 *    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *    which can be found in the file epl-v10.html at the root of this distribution.
 *    By using this software in any fashion, you are agreeing to be bound by
 *    the terms of this license.
 *    You must not remove this notice, or any other, from this software.
 *
 *    Author: Narayan Singhal, Eric Thorsen
)
 */

/*
 * ReplPanel.java
 *
 * Created on Nov 24, 2008, 11:52:02 AM
 */
package org.enclojure.ide.repl;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import java.awt.Event;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JEditorPane;
import javax.swing.JPopupMenu.Separator;
import javax.swing.KeyStroke;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;


/**
 *
 * @author nsinghal
 */
public class ReplPanel extends javax.swing.JPanel {
    public int _promptPos = 0;
    volatile IFn _evaluateInReplFn = (IFn)RT.var("org.enclojure.ide.repl.repl-panel", "evaluate-in-repl");
    volatile IFn _processKeyEventFunc = (IFn)RT.var("org.enclojure.ide.repl.repl-panel", "process-key-input");
    volatile IFn _dispShowHistoryEventsFunc = (IFn)RT.var("org.enclojure.ide.repl.repl-panel", "show-repl-history");
    volatile IFn _clearReplHistoryEventsFunc = (IFn)RT.var("org.enclojure.ide.repl.repl-panel", "clear-history");
    volatile IFn _toggleStackTraceFunc = (IFn)RT.var("org.enclojure.ide.repl.repl-panel", "set-print-stack-trace-on-error");
    volatile IFn _togglePrettyPrintFunc = (IFn)RT.var("org.enclojure.ide.repl.repl-panel", "set-print-pretty");

    static final Var getPrettyInfo =
            RT.var("org.enclojure.ide.repl.repl-manager", "get-pretty-info");
    volatile IFn _replDataFn;
    

    static {
        try {
            RT.var("clojure.core", "require").invoke(Symbol.create("org.enclojure.ide.repl.repl-panel"));
            RT.var("clojure.core", "require").invoke(Symbol.create("org.enclojure.repl.main"));
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    
    /////////////////////////////////////////////////////
    /////////NEW START
    /////////////////////////////////////////////////////
    static final Var bindLocalRepl =
        RT.var("org.enclojure.repl.repl-main", "bind-local-repl");
    String _replID;
    private IFn _replFn = null;
    private IFn _resetReplFn = null;
    private Boolean menuSetupDone = false;
    public int _debugPort = 0;
    private UndoManager _undoManager = new UndoManager();

    public ReplPanel(String replID)
    {
        _replID = replID;
        initComponents();

        createReplEditorPane();
        setupMenu();
    }

    public javax.swing.JEditorPane _replEditorPane;
    public void createReplEditorPane()
    {
        try {
            Var createReplEditorPaneFn = 
                    RT.var("org.enclojure.ide.repl.repl-panel"
                            , "create-repl-editor-pane");
            _replEditorPane = (JEditorPane) createReplEditorPaneFn.invoke(this);
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        //??_replEditorPane.setInheritsPopupMenu(true);
        jScrollPaneRepl.setViewportView(_replEditorPane);

        _replEditorPane.getDocument().addUndoableEditListener(new UndoableEditListener() {

            public void undoableEditHappened(UndoableEditEvent e) {
                _undoManager.addEdit(e.getEdit());
            }
        });

        _replEditorPane.addKeyListener(new java.awt.event.KeyAdapter() {

            public void keyTyped(java.awt.event.KeyEvent evt) {
                processCharInput(evt);
            }

            public void keyPressed(java.awt.event.KeyEvent evt) {
                processKeyInput(evt);
            }
        });
    }

    public void resultReceived(JEditorPane pane, String result)
    {
       Document doc = (Document) pane.getDocument();
       try {
           if(pane != _replEditorPane)
               result = "\n" + result;
           
           doc.insertString(doc.getLength(), result, null);
           pane.setCaretPosition(doc.getLength());
       } catch (BadLocationException ex) {
           Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
       }

       if(pane == jEditorPaneOut && result.contains("Listening for transport dt_socket at address"))
       {
           //Listening for transport dt_socket at address: 58896
           String portString = result.substring(result.indexOf(":") + 1);
           _debugPort = Integer.parseInt(portString.trim());
       }

       if(pane == _replEditorPane)
       {
           _promptPos =  _replEditorPane.getDocument().getLength();
           jTabbedPane1.setSelectedIndex(0);
           _undoManager.die();
       }
    }

    public void setReplFunction(IFn replFn)
    {
        _promptPos = 0;
        _replFn = replFn;
    }

    public void setResetReplFn(IFn resetReplFn)
    {
        _resetReplFn = resetReplFn;
    }

    protected void processCharInput(java.awt.event.KeyEvent evt) {
        try {
            int code = evt.getKeyCode();
            char kc = evt.getKeyChar();

            int caretPos = _replEditorPane.getCaretPosition();
            if ((!(code == evt.VK_UP || code == evt.VK_DOWN || 
                    code == evt.VK_LEFT ||
                    code == evt.VK_RIGHT) && caretPos < _promptPos) ||
                    (code == evt.VK_BACK_SPACE && caretPos <= _promptPos) ||
                    (kc == '\b' && caretPos <= _promptPos)) {
                evt.consume();
                return;
            }

        } catch (Exception e) {
            //cljLog.log(java.util.logging.Level.SEVERE, "Exception in jEditorPane1KeyTyped: " + e.getMessage());
        }
    }

    protected void processKeyInput(java.awt.event.KeyEvent evt) {
        try {
            this._processKeyEventFunc.invoke (_replID,
                    _replEditorPane,
                    _promptPos,
                    evt);
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void showReplHistoryActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            _dispShowHistoryEventsFunc.invoke(this._replID,this);
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void clearReplHistoryActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            _clearReplHistoryEventsFunc.invoke(this._replID);
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }



    private void showReplInfoMenuActionPerformed(java.awt.event.ActionEvent evt) {
        try {
            ReplInfoFrame.Show(_replID + ": Repl Startup Information",
                    (String) getPrettyInfo.invoke(_replID));
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void ResetRepl()
    {
        if(_resetReplFn != null)
        {
            try {
                _resetReplFn.invoke();
            } catch (Exception ex) {
                Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    public void printStackTrace ()
    {
        EvaluateInRepl("(.printStackTrace *e *out*)", null);
    }

    Boolean _stackTraceOnError = false;
    public  JCheckBoxMenuItem stackTraceOnErrorMenu = new JCheckBoxMenuItem("*print-stack-trace-on-error*", _stackTraceOnError);

    private void setupMenu()
    {
        if (menuSetupDone) return;
        menuSetupDone = true;

        javax.swing.JPopupMenu contextMenu = _replEditorPane.getComponentPopupMenu();
        if(contextMenu == null)
            contextMenu = new javax.swing.JPopupMenu();

        javax.swing.JMenuItem showStackTrace = new javax.swing.JMenuItem("Print StackTrace");
        showStackTrace.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, Event.CTRL_MASK, false));
        showStackTrace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printStackTrace();
            }
        });
        contextMenu.add(showStackTrace);

       
        stackTraceOnErrorMenu.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printStackTraceToggleButtonActionPerformed(evt);
//                _stackTraceOnError = !_stackTraceOnError;
//                if(_stackTraceOnError)
//                {
//                    EvaluateInRepl("(set! org.enclojure.repl.main/*print-stack-trace-on-error* true)", null);
//                }
//                else
//                {
//                    EvaluateInRepl("(set! org.enclojure.repl.main/*print-stack-trace-on-error* false)", null);
//                }
            }
        });
        contextMenu.add(stackTraceOnErrorMenu);

            javax.swing.JMenuItem replHistoryInfoMenu = new javax.swing.JMenuItem();
            replHistoryInfoMenu.setText("Repl History");
            replHistoryInfoMenu.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    showReplHistoryActionPerformed(evt);
                }
            });
            //Show Repl Information
            javax.swing.JMenuItem showReplInfoMenu = new javax.swing.JMenuItem();
            showReplInfoMenu.setText("Show Repl Information");
            showReplInfoMenu.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    showReplInfoMenuActionPerformed(evt);
                }
            });

            //Reset Repl
            javax.swing.JMenuItem resetReplMenu = new javax.swing.JMenuItem();
            resetReplMenu.setText("Reset Repl");
            resetReplMenu.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    ResetRepl();
                }
            });

            contextMenu.add(new Separator());
            contextMenu.add(showReplInfoMenu);
            contextMenu.add(resetReplMenu);

        _replEditorPane.setComponentPopupMenu(contextMenu);
    }

    
    public void bindKeyListeners() {
        _replEditorPane.addKeyListener(new java.awt.event.KeyAdapter() {

            public void keyTyped(java.awt.event.KeyEvent evt) {
                processCharInput(evt);
            }

            public void keyPressed(java.awt.event.KeyEvent evt) {
                processKeyInput(evt);
            }
        });

        _replErrorPane.addKeyListener(new java.awt.event.KeyAdapter() {

            public void keyPressed(java.awt.event.KeyEvent evt) {
                processKeyInput(evt);
            }
        });

        jEditorPaneOut.addKeyListener(new java.awt.event.KeyAdapter() {

            public void keyPressed(java.awt.event.KeyEvent evt) {
                processKeyInput(evt);
            }
        });
    }


    public JEditorPane GetEditorPane()
    {
        return _replEditorPane;
    }

    public void Disconnect()
    {
        EvaluateInRepl("(in-ns 'user)", null);
        //??replAppendText("REPL is disconnected.", null);
    }


    public void EvaluateInRepl(String exp, String nsNode) {
        try {
            if(nsNode != null)
            {
                this._evaluateInReplFn.invoke(this._replID, exp, nsNode);
            }
            else
            {
                this._evaluateInReplFn.invoke(this._replID, exp);
            }
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //@Override
    //public  String getUIClassID() { return "ReplEditorPaneUI";}
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jToolBar1 = new javax.swing.JToolBar();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jScrollPaneRepl = new javax.swing.JScrollPane();
        jScrollPaneErr = new javax.swing.JScrollPane();
        _replErrorPane = new javax.swing.JEditorPane();
        jScrollPaneOut = new javax.swing.JScrollPane();
        jEditorPaneOut = new javax.swing.JEditorPane();
        replTooBar = new javax.swing.JToolBar();
        prettyPrintToggleButton = new javax.swing.JToggleButton();
        printStackTraceToggleButton = new javax.swing.JToggleButton();
        printStackTraceButton = new javax.swing.JButton();
        replHistoryButton = new javax.swing.JButton();
        clearReplHistoryButton = new javax.swing.JButton();
        replStartupSettingsButton = new javax.swing.JButton();
        replResetButton = new javax.swing.JButton();

        jToolBar1.setRollover(true);

        jTabbedPane1.setTabPlacement(javax.swing.JTabbedPane.BOTTOM);
        jTabbedPane1.addTab("Repl", jScrollPaneRepl);

        jScrollPaneErr.setViewportView(_replErrorPane);

        jTabbedPane1.addTab("*err*", jScrollPaneErr);

        jScrollPaneOut.setViewportView(jEditorPaneOut);

        jTabbedPane1.addTab("*out*", jScrollPaneOut);

        replTooBar.setFloatable(false);
        replTooBar.setOrientation(1);
        replTooBar.setRollover(true);

        prettyPrintToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/enclojure/ide/resources/toggle_pprint.png"))); // NOI18N
        prettyPrintToggleButton.setSelected(true);
        prettyPrintToggleButton.setToolTipText("Toggle Pretty Print");
        prettyPrintToggleButton.setFocusable(false);
        prettyPrintToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        prettyPrintToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        prettyPrintToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prettyPrintToggleButtonActionPerformed(evt);
            }
        });
        replTooBar.add(prettyPrintToggleButton);

        printStackTraceToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/enclojure/ide/resources/toggle_print_stacktrace.png"))); // NOI18N
        printStackTraceToggleButton.setToolTipText("Toggle Print Stacktrace");
        printStackTraceToggleButton.setFocusable(false);
        printStackTraceToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        printStackTraceToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        printStackTraceToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printStackTraceToggleButtonActionPerformed(evt);
            }
        });
        replTooBar.add(printStackTraceToggleButton);

        printStackTraceButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/enclojure/ide/resources/print_last_stacktrace.png"))); // NOI18N
        printStackTraceButton.setToolTipText("Print stack trace of last error.");
        printStackTraceButton.setFocusable(false);
        printStackTraceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        printStackTraceButton.setPreferredSize(new java.awt.Dimension(28, 28));
        printStackTraceButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        printStackTraceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                printStackTraceButtonActionPerformed(evt);
            }
        });
        replTooBar.add(printStackTraceButton);

        replHistoryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/enclojure/ide/resources/repl_history.png"))); // NOI18N
        replHistoryButton.setToolTipText("Show Repl History");
        replHistoryButton.setFocusable(false);
        replHistoryButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        replHistoryButton.setPreferredSize(new java.awt.Dimension(28, 28));
        replHistoryButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        replHistoryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replHistoryButtonActionPerformed(evt);
            }
        });
        replTooBar.add(replHistoryButton);

        clearReplHistoryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/enclojure/ide/resources/clear_repl_history.png"))); // NOI18N
        clearReplHistoryButton.setToolTipText("Clear Repl History");
        clearReplHistoryButton.setFocusable(false);
        clearReplHistoryButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        clearReplHistoryButton.setPreferredSize(new java.awt.Dimension(28, 28));
        clearReplHistoryButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        clearReplHistoryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearReplHistoryButtonActionPerformed(evt);
            }
        });
        replTooBar.add(clearReplHistoryButton);

        replStartupSettingsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/enclojure/ide/resources/metadata.png"))); // NOI18N
        replStartupSettingsButton.setToolTipText("Show Repl Startup Settings");
        replStartupSettingsButton.setFocusable(false);
        replStartupSettingsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        replStartupSettingsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        replStartupSettingsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replStartupSettingsButtonActionPerformed(evt);
            }
        });
        replTooBar.add(replStartupSettingsButton);

        replResetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/enclojure/ide/resources/reset_repl.png"))); // NOI18N
        replResetButton.setToolTipText("Reset REPL");
        replResetButton.setFocusable(false);
        replResetButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        replResetButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        replResetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                replResetButtonActionPerformed(evt);
            }
        });
        replTooBar.add(replResetButton);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(replTooBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 524, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
            .addComponent(replTooBar, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void prettyPrintToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prettyPrintToggleButtonActionPerformed
        try {
            _togglePrettyPrintFunc.invoke(this._replID, this, this.prettyPrintToggleButton.isSelected());
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_prettyPrintToggleButtonActionPerformed

    private void printStackTraceToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printStackTraceToggleButtonActionPerformed
        try {
            _toggleStackTraceFunc.invoke(this._replID, this, this.printStackTraceToggleButton.isSelected());
        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_printStackTraceToggleButtonActionPerformed

    private void replResetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replResetButtonActionPerformed
        this.ResetRepl();
    }//GEN-LAST:event_replResetButtonActionPerformed

    private void replStartupSettingsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replStartupSettingsButtonActionPerformed
        showReplInfoMenuActionPerformed(evt);
    }//GEN-LAST:event_replStartupSettingsButtonActionPerformed

    private void printStackTraceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_printStackTraceButtonActionPerformed
       printStackTrace();
    }//GEN-LAST:event_printStackTraceButtonActionPerformed

    private void replHistoryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_replHistoryButtonActionPerformed
       showReplHistoryActionPerformed(evt);
    }//GEN-LAST:event_replHistoryButtonActionPerformed

    private void clearReplHistoryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearReplHistoryButtonActionPerformed
        clearReplHistoryActionPerformed(evt);
    }//GEN-LAST:event_clearReplHistoryButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JEditorPane _replErrorPane;
    public javax.swing.JButton clearReplHistoryButton;
    public javax.swing.JEditorPane jEditorPaneOut;
    public javax.swing.JScrollPane jScrollPaneErr;
    public javax.swing.JScrollPane jScrollPaneOut;
    public javax.swing.JScrollPane jScrollPaneRepl;
    public javax.swing.JTabbedPane jTabbedPane1;
    public javax.swing.JToolBar jToolBar1;
    public javax.swing.JToggleButton prettyPrintToggleButton;
    public javax.swing.JButton printStackTraceButton;
    public javax.swing.JToggleButton printStackTraceToggleButton;
    public javax.swing.JButton replHistoryButton;
    public javax.swing.JButton replResetButton;
    public javax.swing.JButton replStartupSettingsButton;
    public javax.swing.JToolBar replTooBar;
    // End of variables declaration//GEN-END:variables
}
