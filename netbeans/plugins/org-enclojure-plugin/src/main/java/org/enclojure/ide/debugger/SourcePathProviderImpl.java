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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.enclojure.ide.debugger.util.Utils;
import org.netbeans.api.java.platform.JavaPlatformManager;

import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.spi.debugger.jpda.SourcePathProvider;
import org.netbeans.spi.debugger.ContextProvider;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.netbeans.api.java.classpath.GlobalPathRegistryEvent;
import org.netbeans.api.java.classpath.GlobalPathRegistryListener;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.queries.SourceForBinaryQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.JarFileSystem;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.WeakListeners;

public class SourcePathProviderImpl extends SourcePathProvider {

    private static final LogAdapter LOG = new LogAdapter(SourcePathProviderImpl.class.getName());

    private static boolean verbose =
            System.getProperty("netbeans.debugger.sourcepathproviderimpl") != null;
    
    private static final Pattern thisDirectoryPattern = Pattern.compile("(/|\\A)\\./");
    private static final Pattern parentDirectoryPattern = Pattern.compile("(/|\\A)([^/]+?)/\\.\\./");
    /** Contains all known source paths + jdk source path for JPDAStart task */
    private ClassPath originalSourcePath;
    /** Contains the additional source roots, added at a later time to the original roots. */
    private Set<String> additionalSourceRoots;
    /** Contains just the source paths which are selected for debugging. */
    private ClassPath smartSteppingSourcePath;
    private String[] projectSourceRoots;
    private PropertyChangeSupport pcs;
    private PathRegistryListener pathRegistryListener;
    // private SourcesHelper sourcesHelper;

    public SourcePathProviderImpl() {
        pcs = new PropertyChangeSupport(this);
    }

