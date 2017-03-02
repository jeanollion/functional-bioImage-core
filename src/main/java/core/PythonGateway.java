/*
 * Copyright (C) 2017 jollion
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
package core;

import boa.gui.GUI;
import boa.gui.selection.SelectionUtils;
import dataStructure.objects.Selection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;
import utils.HashMapGetCreate;

/**
 *
 * @author jollion
 */
public class PythonGateway {
    public static final Logger logger = LoggerFactory.getLogger(PythonGateway.class);
    GatewayServer server;
    public PythonGateway() {
    }
    
    public void startGateway() {
        server = new GatewayServer(this);
        server.start();
    }
    public void setExperimentToGUI(String xpName) {
        GUI.getInstance().setDBConnection(xpName, null);
    }
    
    public void saveCurrentSelection(String dbName, int structureIdx, String selectionName, List<String> ids, List<String> positions, boolean showObjects, boolean showTracks, boolean open, boolean openWholeSelection, int structureDisplay, int interactiveStructure) {
        if (ids.isEmpty()) return;
        if (ids.size()!=positions.size()) throw new IllegalArgumentException("idx & position lists should be of same size "+ids.size() +" vs "+ positions.size());
        if (selectionName.length()==0) selectionName=null;
        HashMapGetCreate<String, List<String>> idsByPosition = new HashMapGetCreate<>(ids.size(), new HashMapGetCreate.ListFactory());
        for (int i = 0; i<ids.size(); ++i) idsByPosition.getAndCreateIfNecessary(positions.get(i)).add(ids.get(i));
        Selection res = Selection.generateSelection(selectionName, structureIdx, idsByPosition);
        logger.info("Generating selection: size: {} ({})", positions.size(), res.count());
        res.setIsDisplayingObjects(showObjects);
        res.setIsDisplayingTracks(showTracks);
        res.setHighlightingTracks(true);
        
        if (GUI.getDBConnection()==null || !GUI.getDBConnection().getDBName().equals(dbName)) {
            if (GUI.getDBConnection()!=null) logger.debug("current xp name : {} vs {}", GUI.getDBConnection().getDBName(), dbName);
            GUI.getInstance().setDBConnection(dbName, null);
            GUI.getInstance().setSelectedTab(2);
        }
        GUI.getDBConnection().getSelectionDAO().store(res);
        logger.debug("pop sels..");
        GUI.getInstance().populateSelections();
        logger.debug("sel sel..");
        GUI.getInstance().setSelectedSelection(res);
        if (openWholeSelection) {
            SelectionUtils.displaySelection(res, -2, structureDisplay);
        } else {
            GUI.getInstance().navigateToNextObjects(true, false, structureDisplay, interactiveStructure<0);
        }
        if (interactiveStructure>=0) GUI.getInstance().setInteractiveStructureIdx(interactiveStructure);
    }
    
}
