/*
 * Copyright (C) 2015 nasique
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package boa.gui.objects;

import static boa.gui.configuration.ConfigurationTreeGenerator.addToMenu;
import static boa.gui.GUI.logger;
import boa.gui.configuration.TransparentTreeCellRenderer;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import javax.swing.Icon;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Utils;

/**
 *
 * @author nasique
 */
public class StructureObjectTreeGenerator {
    ExperimentDAO xpDAO;
    ObjectDAO objectDAO;
    protected StructureObjectTreeModel treeModel;
    protected JTree tree;
    protected ExperimentNode experimentNode;
    
    public StructureObjectTreeGenerator(ObjectDAO dao, ExperimentDAO xpDAO) {
        this.objectDAO=dao;
        this.xpDAO=xpDAO;
        this.experimentNode=new ExperimentNode(this);
        
    }
    
    private void generateTree() {
        treeModel = new StructureObjectTreeModel(experimentNode);
        tree=new JTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        DefaultTreeCellRenderer renderer = new TransparentTreeCellRenderer();
        Icon icon = null;
        renderer.setLeafIcon(icon);
        renderer.setClosedIcon(icon);
        renderer.setOpenIcon(icon);
        tree.setCellRenderer(renderer);
        tree.setScrollsOnExpand(true);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(path);
                    Rectangle pathBounds = tree.getUI().getPathBounds(tree, path);
                    if (pathBounds != null && pathBounds.contains(e.getX(), e.getY())) {
                        JPopupMenu menu = new JPopupMenu();
                        Object lastO = path.getLastPathComponent();
                        logger.debug("right-click on element: {}", lastO);
                        if (lastO instanceof UIContainer) {
                            UIContainer UIC=(UIContainer)lastO;
                            addToMenu(UIC.getDisplayComponent(), menu);
                        }
                        menu.show(tree, pathBounds.x, pathBounds.y + pathBounds.height);
                    }
                }
            }
        });
    }
    
    public Experiment getExperiment() {return xpDAO.getExperiment();}
    
    public JTree getTree() {
        if (tree==null) generateTree();
        return tree;
    }
    
    public void collapseAll() {
        if (treeModel!=null) treeModel.reload();
    }
    
    public void selectObject(StructureObject object) {
        if (object==null) tree.setSelectionRow(-1);
        else tree.setSelectionPath(getObjectTreePath(object));
    }
    
    public TreePath getObjectTreePath(StructureObject object) {
        ArrayList<TreeNode> path = new ArrayList<TreeNode>(); 
        path.add(experimentNode);
        FieldNode f = experimentNode.getFieldNode(object.getFieldName());
        path.add(f);
        TimePointNode t = f.getChildren()[object.getTimePoint()];
        path.add(t);
        ArrayList<StructureObject> objectPath = getObjectPath(object);
        StructureNodeContainer lastStructureContainer = t;
        for (StructureObject o : objectPath) {
            StructureNode s = lastStructureContainer.getStructureNode(o.getStructureIdx());
            path.add(s);
            ObjectNode on = s.getChildren()[o.getIdx()];
            path.add(on);
            lastStructureContainer=on;
        }
        return new TreePath(path.toArray(new TreeNode[path.size()]));
    }
    
    
    public ObjectNode getObjectNode(StructureObject object) {
        FieldNode f = experimentNode.getFieldNode(object.getFieldName());
        TimePointNode t = f.getChildren()[object.getTimePoint()];
        ArrayList<StructureObject> objectPath = getObjectPath(object);
        StructureNodeContainer lastStructureContainer = t;
        for (StructureObject o : objectPath) {
            StructureNode s = lastStructureContainer.getStructureNode(o.getStructureIdx());
            ObjectNode on = s.getChildren()[o.getIdx()];
            lastStructureContainer=on;
        }
        return (ObjectNode)lastStructureContainer;
    }
    
    private ArrayList<StructureObject> getObjectPath(StructureObject object) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        res.add(object);
        while(!object.getParent().isRoot()) {
            res.add(object.getParent());
            object=object.getParent();
        }
        return Utils.reverseOrder(res);
    }
    
    /*public void setCollapsedNode(TreePath path, boolean collapsed) {
        if (collapsed) tree.collapsePath(path);
        else tree.expandPath(path);
    }*/
    
    public StructureObjectTreeModel getModel() {
        return this.treeModel;
    }
    
}
