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
 * Microsystems, Inc. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import org.netbeans.api.debugger.DebuggerManager;
import org.openide.cookies.EditorCookie;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

import org.netbeans.spi.debugger.jpda.EditorContext;
@SuppressWarnings("unchecked") 
public class CljEditorContextImpl extends EditorContext {

    private static final LogAdapter LOG = new LogAdapter(CljEditorContextImpl.class.getName());

    private static String fronting =
            System.getProperty("netbeans.debugger.fronting");
    private PropertyChangeSupport pcs;
    private Map annotationToURL = new HashMap();
    private PropertyChangeListener editorObservableListener;
    private PropertyChangeListener tcListener;
    private RequestProcessor refreshProcessor;
    private Lookup.Result resDataObject;
    private Lookup.Result resEditorCookie;
    private Lookup.Result resNode;
    private Object currentLock = new Object();
    private String currentURL = null;

    private EditorCookie currentEditorCookie = null;
    public EditorContext _proxy = null;

    public CljEditorContextImpl() {
    }

    public EditorContext proxy()  {
        if (_proxy == null) {
            List l = DebuggerManager.getDebuggerManager().lookup(null, EditorContext.class);
            List notme = new ArrayList();
            for (Object o : l) {
                if (!o.equals(this)) {
                    notme.add(o);
                }
            }
            
            if (notme.size() == 0) {
                return null;
                //throw new Exception("Unable to find EditoContext for proxy!");
            }
            
            if (notme.size() == 1) {
                _proxy = (EditorContext) notme.get(0);
            }
            else
            {
            int i, k = notme.size();
            for (i = 1; i < k; i++) {
                _proxy = new CompoundContextProvider(
                        (EditorContext) notme.get(i),
                        _proxy);
                }
            }
        }
        return _proxy;
    }
    

    {
//        pcs = new PropertyChangeSupport (this);
//        
//        refreshProcessor = new RequestProcessor("Refresh Editor Context", 1);
//
//        resDataObject = Utilities.actionsGlobalContext().lookup(new Lookup.Template(DataObject.class));
//        resDataObject.addLookupListener(new EditorLookupListener(DataObject.class));
//
//        resEditorCookie = Utilities.actionsGlobalContext().lookup(new Lookup.Template(EditorCookie.class));
//        resEditorCookie.addLookupListener(new EditorLookupListener(EditorCookie.class));
//
//        resNode = Utilities.actionsGlobalContext().lookup(new Lookup.Template(Node.class));
//        resNode.addLookupListener(new EditorLookupListener(Node.class));
//
//        tcListener = new EditorLookupListener(TopComponent.class);
//        TopComponent.getRegistry ().addPropertyChangeListener (WeakListeners.propertyChange(
//                tcListener, TopComponent.getRegistry()));
    }

