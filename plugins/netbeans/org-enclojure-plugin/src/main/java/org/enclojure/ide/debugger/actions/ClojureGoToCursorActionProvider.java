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

import java.beans.*;
import java.util.*;

import org.netbeans.api.debugger.*;
import org.netbeans.api.debugger.jpda.*;
import org.netbeans.spi.debugger.*;
import org.enclojure.ide.debugger.Context;
import org.enclojure.ide.debugger.util.*;
import org.enclojure.ide.debugger.breakpoints.*;


public class ClojureGoToCursorActionProvider extends ActionsProviderSupport implements PropertyChangeListener {

    private JPDADebugger debugger;
    private Session session;
    private ClojureLineBreakpoint breakpoint;
    
    
    public ClojureGoToCursorActionProvider(ContextProvider contextProvider) {
        debugger = (JPDADebugger) contextProvider.lookupFirst(null, JPDADebugger.class);
        session = (Session) contextProvider.lookupFirst(null, Session.class);
        debugger.addPropertyChangeListener(debugger.PROP_STATE, this);
        Context.addPropertyChangeListener(this);
    }

    private void destroy () {
        debugger.removePropertyChangeListener (debugger.PROP_STATE, this);
        Context.removePropertyChangeListener (this);
    }
    
    public void propertyChange (PropertyChangeEvent evt) {
        setEnabled (
            ActionsManager.ACTION_RUN_TO_CURSOR,
            (debugger.getState () == debugger.STATE_STOPPED) &&
            (Utils.isClojure(Context.getCurrentURL()))
        );
        if ((debugger.getState () != debugger.STATE_RUNNING) && (breakpoint != null)) {
            DebuggerManager.getDebuggerManager ().removeBreakpoint (breakpoint);
            breakpoint = null;
        }
        if (debugger.getState () == debugger.STATE_DISCONNECTED) {
            destroy ();
        }
    }
    
    public Set getActions () {
        return Collections.singleton (ActionsManager.ACTION_RUN_TO_CURSOR);
    }
    
    public void doAction (Object action) {
        if (breakpoint != null) {
            DebuggerManager.getDebuggerManager ().removeBreakpoint (breakpoint);
            breakpoint = null;
        }
        breakpoint = ClojureLineBreakpoint.create (
            Context.getCurrentURL(),
            Context.getCurrentLineNumber()
        );
        breakpoint.setHidden(true);
        DebuggerManager.getDebuggerManager().addBreakpoint (breakpoint);
        session.getEngineForLanguage ("Clojure").getActionsManager ().doAction (
            ActionsManager.ACTION_CONTINUE
        );
    }
}
