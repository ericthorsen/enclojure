/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.enclojure.ide.preferences;

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.windows.TopComponent;

/**
 * Action which shows ReplProperties component.
 */
public class ReplPropertiesAction extends AbstractAction {

    public ReplPropertiesAction() {
        super(NbBundle.getMessage(ReplPropertiesAction.class, "CTL_ReplPropertiesAction"));
//        putValue(SMALL_ICON, new ImageIcon(Utilities.loadImage(ReplPropertiesTopComponent.ICON_PATH, true)));
    }

    public void actionPerformed(ActionEvent evt) {
        TopComponent win = ReplPropertiesTopComponent.findInstance();
        win.open();
        win.requestActive();
    }
}
