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
import clojure.lang.Keyword;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.text.NbDocument;
import java.util.Hashtable;
import clojure.lang.RT;
import clojure.lang.Var;
import clojure.lang.Symbol;
import clojure.lang.PersistentArrayMap;
import java.awt.Container;
import java.util.Iterator;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import javax.swing.JViewport;
import org.openide.util.Exceptions;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;


@SuppressWarnings("unchecked") 
public class ClojureCodeCompletionItem implements CompletionItem {

    private static final LogAdapter LOG = new LogAdapter(ClojureCodeCompletionItem.class.getName());

    final static Var getalljavaclassesfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-all-java-classes-with-ns");
    final static Var getallclojurenamespacesfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "get-all-clojure-namespaces-within-nsnode");
    final static Var addimportlistfn = RT.var("org.enclojure.ide.nb.editor.completion.cljcodecompletion", "add-import-list-new");

    private static Color fieldColor = Color.decode("0x0000B2");
    private static ImageIcon fieldIcon = null;
    private ImageIcon  _icon;
    private int _type;
    private int _carretOffset;
    private int _dotOffset;
    private int _layout=-1;
    private String _text;
    private String _doc;

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
    private boolean _isStatic;
    public boolean _isClojure;
    private int _priority;
    public boolean _isMethodOrFunction;
    public boolean _isNamespaceOrPkg;
    public boolean _isConstructor;
    public String _fullclassname;
    private Hashtable _pInfo;
    public String _source;
    private boolean hasPkg= false;

    public String getText()
    {
    
        int pos=_text.indexOf("(");

        int staticPos=_text.indexOf("-->static");

        if (staticPos > -1)
            _isStatic=true;
        else
            _isStatic=false;

        if (_isClojure==true)
            _isStatic=true;


        if (pos > -1)
            return _text.substring(0,pos);
        else
            return _text;



    }

    public ClojureCodeCompletionItem(String text, int dotOffset, int carretOffset)
    {
    _text = text;
    _dotOffset = dotOffset;
    _carretOffset = carretOffset;
    }

     public ClojureCodeCompletionItem(String text, int dotOffset, int carretOffset,int layout)
    {
    _text = text;
    _dotOffset = dotOffset;
    _carretOffset = carretOffset;
    _layout=layout;
    }

        public ClojureCodeCompletionItem(String text, int dotOffset, int carretOffset,Hashtable processInfo)
    {
    _text = text;
    _dotOffset = dotOffset;
    _carretOffset = carretOffset;

    _pInfo=processInfo;
    _layout=(Integer)processInfo.get("layout");
    _isClojure=(Boolean)processInfo.get("isClojure");
    _priority=(Integer)processInfo.get("priority");
    _isMethodOrFunction=(Boolean)processInfo.get("isMethodOrFunction");
    _isNamespaceOrPkg=(Boolean)processInfo.get("isNamespaceOrPackage");
    _fullclassname=(String)processInfo.get("fullclassname");
    _source=(String)processInfo.get("source");
    _isConstructor=(Boolean)processInfo.get("isConstructor");
    }



    private void doSubstitute(final JTextComponent component, final String toAdd, final int backOffset) {

        final StyledDocument doc = (StyledDocument) component.getDocument();

        class AtomicChange implements Runnable {

            public void run() {

                int caretOffset = component.getCaretPosition();
                String cname= component.getClass().getName();
                
                Boolean _isReplPanel=false;

                if (cname.equals("javax.swing.JEditorPane"))
                    _isReplPanel=true;
               
                String value = getText();
                String javaList="";

                if (toAdd != null) {
                    value += toAdd;
                }
                
                try {

                    String c=component.getText(caretOffset-1,1);

                    if (!_isClojure && _isNamespaceOrPkg) //java package containing the Class at the end
                    {
                        value=setValueForClass(value,component);
                        javaList=getJavaImportListStr(value);

                        

                        if ((javaList.contains("(")) && (! _isReplPanel))
                        {
                            int messageret=(JOptionPane.showConfirmDialog(component, "Do you want to add the import " + value + " to your ns imports?", "add import ?",0));

                            if (messageret==0)
                            {
                                PersistentArrayMap entry= addImportList(component, javaList);
                                //component.setCaretPosition(caretOffset + javaList.length());
                                value=getClassPart(value);
                                
                                if (entry!=null)
                                {
                                    String origNS=(String)entry.get(Keyword.intern(Symbol.create("orignodestr")));
                                    String newNS=(String)entry.get(Keyword.intern(Symbol.create("newnodestr")));

                                    int insertOffset=newNS.length()-origNS.length();

                                    
                                    _carretOffset = _carretOffset + insertOffset;
                                    _dotOffset=_dotOffset + insertOffset;

                                }
                            }
                        }

                    }

                        
                    if (! c.equals("/") && (_layout!=backSlashAfterParen) && (_layout!=backSlashNoParen))
                    {
                        
                       

                        switch(_layout)
                        {

                        case LowercaseAfterParen:
                            doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                        case UppercaseAfterParen:
                            doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                        case UppercaseAfterParenWithDot:
                           if (_isMethodOrFunction && (!_isClojure)) //java method
                            {
                                if (_isConstructor) {
                                    value = "";
                                } else if (Character.isLetter(value.charAt(0))) {
                                    {
                                        doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                        value = "." + value;

                                    }
                                }
                            }
                            else
                               doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                       case LowercaseAfterParenWithDot:
                            if (_isMethodOrFunction && (!_isClojure)) //java method
                            {
                                if (_isConstructor) {
                                    value = "";
                                } else if (Character.isLetter(value.charAt(0))) {
                                    {
                                        doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                        value = "." + value;

                                    }
                                }
                            }
                            else
                               doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                       case backSlashAfterParen:
                            doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                        case FirstDotAfterParen:
                            doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                        case FirstDotNoParen:
                            doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                        case UppercaseNoParen:
                            doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                        case UppercaseNoParenWithDot:
                          if (_isMethodOrFunction && (!_isClojure)) //java method
                            {
                                if (_isConstructor) {
                                    value = "";
                                } else if (Character.isLetter(value.charAt(0))) {
                                    {
                                        doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                        value = "." + value;

                                    }
                                }
                            }
                            else
                               doc.remove(_dotOffset, _carretOffset - _dotOffset ); 
                            break;
                        case LowercaseNoParenWithDot:
                            if (_isMethodOrFunction && (!_isClojure)) //java method
                            {
                                if (_isConstructor) {
                                    value = "";
                                } else if (Character.isLetter(value.charAt(0))) {
                                    {
                                        doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                        value = "." + value;

                                    }
                                }
                            }
                            else
                               doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;
                         case LowercaseNoParen:
                            doc.remove(_dotOffset, _carretOffset - _dotOffset );
                            break;

                        }



                        doc.insertString(_dotOffset, value, null);
                    }
                    else
                    {
                        if (! c.equals("/"))
                        {
                            int backSlashOffset=indexOfBackSlash(component);
                            if (backSlashOffset==-1)
                                backSlashOffset=caretOffset;

                            if (_isStatic || _layout==backSlashAfterParen || _layout==backSlashNoParen )
                            {
                                if (_isClojure && _isMethodOrFunction)
                                {
                                    if (isInCurrentNamespace(_fullclassname, component))
                                    {
                                        doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                        doc.insertString(_dotOffset,setValue(value), null);
                                    }
                                    else
                                    {
                                        doc.remove(backSlashOffset + 1, caretOffset - backSlashOffset-1);
                                        doc.insertString(backSlashOffset+1, value, null);
                                    }
                                }
                                else
                                    {
                                        doc.remove(backSlashOffset + 1, caretOffset - backSlashOffset-1);
                                        doc.insertString(backSlashOffset+1, value, null);
                                    }
                            }
                            else
                            {
                                doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                doc.insertString(_dotOffset,setValue(value), null);
                            }

                        }
                        else
                            if (_isStatic || _layout==backSlashAfterParen || _layout==backSlashNoParen )

                                if (_isClojure && _isMethodOrFunction)
                                {
                                    if (isInCurrentNamespace(_fullclassname, component))
                                    {
                                        doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                        doc.insertString(_dotOffset,setValue(value), null);
                                    }
                                    else
                                         doc.insertString(_carretOffset, value, null);
                                }
                                else
                                    doc.insertString(_carretOffset, value, null);
                            else
                            {
                                doc.remove(_dotOffset, _carretOffset - _dotOffset );
                                doc.insertString(_dotOffset,setValue(value), null);

                            }
                    }


                    
                    component.setCaretPosition(component.getCaretPosition() - backOffset);

                } catch (BadLocationException e) {
                    LOG.log(Level.FINEST, e.getMessage());
                }

            }
        }

         AtomicChange change = new AtomicChange();
            try {
                NbDocument.runAtomicAsUser(doc, change);
            } catch (BadLocationException ex) {
                LOG.log(Level.FINEST, ex.getMessage());
            }

    }


    private String getJavaImportListStr (String fullclassstr)
    {
         Integer dotPos=fullclassstr.lastIndexOf(".");
         String clsstr="";
         String pkgstr="";

        if (dotPos > -1)
        {
            clsstr= fullclassstr.substring(dotPos + 1);
            pkgstr=fullclassstr.substring(0,dotPos);

            return "(" + pkgstr + " " + clsstr + ")\n";

        }
        else
            return fullclassstr;


    }

    private String setValueForClass(String value,JTextComponent component)
    {

      Iterator iter=getAllJavaClasses(component);
        ArrayList<String> li=new ArrayList<String>();
        Hashtable ht=new Hashtable();



        String entry="";

        if (iter != null)
        {
            while (iter.hasNext())
            {
                entry=(String)iter.next();

                if (entry !=null)
                    ht.put(entry, getPkgPart(entry));

            }
        }

        if (ht.containsKey(value))
            value=getClassPart(value);

        if (ht.containsValue(getPkgPart(value)))
            hasPkg=true;

        return value;

    }

    private Boolean isInCurrentNamespace(String value,JTextComponent component)
    {

      //value will have format of namespace/function
        Integer slashPos=value.indexOf("/");
        
        if (slashPos > -1)
            value=value.substring(0,slashPos);
        
        Iterator iter=getAllClojureNamespacesWithinNSnode(component);
        ArrayList<String> li=new ArrayList<String>();

        String entry="";

        if (iter != null)
        {
            while (iter.hasNext())
            {
                entry=(String)iter.next();

                if (entry !=null)
                    li.add(entry);
            }
        }

        if (li.contains(value))
            return true;
        else
            return false;

    }

    private String getClassPart(String fullclassstr)
    {

        Integer dotPos=fullclassstr.lastIndexOf(".");

        if (dotPos > -1)
            return fullclassstr.substring(dotPos + 1);
        else
            return fullclassstr;

    }

    private String getPkgPart(String fullclassstr)
    {

        Integer dotPos=fullclassstr.lastIndexOf(".");

        if (dotPos > -1)
            return fullclassstr.substring(0,dotPos);
        else
            return fullclassstr;
    }

    private String setValue(String value)
    {



        if (_isClojure)
            return value;
        else
            return "." + value;

    }



    private Iterator getAllJavaClasses(JTextComponent component)
    {
            try {
                return ((Iterable)getalljavaclassesfn.invoke(component)).iterator();

            } catch (Exception ex) {
                //Exceptions.printStackTrace(ex);
                return null;
            }

    }

    private Iterator getAllClojureNamespacesWithinNSnode(JTextComponent component)
    {
            try {
                return ((Iterable)getallclojurenamespacesfn.invoke(component)).iterator();

            } catch (Exception ex) {
                //Exceptions.printStackTrace(ex);
                return null;
            }

    }

    private PersistentArrayMap addImportList(JTextComponent component,String javaList)
    {
            try {
                return (PersistentArrayMap)addimportlistfn.invoke(component,javaList);

            } catch (Exception ex) {
                //Exceptions.printStackTrace(ex);
                    return null;
            }

    }

    public void defaultAction(JTextComponent jTextComponent) {
        Completion.get().hideAll();
        doSubstitute(jTextComponent, null, 0);
    }

    public void processKeyEvent(KeyEvent arg0) {

//        boolean blnShift =arg0.isShiftDown();
//        char c=arg0.getKeyChar();
//        int keyCode=arg0.getKeyCode();

    }

    public int getPreferredWidth(Graphics graphics, Font font) {
         return CompletionUtilities.getPreferredWidth(encodeHTML(_text), null, graphics, font);
    }

    public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width,
            int height, boolean selected) {
        CompletionUtilities.renderHtml(_icon, encodeHTML(_text), null, g, defaultFont,
            (selected ? Color.white : fieldColor), width, height, selected);
    }

    public CompletionTask createDocumentationTask() {

        //return null;

        return new AsyncCompletionTask( new AsyncCompletionQuery() {

        protected void query(CompletionResultSet completionResultSet, Document document, int i)
        {
           completionResultSet.setDocumentation(new ClojureCodeCompletionDocumentation(ClojureCodeCompletionItem.this));
           completionResultSet.finish();
        }

        });

    }

    public CompletionTask createToolTipTask() {
        return null;
    }

    public boolean instantSubstitution(JTextComponent arg0) {
        return false;
    }

    public int getSortPriority() {
        return _priority;
    }

    public CharSequence getSortText() {
        return getText();
    }

    public CharSequence getInsertPrefix() {
        return getText();
    }

    public static String encodeHTML(String s)
{
    StringBuffer out = new StringBuffer();
    for(int i=0; i<s.length(); i++)
    {
        char c = s.charAt(i);
        if(c > 127 || c=='"' || c=='<' || c=='>')
        {
           out.append("&#"+(int)c+";");
        }
        else
        {
            out.append(c);
        }
    }
    return out.toString();
}

    static int indexOfBackSlash(final JTextComponent component) {

        int caretOffset = component.getCaretPosition();

        String c;

        int i = caretOffset-1;

        try
        {
        while (--i > -1)
        {
            c = component.getText(i,1);
            if (c.equals("/"))
            {
               return i;
            }
        }
        return -1;
        }
        catch (BadLocationException ex)
        {
                return -1;
        }
    }

}

