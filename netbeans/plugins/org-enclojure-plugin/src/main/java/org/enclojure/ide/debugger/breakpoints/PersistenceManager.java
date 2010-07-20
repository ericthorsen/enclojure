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
package org.enclojure.ide.debugger.breakpoints;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import org.netbeans.api.debugger.Breakpoint;
import org.netbeans.api.debugger.DebuggerEngine;

import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.LazyDebuggerManagerListener;
import org.netbeans.api.debugger.Properties;
import org.netbeans.api.debugger.Session;
import org.netbeans.api.debugger.Watch;

@SuppressWarnings("unchecked") 
public class PersistenceManager implements LazyDebuggerManagerListener {
    
    private static final LogAdapter LOG = new LogAdapter(PersistenceManager.class.getName());

    private static final String CLOJURE_PROPERTY = "clj";

    public Breakpoint[] initBreakpoints () {
        try {

            Properties p = Properties.getDefault ().getProperties ("debugger").
                getProperties (DebuggerManager.PROP_BREAKPOINTS);
            Breakpoint[] breakpoints = (Breakpoint[]) p.getArray (
                CLOJURE_PROPERTY,
                new Breakpoint [0]
            );
            for (int i = 0; i < breakpoints.length; i++) {
                breakpoints[i].addPropertyChangeListener(this);
            }
            return breakpoints;
        } catch (Exception exc) {
            LOG.log(Level.SEVERE, exc.getMessage());
            exc.printStackTrace();
        }
        return new Breakpoint[] {};
    }
    
    public void initWatches () {
    }
    
    public String[] getProperties () {
        return new String [] {
            DebuggerManager.PROP_BREAKPOINTS_INIT,
            DebuggerManager.PROP_BREAKPOINTS,
        };
    }
    
    public void breakpointAdded (Breakpoint breakpoint) {
        if (breakpoint instanceof ClojureLineBreakpoint) {
            Properties p = Properties.getDefault ().getProperties ("debugger").
                getProperties (DebuggerManager.PROP_BREAKPOINTS);
            p.setArray (
                CLOJURE_PROPERTY, 
                getBreakpoints ()
            );
            breakpoint.addPropertyChangeListener(this);
        }
    }

    public void breakpointRemoved (Breakpoint breakpoint) {
        if (breakpoint instanceof ClojureLineBreakpoint) {
            Properties p = Properties.getDefault ().getProperties ("debugger").
                getProperties (DebuggerManager.PROP_BREAKPOINTS);
            p.setArray (
                CLOJURE_PROPERTY, 
                getBreakpoints ()
            );
            breakpoint.removePropertyChangeListener(this);
        }
    }
    public void watchAdded (Watch watch) {
    }
    
    public void watchRemoved (Watch watch) {
    }
    
    public void propertyChange (PropertyChangeEvent evt) {
        if (evt.getSource() instanceof Breakpoint) {
            Properties.getDefault ().getProperties ("debugger").
                getProperties (DebuggerManager.PROP_BREAKPOINTS).setArray (
                    CLOJURE_PROPERTY,
                    getBreakpoints ()
                );
        }
    }
    
    public void sessionAdded (Session session) {}
    public void sessionRemoved (Session session) {}
    public void engineAdded (DebuggerEngine engine) {}
    public void engineRemoved (DebuggerEngine engine) {}
    
    
    private static Breakpoint[] getBreakpoints () {
        Breakpoint[] bs = DebuggerManager.getDebuggerManager ().
            getBreakpoints ();
        int i, k = bs.length;
        ArrayList bb = new ArrayList ();
        for (i = 0; i < k; i++)
            // We store only the JSP breakpoints
            if (bs[i] instanceof ClojureLineBreakpoint)
                bb.add (bs [i]);
        bs = new Breakpoint [bb.size ()];
        return (Breakpoint[]) bb.toArray (bs);
    }
}

