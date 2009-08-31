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
*    Author: Eric Thorsen
*******************************************************************************
)
*/
package org.enclojure.ide.nb.editor.completion;

import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.FinderFactory.GenericBwdFinder;
import org.netbeans.editor.FinderFactory.GenericFwdFinder;

/**
 *
 * @author ericthor
 */
public class CljFinderFactory {
    /** Next word forward finder */
    static boolean isCljIdentifierPart (BaseDocument doc,char c)
    {
        if(doc.isIdentifierPart(c)
                || c == '?' || c == '-' || c == '*' || c == '$')
            return true;
        return false;
    }
    
    public static class NextWordFwdFinder extends GenericFwdFinder {

        /** Document used to recognize the character types */
        BaseDocument doc;

        /** Currently inside whitespace */
        boolean inWhitespace;

        /** Currently inside identifier */
        boolean inIdentifier;

        /** Currently inside not in word and not in whitespace */
        boolean inPunct;

        /** Whether scanning the first character */
        boolean firstChar;

        /** Whether stop on EOL */
        boolean stopOnEOL;

        /** Stop with successful find on the first white character */
        boolean stopOnWhitespace;

        public NextWordFwdFinder(BaseDocument doc, boolean stopOnEOL, boolean stopOnWhitespace) {
            this.doc = doc;
            this.stopOnEOL = stopOnEOL;
            this.stopOnWhitespace = stopOnWhitespace;
        }

        public void reset() {
            super.reset();
            inWhitespace = false;
            inIdentifier = false;
            inPunct = false;
            firstChar = true;
        }

        protected int scan(char ch, boolean lastChar) {
            if (stopOnEOL) {
                if (ch == '\n') {
                    found = true;
                    return firstChar ? 1 : 0;
                }
                firstChar = false;
            }

            if (doc.isWhitespace(ch)) { // whitespace char found
                if (stopOnWhitespace) {
                    found = true;
                    return 0;
                } else {
                    inWhitespace = true;
                    return 1;
                }
            }

            if (inWhitespace) {
                found = true;
                return 0;
            }
            if (inIdentifier) { // inside word
                if (isCljIdentifierPart(doc,ch)) { // still in word
                    return 1;
                }
                found = true;
                return 0; // found punct
            }
            if (inPunct) { // inside punctuation
                if (isCljIdentifierPart(doc,ch)) { // a word starts after punct
                    found = true;
                    return 0;
                }
                return 1; // still in punct
            }

            // just starting - no state assigned yet
            if (isCljIdentifierPart(doc,ch)) {
                inIdentifier = true;
                return 1;
            } else {
                inPunct = true;
                return 1;
            }
        }

    }

    /** Find start of the word. This finder can be used to go to previous
    * word or to the start of the current word.
    */
    public static class PreviousWordBwdFinder extends GenericBwdFinder {

        BaseDocument doc;

        /** Currently inside identifier */
        boolean inIdentifier;

        /** Currently inside not in word and not in whitespace */
        boolean inPunct;

        /** Stop on EOL */
        boolean stopOnEOL;

        /** Stop with successful find on the first white character */
        boolean stopOnWhitespace;

        boolean firstChar;

        public PreviousWordBwdFinder(BaseDocument doc, boolean stopOnEOL, boolean stopOnWhitespace) {
            this.doc = doc;
            this.stopOnEOL = stopOnEOL;
            this.stopOnWhitespace = stopOnWhitespace;
        }

        public void reset() {
            super.reset();
            inIdentifier = false;
            inPunct = false;
            firstChar = true;
        }

        protected int scan(char ch, boolean lastChar) {
            if (stopOnEOL) {
                if (ch == '\n') {
                    found = true;
                    return firstChar ? 0 : 1;
                }
                firstChar = false;
            }

            if (inIdentifier) { // inside word
                if (isCljIdentifierPart(doc,ch)) {
                    if (lastChar) {
                        found = true;
                        return 0;
                    }
                    return -1;
                }
                found = true;
                return 1; // found punct or whitespace
            }
            if (inPunct) { // inside punctuation
                if (isCljIdentifierPart(doc,ch) || doc.isWhitespace(ch) || lastChar) {
                    found = true;
                    return 1;
                }
                return -1; // still in punct
            }
            if (doc.isWhitespace(ch)) {
                if (stopOnWhitespace) {
                    found = true;
                    return 1;
                }
                return -1;
            }
            if (isCljIdentifierPart(doc,ch)) {
                inIdentifier = true;
                if (lastChar) {
                    found = true;
                    return 0;
                }
                return -1;
            }
            inPunct = true;
            return -1;
        }

    }
}
