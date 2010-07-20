/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.enclojure.ide.nb.source;

import clojure.lang.IFn;
import java.awt.Component;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.enclojure.ide.core.LogAdapter;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.WizardDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;

public class CljSourceWizardPanel1 implements WizardDescriptor.Panel<WizardDescriptor>,DocumentListener {

    private static final LogAdapter LOG = new LogAdapter(CljSourceWizardPanel1.class.getName());

    public boolean _isValid = true;
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    public CljSourceWizardPanel1()
    {
    
    }
    private CljSourceVisualPanel1 component;
    private final Set<ChangeListener> listeners = new HashSet<ChangeListener>(1); // or can use ChangeSupport in NB 6.0
    public IFn _validatorFunc;
    // Get the visual component for the panel. In this template, the component
    // is kept separate. This can be more efficient: if the wizard is created
    // but never displayed, or not all panels are displayed, it is better to
    // create only those which really need to be visible.
    public Component getComponent() {
        if (component == null) {
            component = new CljSourceVisualPanel1();
            //component = new ClojureSourceFileDlg();

            ((JTextField)component.packagesComboBox.getEditor().
                    getEditorComponent()).getDocument().addDocumentListener(this);
            component.filenameTextField.getDocument().addDocumentListener(this);
        }
        return component;
    }

    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx(SampleWizardPanel1.class);
    }

    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return _isValid;
        //return getComponent().isValid();
        // If it depends on some condition (form filled out...), then:
        // return someCondition();
        // and when this condition changes (last form field filled in...) then:
        // fireChangeEvent();
        // and uncomment the complicated stuff below.
    }
    
   public void setValid(boolean val) {
        if (_isValid != val) {
            _isValid = val;
           fireChangeEvent();  // must do this to enable next/finish button
        }
    }
    
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
   public void insertUpdate(DocumentEvent e) {
        change(e);
    }

    public void removeUpdate(DocumentEvent e) {
        change(e);
    }

    public void changedUpdate(DocumentEvent e) {
        change(e);
    }

    private void change(DocumentEvent e) {
        if(this._validatorFunc!=null)
        {
        try {
            Boolean v = (Boolean) this._validatorFunc.invoke(e);
            if (v) {
                setValid(true);
            } else {
                setValid(false);
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        }
    }

    // You can use a settings object to keep track of state. Normally the
    // settings object will be the WizardDescriptor, so you can use
    // WizardDescriptor.getProperty & putProperty to store information entered
    // by the user.    
    public void readSettings(WizardDescriptor settings) {
    }

    public void storeSettings(WizardDescriptor settings) {
    }
}

