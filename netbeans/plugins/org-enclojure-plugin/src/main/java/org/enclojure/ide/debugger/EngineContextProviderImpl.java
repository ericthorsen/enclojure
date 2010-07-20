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
 * MicroSystems, Inc. All Rights Reserved.
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

import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

import org.netbeans.spi.debugger.jpda.SourcePathProvider;
import org.netbeans.api.debugger.Session;
import org.netbeans.spi.debugger.ContextProvider;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;

import org.openide.filesystems.FileObject;

@SuppressWarnings("unchecked") 
public class EngineContextProviderImpl extends SourcePathProvider {
    
    private static boolean verbose = 
    System.getProperty ("netbeans.debugger.enginecontextproviderimpl") != null;
    
    private static final LogAdapter LOG = new LogAdapter(EngineContextProviderImpl.class.getName());

    private static final Pattern thisDirectoryPattern = Pattern.compile("(/|\\A)\\./");
    private static final Pattern parentDirectoryPattern = Pattern.compile("(/|\\A)([^/]+?)/\\.\\./");

    private Session         session;
    private ClassPath       sourcePath;
    private String[]        originalSourceRoots;
    private PropertyChangeSupport pcs;

    
    {pcs = new PropertyChangeSupport (this);}
    
    private static String normalize(String path) {
      for (Matcher m = thisDirectoryPattern.matcher(path); m.find(); )
      {
        path = m.replaceAll("$1");
        m = thisDirectoryPattern.matcher(path);
      }
      for (Matcher m = parentDirectoryPattern.matcher(path); m.find(); )
      {
        if (!m.group(2).equals("..")) {
          path = path.substring(0, m.start()) + m.group(1) + path.substring(m.end());
          m = parentDirectoryPattern.matcher(path);        
        }
      }
      return path;
    }

    public EngineContextProviderImpl (ContextProvider contextProvider) {
        this.session = (Session) contextProvider.lookupFirst (null, Session.class);
        sourcePath = (ClassPath) contextProvider.lookupFirst (null, ClassPath.class);
        //defaultSourcePathProvider = (SourcePathProvider)contextProvider.lookupFirst (null, SourcePathProvider.class);
        if (sourcePath == null) {
            Set s = GlobalPathRegistry.getDefault ().getPaths 
                (ClassPath.SOURCE);
            
            Set s2 = GlobalPathRegistry.getDefault ().getSourceRoots(); 

            sourcePath = ClassPathSupport.createProxyClassPath (
                (ClassPath[]) s.toArray (new ClassPath [s.size ()])
            );
        }
    }

     
   @Override
    public String getURL (String relativePath,boolean notSure) {
       if(org.enclojure.ide.debugger.util.Utils.isClojure(relativePath))
       {
        try {
        FileObject fo = sourcePath.findResource(normalize(relativePath));
        if(fo != null)
            return fo.getURL ().toString ();
        } catch(Exception e) {
            LOG.log(Level.WARNING,"cannot find URL for "+relativePath);
        } 
       } 
       return null;
    }
    
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
        return "clojure1/src/test.clj";
    }
   
    
    /**
     * Returns set of original source roots.
     *
     * @return set of original source roots
     */
    public String[] getOriginalSourceRoots () {
        return new String[0];
        //return this.defaultSourcePathProvider.getOriginalSourceRoots();//originalSourceRoots;
    }
    
    /**
     * Returns array of source roots.
     *
     * @return array of source roots
     */
    public String[] getSourceRoots () {
              return new String[0];
    }
    
    /**
     * Sets array of source roots.
     *
     * @param sourceRoots a new array of sourceRoots
     */
    public void setSourceRoots (String[] sourceRoots) {
    }
    
    /**
     * Adds property change listener.
     *
     * @param l new listener.
     */
    public void addPropertyChangeListener (PropertyChangeListener l) {
    }

    /**
     * Removes property change listener.
     *
     * @param l removed listener.
     */
    public void removePropertyChangeListener (
        PropertyChangeListener l
    ) {
  }        
}






