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
package org.enclojure.ide.debugger;

import java.util.logging.Logger;
import org.enclojure.ide.core.LogAdapter;
import java.util.logging.Level;
import org.openide.modules.ModuleInstall;

public class Installer extends ModuleInstall {
    
    private static final LogAdapter LOG = new LogAdapter(Installer.class.getName());

    @Override
    public void restored()  {
         try {
            Logger logger = Logger.getLogger("org.netbeans.modules.debugger.jpda.breakpoints");
            logger.setLevel(java.util.logging.Level.ALL );
     } catch(Exception e) {
        LOG.log(Level.SEVERE, "restored", e);
    }
  }
}