    /**
     * Shows source with given url on given line number.
     *
     * @param url a url of source to be shown
     * @param lineNumber a number of line to be shown
     * @param timeStamp a time stamp to be used
     */
    public boolean showSource(String url, int lineNumber, Object timeStamp) {
        if(url != null && url.endsWith(".clj"))
            LOG.log(Level.INFO,"Trying to show source for :"+url);
        return proxy().showSource(url, lineNumber, timeStamp);
    }

//    static Line showSourceLine (String url, int lineNumber, Object timeStamp) {
//        proxy().
//        Line l = LineTranslations.getTranslations().getLine (url, lineNumber, timeStamp); // false = use original ln
//        if (l == null) {
//            ErrorManager.getDefault().log(ErrorManager.WARNING,
//                    "Show Source: Have no line for URL = "+url+", line number = "+lineNumber);
//            return null;
//        }
//        if ("true".equalsIgnoreCase(fronting) || Utilities.isWindows()) {
//            l.show (Line.SHOW_REUSE);
//            l.show (Line.SHOW_TOFRONT); //FIX 47825
//        } else {
//            l.show (Line.SHOW_REUSE);
//        }
//        return l;
//    }
//    /**
//     * Shows source with given url on given line number.
//     *
//     * @param url a url of source to be shown
//     * @param lineNumber a number of line to be shown
//     * @param timeStamp a time stamp to be used
//     */
//    public boolean showSource (String url, int lineNumber, int column, int length, Object timeStamp) {
//        proxy().showSource(url, length, timeStamp);
//        Line l = LineTranslations.getTranslations().getLine (url, lineNumber, timeStamp); // false = use original ln
//        if (l == null) {
//            ErrorManager.getDefault().log(ErrorManager.WARNING,
//                    "Show Source: Have no line for URL = "+url+", line number = "+lineNumber);
//            return false;
//        }
//        if ("true".equalsIgnoreCase(fronting) || Utilities.isWindows()) {
//            l.show (Line.SHOW_TOFRONT, column); //FIX 47825
//        } else {
//            l.show (Line.SHOW_GOTO, column);
//        }
//        addPositionToJumpList(url, l, column);
//        return true;
//    }
//    
//    /** Add the line offset into the jump history */
//    private void addPositionToJumpList(String url, Line l, int column) {
//        DataObject dataObject = getDataObject (url);
//        if (dataObject != null) {
//            EditorCookie ec = dataObject.getLookup().lookup(EditorCookie.class);
//            if (ec != null) {
//                try {
//                    StyledDocument doc = ec.openDocument();
//                    JEditorPane[] eps = ec.getOpenedPanes();
//                    if (eps != null && eps.length > 0) {
//                        JumpList.addEntry(eps[0], NbDocument.findLineOffset(doc, l.getLineNumber()) + column);
//                    }
//                } catch (java.io.IOException ioex) {
//                    ErrorManager.getDefault().notify(ioex);
//                }
//            }
//        }
//    }
    /**
     * Creates a new time stamp.
     *
     * @param timeStamp a new time stamp
     */
    public void createTimeStamp(Object timeStamp) {
        proxy().createTimeStamp(timeStamp);
    //LineTranslations.getTranslations().createTimeStamp(timeStamp);
    }

    /**
     * Disposes given time stamp.
     *
     * @param timeStamp a time stamp to be disposed
     */
    public void disposeTimeStamp(Object timeStamp) {
        proxy().disposeTimeStamp(timeStamp);
    //LineTranslations.getTranslations().disposeTimeStamp(timeStamp);
    }

    public Object annotate(
            String url,
            int lineNumber,
            String annotationType,
            Object timeStamp) {
        return null;
        //ET return proxy().annotate(url, lineNumber, url, timeStamp);
    }

    public Object annotate(
            String url,
            int startPosition,
            int endPosition,
            String annotationType,
            Object timeStamp) {
        return null;//proxy().annotate(url, startPosition, endPosition, annotationType, timeStamp);
    }

//    private static Color getColor(String annotationType) {
//        if (annotationType.endsWith("_broken")) {
//            annotationType = annotationType.substring(0, annotationType.length() - "_broken".length());
//        }
//        if (EditorContext.BREAKPOINT_ANNOTATION_TYPE.equals(annotationType)) {
//            return new Color(0xFC9D9F);
//        } else if (EditorContext.CURRENT_LINE_ANNOTATION_TYPE.equals(annotationType) ||
//                   EditorContext.CURRENT_OUT_OPERATION_ANNOTATION_TYPE.equals(annotationType)) {
//            return new Color(0xBDE6AA);
//        } else if (EditorContext.CURRENT_EXPRESSION_CURRENT_LINE_ANNOTATION_TYPE.equals(annotationType)) {
//            return new Color(0xE9FFE6); // 0xE3FFD2// 0xD1FFBC
//        } else if (EditorContext.CURRENT_LAST_OPERATION_ANNOTATION_TYPE.equals(annotationType)) {
//            return new Color(0x99BB8A);
//        } else {
//            return new Color(0x0000FF);
//        }
//    }
    /**
     * Removes given annotation.
     *
     * @return true if annotation has been successfully removed
     */
    public void removeAnnotation(
            Object a) {
//ET        proxy().removeAnnotation(a);
    }

//    private void removeAnnotation(Annotation annotation) {
//        annotation.detach ();
//        annotationToURL.remove (annotation);
//    }
    /**
     * Returns line number given annotation is associated with.
     *
     * @param annotation an annotation, or an array of "url" and new Integer(line number)
     * @param timeStamp a time stamp to be used
     *
     * @return line number given annotation is associated with
     */
    public int getLineNumber(
            Object annotation,
            Object timeStamp) {
        return proxy().getLineNumber(annotation, timeStamp);
    }

