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
*    Author: Frank Failla
*******************************************************************************
)
*/
package org.enclojure.ide.nb.editor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.netbeans.editor.ActionFactory.ReindentLineAction;

public final class ClojureReindentAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        org.netbeans.editor.ActionFactory.ReindentLineAction a = new ReindentLineAction();
        a.actionPerformed(e);
    }
}
