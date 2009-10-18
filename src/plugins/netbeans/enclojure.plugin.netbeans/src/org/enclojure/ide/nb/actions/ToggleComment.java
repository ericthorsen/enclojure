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
*    Author: Eric Thorsen
*******************************************************************************
)
*/
package org.enclojure.ide.nb.actions;

import org.openide.cookies.EditCookie;
import org.openide.cookies.EditorCookie;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CookieAction;
import clojure.lang.RT;
import clojure.lang.Var;

public final class ToggleComment extends CookieAction {

    final Var toggleCommentFn = RT.var("org.enclojure.ide.nb.actions.CljComment", "perform-action");
    protected void performAction(Node[] activatedNodes) {
        try {
            EditorCookie editCookie = activatedNodes[0].getLookup().lookup(EditorCookie.class);
            toggleCommentFn.invoke(editCookie);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    public String getName() {
        return NbBundle.getMessage(ToggleComment.class, "CTL_ToggleComment");
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

