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
package org.enclojure.ide.debugger.actions;

import java.beans.*;
import javax.swing.Action;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ui.support.*;


public class MainProjectManager implements ProjectActionPerformer {

    private static MainProjectManager mainProjectManager = new MainProjectManager ();

    public static MainProjectManager getDefault () {
        return mainProjectManager;
    }

    private Action a;
    private Project mainProject;
    private PropertyChangeSupport pcs;


    private MainProjectManager () {
        pcs = new PropertyChangeSupport (this);
        a = MainProjectSensitiveActions.mainProjectSensitiveAction (
            this, null, null
        );
        //??a.isEnabled ();
    }

    public Project getMainProject () {
        return mainProject;
    }

    public void perform (Project p) {
    }

    public boolean enable (Project p) {
        if (mainProject == p) return true;
        Project o = mainProject;
        mainProject = p;
        pcs.firePropertyChange ("mainProject", o, mainProject);
        return true;
    }

    public void addPropertyChangeListener (PropertyChangeListener l) {
        pcs.addPropertyChangeListener (l);
    }

    public void removePropertyChangeListener (PropertyChangeListener l) {
        pcs.removePropertyChangeListener (l);
    }
}
