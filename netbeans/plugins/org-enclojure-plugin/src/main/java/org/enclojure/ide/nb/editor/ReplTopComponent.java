/*
(comment
*******************************************************************************
*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
*    The use and distribution terms for this software are covered by the
*    GNU General Public License, version 2
*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
*    exception (http://www.gnu.org/software/classpath/license.html)
*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
*    of this distribution.
*    By using this software in any fashion, you are agreeing to be bound by
*    the terms of this license.
*    You must not remove this notice, or any other, from this software.
*******************************************************************************
*    Author: Narayan Singhal
*******************************************************************************
)
*/
package org.enclojure.ide.nb.editor;

import java.io.Serializable;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import clojure.lang.RT;
import clojure.lang.Var;

import javax.swing.text.Document;
import org.enclojure.ide.repl.ReplPanel;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.nodes.Node;
import org.openide.windows.Mode;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.openide.util.Lookup;
import java.util.prefs.Preferences;
import org.netbeans.api.editor.settings.SimpleValueNames;

/**
 * Top component which displays ReplPanel.
 */
@SuppressWarnings("unchecked") 
final public class ReplTopComponent extends TopComponent {

    private static final LogAdapter LOG = new LogAdapter(ReplTopComponent.class.getName());

    private static ReplTopComponent instance;
    //??private static ReplTopComponent clojureInstance;
    /** path to the icon used by the component and its open action */
    static public final String ICON_PATH = "org/enclojure/ide/nb/editor/resources/enclojure 16x16.png";

    private static final String PREFERRED_ID_prefix = "ReplTopComponent";
    public static final String IDE_REPL = "Enclojure IDE";
    public static final String CLOJURE_REPL = "Clojure";

    private String _projectName;
    private Boolean _debugging = false;

    static final Var stopProjectReplFn =
      RT.var("org.enclojure.ide.nb.editor.repl-win", "stop-project-repl");

    static final Var setCaretVisibilityFn =
      RT.var("org.enclojure.ide.nb.editor.repl-focus", "set-caret-visibility");

    static final Var replFocusFn =
      RT.var("org.enclojure.ide.nb.editor.repl-win", "repl-focus");


    public ReplPanel _replPanel;

    public ReplTopComponent(String replName, ReplPanel replPanel)
    {
        _projectName = replName;

        initComponents();
        setName(getBundleProperty("CTL_ReplTopComponent", _projectName));
        setToolTipText(getBundleProperty("HINT_ReplTopComponent", _projectName));
        setIcon(ImageUtilities.loadImage(ICON_PATH, true));
        Lookup l =  MimeLookup.getLookup(MimePath.get ("text/x-clojure"));
        Preferences p = l.lookup(Preferences.class);
        p.put(SimpleValueNames.CODE_FOLDING_ENABLE,"true");
        //Create project repl
        this._replPanel = replPanel;//??new ReplPanel(special);
        //??_replPanel.setName("replPanel" + projectName);
        jScrollPane1.setViewportView(_replPanel);
    }

    public String GetReplID()
    {
        return _projectName;
    }

    @Override
    public void open()
    {
        //Open the repl in the output pane by default
        Mode m = WindowManager.getDefault().findMode ("output");
        if (m != null) {
           m.dockInto(this);
        }
        super.open();
    }

//    private void initReplTopComponent(String projectName, IFn replDataFn)
//    {
//        _projectName = projectName;
//
//        initComponents();
//        setName(getBundleProperty("CTL_ReplTopComponent", _projectName));
//        setToolTipText(getBundleProperty("HINT_ReplTopComponent", _projectName));
//        setIcon(Utilities.loadImage(ICON_PATH, true));
//
//        //Create project repl
//        this._replPanel = new ReplPanel(replDataFn);
//        _replPanel.setName("replPanel" + projectName);
//        jScrollPane1.setViewportView(_replPanel);
//    }

    public String ReplName() { return _projectName; }

    public static String getBundleProperty(String propertyName, String projectName)
    {
        String propertyText = NbBundle.getMessage(ReplTopComponent.class, propertyName);
        return propertyText.replace("<<Project Name>>", projectName);
    }


    public void ReconnectRepl ()
    {
        this._replPanel.EvaluateInRepl("(in-ns 'user)", null);
    }

