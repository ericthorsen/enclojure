/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Micro//S ystems, Inc. All Rights Reserved.
 *
 * ThorTech L.L.C. elects to include this software in this distribution
 * under the GNU General Public License, version 2
 * (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
 * exception (http://www.gnu.org/software/classpath/license.html).
 *
 *(comment
 *******************************************************************************
 *    Copyright (c) ThorTech, L.L.C. All rights reserved.
 *    The use and distribution terms for this software are covered by the
 *    GNU General Public License, version 2
 *    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
 *    exception (http://www.gnu.org/software/classpath/license.html)
 *    which can be found in the file GPL-2.0+ClasspathException.txt at the root
 *    of this distribution.
 *    By using this software in any fashion, you are agreeing to be bound by
 *    the terms of this license.
 *    You must not remove this notice, or any other, from this software.
 *******************************************************************************)
*/
package org.enclojure.ide.debugger.actions;

import clojure.lang.RT;
import clojure.lang.Var;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Set;
import org.netbeans.api.debugger.ActionsManager;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.api.debugger.DebuggerEngine;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.DebuggerManagerListener;
import org.netbeans.api.debugger.Session;
import org.netbeans.api.debugger.Watch;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.project.Project;
import org.netbeans.spi.debugger.jpda.EditorContext;
import org.netbeans.spi.project.ActionProvider;
import org.openide.util.Exceptions;
import org.openide.windows.TopComponent;
import org.enclojure.ide.debugger.breakpoints.ClojureLineBreakpoint;
import org.enclojure.ide.debugger.util.Utils;
import org.netbeans.spi.debugger.ActionsProviderSupport;

public class ClojureRunToCursorActionProvider extends ActionsProviderSupport {
    
    static final Var sessionRemovedFn =
            RT.var("org.enclojure.ide.debugger.jdi", "session-removed");

    private EditorContext editorContext;
    private ClojureLineBreakpoint breakpoint;
        
    {
        editorContext = (EditorContext) DebuggerManager.
            getDebuggerManager().lookupFirst(null, EditorContext.class);
        
        Listener listener = new Listener ();
        MainProjectManager.getDefault ().addPropertyChangeListener (listener);
        TopComponent.getRegistry ().addPropertyChangeListener (listener);
        DebuggerManager.getDebuggerManager ().addDebuggerListener (listener);

        setEnabled(ActionsManager.ACTION_RUN_TO_CURSOR, shouldBeEnabled());
    }
    
    public Set getActions() {
        return Collections.singleton (ActionsManager.ACTION_RUN_TO_CURSOR);
    }
    
    public void doAction (Object action) {
        
        // 1) set breakpoint
        removeBreakpoint();
        createBreakpoint();
        
        // 2) start debugging of project
        ((ActionProvider) MainProjectManager.getDefault().
            getMainProject().getLookup().lookup(
                ActionProvider.class
            )).invokeAction (
                ActionProvider.COMMAND_DEBUG, 
                MainProjectManager.getDefault ().getMainProject ().getLookup ()
            );
    }
    
    private boolean shouldBeEnabled () {

        if (/* some module disabled? */ editorContext == null || !Utils.isClojure(editorContext.getCurrentURL())) {
            return false;
        }
        
        // check if current project supports this action
        Project p = MainProjectManager.getDefault ().getMainProject ();
        if (p == null) return false;
        ActionProvider actionProvider = (ActionProvider)p.getLookup ().lookup (ActionProvider.class);
        if (actionProvider == null) return false;

        String[] sa = actionProvider.getSupportedActions ();
        int i, k = sa.length;
        for (i = 0; i < k; i++) {
            if (ActionProvider.COMMAND_DEBUG.equals (sa [i])) {
                break;
            }
        }
        if (i == k) {
            return false;
        }

        // check if this action should be enabled
        return ((ActionProvider) p.getLookup ().lookup (
                ActionProvider.class
            )).isActionEnabled (
                ActionProvider.COMMAND_DEBUG, 
                p.getLookup ()
            );
    }

    private void createBreakpoint() {
        breakpoint = ClojureLineBreakpoint.create (
            editorContext.getCurrentURL (),
            editorContext.getCurrentLineNumber ()
        );
        breakpoint.setHidden (true);
        DebuggerManager.getDebuggerManager ().addBreakpoint (breakpoint);
    }
    
    private void removeBreakpoint() {
        if (breakpoint != null) {
            DebuggerManager.getDebuggerManager ().removeBreakpoint (breakpoint);
            breakpoint = null;
        }
    }
    
    private class Listener implements PropertyChangeListener, DebuggerManagerListener {
        public void propertyChange (PropertyChangeEvent e) {
            if ((e == null) || (TopComponent.Registry.PROP_OPENED.equals(e.getPropertyName())))
                return;
            if (e.getPropertyName () == JPDADebugger.PROP_STATE) {
                int state = ((Integer) e.getNewValue ()).intValue ();
                if (state == JPDADebugger.STATE_DISCONNECTED || state == JPDADebugger.STATE_STOPPED)
                    removeBreakpoint ();
                return;
            }

            setEnabled (
                ActionsManager.ACTION_RUN_TO_CURSOR,
                shouldBeEnabled ()
            );
        }
        
        public void sessionRemoved (Session session) {
            try {
                sessionRemovedFn.invoke(session);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }

            removeBreakpoint();
        }
        
        public void breakpointAdded (Breakpoint breakpoint) {}
        public void breakpointRemoved (Breakpoint breakpoint) {}
        public Breakpoint[] initBreakpoints () {
            return new Breakpoint [0];
        }
        public void initWatches () {}
        public void sessionAdded (Session session) {}
        public void watchAdded (Watch watch) {}
        public void watchRemoved (Watch watch) {}

        public void engineAdded (DebuggerEngine engine) {
            JPDADebugger debugger = (JPDADebugger) engine.lookupFirst 
                (null, JPDADebugger.class);
            if (debugger == null) return;
            debugger.addPropertyChangeListener (
                JPDADebugger.PROP_STATE,
                this
            );
        }
        
        public void engineRemoved (DebuggerEngine engine) {
            JPDADebugger debugger = (JPDADebugger) engine.lookupFirst 
                (null, JPDADebugger.class);
            if (debugger == null) return;
            debugger.removePropertyChangeListener (
                JPDADebugger.PROP_STATE,
                this
            );
        }

    }
}
