/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.idetools;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PushbackReader;
import java.io.Reader;
/**
 *
 */
public class PositionalPushbackReader extends PushbackReader {
    protected long counter = 0;

public PositionalPushbackReader(Reader r){
	super(new LineNumberReader(r));
}

public int getLineNumber(){
	return ((LineNumberReader) in).getLineNumber() + 1;
}

public int read() throws IOException{
    int c = super.read();
    ++counter;
    return c;
    }

public int read(char[] buff, int off, int len) throws IOException{
    int c = super.read(buff,off,len);
    counter += c;;
    return c;
    }


public void unread(char[] buff, int off, int len) throws IOException{
    super.unread(buff,off,len);
    counter -= len;//Not sure on this???
    }

public long getPosition() { return counter;}
}

