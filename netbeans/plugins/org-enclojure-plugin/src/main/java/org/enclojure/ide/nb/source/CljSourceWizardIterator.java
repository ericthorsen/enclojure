/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.enclojure.ide.nb.source;

import clojure.lang.IFn;
import clojure.lang.RT;
import java.awt.Component;
import java.io.IOException;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.java.project.JavaProjectConstants;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.SourceGroup;
import org.netbeans.api.project.Sources;
import org.netbeans.spi.java.project.support.ui.templates.JavaTemplates;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.util.Exceptions;

public final class CljSourceWizardIterator implements WizardDescriptor.InstantiatingIterator {

    private static final LogAdapter LOG = new LogAdapter(CljSourceWizardIterator.class.getName());
    
    private int index;
    private WizardDescriptor wizard;
    private WizardDescriptor.Panel[] panels;
    public Project project = null;
    IFn _setupWizard = RT.var("org.enclojure.ide.nb.source.add-file","setup-wizard");
    public IFn _validatorFunc;
    IFn _createFile = RT.var("org.enclojure.ide.nb.source.add-file","create-file");
    public Sources sources;
    public SourceGroup[] groups;

    private WizardDescriptor.Panel packageChooserPanel;

    /**
     * Initialize panels representing individual wizard's steps and sets
     * various properties for them influencing wizard appearance.
     */
    private WizardDescriptor.Panel[] getPanels() {
        project = Templates.getProject(wizard);
        sources = project.getLookup().lookup(Sources.class);
        groups = sources.getSourceGroups(JavaProjectConstants.SOURCES_TYPE_JAVA);
        //;packageChooserPanel = JavaTemplates.createPackageChooser(project,groups,new CljSourceWizardPanel1());
        if (panels == null) {
            panels = new WizardDescriptor.Panel[]{
                 //JavaTemplates.createPackageChooser(project, groups)
                        new CljSourceWizardPanel1()                        
                    };
            String[] steps = createSteps();
            for (int i = 0; i < panels.length; i++) {
                Component c = panels[i].getComponent();
                if (steps[i] == null) {
                    // Default step name to component name of panel. Mainly
                    // useful for getting the name of the target chooser to
                    // appear in the list of steps.
                    steps[i] = c.getName();
                }
                if (c instanceof JComponent) { // assume Swing components
                    JComponent jc = (JComponent) c;
                    // Sets step number of a component
                    // TODO if using org.openide.dialogs >= 7.8, can use WizardDescriptor.PROP_*:
                    jc.putClientProperty("WizardPanel_contentSelectedIndex", new Integer(i));
                    // Sets steps names for a panel
                    jc.putClientProperty("WizardPanel_contentData", steps);
                    // Turn on subtitle creation on each step
                    jc.putClientProperty("WizardPanel_autoWizardStyle", Boolean.TRUE);
                    // Show steps on the left side with the image on the background
                    jc.putClientProperty("WizardPanel_contentDisplayed", Boolean.TRUE);
                    // Turn on numbering of all steps
                    jc.putClientProperty("WizardPanel_contentNumbered", Boolean.TRUE);
                }
            }
        }
        LOG.log(Level.INFO,"Leaving getPanels");
        return panels;
    }

    public Set instantiate() throws IOException {
        try {
            _createFile.invoke(this, wizard);
//        String className = Templates.getTargetName(wizard);
//        FileObject pkg = Templates.getTargetFolder(wizard);
//        DataFolder targetFolder = DataFolder.findFolder(pkg);
//        TemplateWizard template = (TemplateWizard)wizard;
//        DataObject doTemplate = template.getTemplate();
//        OpenCookie open = (OpenCookie) doTemplate.getCookie(OpenCookie.class);
//        if (open != null) {
//            open.open();
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        
        }
        return Collections.EMPTY_SET;
    }

    public void initialize(WizardDescriptor wizard) {
        this.wizard = wizard;
        try {
            LOG.log(Level.INFO,"Calling clojure init code.");
            this._validatorFunc =(IFn)this._setupWizard.invoke(this, wizard);
            if(current()!=null)
                ((CljSourceWizardPanel1)current())._validatorFunc = this._validatorFunc;
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        LOG.log(Level.INFO,"Calling setVisible");
    }

    public void uninitialize(WizardDescriptor wizard) {
        panels = null;
    }

    public WizardDescriptor.Panel current() {
        return getPanels()[index];
    }

    public String name() {
        return index + 1 + ". from " + getPanels().length;
    }

    public boolean hasNext() {
        return index < getPanels().length - 1;
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public void nextPanel() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        index++;
    }

    public void previousPanel() {
        if (!hasPrevious()) {
            throw new NoSuchElementException();
        }
        index--;
    }

    // If nothing unusual changes in the middle of the wizard, simply:
    public void addChangeListener(ChangeListener l) {
    }

    public void removeChangeListener(ChangeListener l) {
    }

    // If something changes dynamically (besides moving between panels), e.g.
    // the number of panels changes in response to user input, then uncomment
    // the following and call when needed: fireChangeEvent();
    /*
    private Set<ChangeListener> listeners = new HashSet<ChangeListener>(1); // or can use ChangeSupport in NB 6.0
    public final void addChangeListener(ChangeListener l) {
    synchronized (listeners) {
    listeners.add(l);
    }
    }
    public final void removeChangeListener(ChangeListener l) {
    synchronized (listeners) {
    listeners.remove(l);
    }
    }
    protected final void fireChangeEvent() {
    Iterator<ChangeListener> it;
    synchronized (listeners) {
    it = new HashSet<ChangeListener>(listeners).iterator();
    }
    ChangeEvent ev = new ChangeEvent(this);
    while (it.hasNext()) {
    it.next().stateChanged(ev);
    }
    }
     */
    // You could safely ignore this method. Is is here to keep steps which were
    // there before this wizard was instantiated. It should be better handled
    // by NetBeans Wizard API itself rather than needed to be implemented by a
    // client code.
    private String[] createSteps() {
        String[] beforeSteps = null;
        Object prop = wizard.getProperty("WizardPanel_contentData");
        if (prop != null && prop instanceof String[]) {
            beforeSteps = (String[]) prop;
        }

        if (beforeSteps == null) {
            beforeSteps = new String[0];
        }

        String[] res = new String[(beforeSteps.length - 1) + panels.length];
        for (int i = 0; i < res.length; i++) {
            if (i < (beforeSteps.length - 1)) {
                res[i] = beforeSteps[i];
            } else {
                res[i] = panels[i - beforeSteps.length + 1].getComponent().getName();
            }
        }
        return res;
    }
}
