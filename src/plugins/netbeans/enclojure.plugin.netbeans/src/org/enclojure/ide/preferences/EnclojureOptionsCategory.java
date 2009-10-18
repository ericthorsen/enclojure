/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.ide.preferences;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.netbeans.spi.options.OptionsCategory;
import org.netbeans.spi.options.OptionsPanelController;
import clojure.lang.*;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
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
import org.openide.util.NbBundle;

/**
 *
 * @author ericthorsen
 */
public class EnclojureOptionsCategory extends OptionsCategory {
 public static final String PATH_IN_LAYER = "org-enclojure-ide-preferences-EnclojureOptionsCategory"; //NOI18N

    Var getOptionsControllerFunc = RT.var("org.enclojure.ide.preferences.enclojure-options-category", "get-options-controller");
    ImageIcon icon = null;

    static public OptionsCategory createCategory()
    {
        return new EnclojureOptionsCategory();
    }
    

    @Override
    public Icon getIcon()
    {
        return (icon != null)?icon:
            (icon=
                new ImageIcon(
                    ImageUtilities.loadImage("org/enclojure/ide/nb/editor/resources/enclojure 32x32.png")));
    }

    @Override
    public String getCategoryName() {
        return NbBundle.getMessage(org.enclojure.ide.nb.editor.ClojureEditorKit.class, "OptionsCategory_Name_Enclojure");
    }
    
    @Override
    public String getTitle() {
        return NbBundle.getMessage(org.enclojure.ide.nb.editor.ClojureEditorKit.class, "OptionsCategory_Title_Enclojure");
    }

    @Override
    public OptionsPanelController create() {        
        try {
            return (OptionsPanelController) this.getOptionsControllerFunc.invoke();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }
}
