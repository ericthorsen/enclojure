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

package org.enclojure.ide.nb.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.spi.editor.fold.FoldHierarchyTransaction;
import org.netbeans.spi.editor.fold.FoldManager;
import org.netbeans.spi.editor.fold.FoldOperation;
import org.openide.util.Exceptions;

/**
 *
 * @author ffailla
 */
@SuppressWarnings("unchecked") 
public class ClojureFoldManager implements FoldManager {

    private FoldOperation operation;
    private BaseDocument document;

    public ClojureFoldManager() {
        
    }

    protected FoldOperation getOperation() {
        return this.operation;
    }
    
    protected BaseDocument getDocument() {
        return this.document;
    }

    public void init(FoldOperation op) {
        this.operation = op;
    }

    public void initFolds(FoldHierarchyTransaction tran) {
        Document doc = getOperation().getHierarchy().getComponent().getDocument();
        if(doc instanceof BaseDocument) {
            this.document = (BaseDocument)doc;
        }

        updateFolds(document, tran);
    }

    public void insertUpdate(DocumentEvent docEvent, FoldHierarchyTransaction tran) {
        updateFolds(document, tran);
    }

    public void removeUpdate(DocumentEvent docEvent, FoldHierarchyTransaction tran) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void changedUpdate(DocumentEvent docEvent, FoldHierarchyTransaction tran) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void removeEmptyNotify(Fold fold) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void removeDamagedNotify(Fold fold) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void expandNotify(Fold fold) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void release() {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    private static final int MAX_FOLD_DESC_LEN = 50;
    private static final String LIST_START = "list-start";
    private static final String LIST_END = "list-end";
    private static final String DEF = "def";
    private static final String DEFN = "defn";
    private static final String DEFMACRO = "defmacro";
    private static final String EMPTY_STRING = "";

    private static String getFoldString(StringBuilder foldText, String foldDesc, int maxLen) {
        if(foldDesc.length()==0) {
            int len = Math.min(maxLen, foldText.toString().length() - 1);
            return foldText.toString().substring(0, len) + "... ";
        }
        else
            return foldDesc;
    }

    private static String getFoldDescription(String foldType, String funcName) {
        return String.format("%s %s ... ", foldType, funcName);
    }

    private static FoldType getFoldType(String foldTypeDesc) {
        return new FoldType(foldTypeDesc);
    }

    private static FoldInfo getFoldInfo(int start, int end, FoldType foldType, String desc) {
        return new FoldInfo(start, end, foldType, desc);
    }

    public static List<FoldInfo> getTopLevelFolds(BaseDocument doc) {
        ArrayList<FoldInfo> foldList = new ArrayList<FoldInfo>();
        Stack stack = new Stack();

        int start = 0;
        int end = 0;
        String foldDesc = EMPTY_STRING;
        String foldTypeDesc = EMPTY_STRING;
        boolean readName = false;
        StringBuilder foldText = new StringBuilder();

        TokenSequence tokens = TokenHierarchy.get(doc).tokenSequence();
        tokens.moveStart();

        while(tokens.moveNext()) {
            String tk = tokens.token().id().name();
            String tkn = tokens.token().toString();
            foldText.append(tkn);
            
            if(tk.equals(LIST_START)) {
                stack.push(tk);
                if(stack.size()==1)
                    start = tokens.offset();
            }
            else if(tk.equals(LIST_END)) {
                if(!stack.isEmpty())
                    stack.pop();
                
                if(stack.empty()) {
                    end = tokens.offset() + 1;
                    foldList.add(getFoldInfo(start,
                                             end,
                                             getFoldType(foldTypeDesc),
                                             getFoldString(foldText, foldDesc, MAX_FOLD_DESC_LEN)));

                    start = 0;
                    end = 0;
                    foldTypeDesc = EMPTY_STRING;
                    foldDesc = EMPTY_STRING;
                    foldText = foldText.replace(0, foldText.length() - 1, EMPTY_STRING);
                }
            }
            else if(tkn.equals(DEF) || tkn.equals(DEFN) || tkn.equals(DEFMACRO)) {
                foldTypeDesc = tkn;
                readName = true;
            }
            else if(readName) {
                foldDesc = getFoldDescription(foldTypeDesc, tkn);
                readName = false;
            }            
        }

        return foldList;
    }

    private synchronized void updateFolds(BaseDocument doc, FoldHierarchyTransaction tran) {
        List<FoldInfo> foldList = getTopLevelFolds(doc);
        for(FoldInfo fi : foldList)
        {
            try {
                getOperation().addToHierarchy(
                        fi.foldType,
                        fi.description,
                        false,
                        fi.startOffset,
                        fi.endOffset,
                        0,
                        0,
                        null,
                        tran);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                //tran.commit();
            }
        }
     }

     public static class FoldInfo {
        public int startOffset;
        public int endOffset;
        public FoldType foldType = null;
        public String description = null;

        public FoldInfo(int startOffset, int endOffset, FoldType foldType, String description) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.foldType = foldType;
            this.description = description;
        }

        public String toString() {
            return "FoldInfo[start=" + startOffset + ", end=" + endOffset + ", descr=" + description + ", type=" + foldType + "]";
        }
    }
    
}