    /**
     * Updates timeStamp for gived url.
     *
     * @param timeStamp time stamp to be updated
     * @param url an url
     */
    public void updateTimeStamp(Object timeStamp, String url) {
        proxy().updateTimeStamp(timeStamp, url);
    }

    /**
     * Returns number of line currently selected in editor or <code>-1</code>.
     *
     * @return number of line currently selected in editor or <code>-1</code>
     */
    public int getCurrentLineNumber() {
        return proxy().getCurrentLineNumber();
    }

//    private int getCurrentLineNumber_() {
//        EditorCookie e = getCurrentEditorCookie ();
//        if (e == null) return -1;
//        JEditorPane ep = getCurrentEditor ();
//        if (ep == null) return -1;
//        StyledDocument d = e.getDocument ();
//        if (d == null) return -1;
//        Caret caret = ep.getCaret ();
//        if (caret == null) return -1;
//        int ln = NbDocument.findLineNumber (
//            d,
//            caret.getDot ()
//        );
//        return ln + 1;
//    }
    /**
     * Returns number of line currently selected in editor or <code>-1</code>.
     *
     * @return number of line currently selected in editor or <code>-1</code>
     */
//    public int getCurrentOffset () {
//        if (SwingUtilities.isEventDispatchThread()) {
//            return getCurrentOffset_();
//        } else {
//            final int[] ln = new int[1];
//            try {
//                SwingUtilities.invokeAndWait(new Runnable() {
//                    public void run() {
//                        ln[0] = getCurrentOffset_();
//                    }
//                });
//            } catch (InvocationTargetException ex) {
//                ErrorManager.getDefault().notify(ex.getTargetException());
//            } catch (InterruptedException ex) {
//                // interrupted, ignored.
//            }
//            return ln[0];
//        }
//    }
//    
//    private int getCurrentOffset_() {
//        EditorCookie e = getCurrentEditorCookie ();
//        if (e == null) return -1;
//        JEditorPane ep = getCurrentEditor ();
//        if (ep == null) return -1;
//        StyledDocument d = e.getDocument ();
//        if (d == null) return -1;
//        Caret caret = ep.getCaret ();
//        if (caret == null) return -1;
//        return caret.getDot();
//    }
    /**
     * Returns name of class currently selected in editor or empty string.
     *
     * @return name of class currently selected in editor or empty string
     */
    public String getCurrentClassName() {
        return proxy().getCurrentClassName();
    }

    /**
     * Returns URL of source currently selected in editor or empty string.
     *
     * @return URL of source currently selected in editor or empty string
     */
    public String getCurrentURL() {
        return proxy().getCurrentURL();
    }

    /**
     * Returns name of method currently selected in editor or empty string.
     *
     * @return name of method currently selected in editor or empty string
     */
    public String getCurrentMethodName() {
        return proxy().getCurrentMethodName();
    }

    /**
     * Returns name of field currently selected in editor or <code>null</code>.
     *
     * @return name of field currently selected in editor or <code>null</code>
     */
    public String getCurrentFieldName() {
        return proxy().getCurrentFieldName();
    }

    /**
     * Returns identifier currently selected in editor or <code>null</code>.
     *
     * @return identifier currently selected in editor or <code>null</code>
     */
    public String getSelectedIdentifier() {
        return proxy().getSelectedIdentifier();
    }

    /**
     * Returns method name currently selected in editor or empty string.
     *
     * @return method name currently selected in editor or empty string
     */
    public String getSelectedMethodName() {
        return proxy().getSelectedMethodName();
    }

    /**
     * Returns line number of given field in given class.
     *
     * @param url the url of file the class is deined in
     * @param className the name of class (or innerclass) the field is 
     *                  defined in
     * @param fieldName the name of field
     *
     * @return line number or -1
     */
    public int getFieldLineNumber(
            String url,
            final String className,
            final String fieldName) {
        return proxy().getFieldLineNumber(url, className, fieldName);
    }

    /**
     * Returns line number of given method in given class.
     *
     * @param url the url of file the class is deined in
     * @param className the name of class (or innerclass) the method is 
     *                  defined in
     * @param methodName the name of method
     * @param methodSignature the JNI-style signature of the method.
     *        If <code>null</code>, then the first method found is returned.
     *
     * @return line number or -1
     */
    public int getMethodLineNumber(
            String url,
            final String className,
            final String methodName,
            final String methodSignature) {
        return proxy().getMethodLineNumber(url, className, methodName, methodSignature);
    }

