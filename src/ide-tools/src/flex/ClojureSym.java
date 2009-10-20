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

package flex;
import java_cup.runtime.Symbol;
import clojure.lang.Var;
import clojure.lang.IPersistentMap;

public class ClojureSym extends Symbol {
    public IPersistentMap _tokenType;
    public Object _data;
    public int _line;
    public int _col;
    public int _char;

    public ClojureSym(IPersistentMap tokenType,int t,int line, int column , int chari)
    {
        super(t);
        this._tokenType = tokenType;
        this._line = line;
        this._col = column;
        this._char = chari;
    }

    public ClojureSym( IPersistentMap tokenType,int t,int line, int column
                        , int chari, Object data)
    {
        super(t,data);
        this._tokenType = tokenType;
        this._data = data;
        this._line = line;
        this._col = column;
        this._char = chari;
    }

    static public ClojureSym create(IPersistentMap tokenType,int t,int line
                                    , int column , int chari)
    {
        return new ClojureSym( tokenType,t,line, column ,chari);
    }
    
    static public ClojureSym create(IPersistentMap tokenType,int t,int line, int column
                                    , int chari ,  Object data)
    {
        return new ClojureSym(tokenType,t,line, column ,chari, data);
    }    
}
