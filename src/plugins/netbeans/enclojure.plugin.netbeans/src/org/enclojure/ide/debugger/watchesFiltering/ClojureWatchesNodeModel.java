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

import org.netbeans.spi.viewmodel.NodeModel;
import org.netbeans.spi.viewmodel.UnknownTypeException;
import org.netbeans.spi.viewmodel.ModelListener;
import org.netbeans.api.debugger.jpda.InvalidExpressionException;

public class ClojureWatchesNodeModel implements NodeModel {

    private static final String ICON_BASE ="org/enclojure/clojure/debugger/resources/watchesView/Watch";

    public String getDisplayName(Object node) throws UnknownTypeException {
        if (!(node instanceof ClojureElWatch)) throw new UnknownTypeException(node);
        ClojureElWatch watch = (ClojureElWatch) node;
        return watch.getExpression();
    }

    public String getIconBase(Object node) throws UnknownTypeException {
        if (!(node instanceof ClojureElWatch)) throw new UnknownTypeException(node);
        return ICON_BASE;
    }

    public String getShortDescription(Object node) throws UnknownTypeException {
        if (!(node instanceof ClojureElWatch)) throw new UnknownTypeException(node);
        ClojureElWatch watch = (ClojureElWatch) node;
        
        String t = watch.getType ();
        String e = watch.getExceptionDescription ();
        if (e != null) {
            return watch.getExpression() + " = >" + e + "<";
        }
        if (t == null) {
            return watch.getExpression() + " = " + watch.getValue();
        } else {
            try {
                return watch.getExpression() + " = (" + watch.getType () + ") " + watch.getToStringValue();
            } catch (InvalidExpressionException ex) {
                return ex.getLocalizedMessage ();
            }
        }
    }

    public void addModelListener(ModelListener l) {
    }

    public void removeModelListener(ModelListener l) {
    }
}
