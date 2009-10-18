/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.enclojure.ide.nb.actions;

import org.openide.cookies.EditCookie;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CookieAction;
import clojure.lang.RT;
import clojure.lang.IFn;

public final class LoadSourcesInREPL extends CookieAction {
    final static IFn loadSourcesFn = 
            (IFn)RT.var("org.enclojure.ide.nb.actions.action-handler"
                        , "load-sources-in-repl");
    
    protected void performAction(Node[] activatedNodes) {
        try {
            EditCookie editCookie = activatedNodes[0].getLookup().lookup(EditCookie.class);
            loadSourcesFn.invoke(activatedNodes);
            // TODO use editCookie
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        // TODO use editCookie
    }

    protected int mode() {
        return CookieAction.MODE_ALL;
    }

    public String getName() {
        return NbBundle.getMessage(LoadSourcesInREPL.class, "CTL_LoadSourcesInREPL");
    }

    protected Class[] cookieClasses() {
        return new Class[]{EditCookie.class};
    }

    @Override
    protected void initialize() {
        super.initialize();
        // see org.openide.util.actions.SystemAction.iconResource() Javadoc for more details
        putValue("noIconInMenu", Boolean.TRUE);
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}

