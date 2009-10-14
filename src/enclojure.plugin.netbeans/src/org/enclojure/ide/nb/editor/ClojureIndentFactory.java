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

import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.modules.editor.indent.spi.ExtraLock;
import org.netbeans.modules.editor.indent.spi.IndentTask;
import org.openide.cookies.EditorCookie;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;

/**
 *
 * @author ffailla (markp)
 */
public class ClojureIndentFactory implements IndentTask.Factory {

    final static int PREV_CONTEXT_COUNT = 20000;

    public IndentTask createTask(Context ctx) {
        return new CljIndentTask(ctx);
    }

    private static class CljIndentTask implements IndentTask {

        private Context _ctx;

        public CljIndentTask(Context ctx)
        {
         _ctx = ctx;
        }

        public int getIndentLevel(String codeAbove, final int indentSize)//, int nLineStart)
        {
            int npos = codeAbove.length() - 1; //nLineStart - 1;
            int openParens = 0;
            int openBracket = 0;
            int openCurly = 0;
            boolean inString = false;
            while (npos >= 0)
            {
               boolean inComment = false;

               //Should really use AST to find if in comment.
               int nCommentPos = npos;
               char chSemiCheck = codeAbove.charAt(nCommentPos);
               char chLastNonWhite = ' ';
               while(nCommentPos > 0 && chSemiCheck != '\r' && chSemiCheck != '\n')
               {

                  if(!Character.isWhitespace(chSemiCheck))
                    chLastNonWhite = chSemiCheck;

                  --nCommentPos;
                  chSemiCheck = codeAbove.charAt(nCommentPos);
               }

               inComment = (chLastNonWhite == ';');

               if(!inComment)
               {
                   //ASTNode n = ParserManager.get(doc).getAST();
                   //n.findNode("string",
                    switch(codeAbove.charAt(npos))
                    {
                        //Should really use AST to find if in string.
                        case '\"':
                            if(npos > 0 && codeAbove.charAt(npos-1) != '\\')
                                inString = !inString;
                            break;
                        case '}': if(!inString) --openCurly;
                            break;
                        case '{': if(!inString) ++openCurly;
                            break;
                        case ']': if(!inString) --openBracket;
                            break;
                        case '[': if(!inString) ++openBracket;
                            break;
                        case ')': if(!inString) --openParens;
                            break;
                        case '(': if(!inString) ++openParens;
                            break;
                    }

                    if(openParens > 0 || openBracket > 0 || openCurly > 0)
                    {
                        int nStart = npos;
                        char ch = codeAbove.charAt(nStart);
                        while(nStart > 0 && ch != '\r' && ch != '\n')
                        {
                            --nStart;
                            ch = codeAbove.charAt(nStart);
                        }

                        if(openBracket > 0 || openCurly > 0)
                            return npos - nStart;
                        else
                            return npos - nStart - 1 + indentSize;
                    }
               }

                npos--;
            }

            return 0;
        }

        public void reindent() throws BadLocationException {
            StyledDocument doc = (StyledDocument) _ctx.document ();

            //No indentation in REPL
           //if(doc.getProperty(StyledDocument.TitleProperty) == CljREPLTopWindowTopComponent.REPL_DOCUMENT)
           //     return;

            final int indentSize =2;
            //NbPreferences.forModule(EnclojurePreferencesPanel2.class).getInt(EnclojurePreferencesPanel2.PREF_INDENT_VALUE,
             //       EnclojurePreferencesPanel2.DEFAULT_INDENT_SIZE);

            //reformat
            if(!_ctx.isIndent())
            {

               //check if there is any selected text.
               //if not then just indent the one line
               Node[] n = TopComponent.getRegistry().getActivatedNodes();
               if (n.length == 1) {
                  EditorCookie ec = n[0].getCookie(EditorCookie.class);
                  if (ec != null) {
                     JEditorPane[] panes = ec.getOpenedPanes();
                     if (panes.length > 0) {
                        if (panes[0].getSelectedText() == null || panes[0].getSelectedText().trim() == "") {
                           int nLineStart = _ctx.lineStartOffset(panes[0].getCaretPosition());
                           String codeAbove = doc.getText(java.lang.Math.max(0, nLineStart - PREV_CONTEXT_COUNT),
                                   java.lang.Math.min(nLineStart, PREV_CONTEXT_COUNT));
                           int indentLevel = getIndentLevel(codeAbove, indentSize);
                           _ctx.modifyIndent(nLineStart, indentLevel);

                           return;
                        }
                     }
                  }
               }


                int nStartOfLine = 0;
                int nLastStartOfLine = 0;

                String codeAbove = doc.getText(java.lang.Math.max(0, _ctx.lineStartOffset(_ctx.startOffset()) - PREV_CONTEXT_COUNT),
                         java.lang.Math.min(_ctx.lineStartOffset(_ctx.startOffset()), PREV_CONTEXT_COUNT));

                while(nStartOfLine != -1)
                {
                    int nIndentLevel = getIndentLevel(codeAbove, indentSize);

                    if(nIndentLevel != _ctx.lineIndent(_ctx.lineStartOffset(_ctx.startOffset()) + nStartOfLine))
                        _ctx.modifyIndent(_ctx.lineStartOffset(_ctx.startOffset()) + nStartOfLine, nIndentLevel);

                    nLastStartOfLine = nStartOfLine;

                    String codeBlock = doc.getText(_ctx.lineStartOffset(_ctx.startOffset()), _ctx.endOffset() - _ctx.startOffset());

                    nStartOfLine = codeBlock.indexOf('\n', nStartOfLine);

                    if(nStartOfLine >= 0)

                    {
                        ++nStartOfLine;//move to first char after \n
                         codeAbove = doc.getText(java.lang.Math.max(0, _ctx.lineStartOffset(_ctx.startOffset()) + nStartOfLine - PREV_CONTEXT_COUNT),
                        java.lang.Math.min(_ctx.lineStartOffset(_ctx.startOffset()) + nStartOfLine, PREV_CONTEXT_COUNT) );
                        //codeAbove += codeBlock.substring(nLastStartOfLine, nStartOfLine);
                    }
                }

            }
            else
            {
                int nLineStart = _ctx.startOffset();
                String codeAbove = doc.getText(java.lang.Math.max(0, nLineStart - PREV_CONTEXT_COUNT),
                        java.lang.Math.min(nLineStart, PREV_CONTEXT_COUNT));
                int indentLevel = getIndentLevel(codeAbove, indentSize);
                _ctx.modifyIndent(nLineStart, indentLevel);
            }

        }

        public ExtraLock indentLock() {
            return null;
        }
    }

}
