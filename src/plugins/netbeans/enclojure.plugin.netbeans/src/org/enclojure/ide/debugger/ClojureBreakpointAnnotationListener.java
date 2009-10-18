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
package org.enclojure.ide.debugger;

import java.beans.*;
import java.util.HashMap;
import java.util.Iterator;

import org.netbeans.api.debugger.*;
import org.netbeans.api.debugger.jpda.*;

import org.enclojure.ide.debugger.breakpoints.*;

@SuppressWarnings("unchecked") 
public class ClojureBreakpointAnnotationListener extends DebuggerManagerAdapter {
    
    private HashMap breakpointToAnnotation = new HashMap ();
    private boolean listen = true;
    
    public String[] getProperties () {
        return new String[] {DebuggerManager.PROP_BREAKPOINTS};
    }
    
    public ClojureBreakpointAnnotationListener()
    {
        
    }
    /**
     * Listens on breakpoint.
     */
    public void propertyChange (PropertyChangeEvent e) {
        String propertyName = e.getPropertyName ();
        if (propertyName == null) return;
        if (!listen) return;
        if ( (!propertyName.equals (ClojureLineBreakpoint.PROP_CONDITION)) &&
             (!propertyName.equals (ClojureLineBreakpoint.PROP_URL)) &&
             (!propertyName.equals (ClojureLineBreakpoint.PROP_LINE_NUMBER)) &&
             (!propertyName.equals (ClojureLineBreakpoint.PROP_ENABLED))
        ) return;
        ClojureLineBreakpoint b = (ClojureLineBreakpoint) e.getSource ();
        annotate (b);
    }

    /**
    * Called when some breakpoint is added.
    *
    * @param b breakpoint
    */
    public void breakpointAdded (Breakpoint b) {
        if (b instanceof ClojureLineBreakpoint) {
            ((ClojureLineBreakpoint) b).addPropertyChangeListener (this);
            annotate ((ClojureLineBreakpoint) b);
        }
    }

    /**
    * Called when some breakpoint is removed.
    *
    * @param breakpoint
    */
    public void breakpointRemoved (Breakpoint b) {
        if (b instanceof ClojureLineBreakpoint) {
            ((ClojureLineBreakpoint) b).removePropertyChangeListener (this);
            removeAnnotation ((ClojureLineBreakpoint) b);
        }
    }

    public ClojureLineBreakpoint findBreakpoint (String url, int lineNumber) {
        Iterator i = breakpointToAnnotation.keySet ().iterator ();
        while (i.hasNext ()) {
            ClojureLineBreakpoint lb = (ClojureLineBreakpoint) i.next ();
            if (!lb.getURL ().equals (url)) continue;
            Object annotation = breakpointToAnnotation.get (lb);
            int ln = Context.getLineNumber (annotation, null);
            if (ln == lineNumber) return lb;
        }
        return null;
    }
    
    // helper methods ..........................................................
    
    private void annotate (ClojureLineBreakpoint b) {
        // remove old annotation
        Object annotation = breakpointToAnnotation.get (b);
        if (annotation != null)
            Context.removeAnnotation (annotation);
        if (b.isHidden ()) return;
        
        // add new one
        annotation = Context.annotate (b);
        if (annotation == null)
            return;
        
        breakpointToAnnotation.put (b, annotation);
        
        DebuggerEngine de = DebuggerManager.getDebuggerManager ().
            getCurrentEngine ();
        Object timeStamp = null;
        if (de != null)
            timeStamp = de.lookupFirst (null, JPDADebugger.class);
        update (b, timeStamp);        
    }

    public void updateClojureLineBreakpoints () {
        Iterator it = breakpointToAnnotation.keySet ().iterator (); 
        while (it.hasNext ()) {
            ClojureLineBreakpoint lb = (ClojureLineBreakpoint) it.next ();
            update (lb, null);
        }
    }
    
    private void update (ClojureLineBreakpoint b, Object timeStamp) {
        Object annotation = breakpointToAnnotation.get (b);
        if (annotation == null) 
            return;
        int ln = Context.getLineNumber (annotation, timeStamp);
        listen = false;
        b.setLineNumber (ln);
        listen = true;
    }
    
    private void removeAnnotation(ClojureLineBreakpoint b) {
        Object annotation = breakpointToAnnotation.remove (b);
        if (annotation != null)
            Context.removeAnnotation (annotation);
    }
}
