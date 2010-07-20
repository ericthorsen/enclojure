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
*    Author: Frank Failla
*******************************************************************************
)
*/
package org.enclojure.ide.nb.clojure.project;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.tools.ant.module.api.support.ActionUtils;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.support.ant.PropertyUtils;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;

/**
 *
 * @author Frank Failla
 */
public class ClojureActionProvider implements ActionProvider {

    private static final LogAdapter LOG = new LogAdapter(ClojureActionProvider.class.getName());

    private static final String[] supportedActions = {
        ActionProvider.COMMAND_RUN,
        ActionProvider.COMMAND_DEBUG,
        ActionProvider.COMMAND_BUILD,
        ActionProvider.COMMAND_CLEAN,
        ActionProvider.COMMAND_REBUILD
    };

    private Map<String, String[]> commands;
    private final Project project;

    public ClojureActionProvider(Project project) {
        this.project = project;
        commands = new HashMap<String, String[]>();

        commands.put(ActionProvider.COMMAND_RUN, new String[] {"run"}); // NOI18N
        commands.put(ActionProvider.COMMAND_DEBUG, new String[] {"debug"}); // NOI18N
        commands.put(ActionProvider.COMMAND_BUILD, new String[] {"jar"}); // NOI18N
        commands.put(ActionProvider.COMMAND_CLEAN, new String[] {"clean"}); // NOI18N
        commands.put(ActionProvider.COMMAND_REBUILD, new String[] {"clean", "jar"}); // NOI18N
    }

    public String[] getSupportedActions() {
        return supportedActions;
    }

    public static String getBuildXmlFileName (final Project project) {
        File propFile = FileUtil.toFile(project.getProjectDirectory().getFileObject("nbproject/project.properties")); // NOI18N
        Map<String, String> map = PropertyUtils.propertiesFilePropertyProvider(propFile).getProperties();
        String buildScriptPath = map.get("buildfile");
        if (buildScriptPath == null) {
            buildScriptPath = "build.xml";
        }
        return buildScriptPath;
    }

    public void invokeAction(final String command, final Lookup context) throws IllegalArgumentException {
        //Runnable action = new Runnable() { public void run () {} };
        //*
         final Runnable action = new Runnable () {
            public void run () {
                try {
                    FileObject fo = project.getProjectDirectory().getFileObject(getBuildXmlFileName(project));
                    ActionUtils.runTarget(fo, commands.get(command), null);
                }
                catch (IOException e) {
                    LOG.log(Level.FINEST, e.getMessage());
                }
            }
        };
        //*/
        action.run();

    }

    public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
        for(String action : getSupportedActions()) {
            if(command.equals(action))
                return true;
        }
        
        return false;
    }

}
