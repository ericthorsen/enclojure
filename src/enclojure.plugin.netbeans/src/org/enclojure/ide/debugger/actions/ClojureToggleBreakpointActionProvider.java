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

import com.sun.tools.doclets.internal.toolkit.util.SourcePath;
import java.util.*;
import java.beans.*;

import org.enclojure.ide.debugger.breakpoints.ClojureBreakpointsReader;
import org.netbeans.api.debugger.*;
import org.netbeans.api.debugger.jpda.*;
import org.netbeans.spi.debugger.*;

import org.enclojure.ide.debugger.Context;
import org.enclojure.ide.debugger.ClojureBreakpointAnnotationListener;
import org.enclojure.ide.debugger.util.Utils;
import org.enclojure.ide.debugger.breakpoints.ClojureLineBreakpoint;
import org.openide.filesystems.FileObject;

public class ClojureToggleBreakpointActionProvider extends ActionsProviderSupport implements PropertyChangeListener {
    
    
    private JPDADebugger debugger;
    private SourcePath           engineContext;
    private boolean                 started = false;
    private Session                 session;
    private ClojureBreakpointsReader       breakpointsReader;
    
    public ClojureToggleBreakpointActionProvider () {
        Context.addPropertyChangeListener (this);
    }
    
    public ClojureToggleBreakpointActionProvider (ContextProvider contextProvider) {
        debugger = (JPDADebugger) contextProvider.lookupFirst 
                (null, JPDADebugger.class);
                engineContext = (SourcePath) contextProvider.
            lookupFirst (null, SourcePath.class);
        session = (Session) contextProvider.lookupFirst(null, Session.class);
        breakpointsReader = (ClojureBreakpointsReader) contextProvider.lookupFirst(null, ClojureBreakpointsReader.class);
        debugger.addPropertyChangeListener (debugger.PROP_STATE, this);
        Context.addPropertyChangeListener (this);
    }
    
    private void destroy () {
        debugger.removePropertyChangeListener (debugger.PROP_STATE, this);
        Context.removePropertyChangeListener (this);
    }
    
    public void propertyChange (PropertyChangeEvent evt) {
        String url = Context.getCurrentURL();

        //#67910 - setting of a bp allowed only in JSP contained in some web module
        FileObject fo = Utils.getFileObjectFromUrl(url);
        
        boolean isClojure = Utils.isClojure(fo);
       
        //#issue 65969 fix:
        //we allow bp setting only if the file is JSP or TAG file and target server of it's module is NOT WebLogic 9;
        //TODO it should be solved by adding new API into j2eeserver which should announce whether the target server
        //supports JSP debugging or not
        // String serverID = Utils.getTargetServerID(fo);

        setEnabled(ActionsManager.ACTION_TOGGLE_BREAKPOINT , isClojure);
        if ( debugger != null && 
             debugger.getState () == debugger.STATE_DISCONNECTED
            ) 
            destroy ();
    }
    
    public Set getActions () {
        return Collections.singleton (ActionsManager.ACTION_TOGGLE_BREAKPOINT);
    }
    
    public void doAction (Object action) {
        DebuggerManager d = DebuggerManager.getDebuggerManager ();
        
        // 1) get source name & line number
        int ln = Context.getCurrentLineNumber ();
        String url = Context.getCurrentURL ();
        if (url == null) return;               

        // 2) find and remove existing line breakpoint
        ClojureLineBreakpoint lb = getClojureBreakpointAnnotationListener().findBreakpoint(url, ln);        
        if (lb != null) {
            d.removeBreakpoint(lb);
            return;
        }
        lb = ClojureLineBreakpoint.create(url
                                        , ln
                                        ,debugger
                                        ,engineContext
                                        ,session
                                        ,breakpointsReader);
      d.addBreakpoint(lb);
         
    }

    private ClojureBreakpointAnnotationListener ClojureBreakpointAnnotationListener;
    private ClojureBreakpointAnnotationListener getClojureBreakpointAnnotationListener () {
        if (ClojureBreakpointAnnotationListener == null)
            ClojureBreakpointAnnotationListener = (ClojureBreakpointAnnotationListener) 
                DebuggerManager.getDebuggerManager ().lookupFirst 
                (null, ClojureBreakpointAnnotationListener.class);
        return ClojureBreakpointAnnotationListener;
    }
}
