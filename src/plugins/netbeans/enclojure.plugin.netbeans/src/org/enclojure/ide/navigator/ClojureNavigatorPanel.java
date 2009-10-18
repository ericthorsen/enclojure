/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.ide.navigator;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Var;
import java.awt.BorderLayout;
import java.io.File;
import java.util.Collection;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import javax.swing.tree.DefaultTreeCellRenderer;

@SuppressWarnings("unchecked") 
public class ClojureNavigatorPanel implements NavigatorPanel {

    private IFn _contextChanged = null;
    private IFn _openFileFn = (IFn)RT.var("org.enclojure.ide.nb.editor.utils"
                                            ,"open-editor-file");
    private IFn _getDataForFileFn = 
            (IFn)RT.var("org.enclojure.ide.nb.editor.completion.symbol-caching"
                            , "get-nav-data-for");


    /** holds UI of this panel */
    private JComponent panelUI;
    /** template for finding data in given context.
     * Object used as example, replace with your own data source, for example JavaDataObject etc */
    private static final Lookup.Template MY_DATA = new Lookup.Template(Object.class);
    /** current context to work on */
    private Lookup.Result curContext;
    /** listener to context changes */
    private LookupListener contextL;

    /** public no arg constructor needed for system to instantiate provider well */
    public ClojureNavigatorPanel() {
    }

    public String getDisplayHint() {
        return "Basic dummy implementation of NavigatorPanel interface";
    }

    public String getDisplayName() {
        return "Dummy View";
    }

    public JComponent getComponent() {
        if (panelUI == null) {
            Var createNavigationTree =
                    RT.var("org.enclojure.ide.navigator.views.navigator-panel",
                            "create-navigator-tree");
            panelUI = new JPanel(new BorderLayout());
            try {
                _contextChanged = (IFn) createNavigationTree.invoke(panelUI,_openFileFn);
                // You can override requestFocusInWindow() on the component if desired.
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            // You can override requestFocusInWindow() on the component if desired.
        }
        return panelUI;
    }

    @SuppressWarnings("unchecked") 
    public void panelActivated(Lookup context) {

        //This is getting called without the panel created for the very first time
        //So calling getComponent to force creation;
        getComponent();

        // lookup context and listen to result to get notified about context changes
        curContext = context.lookup(MY_DATA);
        curContext.addLookupListener(getContextListener());
        // get actual data and recompute content
        Collection data = curContext.allInstances();
        setNewContent(data);
    }

    public void panelDeactivated() {
        curContext.removeLookupListener(getContextListener());
        curContext = null;
    }

    public Lookup getLookup () {
        // go with default activated Node strategy
        return null;
    }

    /************* non - public part ************/

    private void setNewContent (Collection newData) {
//       Node[] activatedNodes = TopComponent.getRegistry().getActivatedNodes();
//       if(activatedNodes != null && activatedNodes.length > 0)
//       {
//           EditorCookie ec = activatedNodes[0].getLookup().lookup(EditorCookie.class);
//           if(ec != null)
//           {
//               Document document = ec.getDocument();
//               DataObject dataObject = NbEditorUtilities.getDataObject(document);
//               if(dataObject != null)
//               {
//                   FileObject fileObject = (FileObject) dataObject.files().toArray()[0];
//                   int i;
//                   i = 100;
//               }
//           }
//       }

        for(Object object : newData.toArray())
        {
            if(object instanceof FileObject)
            {
                FileObject fileObject = (FileObject)object;
                File file = FileUtil.toFile(fileObject);
                try {
                    if (_contextChanged != null)
                    {
                        Object sampleData = _getDataForFileFn.invoke(file);
                        _contextChanged.invoke(sampleData);
                    }
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
    }

    /** Accessor for listener to context */
    private LookupListener getContextListener () {
        if (contextL == null) {
            contextL = new ContextListener();
        }
        return contextL;
    }

    /** Listens to changes of context and triggers proper action */
    private class ContextListener implements LookupListener {

        public void resultChanged(LookupEvent ev) {
            Collection data = ((Lookup.Result)ev.getSource()).allInstances();
            setNewContent(data);
        }

    } // end of ContextListener

}
