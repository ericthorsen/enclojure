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

package org.enclojure.ide.nb.editor;

import org.netbeans.lib.editor.hyperlink.spi.HyperlinkProvider;
import clojure.lang.RT;
import clojure.lang.IFn;
import org.openide.util.Exceptions;

/**
 *
 * @author ericthorsen
 */
public class HyperProviderFactory {
    static final IFn createFn = (IFn)RT.var("org.enclojure.ide.nb.editor.hyperlinks"
                                            ,"get-hyperlink-provider-func");
    static public HyperlinkProvider create ()
    {
        try {
            return (HyperlinkProvider) createFn.invoke();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

}
