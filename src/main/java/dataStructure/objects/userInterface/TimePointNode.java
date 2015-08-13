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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.TreeNode;

/**
 *
 * @author nasique
 */
public class TimePointNode implements TreeNode {
    FieldNode parent;
    int timePoint;
    private StructureNode[] children;
    private StructureObject data;
    
    public TimePointNode(FieldNode parent, int timePoint) {
        this.timePoint=timePoint;
        this.parent=parent;
    }
    
    public StructureObjectTreeGenerator getGenerator() {
        return parent.getGenerator();
    }
    
    public StructureObject getData() {
        if (data==null) {
            data = getGenerator().objectDAO.getRoot(parent.fieldName, timePoint);
            StructureObjectTreeGenerator.logger.debug("Time Point: {} retrieving root object from db: {}", timePoint, data);
        }
        return data;
    }
    
    public StructureNode[] getChildren() {
        if (children==null) {
            int[] childrenIndicies = getGenerator().xp.getChildStructures(-1);
            children = new StructureNode[childrenIndicies.length];
            for (int i = 0; i<children.length; ++i) children[i]=new StructureNode(childrenIndicies[i], this);
        }
        return children;
    }
    
    // TreeNode implementation
    @Override public String toString() {
        return "Time Point: "+timePoint;
    }
    
    public StructureNode getChildAt(int childIndex) {
        return getChildren()[childIndex];
    }

    public int getChildCount() {
        return getChildren().length;
    }

    public TreeNode getParent() {
        return parent;
    }

    public int getIndex(TreeNode node) {
        if (node==null) return -1;
        for (int i = 0; i<getChildren().length; ++i) if (node.equals(children[i])) return i;
        return -1;
    }

    public boolean getAllowsChildren() {
        return true;
    }

    public boolean isLeaf() {
        return getChildCount()==0;
    }

    public Enumeration children() {
        return Collections.enumeration(Arrays.asList(getChildren()));
    }
    
}
