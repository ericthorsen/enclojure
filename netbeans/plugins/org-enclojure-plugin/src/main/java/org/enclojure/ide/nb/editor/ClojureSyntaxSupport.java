/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.ide.nb.editor;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.ext.ExtSyntaxSupport;

/**
 *
 * @author ericthorsen
 */
public class ClojureSyntaxSupport extends ExtSyntaxSupport {
    
    ClojureSyntaxSupport(BaseDocument doc)
    {
        super(doc);
    }
}
