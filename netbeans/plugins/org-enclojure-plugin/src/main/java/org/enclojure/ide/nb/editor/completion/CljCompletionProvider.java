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
*    Author:Paul Wade
*******************************************************************************
)
*/
package org.enclojure.ide.nb.editor.completion;

/**
 *
 * @author pwade
 */
import clojure.lang.IFn;
import clojure.lang.RT;
import org.enclojure.ide.core.LogAdapter;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.openide.util.Exceptions;

@SuppressWarnings("unchecked")
public class CljCompletionProvider implements CompletionProvider {
    private static final LogAdapter LOG = new LogAdapter(CljCompletionProvider.class.getName());
    
    final IFn createTaskFn = (IFn)RT.var("org.enclojure.ide.nb.editor.completion.completion-task"
                                    ,"get-completion-task");
    final IFn prepareQueryFn = (IFn)RT.var("org.enclojure.ide.nb.editor.completion.completion-task"
                                    ,"prepare-query");
    final IFn getAutoQueryTypesFn = (IFn)RT.var("org.enclojure.ide.nb.editor.completion.completion-provider"
                                    ,"get-auto-query-types");

    Object prepdata; // yuck
    
    public int getAutoQueryTypes(JTextComponent arg0, String arg1) {
        try {
            return ((Integer) getAutoQueryTypesFn.invoke(arg0,arg1)).intValue();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return 0;
    }

    public CompletionTask createTask(int i, final JTextComponent jTextComponent) {
        try {
            return (CompletionTask) createTaskFn.invoke(i,jTextComponent);
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }

//    void prepareQuery(JTextComponent component)
//    {
//        try {
//            prepdata = prepareQueryFn.invoke(component);
//        } catch (Exception ex) {
//            Exceptions.printStackTrace(ex);
//        }
//    }
//    boolean isTaskCancelled()
//    {
//
//    }
//    }



}
