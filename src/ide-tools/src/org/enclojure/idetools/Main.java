package org.enclojure.idetools;

import org.enclojure.flex.*;

/**
 *
 * @author ericthor
 */
public class Main {

    static public void main (String args[])
    {
        try {
    _Lexer lex = new _Lexer(new java.io.FileReader(args[0]));
        } catch(Exception e)
        {}
    }

}
