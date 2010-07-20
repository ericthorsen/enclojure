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
package org.enclojure.ide.debugger.actions;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.util.ResourceBundle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import org.netbeans.api.debugger.DebuggerManager;
import org.enclojure.ide.debugger.util.Utils;

import org.openide.DialogDisplayer;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;

import org.enclojure.ide.core.LogAdapter;
import java.util.logging.Level;

public class AddClojureWatchAction extends CallableSystemAction {

    private static final LogAdapter LOG = new LogAdapter(AddClojureWatchAction.class.getName());
    
    private static String watchHistory = ""; // NOI18N
   
    protected boolean asynchronous () {
        return false;
    }

    public String getName () {
        return NbBundle.getMessage (
            AddClojureWatchAction.class, "CTL_New_Watch"
        
        );
    }
    
    public HelpCtx getHelpCtx () {
        return new HelpCtx (AddClojureWatchAction.class);

    }

    /** The action's icon location.
    * @return the action's icon location
    */
    protected String iconResource () {
        return "org/enclojure/clojure/debugger/resources/actions/NewWatch.gif"; 
    }

    
    public void performAction () {
        ResourceBundle bundle = NbBundle.getBundle (AddClojureWatchAction.class);

        JPanel panel = new JPanel();
        panel.getAccessibleContext ().setAccessibleDescription (bundle.getString ("ACSD_WatchPanel")); // NOI18N
        JTextField textField;
        JLabel textLabel = new JLabel (bundle.getString ("CTL_Watch_Name")); // NOI18N
        textLabel.setBorder (new EmptyBorder (0, 0, 0, 10));
        panel.setLayout (new BorderLayout ());
        panel.setBorder (new EmptyBorder (11, 12, 1, 11));
        panel.add ("West", textLabel); // NOI18N
        panel.add ("Center", textField = new JTextField (25)); // NOI18N
        textField.getAccessibleContext ().setAccessibleDescription (bundle.getString ("ACSD_CTL_Watch_Name")); // NOI18N
        textField.setBorder (
            new CompoundBorder (textField.getBorder (), 
            new EmptyBorder (2, 0, 2, 0))
        );
        textLabel.setDisplayedMnemonic (
            bundle.getString ("CTL_Watch_Name_Mnemonic").charAt (0) // NOI18N
        );


        String t = null;//Utils.getELIdentifier();
//        Utils.log("Watch: ELIdentifier = " + t);
        
        boolean isScriptlet = Utils.isScriptlet();
        LOG.log(Level.FINEST, "Watch: isScriptlet: " + isScriptlet);
        
        if ((t == null) && (isScriptlet)) {
            t = Utils.getJavaIdentifier();
            LOG.log(Level.FINEST, "Watch: javaIdentifier = " + t);
        }
        
        if (t != null) {
            textField.setText(t);
        } else {
            textField.setText(watchHistory);
        }
        textField.selectAll ();        
        textLabel.setLabelFor (textField);
        textField.requestFocus ();

        org.openide.DialogDescriptor dd = new org.openide.DialogDescriptor (
            panel, 
            bundle.getString ("CTL_Watch_Title") // NOI18N
        );
        dd.setHelpCtx (new HelpCtx ("debug.add.watch"));
        Dialog dialog = DialogDisplayer.getDefault ().createDialog (dd);
        dialog.setVisible(true);
        dialog.dispose ();

        if (dd.getValue() != org.openide.DialogDescriptor.OK_OPTION) return;
        String watch = textField.getText();
        if ((watch == null) || (watch.trim ().length () == 0)) {
            return;
        }
        
        String s = watch;
        int i = s.indexOf (';');
        while (i > 0) {
            String ss = s.substring (0, i).trim ();
            if (ss.length () > 0)
                DebuggerManager.getDebuggerManager ().createWatch (ss);
            s = s.substring (i + 1);
            i = s.indexOf (';');
        }
        s = s.trim ();
        if (s.length () > 0)
            DebuggerManager.getDebuggerManager ().createWatch (s);
        
        watchHistory = watch;
        
        // open watches view
//        new WatchesAction ().actionPerformed (null); TODO
    }
}

//        // if EL expression
//        if ((watch.startsWith("$")) && (watch.endsWith("}"))) {
//            watch = org.openide.util.Utilities.replaceString(watch, "\"", "\\\"");
//            watch = "pageContext.getExpressionEvaluator().evaluate(\"" + watch +
//                                "\", java.lang.String.class, (javax.servlet.jsp.PageContext)pageContext, null)";
//            Utils.log("Watch: watch = " + watch);
//        }
//
//        w.setVariableName(watch);
//        if (w instanceof JPDAWatch) {
//            Utils.log("it is jpda watch");
//            //((JPDAWatch)w).setDescription(var);
//        }
