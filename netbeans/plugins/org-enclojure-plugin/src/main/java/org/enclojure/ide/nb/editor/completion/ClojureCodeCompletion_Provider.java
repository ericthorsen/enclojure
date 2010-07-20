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
import java.awt.event.KeyEvent;
import java.util.Hashtable;
import java.util.Iterator;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import clojure.lang.RT;
import clojure.lang.Var;
import clojure.lang.Symbol;
import clojure.lang.PersistentArrayMap;
import clojure.lang.Keyword;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import org.openide.util.Exceptions;
import org.netbeans.editor.Utilities;
import javax.swing.text.Caret;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.WordMatch;
import org.netbeans.api.project.Project;
import org.enclojure.ide.nb.editor.ReplTopComponent;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;

@SuppressWarnings("unchecked") 
public class ClojureCodeCompletion_Provider implements CompletionProvider {
    
 private static final LogAdapter LOG = new LogAdapter(ClojureCodeCompletion_Provider.class.getName());

 static {try {
            RT.var("clojure.core","require").invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.cljcodecompletion"));
        } catch (Throwable ex) {
            Exceptions.printStackTrace(ex);
        }
}

final static Var getnamesfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-names");
final static Var getjavamethodsfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "getMethods1");
final static Var getalljavamethodsfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "getAllJavaMethods");
final static Var getalljavainstancemethodsfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-methods-no-static-maplist");
final static Var getfulljavaclassesfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-full-names");
final static Var getclojurenamesfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-clojure-names-with-users");
final static Var getclojurefunctionsfornsfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-clojure-functions-for-nspart");
final static Var getstaticjavamethodsfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "getMethodsWithStatic");
final static Var getalljavaclassesfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "getAllJavaClassesWithNS");
final static Var getalljavaclassesbyfilterfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "getAllJavaClassesByFilterWithNS");
final static Var getallclojurenamespacesfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "getAllClojureNamespacesByFilterWithNS");
final static Var selectcurrentformfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-ns-node-require");
final static Var getprojectjarsfn = RT.var("org.enclojure.ide.common.classpath-utils", "get-project-classpath");
final static Var getresultsforscenario1fn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-all-results-for-scenario1");
final static Var getresultsforscenario2fn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-all-results-for-scenario2");
final static Var getresultsforscenario3fn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-all-results-for-scenario3");
final static Var getresultsforscenario4fn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-all-results-for-scenario4");
final static Var getallclojurenamespaces1fn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-all-clojure-namespaces-maplist");


public Var _completionItemFn = RT.var("clojure.core", "ns-imports");


private static final int LowercaseAfterParen = 0;
private static final int UppercaseAfterParen = 1;
private static final int LowercaseAfterParenWithDot = 2;
private static final int UppercaseAfterParenWithDot = 3;
private static final int LowercaseNoParen = 4;
private static final int UppercaseNoParen = 5;
private static final int LowercaseNoParenWithDot = 6;
private static final int UppercaseNoParenWithDot = 7;
private static final int FirstDotAfterParen = 8;
private static final int FirstDotNoParen = 9;
private static final int backSlashAfterParen = 10;
private static final int backSlashNoParen = 11;
private boolean isClojure=false;
private boolean addFilter=true;
private boolean getExtLibs=false;
private boolean autoCompletion=true;
private boolean keyListenerAdded=false;
private boolean gettingMethods=false;
private KeyListener kl =new CompletionKeyListener();
private ArrayList<PersistentArrayMap> Scenario1List=new ArrayList<PersistentArrayMap>();
private ArrayList<PersistentArrayMap> Scenario2List=new ArrayList<PersistentArrayMap>();
private ArrayList<PersistentArrayMap> Scenario3List=new ArrayList<PersistentArrayMap>();
private ArrayList<PersistentArrayMap> Scenario4List=new ArrayList<PersistentArrayMap>();
private ArrayList<PersistentArrayMap> Scenario1ListExtLib=new ArrayList<PersistentArrayMap>();
private ArrayList<PersistentArrayMap> Scenario2ListExtLib=new ArrayList<PersistentArrayMap>();
private ArrayList<PersistentArrayMap> Scenario4ListExtLib=new ArrayList<PersistentArrayMap>();