    public void DisconnectRepl ()
    {
        this._replPanel.Disconnect();
    }

    public static String GetProjectName(Project p)
    {

        if(p != null)
        {
            ProjectInformation info = p.getLookup().lookup(ProjectInformation.class);
            return info.getDisplayName();
        }
        return null;

    }

    public static Project GetProjectFromDocument(Document document)
    {
        try {
            FileObject fileObject = (FileObject) NbEditorUtilities.getDataObject(document).files().toArray()[0];
            return FileOwnerQuery.getOwner(fileObject);
        } catch (Exception ex) {

            return null;
        }
    }

    public static Project GetProjectFromActivatedNodes(Node[] activatedNodes)
    {
        EditorCookie ec = activatedNodes[0].getLookup().lookup(EditorCookie.class);
        return GetProjectFromDocument(ec.getDocument());
    }

    public boolean IsReplHasFocus() {
        return this._replPanel.GetEditorPane().isFocusOwner();
    }

    public void RequestReplFocus() {
        this.requestVisible();
        //??this.requestFocusInWindow(false);
        //??this.requestActive();
        this._replPanel.GetEditorPane().requestFocusInWindow();
    }

    public Boolean IsProjectReplRunning(Project p)
    {
//        String projectName = GetProjectName(p);
//
//        try {
//            return (Boolean) replRunningFn.invoke(projectName);
//        } catch (Exception ex) {
//            Exceptions.printStackTrace(ex);
//        }
        return false;
    }

    public Boolean IsProjectReplDebugging(Project p)
    {
//        String projectName = GetProjectName(p);
//        return _replRunningDebuggers.containsKey(projectName);
        return _debugging;
    }


    @Override
    protected void componentActivated()
    {
        try {
            setCaretVisibilityFn.invoke(_projectName, this._replPanel.GetEditorPane(), true);
            this.replFocusFn.invoke(this);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    protected void componentDeactivated()
    {
        try {
            setCaretVisibilityFn.invoke(_projectName, this._replPanel.GetEditorPane(), false);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();

        addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                formFocusGained(evt);
            }
        });

        jScrollPane1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                jScrollPane1FocusGained(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 708, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 211, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void jScrollPane1FocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jScrollPane1FocusGained
 
    }//GEN-LAST:event_jScrollPane1FocusGained

    private void formFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_formFocusGained
    
    }//GEN-LAST:event_formFocusGained


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables
    /**
     * Gets default instance. Do not use directly: reserved for *.settings files only,
     * i.e. deserialization routines; otherwise you could get a non-deserialized instance.
     * To obtain the singleton instance, use {@link #findInstance}.
     */

    public static synchronized ReplTopComponent getDefault() {
        if (instance == null) {
            try {
                Var getIdeReplFn = RT.var("org.enclojure.ide.nb.editor.repl-win", "create-ide-repl");
                return (ReplTopComponent)getIdeReplFn.invoke(IDE_REPL);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        return instance;
    }

    /**
     * Obtain the ReplTopComponent instance. Never call {@link #getDefault} directly!
     */
    public static synchronized ReplTopComponent findInstance() {
        String ideReplID = PREFERRED_ID_prefix + IDE_REPL;
        TopComponent win = WindowManager.getDefault().findTopComponent(ideReplID);
        if (win == null) {
            LOG.log(Level.WARNING, "Cannot find " + ideReplID +
                    " component. It will not be located properly in the window system.");
            return getDefault();
        }

        if (win instanceof ReplTopComponent) {
            return (ReplTopComponent) win;
        }

        LOG.log(Level.WARNING,
                "There seem to be multiple components with the '" + ideReplID +
                "' ID. That is a potential source of errors and unexpected behavior.");
        return getDefault();
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        if(_projectName.compareTo(IDE_REPL) != 0)
        {
            try {
                stopProjectReplFn.invoke(_projectName, true);
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    /** replaces this in object stream */
    @Override
    public Object writeReplace() {
        return new ResolvableHelper();
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID_prefix + _projectName;
    }

    final static class ResolvableHelper implements Serializable {

        private static final long serialVersionUID = 1L;

        public Object readResolve() {
            return ReplTopComponent.getDefault();
        }
    }
}
