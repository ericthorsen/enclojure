/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.ide;
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

public int read() throws IOException{
    int c = super.read();
    if(c!=-1)
        ++counter;
    return c;
    }

public void unread(int c) throws IOException{
    super.unread(c);
    --counter;    
    }
public long getPosition() { return counter;}
}

