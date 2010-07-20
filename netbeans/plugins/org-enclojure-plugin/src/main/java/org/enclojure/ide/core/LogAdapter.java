/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.enclojure.ide.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter to act as a drop-in replacement for a Logger. The main purpose is to tweak the existing log(...)-methods
 * to show more and detailed information about which Thread calls the logger. One might add things like the timestamp
 * laster as well.
 * 
 * @author Matthias Schmidt
 */


public class LogAdapter {

    Logger LOG;

    public LogAdapter(String name) {
        LOG = Logger.getLogger(name);
        LOG.setLevel(Level.ALL);
    }

    public void log(Level level, String msg){
        LOG.log(level, formatter(msg));
    }

    public void log(Level level, String msg, Object param1){
        LOG.log(level, formatter(msg), param1);
    }

    public void log(Level level, String msg, Object[] params){
        LOG.log(level, formatter(msg), params);
    }

    private String formatter(String msg){
        return Thread.currentThread().getName() + ":" + msg;
    }

}
