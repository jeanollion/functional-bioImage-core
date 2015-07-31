/*
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
package configuration.parameters.ui;

import configuration.parameters.Deactivatable;
import configuration.parameters.ListParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.SimpleParameter;
import configuration.parameters.ui.ListParameterUI;
import configuration.userInterface.ConfigurationTreeModel;
import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

/**
 *
 * @author jollion
 */
public class SimpleListParameterUI implements ListParameterUI{
    ListParameter list;
    Object[] actions;
    ConfigurationTreeModel model;
    static String[] actionNames=new String[]{"Add Element", "Remove All"};
    static String[] childActionNames=new String[]{"Add", "Remove", "Up", "Down"};
    static String[] deactivatableNames=new String[]{"Deactivate All", "Activate All"};
    static String[] childDeactivatableNames=new String[]{"Deactivate", "Activate"};
    
    public SimpleListParameterUI(ListParameter list_) {
        this.list = list_;
        this.model= ParameterUtils.getModel(list);
        this.actions = new Object[list.isDeactivatable()?5:2];
        JMenuItem action = new JMenuItem(actionNames[0]);
        actions[0] = action;
        action.setAction(
            new AbstractAction(actionNames[0]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    Parameter p = list.createChildInstance();
                    model.insertNodeInto(p, list);
                }
            }
        );
        action = new JMenuItem(actionNames[1]);
        actions[1]=action;
        action.setAction(
            new AbstractAction(actionNames[1]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    list.removeAllElements();
                    model.nodeStructureChanged(list);
                }
            }
        );
        if (list.isDeactivatable()) {
            actions[2]=new JSeparator();
            action = new JMenuItem(deactivatableNames[0]);
            actions[3]=action;
            action.setAction(
                new AbstractAction(deactivatableNames[0]) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        list.setActivatedAll(false);
                        model.nodeStructureChanged(list);
                    }
                }
            );
            action = new JMenuItem(deactivatableNames[1]);
            actions[4]=action;
            action.setAction(
                new AbstractAction(deactivatableNames[1]) {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        list.setActivatedAll(true);
                        model.nodeStructureChanged(list);
                    }
                }
            );
        }
    }
    public Object[] getDisplayComponent() {return actions;}
    
    public Component[] getChildDisplayComponent(final Parameter child) {
        final int unMutableIdx = list.getUnMutableIndex();
        final int idx = list.getIndex(child);
        final boolean mutable = idx>unMutableIdx;
        Component[] childActions = new Component[list.isDeactivatable()?7:4];
        childActions[0] = new JMenuItem(childActionNames[0]);
        ((JMenuItem)childActions[0]).setAction(
            new AbstractAction(childActionNames[0]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    Parameter p = list.createChildInstance();
                    model.insertNodeInto(p, list, mutable?idx+1:unMutableIdx+1);
                }
            }
        );
        childActions[1] = new JMenuItem(childActionNames[1]);
        ((JMenuItem)childActions[1]).setAction(
            new AbstractAction(childActionNames[1]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    model.removeNodeFromParent(child);
                }
            }
        );
        childActions[2] = new JMenuItem(childActionNames[2]);
        ((JMenuItem)childActions[2]).setAction(
            new AbstractAction(childActionNames[2]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    model.moveUp(list, child);
                }
            }
        );
        childActions[3] = new JMenuItem(childActionNames[3]);
        ((JMenuItem)childActions[3]).setAction(
            new AbstractAction(childActionNames[3]) {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    model.moveDown(list, child);
                }
            }
        );
        
        if (!mutable) {
            childActions[1].setEnabled(false);
            childActions[2].setEnabled(false);
            childActions[3].setEnabled(false);
        }
        if (idx==unMutableIdx+1) childActions[2].setEnabled(false);
        if (idx==0) childActions[2].setEnabled(false);
        if (idx==list.getChildCount()-1) childActions[3].setEnabled(false);
        
        if (list.isDeactivatable()) {
            childActions[4]=new JSeparator();
            childActions[5] = new JMenuItem(childActionNames[0]);
            ((JMenuItem)childActions[5]).setAction(
                    new AbstractAction(childDeactivatableNames[0]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            ((Deactivatable)child).setActivated(false);
                            model.nodeChanged(child);
                        }
                    }
            );
            childActions[6] = new JMenuItem(childActionNames[1]);
            ((JMenuItem)childActions[6]).setAction(
                    new AbstractAction(childDeactivatableNames[1]) {
                        @Override
                        public void actionPerformed(ActionEvent ae) {
                            ((Deactivatable)child).setActivated(true);
                            model.nodeChanged(child);
                        }
                    }
            );
            if (((Deactivatable)child).isActivated()) childActions[6].setEnabled(false);
            else childActions[5].setEnabled(false);
            if (!mutable) {
                childActions[5].setEnabled(false);
                childActions[6].setEnabled(false);
            }
        }
        
        
        
        return childActions;
    }
    
}