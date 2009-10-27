/*
(comment
*
*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
*    The use and distribution terms for this software are covered by the
*    Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
*    which can be found in the file epl-v10.html at the root of this distribution.
*    By using this software in any fashion, you are agreeing to be bound by
*    the terms of this license.
*    You must not remove this notice, or any other, from this software.
*
*    Author: Eric Thorsen
)
*/
package org.enclojure.flex;
import java_cup.runtime.Symbol;
import clojure.lang.Var;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;


public class ClojureSymbol extends Symbol {
    public IPersistentMap _tokenType;
    public Object _data;
    public int _line;
    public int _col;
    public int _char;

    static public final Keyword ID = Keyword.intern("ID");
    
    static public int getID(IPersistentMap tokenType)
    {
        return ((Integer)RT.get(tokenType,ID)).intValue();
    }

    public ClojureSymbol(IPersistentMap tokenType,int line, int column , int chari)
    {
        super(getID(tokenType));
        this._tokenType = tokenType;
        this._line = line;
        this._col = column;
        this._char = chari;
    }

    public ClojureSymbol( IPersistentMap tokenType,int line, int column
                        , int chari, Object data)
    {
        super(getID(tokenType));
        this._tokenType = tokenType;
        this._data = data;
        this._line = line;
        this._col = column;
        this._char = chari;
    }

    static public ClojureSymbol create(IPersistentMap tokenType,int line
                                    , int column , int chari)
    {
        return new ClojureSymbol(tokenType,line, column ,chari);
    }

    static public ClojureSymbol create(IPersistentMap tokenType,int line, int column
                                    , int chari ,  Object data)
    {
        return new ClojureSymbol(tokenType,line, column ,chari, data);
    }
}