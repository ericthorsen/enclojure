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

import java.util.logging.Level;
import java.util.logging.Logger;
import org.enclojure.ide.repl.ReplPanel;
import org.openide.modules.ModuleInstall;

/**
 * Manages a module's lifecycle. Remember that an installer is optional and
 * often not needed at all.
 */
public class Installer extends ModuleInstall {

    final Var requireFn = RT.var("clojure.core","require");
    final IFn setupTrackingFn = (IFn)RT.var("org.enclojure.ide.nb.classpaths.listeners", "start-service");

    @Override
    public void restored() {
//        final  Logger logger = Logger.getLogger("org.netbeans.modules");
//       logger.setLevel(java.util.logging.Level.INFO );
        try {
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.classpaths.resource-tracking"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.symbol-caching"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.classpaths.listeners"));

           // requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.symbol-meta"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.file-mapping"));
            
            // This is needed for dynamically loading repl code.
            requireFn.invoke(Symbol.create("org.enclojure.commons.meta-utils"));
            requireFn.invoke(Symbol.create("org.enclojure.repl.main"));

            setupTrackingFn.invoke();

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

            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.repl-focus"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.actions.action-handler"));
            //??RT.var("org.enclojure.ide.nb.editor.repl-tc", "init-repl-tc").invoke();
            requireFn.invoke(Symbol.create("org.enclojure.ide.navigator.analyze"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.folding.manager"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.parser"));

            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.hyperlinks"));


            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.completion-item"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.completion-task"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.completion-provider"));
            // This is now in the ide project.
            requireFn.invoke(Symbol.create("org.enclojure.ide.navigator.views.navigator-panel"));
            //requireFn.invoke(Symbol.create("org.enclojure.ide.navigator.navigator-panel"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.data-object-listener"));

            requireFn.invoke(Symbol.create("org.enclojure.ide.debugger.jdi-eval"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.source.add-file"));
            requireFn.invoke(Symbol.create("org.enclojure.ide.nb.editor.repl-win"));

//            Logger logger = Logger.getLogger("org.netbeans.modules.debugger.jpda.breakpoints");

        } catch (Exception ex) {
            Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
  
    }

   @Override
   public boolean closing() {
       try {
           RT.var("org.enclojure.ide.repl.repl-manager", "stop-repl-servers").invoke();
       } catch (Throwable e) {
           Logger.getLogger(ReplPanel.class.getName()).log(Level.SEVERE, null, e);
       }
       return true;
   }
}
