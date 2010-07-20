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

import java.io.*;
import javax.swing.JEditorPane;
import javax.swing.text.*;

import org.openide.cookies.EditorCookie;
import org.openide.text.*;
import org.openide.util.RequestProcessor;

import org.enclojure.ide.debugger.util.Utils;
import org.netbeans.api.debugger.*;
import org.netbeans.api.debugger.jpda.*;
import org.openide.loaders.DataObject;
import org.enclojure.ide.core.LogAdapter;
import java.util.logging.Level;


public class ClojureToolTipAnnotation extends Annotation implements Runnable {

    private static final LogAdapter LOG = new LogAdapter(ClojureToolTipAnnotation.class.getName());
    
    private String toolTipText = null;

    private StyledDocument doc;

    public String getShortDescription() {
        LOG.log(Level.FINEST, "ClojureTooltip: getShortDescription");
        
        toolTipText = null;
        DebuggerEngine currentEngine = DebuggerManager.getDebuggerManager ().
            getCurrentEngine ();
        if (currentEngine == null) return null;
        JPDADebugger d = (JPDADebugger) currentEngine.lookupFirst (null, JPDADebugger.class);
        if (d == null) return null;

        Line.Part lp = (Line.Part) getAttachedAnnotatable();
        if (lp != null) {
            Line line = lp.getLine ();
            DataObject dob = DataEditorSupport.findDataObject(line);
            EditorCookie ec = (EditorCookie) dob.getCookie(EditorCookie.class);

            if (ec != null) { // Only for editable dataobjects
                try {
                    doc = ec.openDocument ();                    
                    RequestProcessor.getDefault().post(this);                    
                } catch (IOException e) {
                }
            }
        }
        return toolTipText;
    }

    public void run () {

        LOG.log(Level.FINEST, "ClojureTooltip: run");

        //1) get tooltip text
        Line.Part lp = (Line.Part)getAttachedAnnotatable();
        JEditorPane ep = Utils.getCurrentEditor();
        String textForTooltip = "";
        
        if ((lp == null) || (ep == null)) {
            return;
        }
        
        //first try EL
        String text = Utils.getELIdentifier(doc, ep,NbDocument.findLineOffset(doc, lp.getLine().getLineNumber()) + lp.getColumn());
        LOG.log(Level.FINEST, "JspToClojureTooltipoltip: ELIdentifier = " + text);

        boolean isScriptlet = Utils.isScriptlet(
            doc, ep, NbDocument.findLineOffset(doc, lp.getLine().getLineNumber()) + lp.getColumn()
        );
        LOG.log(Level.FINEST, "isScriptlet: " + isScriptlet);
        
        //if not, try Java
        if ((text == null) && (isScriptlet)) {
            text = Utils.getJavaIdentifier(
                doc, ep, NbDocument.findLineOffset(doc, lp.getLine().getLineNumber()) + lp.getColumn()
            );
            textForTooltip = text;
            LOG.log(Level.FINEST, "ClojureTooltip: javaIdentifier = " + text);
            if (text == null) {
                return;
            }
        } else {
            if (text == null) {
                return;
            }
            textForTooltip = text;
            String textEscaped = org.openide.util.Utilities.replaceString(text, "\"", "\\\"");
            text = "pageContext.getExpressionEvaluator().evaluate(\"" + textEscaped +
                                "\", java.lang.String.class, (javax.servlet.jsp.PageContext)pageContext, null)";
        }
        
        LOG.log(Level.FINEST, "JspTooltip: fullWatch = " + text);
        
        //3) obtain text representation of value of watch
        String old = toolTipText;
        toolTipText = null;
        
        DebuggerEngine currentEngine = DebuggerManager.getDebuggerManager().getCurrentEngine();
        if (currentEngine == null) return;
        JPDADebugger d = (JPDADebugger) currentEngine.lookupFirst (null, JPDADebugger.class);
        if (d == null) return;
        
        try {
            Variable v = d.evaluate(text);
            if (v instanceof ObjectVariable) {
                toolTipText = textForTooltip + " = (" + v.getType() + ")" + ((ObjectVariable)v).getToStringValue();
            } else {
                toolTipText = textForTooltip + " = (" + v.getType() + ")" + v.getValue();
            }
        } catch (InvalidExpressionException e) {
            toolTipText = text + " = >" + e.getMessage() + "<";
        }
        LOG.log(Level.FINEST, "ClojureTooltip: " + toolTipText);
        firePropertyChange (PROP_SHORT_DESCRIPTION, old, toolTipText);       
    }

    public String getAnnotationType () {
        return null;
    }
    
}