private ArrayList<PersistentArrayMap> CurrentList;

public class CompletionKeyListener implements KeyListener
{
        

        public void keyTyped(KeyEvent e) {
            //throw new UnsupportedOperationException("Not supported yet.");
        }

        public void keyPressed(KeyEvent e) {
            //throw new UnsupportedOperationException("Not supported yet.");
            int id = e.getID();
            

            if (id==KeyEvent.KEY_PRESSED)
            {
                int keyCode = e.getKeyCode();
               

                 if ((keyCode==44) && e.isControlDown())

                    if (autoCompletion)
                        autoCompletion=false;
                    else
                        autoCompletion=true;

                if ((keyCode==46) && e.isControlDown())
                {
                    Scenario1List.clear();
                    Scenario2List.clear();
                    Scenario3List.clear();
                    Scenario4List.clear();
                    Scenario1ListExtLib.clear();
                    Scenario2ListExtLib.clear();
                    Scenario4ListExtLib.clear();
                }
//                    if (getExtLibs)
//                        getExtLibs=false;
//                    else
//                        getExtLibs=true;

            }
        }

        public void keyReleased(KeyEvent e) {
            //throw new UnsupportedOperationException("Not supported yet.");

           

            
        }
    }

public static ArrayList<String> getAllHippieMatches(JTextComponent target) {
        ArrayList<String> ret = new ArrayList<String>();

        EditorUI editorUI = Utilities.getEditorUI(target);
        Caret caret = target.getCaret();
        int dotPos = caret.getDot();
        
        WordMatch m = editorUI.getWordMatch();


        if(m!=null) {
            String s = null;
            String searchWord="";
            int c=0;
            do {
                c+=1;
                s = m.getMatchWord(dotPos, false);
                if(s!=null && !ret.contains(s))
                {
                    ret.add(s);
                    if (c==1)
                        searchWord=m.getPreviousWord();

                }

            } while(s!=null && m.isFound());

            if (searchWord==null)
                searchWord="";
            do {
                s = m.getMatchWord(dotPos + searchWord.length() + 2, true);
                if(s!=null && !ret.contains(s))
                    if (!s.equalsIgnoreCase(searchWord) && !ret.contains(s))
                       ret.add(s);

            } while(s!=null && m.isFound());
        }
        return ret;
    }


     public int getAutoQueryTypes(JTextComponent arg0, String arg1) {


         arg0.removeKeyListener(kl);

         arg0.addKeyListener(kl);
         

         if (autoCompletion)
         {
             if (arg1.equals(" "))
                 Completion.get().hideAll();

             if (arg1.equals(".") || (arg1.equals("/")))
                 return CompletionProvider.COMPLETION_QUERY_TYPE;
             else
                return 0;
         }
         else
             return 0;
    }

   public CompletionTask createTask(int i, final JTextComponent jTextComponent) {


//       try {
//            RT.var("clojure.core", "require").invoke(Symbol.create("test.CodeCompletion.PW.cljcompletion"));
//        } catch (Throwable ex) {
//            Exceptions.printStackTrace(ex);
//        }
        final int ctype=i;

       if (i != CompletionProvider.COMPLETION_QUERY_TYPE && i != CompletionProvider.COMPLETION_ALL_QUERY_TYPE)
            return null;

        return new AsyncCompletionTask(new AsyncCompletionQuery() {

        protected void query(final CompletionResultSet completionResultSet, final Document document, final int caretOffset) {

            final StyledDocument bDoc = (StyledDocument)document;

            int startOffset = -1;



            class Operation implements Runnable {
                boolean showCompletion = false;
                boolean isDotPressed=false;
                String filter = null;
                int startOffset = caretOffset;

                String nameSpace = null;
                String lastAtom = null;



                public void run()  {
                    
                    nameSpace = null;
                    lastAtom = null;
                    final Iterator it;
                    final String ret;
                    try {
                        final int lineStartOffset = getRowFirstNonWhite(bDoc, caretOffset);

                            String line;

                            if (caretOffset > lineStartOffset)
                                line = bDoc.getText(lineStartOffset, caretOffset-lineStartOffset);
                            else
                                line="";

                            int begLine=indexOfSpace(line);

                            String filter=line.substring(begLine);

                            isDotPressed=dotPressed(filter);

                            int layout=getLayout(filter,isDotPressed);

                            begLine=lineStartOffset + begLine;

                            Hashtable processInfo=new Hashtable();
                            processInfo.put("layout",layout);
                            processInfo.put("filter", filter);
                            processInfo.put("begLine",begLine);
                            processInfo.put("isClojure",isClojure);


                            it=processLayout(processInfo);


                            filter=(String)processInfo.get("filter");
                            begLine=(Integer)processInfo.get("begLine");



                               if (it != null)
                            {

                                handleMaps(it, completionResultSet, processInfo,filter, begLine);
//
                            }
                      if ((layout!=backSlashAfterParen && layout!=backSlashNoParen) && !isDotPressed)
                      {
                         //now add hippie completion results
                         processInfo.put("isClojure",true);
                         processInfo.put("priority", 3);
                         processInfo.put("isMethodOrFunction", true);
                         processInfo.put("source", "Hippie Completion");
                         processInfo.put("isNamespaceOrPackage",false);

                         processInfo.put("isConstructor",false);


                         ArrayList<String> l=getAllHippieMatches(jTextComponent);

                         if (l.size()>0)
                         {
                             Iterator lit;

                             lit=l.listIterator();

                             while (lit.hasNext())
                                        {
                                            final String listentry=(String)lit.next();
                                            completionResultSet.addItem(new ClojureCodeCompletionItem(listentry,begLine,caretOffset,processInfo));
                                        }
                         }


                      }
                    completionResultSet.setAnchorOffset(lineStartOffset);
                    completionResultSet.finish();


                    } catch (BadLocationException ex) {
                        LOG.log(Level.FINEST, ex.getMessage());
                    }
                }



                private String handleNullString(String val)
                {
                    if (val==null)
                        return "";
                    else
                        return val;
                }

                private Integer handleNullInteger(Integer val)
                {
                    if (val==null)
                        return 0;
                    else
                        return val;
                }

                private Boolean handleNullBoolean(Boolean val)
                {
                    if (val==null)
                        return false;
                    else
                        return val;
                }

                private void handleMaps(Iterator iter,CompletionResultSet c,Hashtable pInfo,String filter, int begLine)
                {
                                    Boolean blnCurrentListEmpty=true;

                                    if (CurrentList.size()>0)
                                        blnCurrentListEmpty=false;
                                    
                                    while (iter.hasNext())
                                    {

                                        final PersistentArrayMap entry=(PersistentArrayMap)iter.next();
                                        //final String entry=(String)iter.next();
                                        
                                        if ((blnCurrentListEmpty) && (!gettingMethods))
                                            CurrentList.add(entry);

                                        String displaystr=(String)entry.get(Keyword.intern(Symbol.create("display")));
                                        String fullclassstr=(String)entry.get(Keyword.intern(Symbol.create("fullclassname")));
                                        Boolean isClojure= (Boolean)entry.get(Keyword.intern(Symbol.create("isclojure")));
                                        Boolean isNamespaceOrPackage= (Boolean)entry.get(Keyword.intern(Symbol.create("isnamespaceorpkg")));
                                        Boolean isMethodOrFunction= (Boolean)entry.get(Keyword.intern(Symbol.create("ismethodorfunction")));
                                        Boolean isConstructor= (Boolean)entry.get(Keyword.intern(Symbol.create("isconstructor")));
                                        String namespace=(String)entry.get(Keyword.intern(Symbol.create("namespace")));
                                        String method=(String)entry.get(Keyword.intern(Symbol.create("method")));
                                        Integer priority=(Integer)entry.get(Keyword.intern(Symbol.create("priority")));


                                        pInfo.put("display", handleNullString(displaystr));
                                        pInfo.put("fullclassname", handleNullString(fullclassstr));
                                        pInfo.put("isClojure", isClojure);
                                        pInfo.put("isNamespaceOrPackage",isNamespaceOrPackage);
                                        pInfo.put("isMethodOrFunction", isMethodOrFunction);
                                        pInfo.put("namespace",handleNullString(namespace));
                                        pInfo.put("method",handleNullString(method));
                                        pInfo.put("priority",handleNullInteger(priority));
                                        pInfo.put("isConstructor", handleNullBoolean(isConstructor));


                                        if (isClojure)
                                            pInfo.put("source", "Clojure");
                                        else
                                            pInfo.put("source", "Java");


                                        String origFilter=(String)pInfo.get("origFilter");

                                        int layout=(Integer)pInfo.get("layout");

                                        if (addFilter)
                                        {
                                            if (isMethodOrFunction && (!isClojure))  //if this is a java method
                                            {
                                                if ((filter != null) && (displaystr.startsWith(filter.trim())))
                                                    completionResultSet.addItem(new ClojureCodeCompletionItem(displaystr,begLine,caretOffset,pInfo));
                                            }
                                            else
                                            {
                                                if (layout != backSlashAfterParen && layout != backSlashNoParen)
                                                {
                                                     if (isNamespaceOrPackage)
                                                     {
                                                        if ((filter != null) && (displaystr.contains(origFilter.trim())))
                                                            completionResultSet.addItem(new ClojureCodeCompletionItem(displaystr,begLine,caretOffset,pInfo));
                                                     }
                                                     else
                                                     {
                                                        if ((filter != null) && (displaystr.contains(filter.trim())))
                                                            completionResultSet.addItem(new ClojureCodeCompletionItem(displaystr,begLine,caretOffset,pInfo));
                                                     }
                                                }
                                                else
                                                {
                                                    if ((filter != null) && (displaystr.startsWith(filter.trim())))
                                                        completionResultSet.addItem(new ClojureCodeCompletionItem(displaystr,begLine,caretOffset,pInfo));
                                                }
                                            }
                                        }
                                        else
                                             completionResultSet.addItem(new ClojureCodeCompletionItem(displaystr,begLine,caretOffset,pInfo));

                                    }


                }

                private int getLayout(String filter, boolean isDotPressed)
                {
                //private static final int LowercaseAfterParen = 0;
                //private static final int UppercaseAfterParen = 1;
                //private static final int LowercaseAfterParenWithDot = 2;
                //private static final int UppercaseAfterParenWithDot = 3;
                //private static final int LowercaseNoParen = 4;
                //private static final int UppercaseNoParen = 5;
                //private static final int LowercaseNoParenWithDot = 6;
                //private static final int UppercaseNoParenWithDot = 7;
                //private static final int FirstDotAfterParen = 8;
                //private static final int FirstDotNoParen = 9;

                    try{

                         if (filter.startsWith("("))
                            {
                              filter=filter.substring(1);

                                if (backSlashPressed(filter))
                                {
                                    if ((filter != null) && (filter.trim().length() > 0))
                                            return backSlashAfterParen;
                                    else return -1;

                                }
                                else
                                {
                                    if (isDotPressed && (filter != null) && (filter.trim().length() > 0) && Character.isUpperCase(filter.trim().charAt(0)))
                                    {
                                        return UppercaseAfterParenWithDot;
                                    }
                                    else
                                    {
                                        if (isDotPressed && (filter != null) && (filter.trim().length() > 0) && (filter.trim().charAt(0)=='.'))
                                        {
                                            //a dot was pressed first
                                            //get all java methods available
                                            return FirstDotAfterParen;

                                        }
                                        else
                                        {
                                            if (isDotPressed && (filter != null) && (filter.trim().length() > 0) && Character.isLowerCase(filter.trim().charAt((0))))
                                               return LowercaseAfterParenWithDot;
                                            else
                                            {
                                                if ((filter != null) && (filter.trim().length() > 0) && Character.isUpperCase(filter.trim().charAt((0))))
                                                    return UppercaseAfterParen;
                                                else //lowercase
                                                    return LowercaseAfterParen;
                                            }
                                        }
                                    }
                                }
                            }
                         else  //No Paren
                            {

                             if (backSlashPressed(filter))
                                {
                                    if ((filter != null) && (filter.trim().length() > 0))
                                            return backSlashNoParen;
                                    else return -1;

                                }
                                else
                                {
                                    if (isDotPressed && (filter != null) && (filter.trim().length() > 0) && Character.isUpperCase(filter.trim().charAt(0)))
                                    {
                                        return UppercaseNoParenWithDot;
                                    }
                                    else
                                    {
                                        if (isDotPressed && (filter != null) && (filter.trim().length() > 0) && (filter.trim().charAt(0)=='.'))
                                            {
                                                return FirstDotNoParen;
                                            }
                                        else
                                        {
                                            if (isDotPressed && (filter != null) && (filter.trim().length() > 0) && Character.isLowerCase(filter.trim().charAt((0))))
                                                   return LowercaseNoParenWithDot;
                                                else
                                                {
                                                    if ((filter != null) && (filter.trim().length() > 0) && Character.isUpperCase(filter.trim().charAt((0))))
                                                        return UppercaseNoParen;
                                                    else //lowercase
                                                        return LowercaseNoParen;
                                                }

                                        }

                                    }

                                }
                            }


                    }
                    catch (Exception ex)
                    {Exceptions.printStackTrace(ex);}
                    return -1;
                }

                private Iterator processLayout(Hashtable processInfo)
                {
                    //private static final int LowercaseAfterParen = 0;
                //private static final int UppercaseAfterParen = 1;
                //private static final int LowercaseAfterParenWithDot = 2;
                //private static final int UppercaseAfterParenWithDot = 3;
                //private static final int LowercaseNoParen = 4;
                //private static final int UppercaseNoParen = 5;
                //private static final int LowercaseNoParenWithDot = 6;
                //private static final int UppercaseNoParenWithDot = 7;
                //private static final int FirstDotAfterParen = 8;
                //private static final int FirstDotNoParen = 9;

                    int layout=(Integer)processInfo.get("layout");
                    String filter=(String)processInfo.get("filter");
                    String begFilter;
                    String origFilter="";
                    int begLine=(Integer)processInfo.get("begLine");
                    int dotpos;

                    Iterator iter=null;
                    try
                    {

                        filter=filter.trim();

                        switch(layout) {
                        case LowercaseAfterParen:
                            filter=filter.substring(1); //remove the paranthesis
                            origFilter=filter;


                            addFilter=true;

                            iter=getResultsForScenario2(filter);

                            begLine=begLine +1;

                            break;
                        case UppercaseAfterParen:
                            filter=filter.substring(1); //remove the paranthesis
                            origFilter=filter;


                            addFilter=true;

                            iter=getResultsForScenario2(filter);

                            begLine=begLine +1;

                            break;

                        case LowercaseAfterParenWithDot:
                            filter=filter.substring(1); //remove the paranthesis
                            origFilter=filter;
                            dotpos=filter.lastIndexOf(".");
                            begFilter=filter.substring(0,dotpos);

                            addFilter=true;

                            //iter=getResultsForScenario1(getClsForFilter(begFilter),begFilter + ".");  //check java classes
                            iter=getResultsForScenario1(getClsForFilter(begFilter),origFilter);  //check java classes
                            //iter=getAllClojureNamespaces1(origFilter);

                            filter=filter.substring(dotpos+1);
                            //filter=origFilter;
                            begLine=begLine + 1;


                            break;
                        case UppercaseAfterParenWithDot:
                            filter=filter.substring(1); //remove the paranthesis
                            origFilter=filter;
                            dotpos=filter.lastIndexOf(".");
                            begFilter=filter.substring(0,dotpos);

                            addFilter=true;

                            //iter=getResultsForScenario1(getClsForFilter(begFilter),begFilter + ".");  //check java classes
                            iter=getResultsForScenario1(getClsForFilter(begFilter),origFilter );  //check java classes

                            filter=filter.substring(dotpos+1);
                            //filter=origFilter;
                            begLine=begLine + 1;


                            break;
                        case backSlashAfterParen:
                            filter=filter.substring(1); //remove the paranthesis
                            origFilter=filter;
                            dotpos=filter.lastIndexOf("/");
                            begFilter=filter.substring(0,dotpos);

                            addFilter=true;

                            iter=getResultsForScenario3(getClsForFilter(begFilter),begFilter);

                            filter=filter.substring(dotpos+1);
                            begLine=begLine +1;
                            break;
                        case FirstDotAfterParen:
                            filter=filter.substring(1); //remove the paranthesis
                            origFilter=filter;
                            addFilter=true;

                            if (filter.equalsIgnoreCase("."))
                                filter="";
                             else
                             {
                                dotpos=filter.lastIndexOf(".");
                                filter=filter.substring(dotpos+1);
                             }

                            iter=getResultsForScenario4();

                            begLine=begLine +2;
                            break;
                        case UppercaseNoParen:
                            origFilter=filter;


                            addFilter=true;

                            iter=getResultsForScenario2(filter);


                            break;
                        case UppercaseNoParenWithDot:
                            origFilter=filter;
                            dotpos=filter.lastIndexOf(".");
                            begFilter=filter.substring(0,dotpos);

                            addFilter=true;

                            //iter=getResultsForScenario1(getClsForFilter(begFilter),begFilter + ".");  //check java classes
                            iter=getResultsForScenario1(getClsForFilter(begFilter),origFilter);  //check java classes

                            filter=filter.substring(dotpos+1);
                            //filter=origFilter;
                            break;

                        case FirstDotNoParen:
                            origFilter=filter;
                            addFilter=true;

                            if (filter.equalsIgnoreCase("."))
                                filter="";
                             else
                             {
                                dotpos=filter.lastIndexOf(".");
                                filter=filter.substring(dotpos+1);
                             }

                            iter=getResultsForScenario4();

                            begLine=begLine +1;

                            break;

                         case LowercaseNoParenWithDot:
                            origFilter=filter;
                            dotpos=filter.lastIndexOf(".");
                            begFilter=filter.substring(0,dotpos);

                            addFilter=true;

                            //iter=getResultsForScenario1(getClsForFilter(begFilter),begFilter + ".");  //check java classes
                            iter=getResultsForScenario1(getClsForFilter(begFilter),origFilter);  //check java classes

                            filter=filter.substring(dotpos+1);
                            //filter=origFilter;
                            break;

                          case LowercaseNoParen:
                             origFilter=filter;


                            addFilter=true;

                            iter=getResultsForScenario2(filter);

                            break;
                          case backSlashNoParen:
                            origFilter=filter;
                            dotpos=filter.lastIndexOf("/");
                            begFilter=filter.substring(0,dotpos);

                            addFilter=true;

                            iter=getResultsForScenario3(getClsForFilter(begFilter),begFilter);

                            filter=filter.substring(dotpos+1);
                            break;

                        }

                        processInfo.put("layout",layout);
                        processInfo.put("filter", filter);
                        processInfo.put("begLine", begLine);
                        processInfo.put("isClojure",isClojure);
                        processInfo.put("origFilter", origFilter);

                        return iter;

                    }
                    catch (Exception ex)
                    {
                      Exceptions.printStackTrace(ex);
                    }
                    return iter;
                }


                private Iterator getiterator()
                {
                        try {
                            return ((Iterable)getnamesfn.invoke(Symbol.create("clojure.core"))).iterator();
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        return null;
                }

                 private Iterator getclojureiterator()
                {
                        try {
                            return ((Iterable)getclojurenamesfn.invoke(Symbol.create("clojure.core"))).iterator();
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        return null;
                }

                 private Iterator getclojurefunctionsforns(String nspart)
                {
                        try {
                            java.lang.Object iter=(getclojurefunctionsfornsfn.invoke(nspart));

                            if (iter !=null)
                                return ((Iterable)iter).iterator();
                                //return ((Iterable)getclojurefunctionsfornsfn.invoke(nspart)).iterator();
                            else
                                return null;
                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                            return null;
                        }

                }

                 private Iterator getJavaMethods(String classname)
                {
                        try {
                            return ((Iterable)getjavamethodsfn.invoke(Class.forName(classname))).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }

                  private Iterator getStaticJavaMethods(String classname)
                {
                        try {
                            return ((Iterable)getstaticjavamethodsfn.invoke(Class.forName(classname))).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }




                private Iterator getAllJavaMethods()
                {
                        try {
                            return ((Iterable)getalljavamethodsfn.invoke(Symbol.create("clojure.core"))).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }


                private Iterator getAllJavaInstanceMethods(String classname)
                {
                        try {
                            return ((Iterable)getalljavainstancemethodsfn.invoke(Class.forName(classname))).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }


                 private Iterator getAllClojureNamespaces1(String filter)
                {
                        try {
                            return ((Iterable)getallclojurenamespaces1fn.invoke(1)).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }
                private Iterator getResultsForScenario1(String classname,String filter)
                {
                        try {

                            gettingMethods=false;

                            if (ctype==CompletionProvider.COMPLETION_ALL_QUERY_TYPE)
                            {
                                completionResultSet.setHasAdditionalItems(true);
                                getExtLibs=true;
                            }
                            else
                            {
                                completionResultSet.setHasAdditionalItems(false);
                                getExtLibs=false;
                            }
                            
                            Project proj=ReplTopComponent.GetProjectFromDocument(document);
                            if (!getExtLibs)
                            {
                                proj=null;
                                CurrentList=Scenario1List;
                            }
                            else
                                CurrentList=Scenario1ListExtLib;



                            int listSize = CurrentList.size();
                            



                            if (classname==null)
                            {
                                if (listSize > 0)
                                    return CurrentList.iterator();
                                else
                                    return ((Iterable)getresultsforscenario1fn.invoke(proj,jTextComponent,null,filter)).iterator();
                            }
                            else
                            {
                                gettingMethods=true;
                                return ((Iterable)getresultsforscenario1fn.invoke(proj,jTextComponent,Class.forName(classname),filter)).iterator();
                            }

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                                   return null;
                        }

                }

                private Iterator getResultsForScenario2(String filter)
                {
                        try {
                            gettingMethods=false;

                            if (ctype==CompletionProvider.COMPLETION_ALL_QUERY_TYPE)
                            {
                                completionResultSet.setHasAdditionalItems(true);
                                getExtLibs=true;
                            }
                            else
                            {
                                completionResultSet.setHasAdditionalItems(false);
                                getExtLibs=false;
                            }

                            Project proj=ReplTopComponent.GetProjectFromDocument(document);
                            if (!getExtLibs)
                            {
                                proj=null;
                                CurrentList=Scenario2List;
                            }
                            else
                                CurrentList=Scenario2ListExtLib;

                            int listSize = CurrentList.size();

                            if (listSize > 0)
                                return CurrentList.iterator();
                            else
                                return ((Iterable)getresultsforscenario2fn.invoke(proj,jTextComponent,Symbol.create("clojure.core"),filter)).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                               return null;
                        }

                }

                private Iterator getResultsForScenario3(String classname,String filter)
                {
                        try {
                            gettingMethods=false;
                           
                            CurrentList=Scenario3List;
                            
                            if (classname==null)
                            {
                                gettingMethods=true;
                                return ((Iterable)getresultsforscenario3fn.invoke(null,filter)).iterator();
                            }
                            else
                            {
                                gettingMethods=true;
                                return ((Iterable)getresultsforscenario3fn.invoke(Class.forName(classname),filter)).iterator();
                            }
                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                               return null;
                        }

                }

                 private Iterator getResultsForScenario4()
                {
                        try {
                              
                            gettingMethods=false;

                            if (ctype==CompletionProvider.COMPLETION_ALL_QUERY_TYPE)
                                {
                                    completionResultSet.setHasAdditionalItems(true);
                                    getExtLibs=true;
                                }
                            else
                            {
                                completionResultSet.setHasAdditionalItems(false);
                                getExtLibs=false;
                            }

                              Project proj=ReplTopComponent.GetProjectFromDocument(document);
                              if (!getExtLibs)
                              {
                                  proj=null;
                                  CurrentList=Scenario4List;
                              }
                              else
                                  CurrentList=Scenario4List;

                              int listSize = CurrentList.size();


                              if (listSize > 0)
                                return CurrentList.iterator();
                              else
                                return ((Iterable)getresultsforscenario4fn.invoke(proj)).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                               return null;
                        }

                }

                private Iterator getAllJavaClasses()
                {
                        try {
                            return ((Iterable)getalljavaclassesfn.invoke(jTextComponent)).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }

                private Iterator getAllJavaClasses(String filter)
                {
                        try {
                            return ((Iterable)getalljavaclassesbyfilterfn.invoke(jTextComponent,filter)).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }


                private Iterator getAllClojureNamespaces(String filter)
                {
                        try {
                            return ((Iterable)getallclojurenamespacesfn.invoke(jTextComponent,filter)).iterator();

                        } catch (Exception ex) {
                            //Exceptions.printStackTrace(ex);
                            return null;
                        }

                }

                 private Iterator getFullJavaClasses()
                {
                        try {
                            return ((Iterable)getfulljavaclassesfn.invoke(Symbol.create("clojure.core"))).iterator();

                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        return null;
                }

                
                 private Iterator getEnclosingFormMap(JTextComponent jTextComponent)
                {
                        try {
                            return ((Iterable)selectcurrentformfn.invoke(jTextComponent)).iterator();

                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        return null;
                }

                 private String getClsForFilter(String filterstr)
                {

                     Iterator iter;

                     iter=getAllJavaClasses();

                     if (iter!=null)
                     {
                         while (iter.hasNext())
                        {

                            final String entry=(String)iter.next();

                            if ((entry.endsWith("." + filterstr)) || (entry.equals(filterstr)))
                                return entry;

                        }

                         return null;
                     }
                     else
                         return null;

                     
                }

                  private boolean containsFilter(Iterator iter,String filter)
                {
                        try {

                            if (iter!=null)
                            {
                                while (iter.hasNext())
                                {
                                    final String entry=(String)iter.next();
                                    if ((filter != null) && (entry.startsWith(filter)))
                                        return true;
                                }
                            }

                            return false;

                        } catch (Exception ex) {
                            Exceptions.printStackTrace(ex);
                            return false;
                        }

                }

            }

            Operation oper = new Operation();
            bDoc.render(oper);
        }

    },jTextComponent);

    }

   static int getRowFirstNonWhite(StyledDocument doc, int offset)
            throws BadLocationException {
        Element lineElement = doc.getParagraphElement(offset);
        int start = lineElement.getStartOffset();
        while (start + 1 < lineElement.getEndOffset()) {
            try {
                if (doc.getText(start, 1).charAt(0) != ' ') {
                break;
                }
            } catch (BadLocationException ex) {
                throw (BadLocationException) new BadLocationException(
                        "calling getText(" + start + ", " + (start + 1) +
                        ") on doc of length: " + doc.getLength(), start).initCause(ex);
            }
            start++;
        }
        return start;
    }



    static int indexOfWhite(String line) {
        int i = line.length();
        while (--i > -1) {
            final char c = line.charAt(i);
            if ( Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }

    static int indexOfSpace(String line) {
        int i = line.length();
        int firsti=i-1;

        while (--i > -1) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c) || ((!Character.isLetterOrDigit(c)) && (c!='.') && (c!='/')) ||  (c=='(')) {
                if ((c=='.') && (i==firsti))
                    firsti=i;
                else
                {
                    if (Character.isSpaceChar(c))
                        return i + 1;
                    else
                        return i;
                }
            }
        }
        return 0;
    }

    static boolean dotPressed(String line){

         int i = line.length();

        i =line.indexOf(".");

        if (i>-1)
            return true;
        else
            return false;

    }

 static boolean backSlashPressed(String line){

         int i = line.length();

        i =line.indexOf("/");

        if (i>-1)
            return true;
        else
            return false;

    }




}

