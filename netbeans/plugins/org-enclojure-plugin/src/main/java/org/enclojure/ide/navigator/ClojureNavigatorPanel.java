/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.ide.navigator;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Var;
import java.awt.BorderLayout;
import java.util.Collection;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

@SuppressWarnings("unchecked") 
public class ClojureNavigatorPanel implements NavigatorPanel {


    private Var _checkNewContextFn =
                    RT.var("org.enclojure.ide.navigator.views.navigator-panel",
                            "new-context");

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
            panelUI = new JPanel(new BorderLayout());
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
                    try {
                            _checkNewContextFn.invoke(panelUI,newData);
                        
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
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
