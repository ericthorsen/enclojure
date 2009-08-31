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


import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.StackFrame;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import org.netbeans.spi.debugger.ContextProvider;

import org.netbeans.api.debugger.jpda.CallStackFrame;
import org.netbeans.api.debugger.jpda.Field;
import org.netbeans.api.debugger.jpda.JPDADebugger;
import org.netbeans.api.debugger.jpda.JPDAThread;
import org.netbeans.spi.debugger.jpda.SourcePathProvider;
import org.netbeans.spi.debugger.jpda.EditorContext;
import org.openide.util.Exceptions;

public class ClojureSourcePath {

    private ContextProvider         lookupProvider;
    private SourcePathProvider      contextProvider;
    private JPDADebugger            debugger;
    

    public ClojureSourcePath (ContextProvider lookupProvider) {
        this.lookupProvider = lookupProvider;
        debugger = (JPDADebugger) lookupProvider.lookupFirst 
            (null, JPDADebugger.class);
    }

    public SourcePathProvider getContext () {
        if (contextProvider == null) {
            List l = lookupProvider.lookup (null, SourcePathProvider.class);
            contextProvider = (SourcePathProvider) l.get (0);
            int i, k = l.size ();
            for (i = 1; i < k; i++) {
                contextProvider = new CompoundContextProvider (
                    (SourcePathProvider) l.get (i), 
                    contextProvider
                );
            }
        }
        return contextProvider;
    }

    
    // ContextProvider methods .................................................
    
    /**
     * Returns relative path for given url.
     *
     * @param url a url of resource file
     * @param directorySeparator a directory separator character
     * @param includeExtension whether the file extension should be included 
     *        in the result
     *
     * @return relative path
     */
    public String getRelativePath (
        String url, 
        char directorySeparator, 
        boolean includeExtension
    ) {
        return getContext ().getRelativePath 
            (url, directorySeparator, includeExtension);
    }
    
    /**
     * Returns the source root (if any) for given url.
     *
     * @param url a url of resource file
     *
     * @return the source root or <code>null</code> when no source root was found.
     */
    public String getSourceRoot(String url) {
        return getContext().getSourceRoot(url);
    }

    /**
     * Translates a relative path ("java/lang/Thread.java") to url 
     * ("file:///C:/Sources/java/lang/Thread.java"). Uses GlobalPathRegistry
     * if global == true.
     *
     * @param relativePath a relative path (java/lang/Thread.java)
     * @param global true if global path should be used
     * @return url
     */
    public String getURL (String relativePath, boolean global) {
        return getContext ().getURL (relativePath, global);
    }
    
    public String getURL (
        StackFrame sf,
        String stratumn
    ) {
        try {
            return getURL (
                convertSlash (sf.location ().sourcePath (stratumn)),
                true
            );
        } catch (AbsentInformationException e) {
            return getURL (
                convertClassNameToRelativePath (
                    sf.location ().declaringType ().name ()
                ),
                true
            );
        }
    }
    
    public String getURL (
        Location loc,
        String stratumn
    ) {
        try {
            return getURL (
                convertSlash(loc.sourcePath(stratumn)),
                true
            );
        } catch (AbsentInformationException e) {
            return getURL (
                convertClassNameToRelativePath (
                    loc.declaringType().name()
                ),
                true
            );
        }
    }
    
    /**
     * Returns array of source roots.
     */
    public String[] getSourceRoots () {
        return getContext ().getSourceRoots ();
    }
    
    /**
     * Sets array of source roots.
     *
     * @param sourceRoots a new array of sourceRoots
     */
    public void setSourceRoots (String[] sourceRoots) {
        getContext ().setSourceRoots (sourceRoots);
    }
    
    /**
     * Returns set of original source roots.
     *
     * @return set of original source roots
     */
    public String[] getOriginalSourceRoots () {
        return getContext ().getOriginalSourceRoots ();
    }
    
    /**
     * Returns the project's source roots.
     * 
     * @return array of source roots belonging to the project
     */
    public String[] getProjectSourceRoots() {
        try {
            java.lang.reflect.Method getProjectSourceRootsMethod =
                    getContext().getClass().getMethod("getProjectSourceRoots", new Class[] {}); // NOI18N
            String[] projectSourceRoots =
                        (String[]) getProjectSourceRootsMethod.invoke(getContext(), new Object[] {});
            return projectSourceRoots;
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return new String[] {};
        }
    }
    
    /**
     * Adds property change listener.
     *
     * @param l new listener.
     */
    public void addPropertyChangeListener (PropertyChangeListener l) {
        getContext ().addPropertyChangeListener (l);
    }

