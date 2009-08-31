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
package org.enclojure.ide.debugger.watchesFiltering;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import org.netbeans.spi.viewmodel.TreeModelFilter;
import org.netbeans.spi.viewmodel.TreeModel;
import org.netbeans.spi.viewmodel.UnknownTypeException;
import org.netbeans.spi.viewmodel.ModelListener;
import org.netbeans.spi.debugger.ContextProvider;
import org.netbeans.api.debugger.Watch;
import org.netbeans.api.debugger.DebuggerManager;
import org.netbeans.api.debugger.jpda.JPDADebugger;

import java.util.*;

public class ClojureWatchesTreeFilter implements TreeModelFilter {
    
    private final JPDADebugger debugger;
    private final Map<Watch, ClojureElWatch> watch2JspElWatch = new HashMap<Watch, ClojureElWatch>();
    private DebuggerListener listener;

    public ClojureWatchesTreeFilter(ContextProvider lookupProvider) {
        debugger = (JPDADebugger) lookupProvider.lookupFirst(null, JPDADebugger.class);
    }
    
    public Object getRoot(TreeModel original) {
        return original.getRoot();
    }

    public Object[] getChildren(TreeModel original, Object parent, int from, int to) throws UnknownTypeException {
        if (parent == original.getRoot()) {
            Watch [] allWatches = DebuggerManager.getDebuggerManager().getWatches();
            Object [] result = original.getChildren(parent, from, to);
            
            //original model returns array of JPDAWatch-es, thus we must create an Object array 
            //to allow merging with JspElWatch-es
            Object[] ch = new Object[result.length];
            System.arraycopy(result, 0, ch, 0, result.length);
            
            synchronized (watch2JspElWatch) {
                
                for (int i = from; i < allWatches.length; i++) {
                    Watch w = allWatches[i];
                    String expression = w.getExpression();
                    if (isCLJexpression(expression)) {
                        ClojureElWatch jw = (ClojureElWatch) watch2JspElWatch.get(w);
                        if (jw == null ) {
                            jw = new ClojureElWatch(w, debugger);
                            watch2JspElWatch.put(w, jw);
                        }
                        ch[i - from] = jw;
                    }
                }
            }
            
            if (listener == null) {
                listener = new DebuggerListener(this, debugger);
            }
            
            return ch;
        } else {
            return original.getChildren(parent, from, to);
        }
    }

    public int getChildrenCount(TreeModel original, Object node) throws UnknownTypeException {
        if (node == original.getRoot() && listener == null) {
            listener = new DebuggerListener(this, debugger);
        }
        return original.getChildrenCount(node);
    }

    public boolean isLeaf(TreeModel original, Object node) throws UnknownTypeException {
        if (node instanceof ClojureElWatch) return true;
        return original.isLeaf(node);
    }

    private boolean isCLJexpression(String expression) {
        return true; // Maybe base this on the file?
        //return expression.startsWith("(") && expression.endsWith("}"); // NOI18N
    }
    
    public void addModelListener(ModelListener l) {
    }

    public void removeModelListener(ModelListener l) {
    }
    
    void fireTreeChanged() {
        synchronized (watch2JspElWatch) {
            for (ClojureElWatch jspElWatch : watch2JspElWatch.values()) {
                jspElWatch.setUnevaluated();
            }
        }
    }
    
    private static class DebuggerListener implements PropertyChangeListener {
        
        WeakReference<ClojureWatchesTreeFilter> jspWatchesFilterRef;
        WeakReference<JPDADebugger> debuggerRef;
        
        DebuggerListener(ClojureWatchesTreeFilter jspWatchesFilter, JPDADebugger debugger) {
            jspWatchesFilterRef = new WeakReference<ClojureWatchesTreeFilter>(jspWatchesFilter);
            debuggerRef = new WeakReference<JPDADebugger>(debugger);
            debugger.addPropertyChangeListener(this);
        }

        public void propertyChange (PropertyChangeEvent evt) {
            
            if (debuggerRef.get().getState() == JPDADebugger.STATE_DISCONNECTED) {
                destroy();
                return;
            }
            if (debuggerRef.get().getState() == JPDADebugger.STATE_RUNNING) {
                return;
            }

            final ClojureWatchesTreeFilter jspWatchesFilter = getJspWatchesFilter();
            if (jspWatchesFilter != null) {
                jspWatchesFilter.fireTreeChanged();
            }
        }
        
        private ClojureWatchesTreeFilter getJspWatchesFilter() {
            ClojureWatchesTreeFilter jspWatchesFilter = jspWatchesFilterRef.get();
            if (jspWatchesFilter == null) {
                destroy();
            }
            return jspWatchesFilter;
        }
        
        private void destroy() {
            JPDADebugger debugger = debuggerRef.get();
            if (debugger != null) {
                debugger.removePropertyChangeListener(this);
            }
        }

    }

}
