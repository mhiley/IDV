/*
 * $Id: PublishManager.java,v 1.13 2005/05/13 18:31:06 jeffmc Exp $
 *
 * Copyright  1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package ucar.unidata.idv.publish;


import ucar.unidata.idv.*;
import ucar.unidata.util.TwoFacedObject;
import ucar.unidata.util.ObjectListener;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.Misc;
import ucar.unidata.util.IOUtil;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;


import ucar.unidata.xml.XmlObjectStore;
import ucar.unidata.xml.XmlUtil;


import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import ucar.unidata.xml.XmlResourceCollection;

import org.w3c.dom.Element;
import org.w3c.dom.Node;


import ucar.unidata.idv.ui.*;

import java.lang.reflect.Constructor;

/**
 *  This manages  the nascent publishing facility within the IDV.
 * This whole framework needs to be thought out a bit more but for
 * now we have on instance of a publisher: InfoceteraBlogger
 * that knows how to post articles and files to an infocetera web log
 * server.
 *
 *
 * @author IDV development team
 */


public class PublishManager extends IdvManager {

    /** Xml element &quot;publisher&quot; tag name */
    public static final String TAG_PUBLISHER = "publisher";

    /**
     * Xml element &quot;class&quot; attribute name.
     * This is the class name of  a concrete derived class
     * of this class.
     */
    public static final String ATTR_CLASS = "class";

    public static final String ATTR_NAME = "name";




    /** List of {@link ucar.unidata.idv.publish.IdvPublisher}s */
    private List<IdvPublisher> publishers;

    private List<TwoFacedObject> types = new ArrayList<TwoFacedObject>();



    private List<JComboBox> comboBoxes = new  ArrayList<JComboBox>();


    /**
     * Create me with the IDV
     *
     * @param idv The IDV
     */
    public PublishManager(IntegratedDataViewer idv) {
        super(idv);
        publishers = (List<IdvPublisher>) (List) getIdv().getStore().getEncodedFile("publishers.xml");
        if(publishers == null) {
            publishers = new ArrayList<IdvPublisher>();
        }
        for(IdvPublisher publisher: publishers) {
            publisher.setIdv(getIdv());
        }
    }

    public void initMenu(final JMenu menu) {
        menu.addMenuListener(new MenuListener() {
            public void menuCanceled(MenuEvent e) {}

            public void menuDeselected(MenuEvent e) {}

            public void menuSelected(MenuEvent e) {
                menu.removeAll();
                makeMenu(menu);
            }
            });
    }


    public JComboBox makeSelector() {
        if(!isPublishingEnabled()) return null;
        JComboBox cbx = new JComboBox();
        comboBoxes.add(cbx);
        updatePublishers(false);
        return cbx;
    }

    public void publishContent(String file, ViewManager fromViewmanager, JComboBox box) {
        if(box == null || box.getSelectedIndex()==0) {
            return;
        }
        IdvPublisher publisher = (IdvPublisher) box.getSelectedItem();
        publisher.publishContent(file, fromViewmanager);
    }

    private void  updatePublishers(boolean andWrite) {
        if(andWrite) {
            getIdv().getStore().putEncodedFile("publishers.xml", publishers);
        }
        for(JComboBox publishCbx: comboBoxes) {
            Object selected = publishCbx.getSelectedItem();
            Vector items = new Vector();
            items.add("-Select Publisher-");
            items.addAll(getIdv().getPublishManager().getPublishers());
            GuiUtils.setListData(publishCbx, items);
            if(selected!=null && items.contains(selected)) 
                publishCbx.setSelectedItem(selected);
        
        }
    }



    public void makeMenu(JMenu menu) {
        JMenu newMenu = new JMenu("New");
        menu.add(newMenu);
        for (TwoFacedObject tfo: types) {
            JMenuItem mi = new JMenuItem(tfo.toString());
            newMenu.add(mi);
            mi.addActionListener(new ObjectListener(tfo.getId()) {
                    public void actionPerformed(ActionEvent ae) {
                        try {
                            IdvPublisher newPublisher = (IdvPublisher) theObject.getClass().newInstance();
                            newPublisher.setIdv(getIdv());
                            if(newPublisher.doInitNew()) {
                                publishers.add(newPublisher);
                                updatePublishers(true);
                            }
                        } catch (Exception exc) {
                            logException("Creating publisher" , exc);
                        }
                    }
                });
        }
        if(publishers.size()>0) {
            JMenu configMenu = new JMenu("Configure");
            menu.add(configMenu);
            JMenu deleteMenu = new JMenu("Delete");
            menu.add(deleteMenu);

            //            menu.addSeparator();
            for(IdvPublisher publisher:(List<IdvPublisher>) publishers) {
                deleteMenu.add(GuiUtils.makeMenuItem(publisher.getName(),this,"deletePublisher", publisher));
                configMenu.add(GuiUtils.makeMenuItem(publisher.getName(),this,"configurePublisher", publisher));
                //                publisher.initMenu(menu);
            }
        }
    }

    public void configurePublisher(IdvPublisher publisher) {
        //        if(publisher.configure()) updatePublishers(true);
        publisher.configure();
        updatePublishers(true);
    }


    public void deletePublisher(IdvPublisher publisher) {
        publishers.remove(publisher);
        updatePublishers(true);
    }


    public void newPublisher() {


    }


    /**
     * Do we have any publishers
     *
     * @return Can we publish any content
     */
    public boolean isPublishingEnabled() {
        return types.size()>0;
    }