    /**
     * Removes property change listener.
     *
     * @param l removed listener.
     */
    public void removePropertyChangeListener (
        PropertyChangeListener l
    ) {
        getContext ().removePropertyChangeListener (l);
    }
    
    
    // utility methods .........................................................

    public boolean sourceAvailable (
        String relativePath
    ) {
        return getURL (relativePath, true) != null;
    }

    public boolean sourceAvailable (
        JPDAThread t,
        String stratumn
    ) {
        try {
            return sourceAvailable (convertSlash (t.getSourcePath (stratumn)));
        } catch (AbsentInformationException e) {
            return sourceAvailable (convertClassNameToRelativePath (t.getClassName ()));
        }
    }

    public boolean sourceAvailable (
        Field f
    ) {
        String className = f.getClassName ();
        return sourceAvailable (className);
    }

    public boolean sourceAvailable (
        CallStackFrame csf,
        String stratumn
    ) {
        try {
            return sourceAvailable (convertSlash (csf.getSourcePath (stratumn)));
        } catch (AbsentInformationException e) {
            return sourceAvailable (convertClassNameToRelativePath (csf.getClassName ()));
        }
    }

    public String getURL (
        CallStackFrame csf,
        String stratumn
    ) {
        try {
            return getURL (convertSlash (csf.getSourcePath (stratumn)), true);
        } catch (AbsentInformationException e) {
            return getURL (
                convertClassNameToRelativePath (csf.getClassName ()),
                true
            );
        }
    }

    public boolean showSource (
        JPDAThread t,
        String stratumn
    ) {
        int lineNumber = t.getLineNumber (stratumn);
        if (lineNumber < 1) lineNumber = 1;
        try {
            return EditorContextBridge.getContext().showSource (
                getURL (convertSlash (t.getSourcePath (stratumn)), true),
                lineNumber,
                debugger
            );
        } catch (AbsentInformationException e) {
            return EditorContextBridge.getContext().showSource (
                getURL (
                    convertClassNameToRelativePath (t.getClassName ()), 
                    true
                ),
                lineNumber,
                debugger
            );
        }
    }

    public boolean showSource (Field v) {
        String fieldName = ((Field) v).getName ();
        String className = className = ((Field) v).getClassName ();
        String url = getURL (
            EditorContextBridge.getRelativePath (className), true
        );
        if (url == null) return false;
        int lineNumber = lineNumber = EditorContextBridge.getContext().getFieldLineNumber (
            url,
            className,
            fieldName
        );
        if (lineNumber < 1) lineNumber = 1;
        return EditorContextBridge.getContext().showSource (
            url,
            lineNumber,
            debugger
        );
    }

    private static String convertSlash (String original) {
        return original.replace (File.separatorChar, '/');
    }

    public static String convertClassNameToRelativePath (
        String className
    ) {
        int i = className.indexOf ('$');
        if (i > 0) className = className.substring (0, i);
        String sourceName = className.replace
            ('.', '/') + ".java";
        return sourceName;
    }

    public Object annotate (
        JPDAThread t,
        String stratumn
    ) {
        int lineNumber = t.getLineNumber (stratumn);
        if (lineNumber < 1) return null;
        try {
            return EditorContextBridge.getContext().annotate (
                getURL (convertSlash (t.getSourcePath (stratumn)), true),
                lineNumber,
                EditorContext.CURRENT_LINE_ANNOTATION_TYPE,
                debugger
            );
        } catch (AbsentInformationException e) {
            return EditorContextBridge.getContext().annotate (
                getURL (
                    convertClassNameToRelativePath (t.getClassName ()), true
                ),
                lineNumber,
                EditorContext.CURRENT_LINE_ANNOTATION_TYPE,
                debugger
            );
        }
    }

    public Object annotate (
        CallStackFrame csf,
        String stratumn
    ) {
        int lineNumber = csf.getLineNumber (stratumn);
        if (lineNumber < 1) return null;
        try {
            return EditorContextBridge.getContext().annotate (
                getURL (convertSlash (csf.getSourcePath (stratumn)), true),
                lineNumber,
                EditorContext.CALL_STACK_FRAME_ANNOTATION_TYPE,
                debugger
            );
        } catch (AbsentInformationException e) {
            return EditorContextBridge.getContext().annotate (
                getURL (
                    convertClassNameToRelativePath (csf.getClassName ()), true
                ),
                lineNumber,
                EditorContext.CALL_STACK_FRAME_ANNOTATION_TYPE,
                debugger
            );
        }
    }

    
    // innerclasses ............................................................

    private static class CompoundContextProvider extends SourcePathProvider {

        private SourcePathProvider cp1, cp2;

