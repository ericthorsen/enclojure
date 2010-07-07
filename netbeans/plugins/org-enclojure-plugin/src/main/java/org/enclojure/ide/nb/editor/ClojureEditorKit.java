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
*    Author: Frank Failla, Eric Thorsen
*******************************************************************************
)
*/
package org.enclojure.ide.nb.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.SyntaxUpdateTokens;
import org.netbeans.editor.TokenContextPath;
import org.netbeans.editor.TokenID;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.openide.util.Lookup;
import java.util.prefs.Preferences;
import org.netbeans.api.editor.settings.SimpleValueNames;

import javax.swing.Action;
import javax.swing.text.TextAction;
import org.netbeans.editor.ext.ExtKit;

@SuppressWarnings("unchecked") 
public class ClojureEditorKit extends NbEditorKit {

    public static synchronized ClojureEditorKit create(Map<?,?> args) throws Exception {
        Object mimetype = args.get("mime-type");
        if(mimetype == null || !((String)mimetype).contains("text/x-clojure")) {
            throw new Exception("mime-type passed must contain 'text/x-clojure' in the string");
        }
        return new ClojureEditorKit((String)mimetype);
    }

    public final String MIME_TYPE;

    public ClojureEditorKit(String mimetype) {
        super();
        this.MIME_TYPE = mimetype;
        if (MIME_TYPE.equals("text/x-clojure"))
        {
            Lookup l =  MimeLookup.getLookup(MimePath.get ("text/x-clojure"));
            Preferences p = l.lookup(Preferences.class);
            p.put(SimpleValueNames.CODE_FOLDING_ENABLE,Boolean.TRUE.toString());
        }
    }
    
    @Override
    protected void initDocument(BaseDocument doc) {
        super.initDocument(doc);
        doc.putProperty(SyntaxUpdateTokens.class, new SUT());
    }
    
    public class SUT extends SyntaxUpdateTokens {
        private List list;
        public void syntaxUpdateStart() {
            list = new ArrayList();
        }
        public List syntaxUpdateEnd() {
            return list;
        }
        public void syntaxUpdateToken(TokenID tid, TokenContextPath tcp, int offset, int len) {
            if (tid != null) {
                list.add(new TokenInfo(tid, tcp, offset, len));
            }
        }
    }

    @Override
    public String getContentType() {
        return MIME_TYPE;
    }

    @Override
    protected Action[] createActions() {
        Action[] superActions = super.createActions();

        final String COMMENT_CHAR = ";";

        return TextAction.augmentList(superActions, new Action[]{
                    new ExtKit.ToggleCommentAction(COMMENT_CHAR),
                    new ExtKit.CommentAction(COMMENT_CHAR),
                    new ExtKit.UncommentAction(COMMENT_CHAR),
        });
    }


}
