///*
//(comment
//*******************************************************************************
//*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
//*    The use and distribution terms for this software are covered by the
//*    GNU General Public License, version 2
//*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
//*    exception (http://www.gnu.org/software/classpath/license.html)
//*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
//*    of this distribution.
//*    By using this software in any fashion, you are agreeing to be bound by
//*    the terms of this license.
//*    You must not remove this notice, or any other, from this software.
//*******************************************************************************
//*    Author: Narayan Singhal
//*******************************************************************************
//)
//*/
//
//package org.enclojure.ide.nb.actions;
//
//import clojure.lang.RT;
//import clojure.lang.Var;
//import org.enclojure.ide.nb.editor.ReplTopComponent;
//import org.netbeans.api.project.Project;
//import org.netbeans.api.project.ProjectInformation;
//import org.openide.nodes.Node;
//import org.openide.util.Exceptions;
//import org.openide.util.HelpCtx;
//import org.openide.util.NbBundle;
//import org.openide.util.actions.CookieAction;
//import org.openide.util.actions.Presenter;
//import org.openide.windows.TopComponent;
//import org.openide.windows.WindowManager;
//
//public final class RunProjectWithReplAction extends CookieAction {
//
//    static final Var startStaopProjectReplFn =
//        RT.var("org.enclojure.ide.nb.editor.repl-tc", "start-stop-project-repl");
//
//    protected void performAction(Node[] activatedNodes) {
//        Project project = activatedNodes[0].getLookup().lookup(Project.class);
//        RunProjectWithRepl(project);
//    }
//
//    public static String GetActionName(Project p)
//    {
//        /*String actionName = ReplTopComponent.getDefault().IsProjectReplRunning(p)
//                ? "CTL_RunProjectWithReplAction_Stop" : "CTL_RunProjectWithReplAction";
//        //??Gets the action name
//        //??Start or Stop
//        return NbBundle.getMessage(RunProjectWithReplAction.class, actionName);*/
//        return ReplTopComponent.findInstance().GetRunContextMenuName(p);
//    }
//
//    public static void RunProjectWithRepl(Project p)
//    {
//        try {
//            startStaopProjectReplFn.invoke(p);
//        } catch (Exception ex) {
//            Exceptions.printStackTrace(ex);
//        }
//    }
//
//    protected int mode() {
//        return CookieAction.MODE_EXACTLY_ONE;
//    }
//
//    public String getName() {
////        Node[] activatedNodes = TopComponent.getRegistry().getActivatedNodes();
////        if(activatedNodes != null)
////        {
////            Project p = activatedNodes[0].getLookup().lookup(Project.class);
////            if(p != null)
////            {
////                return GetActionName(p);
////            }
////        }
//        return NbBundle.getMessage(RunProjectWithReplAction.class, "CTL_RunProjectWithReplAction");
//    }
//
//    protected Class[] cookieClasses() {
//        return new Class[]{Project.class};
//    }
//
//    @Override
//    protected String iconResource() {
//        return "org/enclojure/ide/nb/editor/resources/Clojure 16x16.png";
//    }
//
//    public HelpCtx getHelpCtx() {
//        return HelpCtx.DEFAULT_HELP;
//    }
//
//    @Override
//    protected boolean asynchronous() {
//        return false;
//    }
//}
//
