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

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Var;
import clojure.lang.Symbol;


import java.util.logging.Logger;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import org.enclojure.ide.repl.ReplPanel;
import org.openide.modules.ModuleInstall;

/**
 * Manages a module's lifecycle. Remember that an installer is optional and
 * often not needed at all.
 */
public class Installer extends ModuleInstall {

    private static final LogAdapter LOG = new LogAdapter(Installer.class.getName());

    Logger etlog = Logger.getLogger("enclojure-installer");

    final Var requireFn = RT.var("clojure.core","require");
    final IFn setupTrackingFn = (IFn)RT.var("org.enclojure.ide.nb.classpaths.listeners", "start-service");
    final IFn stopTrackingFn = (IFn)RT.var("org.enclojure.ide.nb.classpaths.listeners", "stop-service");

    @Override
    public void restored() {
        etlog.log(Level.INFO,"Enclojure Starting the installer script stuff");
        try {

            LOG.log(Level.INFO, "Enclojure module restored.");

            requireFn.invoke(Symbol.create("org.enclojure.ide.navigator.CljClassVisitor"));
           LOG.log(Level.INFO, "Enclojure CljClassVisitor.");
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.classpaths.resource-tracking"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.symbol-caching"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.classpaths.listeners"));
            etlog.log(Level.INFO,"Enclojure installer 1");
           // requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.symbol-meta"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.file-mapping"));
            etlog.log(Level.INFO,"Enclojure installer 2");
            // This is needed for dynamically loading repl code.
            requireFn.invoke(Symbol.create("org.enclojure.commons.meta-utils"));
            requireFn.invoke(Symbol.create("org.enclojure.repl.main"));

            LOG.log(Level.INFO, "Enclojure module Calling setup tracking.");
            setupTrackingFn.invoke();
            LOG.log(Level.INFO, "Enclojure module Setup tracking completed.");
            etlog.log(Level.INFO,"Enclojure installer 3");
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.utils"));
            //requireFn.invoke(Symbol.create("org.enclojure.repl.repl-manager-ui"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.actions.token-navigator"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.actions.CljComment"));
          //  requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.clj-language-support"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.common.classpath-utils"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.clojure.project.create"));            
            //requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.repl-tc"));
            // options
            requireFn.invoke(Symbol.create("org.enclojure.ide.preferences.platform-options"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.preferences.enclojure-options-category"));
            etlog.log(Level.INFO,"Enclojure installer 4");
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.repl-focus"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.actions.action-handler"));
            //??RT.var("org.enclojure.ide.nb.editor.repl-tc", "init-repl-tc").invoke();
            requireFn.invoke(Symbol.create("org.enclojure.ide.navigator.analyze"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.folding.manager"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.parser"));

            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.hyperlinks"));

            etlog.log(Level.INFO,"Enclojure installer 5");
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.completion-item"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.completion-task"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.completion-provider"));
            // This is now in the ide project.
            requireFn.invoke(Symbol.create("org.enclojure.ide.navigator.views.navigator-panel"));
            //requireFn.invoke(Symbol.create("org.enclojure.ide.navigator.navigator-panel"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.data-object-listener"));
            etlog.log(Level.INFO,"Enclojure installer 6");
            requireFn.invoke(Symbol.create("org.enclojure.ide.debugger.jdi-eval"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.source.add-file"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.repl-win"));
            LOG.log(Level.INFO, "Enclojure module all calls to required are completed.");
            etlog.log(Level.INFO,"Enclojure installer 7");
        } catch (Exception ex) {
            LOG.log(Level.SEVERE,ex.getMessage(), ex);
            etlog.log(Level.SEVERE,"Enclojure installer Exception"+ex.getMessage());
        }
    }

   @Override
   public boolean closing() {
       try {
           stopTrackingFn.invoke();
           RT.var("org.enclojure.ide.repl.repl-manager", "stop-repl-servers").invoke();
       } catch (Throwable e) {
            LOG.log(Level.SEVERE,e.getMessage(), e);
       }
       return true;
   }
}
