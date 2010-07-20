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
package org.enclojure.ide.debugger.util;

import java.io.File;
import java.net.*;
import java.util.Set;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import javax.swing.*;
import javax.swing.text.*;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.openide.nodes.*;
import org.openide.filesystems.*;
import org.openide.text.*;
import org.openide.cookies.*;
import org.openide.util.Exceptions;
import org.openide.util.Mutex;
import org.openide.windows.TopComponent;


public class Utils {
    
    private static final LogAdapter LOG = new LogAdapter(Utils.class.getName());

    public static Set<ClassPath> getClasspathForSource()
    {
        return GlobalPathRegistry.getDefault().getPaths("classpath/source");
    }

    public static String getResourceNameFromFullPath(String fullPath)
    {
        try {
            java.io.File fPath = null;
            if (fullPath.startsWith("file:/")) {
                fPath = new java.io.File(new java.net.URL(fullPath).toURI());
            } else {
                fPath = new java.io.File(fullPath);
            }
            FileObject fo = FileUtil.toFileObject(fPath);
            Object[] pcks = getClasspathForSource().toArray();
            for (int i = 0; i < pcks.length; i++) {
                ClassPath p = (ClassPath)pcks[i];
                if (p.contains(fo)) {
                    return p.getResourceName(fo);
                }
            }
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
        } catch (URISyntaxException ex) {
                    Exceptions.printStackTrace(ex);
        }

        return null;
    }

    public static String getClojureNamespaceFromResourceName(String resourceName)
    {
        //ResourceName = com/tt/core.clj
        //NamespaceName = com.tt.core
        String nameSpace = resourceName.substring(0, resourceName.lastIndexOf('.'));
        return nameSpace.replace(File.separator, ".");
    }
    
    public static FileObject getFileObjectFromUrl(String url) {
        
        FileObject fo = null;
        if(url!=null &&! "".equals(url))
        {
        try {
            fo = URLMapper.findFileObject(new URL(url));
        } catch (MalformedURLException e) {
         LOG.log(java.util.logging.Level.SEVERE,"Exception in getFileObjectFromUrl (" + url + ") " + e.getMessage());
        }
        }
        return fo;
    }
    
    public static boolean isClojure(FileObject fo) {
        return fo != null && "text/x-clojure".equals(fo.getMIMEType());   
    }
    
    public static boolean isClojure(String url) {        
        if(url==null || "".equals(url))
            return false;
        if(url.endsWith(".clj"))
            return true;
        FileObject fo = getFileObjectFromUrl(url);
        return isClojure(fo);
    }



//    public static String getTargetServerID(FileObject fo) {
//        if (fo != null) {
//            Project p = FileOwnerQuery.getOwner(fo);
//            if (p != null) {
//                J2eeModuleProvider mp = (J2eeModuleProvider)p.getLookup().lookup(J2eeModuleProvider.class);
//                if (mp != null) {
//                    String serverID = mp.getServerID();
//                    return serverID;
//                }
//            }
//        }
//        return null;
//    }
    
    public static String getClojureName(String url) {

        FileObject fo = getFileObjectFromUrl(url);
        if (fo != null) {
            return fo.getNameExt();
        }
        return (url == null) ? null : url.toString();
    }
    
    public static String getClojurePath(String url) {

        LOG.log(Level.INFO,"getClojurePath for "+url.toString());
        FileObject fo = getFileObjectFromUrl(url);
        if (fo != null) {
            return "/"+fo.getPath();
        }
        return (url == null) ? null : url.toString();
    }
    

        
    /** 
     * Returns current editor component instance.
     *
     * @return current editor component instance
     */
    public static EditorCookie getCurrentEditorCookie () {
        Node[] nodes = TopComponent.getRegistry ().getCurrentNodes();
        if ( (nodes == null) ||
             (nodes.length != 1) ) return null;
        Node n = nodes [0];
        return (EditorCookie) n.getCookie (
            EditorCookie.class
        );
    }
    
    public static JEditorPane getCurrentEditor () {
        EditorCookie e = getCurrentEditorCookie ();
        if (e == null) {
            return null;
        }
        return getCurrentEditor(e);
    }    
    
    /** 
     * Returns current editor component instance.
     *
     * @return current editor component instance
     */
    public static JEditorPane getCurrentEditor (final EditorCookie e) {
        return Mutex.EVENT.readAccess(new Mutex.Action<JEditorPane>() {
            public JEditorPane run() {
                JEditorPane[] op = e.getOpenedPanes();
                return (op == null ? null : op[0]);
            }
        });
    }
        
