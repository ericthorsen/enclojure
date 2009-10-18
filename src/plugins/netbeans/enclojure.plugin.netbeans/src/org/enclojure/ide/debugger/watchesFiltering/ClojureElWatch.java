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

import org.netbeans.api.debugger.Watch;
import org.netbeans.api.debugger.jpda.InvalidExpressionException;
import org.netbeans.api.debugger.jpda.Variable;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.openide.util.WeakListeners;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

public class ClojureElWatch implements PropertyChangeListener {

    private final Watch     watch;
    
    private boolean         evaluated = false;
    private JPDADebugger    debugger;
    private Variable        variable;
    private Exception       exception;

    public ClojureElWatch(Watch w, JPDADebugger debugger) {
        watch = w;
        this.debugger = debugger;
        w.addPropertyChangeListener((PropertyChangeListener) WeakListeners.create(PropertyChangeListener.class, this, w));    
    }

    public String getExpression () {
        return watch.getExpression();
    }

    public String getType() {
        if (!evaluated) {
            evaluate();
        }
        return variable == null ? "" : variable.getType();
    }

    public String getValue() {
        if (!evaluated) {
            evaluate();
        }
        return variable == null ? "" : variable.getValue();
    }

    public String getExceptionDescription() {
        if (!evaluated) {
            evaluate();
        }
        return exception == null ? null : exception.getMessage();
    }

    public String getToStringValue() throws InvalidExpressionException {
        return getValue().toString();
    }

    public Watch getWatch() {
        return watch;
    }
    
    private synchronized void evaluate() {
        String text = watch.getExpression ();
        text = org.openide.util.Utilities.replaceString(text, "\"", "\\\"");
        text = "pageContext.getExpressionEvaluator().evaluate(\"" + text +
                            "\", java.lang.String.class, (javax.servlet.jsp.PageContext)pageContext, null)";
        try {
            variable = debugger.evaluate(text);
            exception = null;
        } catch (Exception e) {
            exception = e;
        } finally {
            evaluated = true;
        }
    }

    public void propertyChange(PropertyChangeEvent evt) {
        setUnevaluated();
    }
    
    public void setUnevaluated() {
        evaluated = false;
    }
}