    public SourcePathProviderImpl(ContextProvider contextProvider) {
        pcs = new PropertyChangeSupport(this);
        //this.session = (Session) contextProvider.lookupFirst 
        //    (null, Session.class);
        Map properties = contextProvider.lookupFirst(null, Map.class);
        //   sourcesHelper = contextProvider.lookupFirst(null, SourcesHelper.class);


        ClassPath cljClassPath = null;
        try {
            //??cljClassPath = (ClassPath) RT.var("org.enclojure.platform.pathutils", "get-classpath-for-clojure-files").invoke();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }

        // 2) get default allSourceRoots of source roots used for stepping

        if (properties != null) {
            smartSteppingSourcePath = (ClassPath) properties.get("sourcepath");
            ClassPath jdkCP = (ClassPath) properties.get("jdksources");
            if ((jdkCP == null) && (JavaPlatform.getDefault() != null)) {
                jdkCP = JavaPlatform.getDefault().getSourceFolders();
            }
            originalSourcePath = jdkCP == null ? smartSteppingSourcePath : ClassPathSupport.createProxyClassPath(
                    new ClassPath[]{
                        jdkCP,
                        smartSteppingSourcePath
                    });
            projectSourceRoots = getSourceRoots(originalSourcePath);
            Set<FileObject> preferredRoots = new HashSet<FileObject>();
            preferredRoots.addAll(Arrays.asList(originalSourcePath.getRoots()));
            Set<FileObject> globalRoots = new TreeSet<FileObject>(new FileObjectComparator());
            globalRoots.addAll(GlobalPathRegistry.getDefault().getSourceRoots());
            globalRoots.removeAll(preferredRoots);
            ClassPath globalCP = ClassPathSupport.createClassPath(globalRoots.toArray(new FileObject[0]));
            originalSourcePath = ClassPathSupport.createProxyClassPath(
                    new ClassPath[]{
                        cljClassPath
                                , originalSourcePath
                                , globalCP
                    });
        } else {
            pathRegistryListener = new PathRegistryListener();
            GlobalPathRegistry.getDefault().addGlobalPathRegistryListener(
                    WeakListeners.create(GlobalPathRegistryListener.class,
                    pathRegistryListener,
                    GlobalPathRegistry.getDefault()));
            JavaPlatformManager.getDefault().addPropertyChangeListener(
                    WeakListeners.propertyChange(pathRegistryListener,
                    JavaPlatformManager.getDefault()));

            List<FileObject> allSourceRoots = new ArrayList<FileObject>();
            Set<FileObject> preferredRoots = new HashSet<FileObject>();
            Set<FileObject> addedBinaryRoots = new HashSet<FileObject>();
            Project mainProject = OpenProjects.getDefault().getMainProject();
            if (mainProject != null) {
                SourceGroup[] sgs = ProjectUtils.getSources(mainProject).getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
                for (SourceGroup sg : sgs) {
                    ClassPath ecp = ClassPath.getClassPath(sg.getRootFolder(), ClassPath.SOURCE);
                    if (ecp == null) {
                        ecp = ClassPath.getClassPath(sg.getRootFolder(), ClassPath.EXECUTE);
                    }
                    if (ecp != null) {
                        FileObject[] binaryRoots = ecp.getRoots();
                        for (FileObject fo : binaryRoots) {
                            if (addedBinaryRoots.contains(fo)) {
                                continue;
                            }
                            addedBinaryRoots.add(fo);
                            try {
                                FileObject[] roots = SourceForBinaryQuery.findSourceRoots(fo.getURL()).getRoots();
                                for (FileObject fr : roots) {
                                    if (!preferredRoots.contains(fr)) {
                                        allSourceRoots.add(fr);
                                        preferredRoots.add(fr);
                                    }
                                }
                            } catch (FileStateInvalidException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    }
                }
            }
            Set<FileObject> globalRoots = new TreeSet<FileObject>(new FileObjectComparator());
            globalRoots.addAll(GlobalPathRegistry.getDefault().getSourceRoots());
            for (FileObject fo : globalRoots) {
                if (!preferredRoots.contains(fo)) {
                    allSourceRoots.add(fo);
                }
            }


            originalSourcePath =
                    ClassPathSupport.createProxyClassPath(
                    new ClassPath[]{
                        cljClassPath, ClassPathSupport.createClassPath(
                        allSourceRoots.toArray(new FileObject[allSourceRoots.size()]))
                    });

            projectSourceRoots = getSourceRoots(originalSourcePath);

            JavaPlatform[] platforms = JavaPlatformManager.getDefault().
                    getInstalledPlatforms();
            int i, k = platforms.length;
            for (i = 0; i < k; i++) {
                FileObject[] roots = platforms[i].getSourceFolders().
                        getRoots();
                int j, jj = roots.length;
                for (j = 0; j < jj; j++) {
                    allSourceRoots.remove(roots[j]);
                }
            }
            smartSteppingSourcePath = ClassPathSupport.createClassPath(
                    allSourceRoots.toArray(new FileObject[allSourceRoots.size()]));
        }

        if (verbose) {
            System.out.println("SPPI: init originalSourcePath " + originalSourcePath);
        }
        if (verbose) {
            System.out.println(
                    "SPPI: init smartSteppingSourcePath " + smartSteppingSourcePath);
        }
    }

    static String checkClojureFile(String relativePath) {
        try {
            if (Utils.isClojure(relativePath)) {
                File f = new File(relativePath);
                LOG.log(Level.ALL, " checkClojureFile " + relativePath);
                if (f.exists()) {
                    return f.toURL().toString();
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "checkClojureFile failed to resolve " + relativePath + " " + e.getMessage());
        }
        return null;
    }

    /**
     * Translates a relative path ("java/lang/Thread.java") to url 
     * ("file:///C:/Sources/java/lang/Thread.java"). Uses GlobalPathRegistry
     * if global == true.
     *
     * @param relativePath a relative path (java/lang/Thread.java)
     * @param global true if global path should be used
     * @return url or <code>null</code>
     */
    public String getURL(String relativePath, boolean global) {
        if (verbose) {
            System.out.println("SPPI: getURL " + relativePath + " global " + global);
        //ET I seem to get explicit paths here for clojure files?
        }
        String cf = checkClojureFile(relativePath);
        if (cf != null) {
            return cf;
        }
        FileObject fo;
        try {
            LOG.log(Level.ALL, " SourcePathProviderImpl::getURL - " + relativePath);

            if (originalSourcePath == null) {
                fo = GlobalPathRegistry.getDefault().findResource(relativePath);
            } else {
                synchronized (this) {
                    if (!global) {
                        fo = smartSteppingSourcePath.findResource(relativePath);
                        if (verbose) {
                            System.out.println("SPPI:   fo " + fo);
                        }
                    } else {
                        fo = originalSourcePath.findResource(relativePath);
                        if (verbose) {
                            System.out.println("SPPI:   fo " + fo);
                        }
                    }
                }
            }
            if (fo == null) {
                LOG.log(Level.ALL, " SourcePathProviderImpl::getURL - NO GO for: " + relativePath);

                return null;
            }
            return fo.getURL().toString();
        } catch (FileStateInvalidException e) {
            if (verbose) {
                System.out.println("SPPI:   FileStateInvalidException");
            }
            return null;
        }
    }

    /**
     * Translates a relative path to all possible URLs.
     * Uses GlobalPathRegistry if global == true.
     *
     * @param relativePath a relative path (java/lang/Thread.java)
     * @param global true if global path should be used
     * @return url
     */
    public String[] getAllURLs(String relativePath, boolean global) {
        if (verbose) {
            System.out.println("SPPI: getURL " + relativePath + " global " + global);
        }
        List<FileObject> fos;
        relativePath = normalize(relativePath);
        if (originalSourcePath == null) {
            fos = new ArrayList<FileObject>();
            for (ClassPath cp : GlobalPathRegistry.getDefault().getPaths(ClassPath.SOURCE)) {
                fos.addAll(cp.findAllResources(relativePath));
            }
        } else {
            synchronized (this) {
                if (!global) {
                    fos = smartSteppingSourcePath.findAllResources(relativePath);
                    if (verbose) {
                        System.out.println("SPPI:   fos " + fos);
                    }
                } else {
                    fos = originalSourcePath.findAllResources(relativePath);
                    if (verbose) {
                        System.out.println("SPPI:   fos " + fos);
                    }
                }
            }
        }
        List<String> urls = new ArrayList<String>(fos.size());
        for (FileObject fo : fos) {
            try {
                urls.add(fo.getURL().toString());
            } catch (FileStateInvalidException e) {
                if (verbose) {
                    System.out.println("SPPI:   FileStateInvalidException for " + fo);
                // skip it
                }
            }
        }
        return urls.toArray(new String[0]);
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
    public String getRelativePath(
            String url,
            char directorySeparator,
            boolean includeExtension) {
        // 1) url -> FileObject
        FileObject fo = null;
        URL theUrl=null;
        if (verbose) {
            System.out.println("SPPI: getRelativePath " + url);
        }
        try {
            LOG.log(Level.ALL, " SourcePathProviderImpl::getRelativePath - " + url);

            fo = URLMapper.findFileObject(theUrl=new URL(url));
            if (verbose) {
                System.out.println("SPPI:   fo " + fo);
            }
        } catch (MalformedURLException e) {
            //e.printStackTrace ();
            return null;
        }
        String relativePath = smartSteppingSourcePath.getResourceName(
                fo,
                directorySeparator,
                includeExtension);
        if (relativePath == null) {
            // fallback to FileObject's class path
            ClassPath cp = ClassPath.getClassPath(fo, ClassPath.SOURCE);
            if (cp == null) {
                cp = ClassPath.getClassPath(fo, ClassPath.COMPILE);
            }
            if (cp == null) {
                //ET - If the url of the full path exists, return that...
                if(fo!=null && theUrl!=null)
                {                               
                    File f = new File(theUrl.getPath());
                    if(f.exists())
                        return f.getAbsolutePath();                   
                }
                return null;
            }
            relativePath = cp.getResourceName(
                    fo,
                    directorySeparator,
                    includeExtension);
        }
        if (relativePath == null) {
            LOG.log(Level.ALL, " SourcePathProviderImpl::getRelativePath - NO GO! " + url);
        }
        return relativePath;
    }

    /**
     * Returns the source root (if any) for given url.
     *
     * @param url a url of resource file
     *
     * @return the source root or <code>null</code> when no source root was found.
     */
    @Override
    public synchronized String getSourceRoot(String url) {
        for (FileObject fileObject : originalSourcePath.getRoots()) {
            try {
                String rootURL = fileObject.getURL().toString();
                if (url.startsWith(rootURL)) {
                    String root = getRoot(fileObject);
                    if (root != null) {
                        return root;
                    }
                }
            } catch (FileStateInvalidException ex) {
                // Invalid source root - skip
            }
        }
        return null; // not found

    }

    private String[] getSourceRoots(ClassPath classPath) {
        FileObject[] sourceRoots = classPath.getRoots();
        List<String> roots = new ArrayList<String>(sourceRoots.length);
        for (FileObject fo : sourceRoots) {
            String root = getRoot(fo);
            if (root != null) {
                roots.add(root);
            }
        }
        return roots.toArray(new String[0]);
    }

    /**
     * Returns allSourceRoots of original source roots.
     *
     * @return allSourceRoots of original source roots
     */
    public synchronized String[] getOriginalSourceRoots() {
        return getSourceRoots(originalSourcePath);
    }

    /**
     * Returns array of source roots.
     *
     * @return array of source roots
     */
    public synchronized String[] getSourceRoots() {
        //    return getSourceRoots(smartSteppingSourcePath);
        return getSourceRoots(originalSourcePath);

    }

    /**
     * Returns the project's source roots.
     * 
     * @return array of source roots belonging to the project
     */
    public String[] getProjectSourceRoots() {
        return projectSourceRoots;
    }

    /**
     * Sets array of source roots.
     *
     * @param sourceRoots a new array of sourceRoots
     */
    public void setSourceRoots(String[] sourceRoots) {

        LOG.log(Level.FINE, "SourcePathProviderImpl.setSourceRoots(" + java.util.Arrays.asList(sourceRoots) + ")");

        Set<String> newRoots = new HashSet<String>(Arrays.asList(sourceRoots));
        ClassPath oldCP = null;
        ClassPath newCP = null;
        synchronized (this) {
            List<FileObject> sourcePath = new ArrayList<FileObject>(
                    Arrays.asList(smartSteppingSourcePath.getRoots()));
            List<FileObject> sourcePathOriginal = new ArrayList<FileObject>(
                    Arrays.asList(originalSourcePath.getRoots()));

            // First check whether there are some new source roots
            Set<String> newOriginalRoots = new HashSet<String>(newRoots);
            for (FileObject fo : sourcePathOriginal) {
                newOriginalRoots.remove(getRoot(fo));
            }
            if (!newOriginalRoots.isEmpty()) {
                for (String root : newOriginalRoots) {
                    FileObject fo = getFileObject(root);
                    if (fo != null) {
                        sourcePathOriginal.add(fo);
                    }
                }
                originalSourcePath =
                        ClassPathSupport.createClassPath(
                        sourcePathOriginal.toArray(new FileObject[0]));
                if (additionalSourceRoots == null) {
                    additionalSourceRoots = new HashSet<String>();
                }
                additionalSourceRoots.addAll(newOriginalRoots);
            }

            // Then correct the smart-stepping path
            Set<String> newSteppingRoots = new HashSet<String>(newRoots);
            for (FileObject fo : sourcePath) {
                newSteppingRoots.remove(getRoot(fo));
            }
            Set<FileObject> removedSteppingRoots = new HashSet<FileObject>();
            Set<FileObject> removedOriginalRoots = new HashSet<FileObject>();
            for (FileObject fo : sourcePath) {
                String spr = getRoot(fo);
                if (!newRoots.contains(spr)) {
                    removedSteppingRoots.add(fo);
                    if (additionalSourceRoots != null && additionalSourceRoots.contains(spr)) {
                        removedOriginalRoots.add(fo);
                        additionalSourceRoots.remove(spr);
                        if (additionalSourceRoots.size() == 0) {
                            additionalSourceRoots = null;
                        }
                    }
                }
            }
            if (removedOriginalRoots.size() > 0) {
                sourcePathOriginal.removeAll(removedOriginalRoots);
                originalSourcePath =
                        ClassPathSupport.createClassPath(
                        sourcePathOriginal.toArray(new FileObject[0]));
            }
            if (newSteppingRoots.size() > 0 || removedSteppingRoots.size() > 0) {
                for (String root : newSteppingRoots) {
                    FileObject fo = getFileObject(root);
                    if (fo != null) {
                        sourcePath.add(fo);
                    }
                }
                sourcePath.removeAll(removedSteppingRoots);
                oldCP = smartSteppingSourcePath;
                smartSteppingSourcePath =
                        ClassPathSupport.createClassPath(
                        sourcePath.toArray(new FileObject[0]));
                newCP = smartSteppingSourcePath;
            }
        }

        if (oldCP != null) {
            pcs.firePropertyChange(PROP_SOURCE_ROOTS, oldCP, newCP);
        }
    }

    /**
     * Adds property change listener.
     *
     * @param l new listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    /**
     * Removes property change listener.
     *
     * @param l removed listener.
     */
    public void removePropertyChangeListener(
            PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    // helper methods ..........................................................
    /**
     * Normalizes the given path by removing unnecessary "." and ".." sequences.
     * This normalization is needed because the compiler stores source paths like "foo/../inc.jsp" into .class files. 
     * Such paths are not supported by our ClassPath API.
     * TODO: compiler bug? report to JDK?
     * 
     * @param path path to normalize
     * @return normalized path without "." and ".." elements
     */
    public static String normalize(String path) {
        for (Matcher m = thisDirectoryPattern.matcher(path); m.find();) {
            path = m.replaceAll("$1");
            m = thisDirectoryPattern.matcher(path);
        }
        for (Matcher m = parentDirectoryPattern.matcher(path); m.find();) {
            if (!m.group(2).equals("..")) {
                path = path.substring(0, m.start()) + m.group(1) + path.substring(m.end());
                m = parentDirectoryPattern.matcher(path);
            }
        }
        return path;
    }

    /**
     * Returns source root for given ClassPath root as String, or <code>null</code>.
     */
    private static String getRoot(FileObject fileObject) {
        File f = null;
        String path = "";
        try {
            if (fileObject.getFileSystem() instanceof JarFileSystem) {
                f = ((JarFileSystem) fileObject.getFileSystem()).getJarFile();
                if (!fileObject.isRoot()) {
                    path = "!/" + fileObject.getPath();
                }
            } else {
                f = FileUtil.toFile(fileObject);
            }
        } catch (FileStateInvalidException ex) {
        }
        if (f != null) {
            return f.getAbsolutePath() + path;
        } else {
            return null;
        }
    }

    /**
     * Returns FileObject for given String.
     */
    private FileObject getFileObject(String file) {
        File f = new File(file);
        FileObject fo = FileUtil.toFileObject(f);
        String path = null;
        if (fo == null && file.contains("!/")) {
            int index = file.indexOf("!/");
            f = new File(file.substring(0, index));
            fo = FileUtil.toFileObject(f);
            path = file.substring(index + "!/".length());
        }
        if (fo != null && FileUtil.isArchiveFile(fo)) {
            fo = FileUtil.getArchiveRoot(fo);
            if (path != null) {
                fo = fo.getFileObject(path);
            }
        }
        return fo;
    }

    private class PathRegistryListener implements GlobalPathRegistryListener, PropertyChangeListener {

        public void pathsAdded(GlobalPathRegistryEvent event) {
            List<FileObject> addedRoots = new ArrayList<FileObject>();
            for (ClassPath cp : event.getChangedPaths()) {
                for (FileObject fo : cp.getRoots()) {
                    addedRoots.add(fo);
                }
            }
            if (addedRoots.size() > 0) {
                synchronized (SourcePathProviderImpl.this) {
                    List<FileObject> sourcePaths = new ArrayList<FileObject>(
                            Arrays.asList(originalSourcePath.getRoots()));
                    sourcePaths.addAll(addedRoots);
                    originalSourcePath =
                            ClassPathSupport.createClassPath(
                            sourcePaths.toArray(new FileObject[0]));

                    sourcePaths = new ArrayList<FileObject>(
                            Arrays.asList(smartSteppingSourcePath.getRoots()));
                    sourcePaths.addAll(addedRoots);
                    smartSteppingSourcePath =
                            ClassPathSupport.createClassPath(
                            sourcePaths.toArray(new FileObject[0]));
                }
                pcs.firePropertyChange(PROP_SOURCE_ROOTS, null, null);
            }
        }

        public void pathsRemoved(GlobalPathRegistryEvent event) {
            List<FileObject> removedRoots = new ArrayList<FileObject>();
            for (ClassPath cp : event.getChangedPaths()) {
                for (FileObject fo : cp.getRoots()) {
                    removedRoots.add(fo);
                }
            }
            if (removedRoots.size() > 0) {
                synchronized (SourcePathProviderImpl.this) {
                    List<FileObject> sourcePaths = new ArrayList<FileObject>(
                            Arrays.asList(originalSourcePath.getRoots()));
                    sourcePaths.removeAll(removedRoots);
                    originalSourcePath =
                            ClassPathSupport.createClassPath(
                            sourcePaths.toArray(new FileObject[0]));

                    sourcePaths = new ArrayList<FileObject>(
                            Arrays.asList(smartSteppingSourcePath.getRoots()));
                    sourcePaths.removeAll(removedRoots);
                    smartSteppingSourcePath =
                            ClassPathSupport.createClassPath(
                            sourcePaths.toArray(new FileObject[0]));
                }
                pcs.firePropertyChange(PROP_SOURCE_ROOTS, null, null);
            }
        }

        public void propertyChange(PropertyChangeEvent evt) {
            // JDK sources changed
            JavaPlatform[] platforms = JavaPlatformManager.getDefault().
                    getInstalledPlatforms();
            boolean changed = false;
            synchronized (SourcePathProviderImpl.this) {
                List<FileObject> sourcePaths = new ArrayList<FileObject>(
                        Arrays.asList(originalSourcePath.getRoots()));
                for (JavaPlatform jp : platforms) {
                    FileObject[] roots = jp.getSourceFolders().getRoots();
                    for (FileObject fo : roots) {
                        if (!sourcePaths.contains(fo)) {
                            sourcePaths.add(fo);
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    originalSourcePath =
                            ClassPathSupport.createClassPath(
                            sourcePaths.toArray(new FileObject[0]));
                }
            }
            if (changed) {
                pcs.firePropertyChange(PROP_SOURCE_ROOTS, null, null);
            }
        }
    }

    private static final class FileObjectComparator implements Comparator<FileObject> {

        public int compare(FileObject fo1, FileObject fo2) {
            String r1 = getRoot(fo1);
            String r2 = getRoot(fo2);
            if (r1 == null) {
                return -1;
            }
            if (r2 == null) {
                return +1;
            }
            return r1.compareTo(r2);
        }
    }
}
