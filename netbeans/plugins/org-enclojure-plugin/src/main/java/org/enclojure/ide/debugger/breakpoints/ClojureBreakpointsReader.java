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

import org.netbeans.api.debugger.*;
import org.enclojure.ide.core.LogAdapter;
import java.util.logging.Level;

public class ClojureBreakpointsReader implements Properties.Reader {

    private static final LogAdapter LOG = new LogAdapter(ClojureBreakpointsReader.class.getName());

    private void log(Level lvl, String msg) {
        LOG.log(lvl, msg);
    }
    
    public String [] getSupportedClassNames () {
        return new String[] { ClojureLineBreakpoint.class.getName () };
    }

    public Object read (String typeID, Properties properties) {

        ClojureLineBreakpoint b = null;
        try {

            if (typeID.equals (ClojureLineBreakpoint.class.getName ())) {
                String url = properties.getString(ClojureLineBreakpoint.PROP_URL, null);
                // #110349 - ignore loading of breakpoints which do not have URL
                if (url == null || url.trim().length() == 0) {
                    return null;
                }
                b = ClojureLineBreakpoint.create (
                    url,
                    properties.getInt(ClojureLineBreakpoint.PROP_LINE_NUMBER, 1)
                );
                b.setCondition(properties.getString (ClojureLineBreakpoint.PROP_CONDITION, ""));
                b.setPrintText(properties.getString (ClojureLineBreakpoint.PROP_PRINT_TEXT, ""));
                b.setGroupName(properties.getString (Breakpoint.PROP_GROUP_NAME, ""));
                b.setSuspend(properties.getInt (ClojureLineBreakpoint.PROP_SUSPEND, ClojureLineBreakpoint.SUSPEND_ALL));
                if (properties.getBoolean (ClojureLineBreakpoint.PROP_ENABLED, true)) {
                    b.enable ();
                } else {
                    b.disable ();
                }
            }
        } catch (Exception e) {
            log(Level.SEVERE, e.getMessage());
            e.printStackTrace();
        }

        return b;
    }
    
    public void write (Object object, Properties properties) {

        if (object instanceof ClojureLineBreakpoint) {
            ClojureLineBreakpoint b = (ClojureLineBreakpoint) object;
            properties.setString (ClojureLineBreakpoint.PROP_PRINT_TEXT, b.getPrintText ());
            properties.setString (ClojureLineBreakpoint.PROP_GROUP_NAME, b.getGroupName ());
            properties.setInt (ClojureLineBreakpoint.PROP_SUSPEND, b.getSuspend ());
            properties.setBoolean (ClojureLineBreakpoint.PROP_ENABLED, b.isEnabled ());        
            properties.setString (ClojureLineBreakpoint.PROP_URL, b.getURL ());
            properties.setInt (ClojureLineBreakpoint.PROP_LINE_NUMBER, b.getLineNumber ());
            properties.setString (ClojureLineBreakpoint.PROP_CONDITION, b.getCondition ());
        }
        return;
    }
}