    /**
     * Returns binary class name for given url and line number or null.
     *
     * @param url a url
     * @param lineNumber a line number
     *
     * @return binary class name for given url and line number or null
     */
    public String getClassName(
            String url,
            int lineNumber) {
        return proxy().getClassName(url, lineNumber);

    }

    @Override
    public Operation[] getOperations(String url, final int lineNumber,
            BytecodeProvider bytecodeProvider) {
        return proxy().getOperations(url, lineNumber, bytecodeProvider);
    }

    @Override
    public MethodArgument[] getArguments(String url, final Operation operation) {
        return proxy().getArguments(url, operation);
    }

    @Override
    public MethodArgument[] getArguments(String url, final int methodLineNumber) {
        return proxy().getArguments(url, methodLineNumber);
    }

    /**
     * Returns list of imports for given source url.
     *
     * @param url the url of source file
     *
     * @return list of imports for given source url
     */
    public String[] getImports(
            String url) {
        return proxy().getImports(url);
    }

    /**
     * Adds a property change listener.
     *
     * @param l the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        proxy().addPropertyChangeListener (l);
    }

    /**
     * Removes a property change listener.
     *
     * @param l the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener l) {
        proxy().removePropertyChangeListener (l);
    }

    /**
     * Adds a property change listener.
     *
     * @param propertyName the name of property
     * @param l the listener to add
     */
    public void addPropertyChangeListener(
            String propertyName,
            PropertyChangeListener l) {
        proxy().addPropertyChangeListener (propertyName, l);
    }

    /**
     * Removes a property change listener.
     *
     * @param propertyName the name of property
     * @param l the listener to remove
     */
    public void removePropertyChangeListener(
            String propertyName,
            PropertyChangeListener l) {
        pcs.removePropertyChangeListener(propertyName, l);
    }

    private static class CompoundContextProvider extends EditorContext {

        private  EditorContext cp1,  cp2 ;

        CompoundContextProvider(
                EditorContext cp1,
                EditorContext cp2) {
            this.cp1 = cp1;

            if(cp2 == null)
                this.cp2 = cp1;
            else
                this.cp2 = cp2;
        }

        public void createTimeStamp(Object timeStamp) {
            cp1.createTimeStamp(timeStamp);
            cp2.createTimeStamp(timeStamp);
        }

        public void disposeTimeStamp(Object timeStamp) {
            cp1.disposeTimeStamp(timeStamp);
            cp2.disposeTimeStamp(timeStamp);
        }

        public void updateTimeStamp(Object timeStamp, String url) {
            cp1.updateTimeStamp(timeStamp, url);
            cp2.updateTimeStamp(timeStamp, url);
        }

        public String getCurrentClassName() {
            String s = cp1.getCurrentClassName();
            if (s.trim().length() < 1) {
                return cp2.getCurrentClassName();
            }
            return s;
        }

        public String getCurrentURL() {
            String s = cp1.getCurrentURL();
            if (s.trim().length() < 1) {
                return cp2.getCurrentURL();
            }
            return s;
        }

        public String getCurrentFieldName() {
            String s = cp1.getCurrentFieldName();
            if ((s == null) || (s.trim().length() < 1)) {
                return cp2.getCurrentFieldName();
            }
            return s;
        }

        public int getCurrentLineNumber() {
            int i = cp1.getCurrentLineNumber();
            if (i < 1) {
                return cp2.getCurrentLineNumber();
            }
            return i;
        }

        public int getCurrentOffset() {
            Integer i = null;
            try {
                i = (Integer) cp1.getClass().getMethod("getCurrentOffset", new Class[]{}).
                        invoke(this, new Object[]{});
            } catch (java.lang.reflect.InvocationTargetException itex) {
                Throwable tex = itex.getTargetException();
                if (tex instanceof RuntimeException) {
                    throw (RuntimeException) tex;
                } else {
                    LOG.log(Level.FINEST, tex.getMessage());
                    return 0;
                }
            } catch (Exception ex) {
                // Ignore, we have another attempt with cp2
                //ErrorManager.getDefault().notify(ex);
            }
            if (i == null || i.intValue() < 1) {
                try {
                    i = (Integer) cp2.getClass().getMethod("getCurrentOffset", new Class[]{}).
                            invoke(this, new Object[]{});
                } catch (java.lang.reflect.InvocationTargetException itex) {
                    Throwable tex = itex.getTargetException();
                    if (tex instanceof RuntimeException) {
                        throw (RuntimeException) tex;
                    } else {
                        LOG.log(Level.FINEST, tex.getMessage());
                        return 0;
                    }
                } catch (Exception ex) {
                    LOG.log(Level.FINEST, ex.getMessage());
                    return 0;
                }
            }
            return i.intValue();
        }

