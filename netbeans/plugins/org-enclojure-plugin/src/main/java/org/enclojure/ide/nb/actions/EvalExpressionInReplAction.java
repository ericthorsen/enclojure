/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.enclojure.ide.nb.actions;

import clojure.lang.RT;
import clojure.lang.Var;
import org.openide.cookies.EditorCookie;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CookieAction;

public final class EvalExpressionInReplAction extends CookieAction {
   static final Var evalExprFn =
          RT.var("org.enclojure.ide.nb.actions.action-handler", "paste-eval-expr-action");

    protected void performAction(Node[] activatedNodes) {
            try {
            evalExprFn.invoke(activatedNodes);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    public String getName() {
        return NbBundle.getMessage(EvalExpressionInReplAction.class, "CTL_EvalExpressionInReplAction");
    }

    protected Class[] cookieClasses() {
        return new Class[]{EditorCookie.class};
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

