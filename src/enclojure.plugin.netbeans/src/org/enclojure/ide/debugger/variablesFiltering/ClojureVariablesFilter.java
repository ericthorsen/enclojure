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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.netbeans.api.debugger.jpda.InvalidExpressionException;
import org.netbeans.api.debugger.jpda.LocalVariable;
import org.netbeans.api.debugger.jpda.ObjectVariable;
import org.netbeans.api.debugger.jpda.This;
import org.netbeans.api.debugger.jpda.Variable;
import org.netbeans.spi.viewmodel.TreeModel;
import org.netbeans.spi.viewmodel.TreeModelFilter;
import org.netbeans.spi.viewmodel.ModelListener;
import org.netbeans.spi.viewmodel.UnknownTypeException;

@SuppressWarnings("unchecked") 
public class ClojureVariablesFilter implements TreeModelFilter {
    
    private static final boolean verbose = true;
    
    
    /** Creates a new instance of ClojureVariablesFilter */
    public ClojureVariablesFilter() {
    }

    /**
     * 
     * Returns filtered root of hierarchy.
     * 
     * @param   original the original tree model
     * @return  filtered root of hierarchy
     */
    public Object getRoot(TreeModel original) {
        return original.getRoot ();
    }

    
    
    public Object[] getChildren(TreeModel original, Object parent, int from, int to)
        throws UnknownTypeException
    {
        Object[] visibleChildren = null;
        if (parent.equals (original.getRoot())) {
            //retrieve all children
            int parentChildrenCount = original.getChildrenCount(parent);
            Object[] children = original.getChildren(parent, 0, parentChildrenCount);
            parentChildrenCount = children.length;
            if (parentChildrenCount == 1 && children[0] instanceof java.lang.String) 
                return children;

            List visibleChildrenList = new ArrayList();

            Object refThis = null;

            for (int i = 0; i < parentChildrenCount; i++) {
                
                Object var = children[i];
                
                if (var instanceof LocalVariable) {
                    LocalVariable lvar = (LocalVariable)var;
                        if (!isHiddenLocal(lvar.getName())) {
                        visibleChildrenList.add(var);
                    }
                }
                else if (var instanceof This)
                    refThis = var;
            }

 
            if (refThis != null)
                visibleChildrenList.add(0, refThis);
             
            visibleChildren = visibleChildrenList.subList(from
                                , Math.min(visibleChildrenList.size(), to)).toArray();
        }

        else if (parent instanceof ImplicitLocals)
            visibleChildren = ((ImplicitLocals)parent).getLocals().subList(from, to).toArray ();
        else if (parent instanceof AttributeMap) {
            visibleChildren = ((AttributeMap)parent).getAttributes().subList(from, to).toArray();
        }
        else if (parent instanceof AttributeMap.Attribute)
            visibleChildren = original.getChildren(((AttributeMap.Attribute)parent).getValue(), from, to);
        else
            visibleChildren = original.getChildren(parent, from, to);
        
        return visibleChildren;
    }

    public int getChildrenCount(TreeModel original, Object node) 
        throws UnknownTypeException
    {
        
        int countVisible = 0;

        //in case of ROOT
        if (node.equals (original.getRoot())) {
            countVisible = original.getChildrenCount(node);
            Object[] children = original.getChildren (node, 0, countVisible);
            //original.getChildrenCount(...) needn't be equal to original.getChildren (...).length()
            countVisible = children.length;
            if (countVisible == 1 && children[0] instanceof java.lang.String) 
                return countVisible;
            for (int i = 0; i < children.length; i++) {
                Object var = children[i];
                //show the locals except of hidden locals and implicit locals 
                if (var instanceof LocalVariable) {
                    if (isHiddenLocal(((LocalVariable)var).getName()) ||
                        ImplicitLocals.isImplicitLocal(((LocalVariable)var).getName()))
                        countVisible--;
                }
                //do not show anything but this
                else if (!(var instanceof This))
                    countVisible--;
            }
        }

            countVisible = original.getChildrenCount(node);

        return countVisible;
    }

    /**
     * Returns true if node is leaf. You should not throw UnknownTypeException
     * directly from this method!
     * 
     * @param   original the original tree model
     * @throws  UnknownTypeException this exception can be thrown from 
     *          <code>original.isLeaf (...)</code> method call only!
     * @return  true if node is leaf
     */
    public boolean isLeaf(TreeModel original, Object node) 
        throws UnknownTypeException 
    {
        boolean il;
        if (node instanceof ImplicitLocals)
            il = false;
        else if (node instanceof AttributeMap) 
            il = false;
        else if (node instanceof AttributeMap.Attribute) {
            Variable attributeValue = ((AttributeMap.Attribute)node).getValue();
            if (isLeafType(attributeValue.getType()))
                il = true;
            else
                il = original.isLeaf(attributeValue);
        }
        else
            il = original.isLeaf(node);
        
        return il;
    }

    /**
     * 
     * Unregisters given listener.
     * 
     * @param l the listener to remove
     */
    public void removeModelListener(ModelListener l) {
    }

    /**
     * 
     * Registers given listener.
     * 
     * @param l the listener to add
     */
    public void addModelListener(ModelListener l) {
    }