    public static String getJavaIdentifier(StyledDocument doc, JEditorPane ep, int offset) {        
        String t = null;
        if ( (ep.getSelectionStart() <= offset) 
                && (offset <= ep.getSelectionEnd())) {
            t = ep.getSelectedText();
        }
        if (t != null) {
            return t;
        }
        int line = NbDocument.findLineNumber(doc, offset);
        int col = NbDocument.findLineColumn(doc, offset);
        try {
            javax.swing.text.Element lineElem = 
                org.openide.text.NbDocument.findLineRootElement(doc).
                getElement(line);
            if (lineElem == null) {
                return null;
            }
            int lineStartOffset = lineElem.getStartOffset();
            int lineLen = lineElem.getEndOffset() - lineStartOffset;
            t = doc.getText (lineStartOffset, lineLen);
            int identStart = col;
            while (identStart > 0 && 
                (Character.isJavaIdentifierPart (
                    t.charAt (identStart - 1)
                ) ||
                (t.charAt (identStart - 1) == '.'))) {
                identStart--;
            }
            int identEnd = col;
            while (identEnd < lineLen 
                    && Character.isJavaIdentifierPart(t.charAt(identEnd))) {
                identEnd++;
            }
            if (identStart == identEnd) {
                return null;
            }
            return t.substring (identStart, identEnd);
        } catch (javax.swing.text.BadLocationException e) {
            return null;
        }
    }    

    public static boolean isScriptlet(StyledDocument doc
                                        , JEditorPane ep
                                        , int offset) {
        String t;
        int line = NbDocument.findLineNumber(doc, offset);
        int col = NbDocument.findLineColumn(doc, offset);
        try {
            while (line > 0) {
                javax.swing.text.Element lineElem = 
                    org.openide.text.NbDocument.findLineRootElement(doc).getElement(line);
                if (lineElem == null) {
                    continue;
                }
                int lineStartOffset = lineElem.getStartOffset();
                int lineLen = lineElem.getEndOffset() - lineStartOffset;
                t = doc.getText (lineStartOffset, lineLen);
                if ((t != null) && (t.length() > 1)) {
                    int identStart;
                    if (line == NbDocument.findLineNumber(doc, offset)) {
                        identStart = col;
                    } else {
                        identStart = lineLen-1;
                    }
                    while (identStart > 0) {
                        if ((t.charAt(identStart) == '%') 
                                && (t.charAt(identStart-1) == '<')) {
                            return true;
                        }
                        if ((t.charAt(identStart) == '>') 
                                && (t.charAt(identStart-1) == '%')) {
                            return false;
                        }                    
                        identStart--;
                    }
                }
                line--;
            }
        } catch (javax.swing.text.BadLocationException e) {
        }
        return false;
    }        
    
    public static String getELIdentifier(StyledDocument doc, JEditorPane ep, int offset) {
        String t = null;
        if ( (ep.getSelectionStart () <= offset) &&
             (offset <= ep.getSelectionEnd ())
        )   t = ep.getSelectedText ();
        if (t != null) {
            if ((t.startsWith("$")) && (t.endsWith("}"))) {
                return t;
            } else {
                return null;
            }
        }
        
        int line = NbDocument.findLineNumber(doc, offset);
        int col = NbDocument.findLineColumn(doc, offset);
        try {
            javax.swing.text.Element lineElem = 
                org.openide.text.NbDocument.findLineRootElement (doc).
                getElement (line);

            if (lineElem == null) {
                return null;
            }
            int lineStartOffset = lineElem.getStartOffset ();
            int lineLen = lineElem.getEndOffset() - lineStartOffset;
            t = doc.getText (lineStartOffset, lineLen);
            int identStart = col;
            while (identStart > 0 && (t.charAt(identStart) != '$')) {
                identStart--;
            }
            if ((identStart > 0) && (t.charAt(identStart) == '$') 
                    && (t.charAt(identStart-1) == '\\')) {
                return null;
            }
            int identEnd = col;
            while ((identEnd < lineLen) && identEnd > 0 
                    && identEnd <= t.length() 
                    && (t.charAt(identEnd-1) != '}'))  {
                identEnd++;
            }
            if (identStart == identEnd) {
                return null;
            }
            String outp = t.substring(identStart, identEnd);
            if ((outp.startsWith("$")) && (outp.endsWith("}"))) {
                return outp;
            } else {            
                return null;
            }
        } catch (javax.swing.text.BadLocationException e) {
            return null;
        }
    }
    
    public static String getJavaIdentifier () {
        EditorCookie e = getCurrentEditorCookie ();
        if (e == null) {
            return null;
        }
        JEditorPane ep = getCurrentEditor (e);
        if (ep == null) {
            return null;
        }
        return getJavaIdentifier (
            e.getDocument (),
            ep,
            ep.getCaret ().getDot ()
        );
    }

//    public static String getELIdentifier () {
//        EditorCookie e = getCurrentEditorCookie ();
//        if (e == null) {
//            return null;
//        }
//        JEditorPane ep = getCurrentEditor (e);
//        if (ep == null) {
//            return null;
//        }
//        return getELIdentifier (
//            e.getDocument (),
//            ep,
//            ep.getCaret ().getDot ()
//        );
//    }

    public static boolean isScriptlet() {
        EditorCookie e = getCurrentEditorCookie ();
        if (e == null) {
            return false;
        }
        JEditorPane ep = getCurrentEditor (e);
        if (ep == null) {
            return false;
        }
        return isScriptlet(
            e.getDocument (),
            ep,
            ep.getCaret ().getDot ()
        );
    }
  
    
    
}
