/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.enclojure.ide.nb.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class ConnectExternalReplAction implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        ConnectExternalReplDialog dlg = new ConnectExternalReplDialog(null, true);
        dlg.setVisible(true);
    }
}