    /**
     * If publishing is not enabled then show a user message and return false
     *
     * @return Is publishing enabled
     */
    public boolean publishCheck() {
        if ( !isPublishingEnabled()) {
            LogUtil.userMessage("No publishing available");
            return false;
        }
        return true;
    }

    /**
     * Gets a publisher.
     *
     * @return The first publisher in the list of publishers (for now)
     */
    public IdvPublisher getPublisher() {
        //For now jsut return the first one
        return (IdvPublisher) publishers.get(0);
    }



    /**
     * This xml encoders the given object as a xidv bundle file
     * and publishes it with the given description
     *
     * @param desc The description
     * @param object The object to encode and publish
     */
    public void publishObject(String desc, Object object) {
        try {
            publishXml(desc, getIdv().getEncoderForWrite().toElement(object),
                       ".xidv");
        } catch (Exception exc) {
            logException("Publishing " + desc, exc);
        }
    }

    /**
     * Publish the xml
     *
     * @param desc The description
     * @param root The xml root to publish
     */
    public void publishXml(String desc, Element root) {
        publishXml(desc, root, ".xml");
    }

    /**
     * Publish the xml
     *
     * @param desc The description
     * @param root The xml root to publish
     * @param fileExt The file extension
     */
    public void publishXml(String desc, Element root, String fileExt) {
        try {
            String xml  = XmlUtil.toString(root);
            String uid  = Misc.getUniqueId();
            String tail = uid + fileExt;
            String file = IOUtil.joinDir(getStore().getUserTmpDirectory(),
                                         tail);
            IOUtil.writeFile(file, xml);
            doPublish("Publish " + desc, file);
        } catch (Exception exc) {
            logException("Publishing " + desc, exc);
        }
    }


    /**
     * Publish a message
     */
    public void publishMessage() {
        publishMessage(null);
    }

    /**
     * Publish a message with the given properties(?)
     *
     * @param properties The properties
     */
    public void publishMessage(String properties) {
        doPublish("Publish message", null, properties);
    }


    /**
     * Publish the idv bundle
     */
    public void publishState() {
        publishState(null);
    }

    /**
     * Publish the idv bundle
     *
     * @param properties The properties
     */
    public void publishState(String properties) {
        String uid  = Misc.getUniqueId();
        String tail = uid + ".jnlp";
        String file = IOUtil.joinDir(getStore().getUserTmpDirectory(), tail);
        getPersistenceManager().doSave(file);
        doPublish("Publish bundle file", file, properties);
    }

    /**
     * Publish the file
     *
     * @param title Title to use
     * @param filePath The file
     */
    public void doPublish(String title, String filePath) {
        doPublish(title, filePath, null);
    }


    /**
     * Publish the file
     *
     * @param title Title to use
     * @param filePath The file
     * @param properties The properties
     */
    public void doPublish(String title, String filePath, String properties) {
        if ( !publishCheck()) {
            return;
        }
        //        getPublisher().doPublish(title, filePath, properties);
    }

    /**
     * Initialize me
     */
    public void initPublisher() {
        try {
            XmlResourceCollection resources = getIdv().getResourceManager().getXmlResources(
                                                                                       IdvResourceManager.RSC_PUBLISHERTYPES);
            for (int resourceIdx = 0; resourceIdx < resources.size();
                 resourceIdx++) {
                Element root       = resources.getRoot(resourceIdx);
                if(root == null) {
                    continue;
                }
                types.addAll(getPublisherTypes(getIdv(), root));
            }
        } catch (Exception exc) {
            logException("Initializing publishers", exc);
        }
    }


    /**
     * Process the given xml, instantiating a list
     * of <code>IdvPublisher</code>s
     *
     * @param idv The idv
     * @param root Root of the publishers.xml file
     * @return List of publishers
     */
    public static List<TwoFacedObject> getPublisherTypes(IntegratedDataViewer idv, Element root) {
        List<TwoFacedObject> publisherTypes = new ArrayList<TwoFacedObject>();
        List nodes      = XmlUtil.findChildren(root, TAG_PUBLISHER);
        for (int i = 0; i < nodes.size(); i++) {
            try {
                Element child = (Element) nodes.get(i);
                Class publisherClass =
                    Misc.findClass(XmlUtil.getAttribute(child, ATTR_CLASS));
                if (publisherClass == null) {
                    throw new IllegalArgumentException("Could not load publisher class:" + 
                                                       XmlUtil.getAttribute(child, ATTR_CLASS));
                }
                Constructor ctor =
                    Misc.findConstructor(publisherClass,
                                         new Class[]{
                                             IntegratedDataViewer.class,
                                             Element.class });
                if (ctor == null) {
                    continue;
                }
                Object obj =  ctor.newInstance(new Object[]{ idv,
                                                             child });
                IdvPublisher idvPublisher =
                    (IdvPublisher) ctor.newInstance(new Object[]{ idv,
                                                                  child });
                //                idvPublisher.init();
                publisherTypes.add(new TwoFacedObject(XmlUtil.getAttribute(child,ATTR_NAME), idvPublisher));
            } catch (Exception exc) {
                LogUtil.logException("Creating publisher client", exc);
            }
        }

        return publisherTypes;
    }



    /**
     * Get the list of Publishers
     *
     * @return The publishers
     */
    public List getPublishers() {
        return publishers;
    }





}








