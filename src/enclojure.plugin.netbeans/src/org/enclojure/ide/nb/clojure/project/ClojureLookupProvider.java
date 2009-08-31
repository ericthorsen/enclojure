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
*    Author: Frank Failla
*******************************************************************************
)
*/
package org.enclojure.ide.nb.clojure.project;

import java.util.ArrayList;
import java.util.List;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.LookupProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 *
 * @author Frank Failla
 */
public class ClojureLookupProvider implements LookupProvider {

    public static ClojureLookupProvider createJavaSE() {
        return new ClojureLookupProvider();
    }

    private ClojureLookupProvider() {}

    public Lookup createAdditionalLookup(Lookup baseContext) {
        Project project = baseContext.lookup(Project.class);
        if (project == null) {
            throw new IllegalStateException("Lookup " + baseContext + " does not contain a Project");
        }
        List<Object> instances = new ArrayList<Object>(2);
        instances.add(LookupMergerSupport.createActionProviderLookupMerger());
        instances.add(new ClojureActionProvider(project));
        return Lookups.fixed(instances.toArray(new Object[instances.size()]));
    }

}