        public String getCurrentMethodName() {
            String s = cp1.getCurrentMethodName();
            if ((s == null) || (s.trim().length() < 1)) {
                return cp2.getCurrentMethodName();
            }
            return s;
        }

        public String getSelectedIdentifier() {
            String s = cp1.getSelectedIdentifier();
            if ((s == null) || (s.trim().length() < 1)) {
                return cp2.getSelectedIdentifier();
            }
            return s;
        }

        public String getSelectedMethodName() {
            String s = cp1.getSelectedMethodName();
            if ((s == null) || (s.trim().length() < 1)) {
                return cp2.getSelectedMethodName();
            }
            return s;
        }

        public void removeAnnotation(Object annotation) {
            CompoundAnnotation ca = (CompoundAnnotation) annotation;
            cp1.removeAnnotation(ca.annotation1);
            cp2.removeAnnotation(ca.annotation2);
        }

        public Object annotate(
                String sourceName,
                int lineNumber,
                String annotationType,
                Object timeStamp) {
            CompoundAnnotation ca = new CompoundAnnotation();
            ca.annotation1 = cp1.annotate(sourceName, lineNumber, annotationType, timeStamp);
            ca.annotation2 = cp2.annotate(sourceName, lineNumber, annotationType, timeStamp);
            return ca;
        }

        public int getLineNumber(Object annotation, Object timeStamp) {
            int ln = cp1.getLineNumber(annotation, timeStamp);
            if (ln >= 0) {
                return ln;
            }
            return cp2.getLineNumber(annotation, timeStamp);
        }

        public boolean showSource(String sourceName, int lineNumber, Object timeStamp) {
            return cp1.showSource(sourceName, lineNumber, timeStamp) |
                    cp2.showSource(sourceName, lineNumber, timeStamp);
        }

        public int getFieldLineNumber(
                String url,
                String className,
                String fieldName) {
            int ln = cp1.getFieldLineNumber(url, className, fieldName);
            if (ln != -1) {
                return ln;
            }
            return cp2.getFieldLineNumber(url, className, fieldName);
        }

        public String getClassName(
                String url,
                int lineNumber) {
            String className = cp1.getClassName(url, lineNumber);
            if (className != null && className.length() > 0) {
                return className;
            }
            return cp2.getClassName(url, lineNumber);
        }

        public String[] getImports(String url) {
            String[] r1 = cp1.getImports(url);
            String[] r2 = cp2.getImports(url);
            String[] r = new String[r1.length + r2.length];
            System.arraycopy(r1, 0, r, 0, r1.length);
            System.arraycopy(r2, 0, r, r1.length, r2.length);
            return r;
        }

        public void addPropertyChangeListener(PropertyChangeListener l) {
            // cp1.addPropertyChangeListener (l);
            // cp2.addPropertyChangeListener (l);
        }

        public void removePropertyChangeListener(PropertyChangeListener l) {
            //cp1.removePropertyChangeListener (l);
            //cp2.removePropertyChangeListener (l);
        }

        public void addPropertyChangeListener(
                String propertyName,
                PropertyChangeListener l) {
            //cp1.addPropertyChangeListener (propertyName, l);
            //cp2.addPropertyChangeListener (propertyName, l);
        }

        public void removePropertyChangeListener(
                String propertyName,
                PropertyChangeListener l) {
            //cp1.removePropertyChangeListener (propertyName, l);
            //cp2.removePropertyChangeListener (propertyName, l);
        }

        @Override
        public Operation[] getOperations(String url, int lineNumber, BytecodeProvider bytecodeProvider) {
            Operation[] operations = cp1.getOperations(url, lineNumber, bytecodeProvider);
            if (operations != null) {
                return operations;
            } else {
                return cp2.getOperations(url, lineNumber, bytecodeProvider);
            }
        }
    }

    private static class CompoundAnnotation {

        public CompoundAnnotation() {
        }
        Object annotation1;
        Object annotation2;
    }
}

