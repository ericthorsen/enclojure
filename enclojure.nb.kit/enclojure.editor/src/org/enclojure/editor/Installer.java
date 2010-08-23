/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.enclojure.editor;

import org.openide.modules.ModuleInstall;
import clojure.lang.RT;
import clojure.lang.Var;
import clojure.lang.Symbol;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages a module's lifecycle. Remember that an installer is optional and
 * often not needed at all.
 */
public class Installer extends ModuleInstall {
    final Var requireFn = RT.var("clojure.core","require");
     static final  Logger logger = Logger.getLogger("org.netbeans.modules");

    @Override
    public void restored() {
             logger.setLevel(java.util.logging.Level.INFO );
                try {
    requireFn.invoke(Symbol.create("clojure.zip"));
        logger.log(Level.INFO,"In core and loaded the clojure.zip");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception in enclojure.editor module load: {0}", ex.getMessage());
        }
    }
}
