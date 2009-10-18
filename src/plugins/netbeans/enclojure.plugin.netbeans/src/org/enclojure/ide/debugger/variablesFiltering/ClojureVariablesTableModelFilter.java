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
package org.enclojure.ide.debugger.variablesFiltering;

import clojure.lang.RT;
import clojure.lang.Var;
import org.enclojure.ide.debugger.variablesFiltering.ClojureVariablesFilter.AttributeMap;
import org.netbeans.spi.viewmodel.TableModel;
import org.netbeans.spi.viewmodel.TableModelFilter;
import org.netbeans.spi.viewmodel.UnknownTypeException;
import org.netbeans.api.debugger.jpda.LocalVariable;
import org.openide.util.Exceptions;

public class ClojureVariablesTableModelFilter implements TableModelFilter {

    public static Var _getValueFn = RT.var("org.enclojure.ide.debugger.jdi-eval", "get-value");

    public ClojureVariablesTableModelFilter() {
    }

    /**
     * Returns filterred value to be displayed in column <code>columnID</code>
     * and row <code>node</code>. Column ID is defined in by 
     * {@link ColumnModel#getID}, and rows are defined by values returned from 
     * {@TreeModel#getChildren}. You should not throw UnknownTypeException
     * directly from this method!
     *
     * @param   original the original table model
     * @param   node a object returned from {@TreeModel#getChildren} for this row
     * @param   columnID a id of column defined by {@link ColumnModel#getID}
     * @throws  ComputingException if the value is not known yet and will 
     *          be computed later
     * @throws  UnknownTypeException this exception can be thrown from 
     *          <code>original.getValueAt (...)</code> method call only!
     *
     * @return value of variable representing given position in tree table.
     */
    public Object getValueAt(TableModel original, Object node, String columnID)
    throws UnknownTypeException
    {
        if (node instanceof LocalVariable && columnID.compareTo("LocalsValue") == 0)
        {
            LocalVariable localVar = (LocalVariable) node;
            String varName = localVar.getName();
            try {
                return (String) _getValueFn.invoke(varName);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
        Object colValue = "";
        if (node instanceof ClojureVariablesFilter.AttributeMap.Attribute)
            colValue = original.getValueAt(((AttributeMap.Attribute)node).getValue(), columnID);
        else if (node instanceof ClojureVariablesFilter.AttributeMap ||
                 node instanceof ClojureVariablesFilter.ImplicitLocals)
            colValue = "";
        else
            colValue = original.getValueAt(node, columnID);
        
        return colValue;
    }
    
    /**
     * Changes a value displayed in column <code>columnID</code>
     * and row <code>node</code>. Column ID is defined in by 
     * {@link ColumnModel#getID}, and rows are defined by values returned from 
     * {@TreeModel#getChildren}. You should not throw UnknownTypeException
     * directly from this method!
     *
     * @param  original the original table model
     * @param  node a object returned from {@TreeModel#getChildren} for this row
     * @param  columnID a id of column defined by {@link ColumnModel#getID}
     * @param  value a new value of variable on given position
     * @throws  UnknownTypeException this exception can be thrown from 
     *          <code>original.setValueAt (...)</code> method call only!
     */
    public void setValueAt(TableModel original, Object node, String columnID, Object value)
    throws UnknownTypeException
    {
            original.setValueAt(node, columnID, value);
    }

    /**
     * Filters original isReadOnly value from given table model. You should 
     * not throw UnknownTypeException
     * directly from this method!
     *
     * @param  original the original table model
     * @param  node a object returned from {@TreeModel#getChildren} for this row
     * @param  columnID a id of column defined by {@link ColumnModel#getID}
     * @throws  UnknownTypeException this exception can be thrown from 
     *          <code>original.isReadOnly (...)</code> method call only!
     *
     * @return true if variable on given position is read only
     */
    public boolean isReadOnly(TableModel original, Object node, String columnID)
    throws UnknownTypeException
    {
        boolean ro = true;
        if (node instanceof ClojureVariablesFilter.AttributeMap ||
                 node instanceof ClojureVariablesFilter.ImplicitLocals ||
                 node instanceof ClojureVariablesFilter.AttributeMap.Attribute)
            ro = true;
        else
            ro = original.isReadOnly(node, columnID);
        
        return ro;
    }

    public void removeModelListener(org.netbeans.spi.viewmodel.ModelListener l) {
    }

    public void addModelListener(org.netbeans.spi.viewmodel.ModelListener l) {
    }

}
