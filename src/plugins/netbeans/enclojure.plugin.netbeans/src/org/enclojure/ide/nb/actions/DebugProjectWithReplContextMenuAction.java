/*
(comment
*******************************************************************************
*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
*    The use and distribution terms for this software are covered by the
*    GNU General Public License, version 2
*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
*    exception (http://www.gnu.org/software/classpath/license.html)
*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
*    of this distribution.
*    By using this software in any fashion, you are agreeing to be bound by
*    the terms of this license.
*    You must not remove this notice, or any other, from this software.
*******************************************************************************
*    Author: Narayan Singhal
*******************************************************************************
)
*/
package org.enclojure.ide.nb.actions;

import clojure.lang.RT;
import clojure.lang.Var;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.netbeans.api.project.Project;
import org.openide.awt.DynamicMenuContent;
import org.openide.awt.Mnemonics;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.actions.Presenter;

public class DebugProjectWithReplContextMenuAction extends AbstractAction implements ContextAwareAction {

    static final Var debugContextMenuNameFn =
        RT.var("org.enclojure.ide.nb.editor.repl-win", "debug-context-menu-name");
    static final Var attachDetachDebugger =
        RT.var("org.enclojure.ide.nb.editor.repl-win", "start-attach-detach-debugger");

    public void actionPerformed(ActionEvent e) {assert false;}
    public Action createContextAwareInstance(Lookup context) {
        return new ContextAction(context);
    }
    private boolean enable(Project p) {
        assert p != null;

        //??Put code here if we want to enable REPL only for clojure project or
        //any other restrictions.

//        char c = ProjectUtils.getInformation(p).getDisplayName().charAt(0);
//        return c >= 'A' && c <= 'M';
        return true;
    }

    private String labelFor(Project p) {
        assert p != null;
        try {
            //??Project name can also be added to this returning string
            //??return ReplTopComponent.findInstance().GetDebugContextMenuName(p);
            return (String) debugContextMenuNameFn.invoke(p);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }

        return "Error";
    }

    private void perform(Project p) {
        assert p != null;
        try {
//        String msg = "Project location: " + FileUtil.getFileDisplayName(p.getProjectDirectory());
//        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(msg));
            //??ReplTopComponent.findInstance().AttachDetachDebugger(p);
            attachDetachDebugger.invoke(p);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private final class ContextAction extends AbstractAction implements Presenter.Popup {
        private final Project p;

        public ContextAction(Lookup context) {
            Project _p = (Project) context.lookup(Project.class);
            p = (_p != null && enable(_p)) ? _p : null;
        }
        public void actionPerformed(ActionEvent e) {
            perform(p);
        }
        public JMenuItem getPopupPresenter() {
            class Presenter extends JMenuItem implements DynamicMenuContent {
                public Presenter() {
                    super(ContextAction.this);
                }
                public JComponent[] getMenuPresenters() {
                    if (p != null) {
                        Mnemonics.setLocalizedText(this, labelFor(p));
                        return new JComponent[] {this};
                    } else {
                        return new JComponent[0];
                    }
                }
                public JComponent[] synchMenuPresenters(JComponent[] items) {
                    return getMenuPresenters();
                }
            }

            //Not implemented so disabling the menu item
            JMenuItem menuItem = new Presenter();
            //??menuItem.setEnabled(false);
            return menuItem;
        }
    }
}
