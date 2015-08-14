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
package dataStructure.objects.userInterface;

import dataStructure.objects.StructureObject;
import static dataStructure.objects.userInterface.StructureObjectTreeGenerator.logger;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.TreeNode;
import utils.SmallArray;

/**
 *
 * @author nasique
 */
public class StructureNode implements TreeNode, UIContainer {
    TreeNode parent; // can be either TimePointNode or ObjectNode
    ObjectNode[] children;
    int idx;
    
    public StructureNode(int structureIdx, ObjectNode parent) {
        this.parent=parent;
        this.idx=structureIdx;
    }
    
    public StructureNode(int structureIdx, TimePointNode parent) {
        this.parent=parent;
        this.idx=structureIdx;
    }
    
    public ObjectNode[] getChildren() {
        if (idx>=0) {
            if (children==null) {
                StructureObject parentObject = getParentObject();
                StructureObject[] data = parentObject.getChildObjects(idx);
                if (data==null) {
                    data = getGenerator().objectDAO.getObjects(parentObject.getId(), idx);
                    parentObject.setChildObjects(data, idx);
                    if (logger.isDebugEnabled()) logger.debug("retrieving object from db: fieldName: {} timePoint: {} structure: {}, nb objects: {}", getTimePointNode().parent.fieldName, getTimePointNode().timePoint, idx, data==null?"null":data.length);
                }
                if (data!=null) {
                    children = new ObjectNode[data.length];
                    for (int i = 0; i<children.length; ++i) children[i] = new ObjectNode(this, i, data[i]);
                }
            }
        }
        return children;
    }
    
    public StructureObject getParentObject() {
        return (parent instanceof TimePointNode) ? ((TimePointNode)parent).getData() : ((ObjectNode)parent).data;
    }
    
    public TimePointNode getTimePointNode() {
        return (parent instanceof TimePointNode) ? ((TimePointNode)parent) : ((ObjectNode)parent).parent.getTimePointNode();
    }
    
    public void createChildren() {
        children=null;
        getChildren();
    }
    
    public StructureObjectTreeGenerator getGenerator() {
        return (parent instanceof TimePointNode) ? ((TimePointNode)parent).getGenerator() : ((ObjectNode)parent).getGenerator();
    }
    
    // UIContainer implementation
    @Override public Object[] getDisplayComponent() {
        return new Object[0];
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return getGenerator().xp.getStructure(idx).getName();
    }
    
    public ObjectNode getChildAt(int childIndex) {
        return getChildren()[childIndex];
    }

    public int getChildCount() {
        return getChildren().length;
    }

    public TreeNode getParent() {
        return parent;
    }

    public int getIndex(TreeNode node) {
        if (node instanceof ObjectNode) return ((ObjectNode)node).idx;
        else return -1;
    }

    public boolean getAllowsChildren() {
        return true;
    }

    public boolean isLeaf() {
        return getChildCount()==0;
    }

    public Enumeration children() {
        if (getChildren()==null) return Collections.emptyEnumeration();
        return Collections.enumeration(Arrays.asList(getChildren()));
    }
}
