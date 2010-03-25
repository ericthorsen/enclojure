/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 *
 * ThorTech L.L.C. elects to include this software in this distribution
 * under the GNU General Public License, version 2
 * (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
 * exception (http://www.gnu.org/software/classpath/license.html).
 *
 *(comment
 *******************************************************************************
 *    Copyright (c) ThorTech, L.L.C. All rights reserved.
 *    The use and distribution terms for this software are covered by the
 *    GNU General Public License, version 2
 *    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
 *    exception (http://www.gnu.org/software/classpath/license.html)
 *    which can be found in the file GPL-2.0+ClasspathException.txt at the root
 *    of this distribution.
 *    By using this software in any fashion, you are agreeing to be bound by
 *    the terms of this license.
 *    You must not remove this notice, or any other, from this software.
 *******************************************************************************)
 *
 * Modified the package of initial file from org.netbeans.modules.groovy.support
 * to org.enclojure.ide.nb.clojure.project
*/

//package org.netbeans.modules.groovy.support;
package org.enclojure.ide.nb.clojure.project;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.LookupMerger;
import org.openide.util.Lookup;

/**
 * Factory class for creation of {@link org.netbeans.spi.project.LookupMerger} instances.
 *
 * @author Martin Adamek
 */
public final class LookupMergerSupport {

    /**
     * Create a simple instance of LookupMerger for ActionProvider. It takes
     * all implemntations it finds int he provided lookup and iterates them until a result
     * is found.
     * @return
     */
    public static LookupMerger<ActionProvider> createActionProviderLookupMerger() {
        return new ActionProviderMerger();
    }

    private static class ActionProviderMerger implements LookupMerger<ActionProvider> {

        public Class<ActionProvider> getMergeableClass() {
            return ActionProvider.class;
        }

        public ActionProvider merge(Lookup lookup) {
            return new MergedActionProvider(lookup);
        }
    }

    private static class MergedActionProvider implements ActionProvider {

        private final Lookup lookup;

        public MergedActionProvider(Lookup lookup) {
            this.lookup = lookup;
        }

        /*
         * Merges all supported actions from all providers.
         * Does not preserve order of actions.
         */
        public String[] getSupportedActions() {
            Set<String> resultSet = new HashSet<String>();
            Collection<? extends ActionProvider> providers = lookup.lookupAll(ActionProvider.class);
            // merge all supported actions from all providers
            for (ActionProvider impl : providers) {
                resultSet.addAll(Arrays.asList(impl.getSupportedActions()));
            }
            return resultSet.toArray(new String[resultSet.size()]);
        }

        /*
         * Iterates over providers and calls invokeAction on first found provider
         * that has this action enabled.
         */
        public void invokeAction(String command, Lookup context) throws IllegalArgumentException {
            Collection<? extends ActionProvider> providers = lookup.lookupAll(ActionProvider.class);
            for (ActionProvider impl : providers) {
                if (impl.isActionEnabled(command, context)) {
                    impl.invokeAction(command, context);
                    return;
                }
            }
        }

        /*
         * Returns true if at least one provider returns true.
         */
        public boolean isActionEnabled(String command, Lookup context) throws IllegalArgumentException {
            Collection<? extends ActionProvider> providers = lookup.lookupAll(ActionProvider.class);
            for (ActionProvider impl : providers) {
                if (impl.isActionEnabled(command, context)) {
                    return true;
                }
            }
            return false;
        }

    }

}
