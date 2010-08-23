/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.repl;

import clojure.lang.IFn;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.FileHandler;

/**
 *
 * @author ericthorsen
 */
public class launcher {
    static final Var requireFn = RT.var("clojure.core","require");
    static final IFn setupTrackingFn = (IFn)RT.var("org.enclojure.repl.main", "-main");
    static final IFn applyFn = (IFn)RT.var("clojure.core", "apply");
    static Logger l = Logger.getLogger("org.enclojure.repl");
    static public void main(String[] args)
    {
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<args.length;++i)
        {
            sb.append(args[i]);
            sb.append(" ");
        }
        try {
             FileHandler fh = new FileHandler("%t" + java.io.File.separator + "repl%glog");
             l.addHandler(fh);
            requireFn.invoke(Symbol.create("org.enclojure.repl.main"));
            if (args.length > 1) {
                String[] _args = new String[args.length - 1];
                System.arraycopy(args, 1, _args, 0, _args.length);
                applyFn.invoke(setupTrackingFn ,args);
            }
        } catch (Exception ex) {

            Logger.getLogger(launcher.class.getName()).log(Level.SEVERE, null, ex);
        }
    
    }

}
