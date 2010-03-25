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
*    Author: Eric Thorsen
*******************************************************************************
)
*/
package org.enclojure.ide.nb.actions;

import clojure.lang.RT;
import java.awt.event.ActionEvent;
import java.util.Map;
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

public class LoadAllClojureFilesForProjectAction extends AbstractAction implements ContextAwareAction {

    static final String ns = "org.enclojure.ide.nb.editor.repl-win";
    static final String contextMenuNameFunc = "load-all-source-context-menu-name";
    static final String actionFunc = "loadall-source-for-project";
    static final String checkEnabledFunc = "check-enabled-for-load-all-sources?";

       public static synchronized LoadAllClojureFilesForProjectAction create(Map<?,?> args) throws Exception {
        Integer group = (Integer)args.get("source-group");
        if(group == null) {
            throw new Exception("source-group attribute missing! Cannot create LoadAllClojureFilesForProjectAction.");
        }
        return new LoadAllClojureFilesForProjectAction(group==null?0:group.intValue());
       }

    int sourceGroup=0;
    
    public LoadAllClojureFilesForProjectAction(int sourceGroup)
        { this.sourceGroup = sourceGroup;}

    public void actionPerformed(ActionEvent e) {assert false;}
    public Action createContextAwareInstance(Lookup context) {
        return new ContextAction(context);
    }
    
    private boolean enable(Project p) {
        try {
            return ((Boolean) RT.var(ns,checkEnabledFunc).invoke(p,this.sourceGroup)).booleanValue();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    private String labelFor(Project p) {
        assert p != null;
        try {
            return (String) RT.var(ns,contextMenuNameFunc).invoke(p,this.sourceGroup);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return "Error";
    }

    private void perform(Project p) {
        assert p != null;
        try {
            RT.var(ns,actionFunc).invoke(p,this.sourceGroup);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private final class ContextAction extends AbstractAction implements Presenter.Popup {
        private final Project p;

        public ContextAction(Lookup context) {
            Project _p =  context.lookup(Project.class);
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

