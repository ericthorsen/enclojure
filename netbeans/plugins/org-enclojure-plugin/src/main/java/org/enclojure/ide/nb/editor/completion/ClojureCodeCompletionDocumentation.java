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
*    Author: Paul Wade
*******************************************************************************
)
*/
package org.enclojure.ide.nb.editor.completion;
/**
 *
 * @author pwade
 */
import javax.swing.Action;
import java.net.URL;
import org.netbeans.spi.editor.completion.CompletionDocumentation;
import clojure.lang.RT;
import clojure.lang.Var;
import clojure.lang.Symbol;
import org.openide.util.Exceptions;

@SuppressWarnings("unchecked") 
public class ClojureCodeCompletionDocumentation implements CompletionDocumentation {


    static {try {
            RT.var("clojure.core","require").invoke(Symbol.create("org.enclojure.ide.nb.editor.completion.cljcodecompletion"));
        } catch (Throwable ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    final static Var getdocs = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-clojure-docs");
    final static Var getdoc = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-clojure-doc");
    final static Var getargs = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-clojure-args");

   
    private ClojureCodeCompletionItem item1;

    public ClojureCodeCompletionDocumentation (ClojureCodeCompletionItem item)
    {
        item1=item;

    }


    public String getText() {

        String source="<h2>Source: "  + item1._source + "</h2>";
        String namespace="";
        String method="";
        String constructor="";


        if (item1._isConstructor)
            constructor=method="<h3><font color='blue'>This is a constructor</font></h3>";


        if (item1._isNamespaceOrPkg)
        {
            if (item1._isClojure)
                namespace="<h3>Clojure Namespace: " + item1._fullclassname + "</h3>";
            else
                namespace="<h3>Java Package: " + item1._fullclassname + "</h3>";
        }

        if (! item1._source.startsWith("Hippie"))
        {
            if (item1._isMethodOrFunction)
            {
                if (item1._isClojure)
                    method="<h3>Clojure symbol: " + item1._fullclassname + "</h3>";
                else
                    method="<h3>Java method: " + item1._fullclassname + "</h3>";
            }
        }

        
        String retText="Documentation for <h3><font color='blue'>" + item1.getText() + "</font></h3><h3>arguments<font color='green'><br>" + getArgs() + "</font></h3><p>" + getDoc() + "</p>";
        
        retText= source + retText + constructor + namespace + method;
        
        return retText;
    }

    private String getDocs()
    {
        try {
                return (getdocs.invoke(item1.getText()).toString());
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            return null;
    }

     private String getDoc()
    {
        try {
                return (getdoc.invoke(item1.getText()).toString());
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            return null;
    }

      private String getArgs()
    {
        try {
                return (getargs.invoke(item1.getText()).toString());
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
            return null;
    }

    public CompletionDocumentation resolveLink(String string)
    {
        return null;
    }


      public  Action getGotoSourceAction()
    {
       return null;
    }



    public URL getURL()
    {
        return null;


    }
}


