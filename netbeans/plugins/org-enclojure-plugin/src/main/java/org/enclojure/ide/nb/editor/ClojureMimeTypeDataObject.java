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
*    Author: Narayan Singhal
*******************************************************************************
)
*/
package org.enclojure.ide.nb.editor;

import clojure.lang.IFn;
import clojure.lang.RT;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataNode;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.nodes.CookieSet;
import org.openide.nodes.Node;
import org.openide.nodes.Children;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.text.DataEditorSupport;

public class ClojureMimeTypeDataObject extends MultiDataObject {

    private static final LogAdapter LOG = new LogAdapter(ClojureMimeTypeDataObject.class.getName());

    CloneableEditorSupport ed;
    IFn getNewPropertyListenerFn =
            (IFn)RT.var("org.enclojure.ide.nb.editor.data-object-listener"
                        ,"get-property-listener");
    
    public Object clojureAnalyzerData=null;

    public ClojureMimeTypeDataObject(FileObject pf, MultiFileLoader loader)
            throws DataObjectExistsException, IOException {
        super(pf, loader);

        LOG.log(Level.FINEST, "ctr()");

        CookieSet cookies = getCookieSet();
         ed = DataEditorSupport.create(this, getPrimaryEntry(), cookies);        
        cookies.add((Node.Cookie)ed );
        try {
            this.addPropertyChangeListener(
                    (PropertyChangeListener)getNewPropertyListenerFn.invoke(this));
        } catch (Throwable ex) {
            Exceptions.printStackTrace(ex);
        }        
    }

    @Override
    protected Node createNodeDelegate() {        
        return new DataNode(this, Children.LEAF, getLookup());
    }

    @Override
    public Lookup getLookup() {
        return getCookieSet().getLookup();
    }
}

//class CljPropertyChangeListener implements PropertyChangeListener
//{
//    private static final LogAdapter LOG = new LogAdapter(ClojureMimeTypeDataObject.class.getName());
//
//    public void propertyChange (PropertyChangeEvent e)
//    {
//        LOG.log(Level.FINEST, "propertyChange()");
//        PropertyChangeEvent e2 = e;
//        LOG.log(Level.INFO,"Property changed old:", e);
//    }
//}