    private static HashSet hiddenLocals = null;
    private static boolean isHiddenLocal(String aLocalName) {

        if (hiddenLocals == null) {
            hiddenLocals = new HashSet();
            
            hiddenLocals.add("_jspxFactory");
            hiddenLocals.add("_jspx_out");
            hiddenLocals.add("_jspx_page_context");

        }
        
        return hiddenLocals.contains(aLocalName);
    }

    private static HashSet leafType = null;
    private static boolean isLeafType (String type) {
        if (leafType == null) {
            leafType = new HashSet ();
            leafType.add ("java.lang.String");
            leafType.add ("java.lang.Character");
            leafType.add ("java.lang.Integer");
            leafType.add ("java.lang.Float");
            leafType.add ("java.lang.Byte");
            leafType.add ("java.lang.Boolean");
            leafType.add ("java.lang.Double");
            leafType.add ("java.lang.Long");
            leafType.add ("java.lang.Short");
        }
        return leafType.contains (type);
    }
    
//---------------------------------------------------------------------------------------    
//      inner classes
//---------------------------------------------------------------------------------------    
    
    public static class ImplicitLocals {
        private List locals = new ArrayList ();
        private static HashSet localsNames = null;

        public static boolean isImplicitLocal(String aLocalName) {

            if (localsNames == null) {
                localsNames = new HashSet();
                localsNames.add("application");
                localsNames.add("config");
                localsNames.add("out");
                localsNames.add("page");
                localsNames.add("pageContext");
                localsNames.add("request");
                localsNames.add("response");
                localsNames.add("session");
            }

            return localsNames.contains(aLocalName);
        }
        
        void addLocal (LocalVariable local) {
            locals.add (local);
        }
        
        List getLocals () {
            return locals;
        }
        
        public boolean equals (Object o) {
            return o instanceof ImplicitLocals;
        }
        
        public int hashCode () {
            if (locals.size () == 0) return super.hashCode ();
            return locals.get (0).hashCode ();
        }
    }
    
    public static class AttributeMap {// extends java.util.HashMap {
        private ArrayList attributes = new ArrayList();
        private ObjectVariable owner = null;
        private String ownerName = null;

        public class UnknownOwnerNameException extends RuntimeException {
            public UnknownOwnerNameException(String name) {
                super("Unknown owner name: " + name);
            }
        };
        
        public AttributeMap(String aOwnerName) {
            setOwnerName(aOwnerName);
        }
        
        public AttributeMap(ObjectVariable aVar) {
            owner = aVar;
            setOwnerName(((LocalVariable)owner).getName());
            Iterator it = new AttributeIterator();
            while (it.hasNext()) {
                Attribute attribute = (Attribute)it.next();
                if (attribute != null) {
                    attributes.add(attribute);
                }
            }
        }

        private void setOwnerName(String aOwnerName) {
            if (aOwnerName.equals("request") || aOwnerName.equals("session") || aOwnerName.equals("application"))
                ownerName = aOwnerName;
            else
                throw new UnknownOwnerNameException(aOwnerName);
        }
        
        public ArrayList getAttributes() { return attributes; }
        public String getOwnerName() { return ownerName; }
        
        public class Attribute {
            private String name;
            private Variable value;
            public Attribute(String aName, Variable aValue) {
                name = aName;
                value = aValue;
            }
            public String getName() { return name; }
            public Variable getValue() { return value; }
        }
        
        private class AttributeIterator implements Iterator {
            ObjectVariable reqAttributes = null;

            public AttributeIterator() {
                try {
                    reqAttributes = (ObjectVariable)owner.invokeMethod(
                            "getAttributeNames",
                            "()Ljava/util/Enumeration;",
                            new Variable[0]
                    );
                }
                catch (InvalidExpressionException e) {
                }
                catch (NoSuchMethodException e) {
                }
            }
            
            
            public boolean hasNext() {
                
                if (reqAttributes == null) return false;
                
                boolean ret = false;
                try {
                    Variable hasMoreElements = reqAttributes.invokeMethod(
                            "hasMoreElements",
                            "()Z",
                            new Variable[0]
                    );
                    ret = (hasMoreElements != null && "true".equals(hasMoreElements.getValue()));
                }
                catch (InvalidExpressionException e) {
                }
                catch (NoSuchMethodException e) {
                }

                return ret;
            }

            public Object next() {

                Object nextElement = null;
                try {
                    Variable attributeName = reqAttributes.invokeMethod(
                            "nextElement",
                            "()Ljava/lang/Object;",
                            new Variable[0]
                    );
                    // object collected or vm disconnected if null
                    if (attributeName != null) {
                        Variable attributeValue = owner.invokeMethod(
                                "getAttribute",
                                "(Ljava/lang/String;)Ljava/lang/Object;",
                                new Variable[] { attributeName }
                        );
                        nextElement = new AttributeMap.Attribute(
                                (attributeName.getValue() == null ? "" : attributeName.getValue()),
                                 attributeValue);
                    }
                }
                catch (InvalidExpressionException e) {
                }
                catch (NoSuchMethodException e) {
                }

                return nextElement;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

        }
    }
}
