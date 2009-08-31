/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Micro//S ystems, Inc. All Rights Reserved.
 *
 * ThorTech L.L.C. elects to include this software in this distribution
 * under the GNU General Public License, version 2
 * (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
 * exception (http://www.gnu.org/software/classpath/license.html).
 *
 *(comment
 *******************************************************************************
 *    Copyright (c) ThorTech, L.L.C. All rights reserved.
 *    The use and distribution terms for this software are covered by the
 *    GNU General Public License, version 2
 *    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
 *    exception (http://www.gnu.org/software/classpath/license.html)
 *    which can be found in the file GPL-2.0+ClasspathException.txt at the root
 *    of this distribution.
 *    By using this software in any fashion, you are agreeing to be bound by
 *    the terms of this license.
 *    You must not remove this notice, or any other, from this software.
 *******************************************************************************)
*/
package org.enclojure.ide.debugger.breakpoints;

import java.awt.Dialog;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;

import org.netbeans.api.debugger.*;
import org.netbeans.spi.debugger.ui.*;
import org.netbeans.spi.viewmodel.*;

import org.enclojure.ide.debugger.*;

import org.openide.*;
import org.openide.util.*;


public class ClojureBreakpointActionsProvider implements NodeActionsProviderFilter {
    
    private static final Action GO_TO_SOURCE_ACTION = Models.createAction (
        NbBundle.getMessage(ClojureBreakpointActionsProvider.class, "LBL_Action_Go_To_Source"),
        new Models.ActionPerformer () {
            public boolean isEnabled (Object node) {
                return true;
            }
            public void perform (Object[] nodes) {
                goToSource ((ClojureLineBreakpoint) nodes [0]);
            }
        },
        Models.MULTISELECTION_TYPE_EXACTLY_ONE
    
    );
    private static final Action CUSTOMIZE_ACTION = Models.createAction (
        NbBundle.getMessage(ClojureBreakpointActionsProvider.class, "LBL_Action_Customize"),
        new Models.ActionPerformer () {
            public boolean isEnabled (Object node) {
                return true;
            }
            public void perform (Object[] nodes) {
                customize ((Breakpoint) nodes [0]);
            }
        },
        Models.MULTISELECTION_TYPE_EXACTLY_ONE
    
    );
    
    
    public Action[] getActions (NodeActionsProvider original, Object node) throws UnknownTypeException {
        if (!(node instanceof ClojureLineBreakpoint)) 
            return original.getActions (node);
        
        Action[] oas = original.getActions (node);
        if (node instanceof ClojureLineBreakpoint) {
            Action[] as = new Action [oas.length + 3];
            as [0] = GO_TO_SOURCE_ACTION;
            as [1] = null;
            System.arraycopy (oas, 0, as, 2, oas.length);
            as [as.length - 1] = CUSTOMIZE_ACTION;
            return as;
        }
        Action[] as = new Action [oas.length + 1];
        System.arraycopy (oas, 0, as, 0, oas.length);
        as [as.length - 1] = CUSTOMIZE_ACTION;
        return as;
    }
    
    public void performDefaultAction (NodeActionsProvider original, Object node) throws UnknownTypeException {
        if (node instanceof ClojureLineBreakpoint) 
            goToSource ((ClojureLineBreakpoint) node);
        else
            original.performDefaultAction (node);
    }

    public void addModelListener (ModelListener l) {
    }

    public void removeModelListener (ModelListener l) {
    }

    private static void customize (Breakpoint b) {
        JComponent c = null;
        if (b instanceof ClojureLineBreakpoint) {
            c = new ClojureBreakpointPanel((ClojureLineBreakpoint) b);
        }

        DialogDescriptor descriptor = new DialogDescriptor (
            c,
            NbBundle.getMessage (
                ClojureBreakpointActionsProvider.class,
                "CTL_Breakpoint_Customizer_Title"
            )
        
        );

        JButton bOk = null;
        JButton bClose = null;
        descriptor.setOptions (new JButton[] {
            bOk = new JButton (NbBundle.getMessage (
                ClojureBreakpointActionsProvider.class,
                "CTL_Ok" 
            )),
            bClose = new JButton (NbBundle.getMessage (
                ClojureBreakpointActionsProvider.class,
                "CTL_Close"              
            ))
        
        });
        HelpCtx helpCtx = HelpCtx.findHelp (c);
        if (helpCtx == null)
            helpCtx = new HelpCtx ("debug.add.breakpoint");;
        descriptor.setHelpCtx (helpCtx);
        bOk.getAccessibleContext ().setAccessibleDescription (
            NbBundle.getMessage (
                ClojureBreakpointActionsProvider.class,
                "ACSD_CTL_Ok" 
            )
        
        );
        bOk.setMnemonic(NbBundle.getMessage(ClojureBreakpointActionsProvider.class, "CTL_Ok_MNEM").charAt(0)); 
        bClose.getAccessibleContext ().setAccessibleDescription (
            NbBundle.getMessage (
                ClojureBreakpointActionsProvider.class,
                "ACSD_CTL_Close" 
             
            )
        
        );
        bClose.setMnemonic(NbBundle.getMessage(ClojureBreakpointActionsProvider.class, "CTL_Close_MNEM").charAt(0)); 
        descriptor.setClosingOptions (null);
        Dialog d = DialogDisplayer.getDefault ().createDialog (descriptor);
        d.pack ();
        d.setVisible (true);
        if (descriptor.getValue () == bOk) {
            ((Controller) c).ok ();
        }
    }
    
    private static void goToSource (ClojureLineBreakpoint b) {
        Context.showSource (b);
    }
}
