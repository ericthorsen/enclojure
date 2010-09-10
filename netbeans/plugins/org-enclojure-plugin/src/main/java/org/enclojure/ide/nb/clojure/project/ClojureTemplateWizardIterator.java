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
*    Author: Eric Thorsen
*******************************************************************************
)
*/
package org.enclojure.ide.nb.clojure.project;

import java.awt.Component;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.spi.project.ui.support.ProjectChooser;
import org.netbeans.spi.project.ui.templates.support.Templates;
import org.openide.WizardDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.util.logging.Level;
import org.enclojure.ide.core.LogAdapter;
import org.netbeans.api.project.Project;


public class ClojureTemplateWizardIterator implements WizardDescriptor./*Progress*/InstantiatingIterator {

    private static final LogAdapter LOG = new LogAdapter(ClojureTemplateWizardIterator.class.getName());

    private static Object RT;
    private int index;
    private WizardDescriptor.Panel[] panels;
    private WizardDescriptor wiz;

    public ClojureTemplateWizardIterator() {
    }

    public static ClojureTemplateWizardIterator createIterator() {
        return new ClojureTemplateWizardIterator();
    }

    private WizardDescriptor.Panel[] createPanels() {
        return new WizardDescriptor.Panel[]{
                    new ClojureTemplateWizardPanel(),
                };
    }

    private String[] createSteps() {
        return new String[]{
                    NbBundle.getMessage(ClojureTemplateWizardIterator.class, "LBL_CreateProjectStep")
                };
    }


    public Set/*<FileObject>*/ instantiate(/*ProgressHandle handle*/) throws IOException {
        Set<FileObject> resultSet = new LinkedHashSet<FileObject>();
//        File dirF = FileUtil.normalizeFile((File) wiz.getProperty("projdir"));
//        dirF.mkdirs();
//
//        FileObject template = Templates.getTemplate(wiz);
//        FileObject dir = FileUtil.toFileObject(dirF);
        File dirF = (File) wiz.getProperty("projdir");
        dirF.mkdirs();
        FileObject template = Templates.getTemplate(wiz);
        //FileObject dir = FileUtil.toFileObject(dirF);
        try {

            //FF - exclide thie lib/clojure.jar if the user selects another clojure reference
            //if(  dir.getPath().startsWith(ClojureTemplatePanelVisual.getDefaultClojureReferencePath()))
            ClojureTemplatePanelVisual component = (ClojureTemplatePanelVisual)current().getComponent();
            unZipFile(template.getInputStream()
                    , dirF
                    ,component.packageNameTextField.getText()
                    ,component.getProjectName());
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        FileObject dir = FileUtil.toFileObject(dirF);
        // Always open top dir as a project:
        resultSet.add(dir);
        // Look for nested projects to open as well:
        Enumeration<? extends FileObject> e = dir.getFolders(true);
        while (e.hasMoreElements()) {
            FileObject subfolder = e.nextElement();
            if (ProjectManager.getDefault().isProject(subfolder)) {
                resultSet.add(subfolder);
            }
        }

        File parent = dirF.getParentFile();
        if (parent != null && parent.exists()) {
            ProjectChooser.setProjectsFolder(parent);
        }
//        //ET As a final step, see if saving all fixes the maven problem
//        Project p = ProjectManager.getDefault().findProject(dir);
//        if(p!=null)
//        {
//            ProjectManager.getDefault().saveProject(p);
//        }
//         else
//        {
////            throw new Exception("Unable to find project");
//        }
        return resultSet;
    }

    
    public void initialize(WizardDescriptor wiz) {
        this.wiz = wiz;
        index = 0;
        panels = createPanels();
        // Make sure list of steps is accurate.
        String[] steps = createSteps();
        for (int i = 0; i < panels.length; i++) {
            Component c = panels[i].getComponent();
            if (steps[i] == null) {
                // Default step name to component name of panel.
                // Mainly useful for getting the name of the target
                // chooser to appear in the list of steps.
                steps[i] = c.getName();
            }
            if (c instanceof JComponent) { // assume Swing components
                JComponent jc = (JComponent) c;
                // Step #.
                jc.putClientProperty("WizardPanel_contentSelectedIndex", new Integer(i));
                // Step name (actually the whole list for reference).
                jc.putClientProperty("WizardPanel_contentData", steps);
            }
        }
    }

    public void uninitialize(WizardDescriptor wiz) {
        this.wiz.putProperty("projdir", null);
        this.wiz.putProperty("name", null);
        this.wiz = null;
        panels = null;
    }

    public String name() {
        return MessageFormat.format("{0} of {1}",
                new Object[]{new Integer(index + 1), new Integer(panels.length)});
    }

    public boolean hasNext() {
        return index < panels.length - 1;
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

    public WizardDescriptor.Panel current() {
        return panels[index];
    }

    // If nothing unusual changes in the middle of the wizard, simply:
    public final void addChangeListener(ChangeListener l) {
    }

    public final void removeChangeListener(ChangeListener l) {
    }
    
    //FF
    private static void replacePropertyFileValue(String filePath, String key, String value) throws FileNotFoundException, IOException {
        java.util.Properties props = new java.util.Properties();
        props.load(new FileInputStream(filePath));
        props.setProperty(key, value);
        props.store(new FileOutputStream(filePath), "Replaced [key=" + key +"] with [" + value + "].");
    }
    
    private static void unZipFile(InputStream source, File projectRoot,String defPackage,String projectName) throws IOException, Exception {
        try {
            //ET Temporary solution to make sure there is a clojure library setup in the users netbeans environment"
            //clojure.lang.RT.var("org.enclojure.ide.nb.editor.utils","ensure-clojure-lib").invoke("Clojure-1.0.0");
            clojure.lang.RT.var("org.enclojure.ide.nb.clojure.project.create","unzip-create-and-reg-project")
                    .invoke(source,projectRoot,defPackage,projectName);
        } catch (Exception ex) {
          LOG.log(Level.FINEST, ex.getMessage());
        } 
        finally {
            source.close();
        }
    }

    public static void writeFile(ZipInputStream str, FileObject fo) throws IOException {
        OutputStream out = fo.getOutputStream();
        try {
            FileUtil.copy(str, out);
        } finally {
            out.flush();
            out.close();
        }
    }

    public static void filterProjectXML(FileObject fo, ZipInputStream str, String name) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            FileUtil.copy(str, baos);
            Document doc = XMLUtil.parse(new InputSource(new ByteArrayInputStream(baos.toByteArray())), false, false, null, null);
            NodeList nl = doc.getDocumentElement().getElementsByTagName("name");
            if (nl != null) {
                for (int i = 0; i < nl.getLength(); i++) {
                    Element el = (Element) nl.item(i);
                    if (el.getParentNode() != null && "data".equals(el.getParentNode().getNodeName())) {
                        NodeList nl2 = el.getChildNodes();
                        if (nl2.getLength() > 0) {
                            nl2.item(0).setNodeValue(name);
                        }
                        break;
                    }
                }
            }
            OutputStream out = fo.getOutputStream();
            try {
                XMLUtil.write(doc, out, "UTF-8");
            } finally {
                out.flush();
                out.close();
            }
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            writeFile(str, fo);
        }

    }
}
