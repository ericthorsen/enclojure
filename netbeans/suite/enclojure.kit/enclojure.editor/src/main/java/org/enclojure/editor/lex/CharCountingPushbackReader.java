/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.editor.lex;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PushbackReader;
import java.io.Reader;
/**
 *
 */
public class CharCountingPushbackReader extends PushbackReader {
    protected long counter = 0;

public CharCountingPushbackReader(Reader r){
	super(new LineNumberReader(r));
}

public int getLineNumber(){
	return ((LineNumberReader) in).getLineNumber() + 1;
}

    @Override
public int read() throws IOException{
    int c = super.read();
    ++counter;
    return c;
    }

    @Override
public void unread(int c) throws IOException{
    super.unread(c);
    --counter;
    }
public long getPosition() { return counter;}
}