        CompoundContextProvider (
            SourcePathProvider cp1,
            SourcePathProvider cp2
        ) {
            this.cp1 = cp1;
            this.cp2 = cp2;
        }

        public String getURL (String relativePath, boolean global) {
            String p1 = cp1.getURL (relativePath, global);
            if (p1 != null) return p1;
            return cp2.getURL (relativePath, global);
        }

        public String getRelativePath (
            String url, 
            char directorySeparator, 
            boolean includeExtension
        ) {
            String p1 = cp1.getRelativePath (
                url, 
                directorySeparator, 
                includeExtension
            );
            if (p1 != null) return p1;
            return cp2.getRelativePath (
                url, 
                directorySeparator, 
                includeExtension
            );
        }

        public String getSourceRoot(String url) {
            String sourceRoot = cp1.getSourceRoot(url);
            if (sourceRoot == null) {
                sourceRoot = cp2.getSourceRoot(url);
            }
            return sourceRoot;
        }
        
        public String[] getSourceRoots () {
            String[] fs1 = cp1.getSourceRoots ();
            String[] fs2 = cp2.getSourceRoots ();
            String[] fs = new String [fs1.length + fs2.length];
            System.arraycopy (fs1, 0, fs, 0, fs1.length);
            System.arraycopy (fs2, 0, fs, fs1.length, fs2.length);
            return fs;
        }
    
        public String[] getOriginalSourceRoots () {
            String[] fs1 = cp1.getOriginalSourceRoots ();
            String[] fs2 = cp2.getOriginalSourceRoots ();
            String[] fs = new String [fs1.length + fs2.length];
            System.arraycopy (fs1, 0, fs, 0, fs1.length);
            System.arraycopy (fs2, 0, fs, fs1.length, fs2.length);
            return fs;
        }
        
        public String[] getProjectSourceRoots() {
            String[] projectSourceRoots1;
            String[] projectSourceRoots2;
            //System.err.println("\nCompoundContextProvider["+toString()+"].getProjectSourceRoots()...\n");
            try {
                java.lang.reflect.Method getProjectSourceRootsMethod = cp1.getClass().getMethod("getProjectSourceRoots", new Class[] {}); // NOI18N
                projectSourceRoots1 = (String[]) getProjectSourceRootsMethod.invoke(cp1, new Object[] {});
            } catch (Exception ex) {
                projectSourceRoots1 = new String[0];
            }
            try {
                java.lang.reflect.Method getProjectSourceRootsMethod = cp2.getClass().getMethod("getProjectSourceRoots", new Class[] {}); // NOI18N
                projectSourceRoots2 = (String[]) getProjectSourceRootsMethod.invoke(cp2, new Object[] {});
            } catch (Exception ex) {
                projectSourceRoots2 = new String[0];
            }
            if (projectSourceRoots1.length == 0) {
                //System.err.println("\nCompoundContextProvider.getProjectSourceRoots() = "+java.util.Arrays.toString(projectSourceRoots2)+"\n");
                return projectSourceRoots2;
            }
            if (projectSourceRoots2.length == 0) {
                //System.err.println("\nCompoundContextProvider.getProjectSourceRoots() = "+java.util.Arrays.toString(projectSourceRoots1)+"\n");
                return projectSourceRoots1;
            }
            String[] projectSourceRoots = new String[projectSourceRoots1.length + projectSourceRoots2.length];
            System.arraycopy (projectSourceRoots1, 0, projectSourceRoots, 0, projectSourceRoots1.length);
            System.arraycopy (projectSourceRoots2, 0, projectSourceRoots, projectSourceRoots1.length, projectSourceRoots2.length);
            //System.err.println("\nCompoundContextProvider.getProjectSourceRoots() = "+java.util.Arrays.toString(projectSourceRoots)+"\n");
            return projectSourceRoots;
        }

        public void setSourceRoots (String[] sourceRoots) {
            cp1.setSourceRoots (sourceRoots);
            cp2.setSourceRoots (sourceRoots);
        }

        public void addPropertyChangeListener (PropertyChangeListener l) {
            cp1.addPropertyChangeListener (l);
            cp2.addPropertyChangeListener (l);
        }

        public void removePropertyChangeListener (PropertyChangeListener l) {
            cp1.removePropertyChangeListener (l);
            cp2.removePropertyChangeListener (l);
        }

        @Override
        public String toString() {
            return "CompoundContextProvider["+cp1.toString()+", "+cp2.toString()+"]";
        }
        
        
    }

    private static class CompoundAnnotation {
        CompoundAnnotation () {}
        
        Object annotation1;
        Object annotation2;
    }
}


