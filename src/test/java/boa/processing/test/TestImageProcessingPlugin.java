/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.processing.test;

import boa.core.Processor;
import boa.core.Task;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.gui.GUI;
import static boa.gui.PluginConfigurationUtils.displayIntermediateImages;
import static boa.gui.PluginConfigurationUtils.testImageProcessingPlugin;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.plugins.PluginFactory;
import boa.plugins.ProcessingScheme;
import boa.plugins.TestableProcessingPlugin;
import static boa.processing.test.TestTracker.trackPrefilterRange;
import static boa.test_utils.TestUtils.logger;
import boa.utils.Utils;
import ij.ImageJ;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Jean Ollion
 */
public class TestImageProcessingPlugin {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        String dbName = "MF1_180509";
        //String dbName = "fluo160501_uncorr_TestParam";
        //String dbName = "MutH_151220";
        //String dbName = "WT_150616";
        //String dbName = "WT_180318_Fluo";
        int pIdx =1;
        int mcIdx =6;
        int structureIdx =1;
        boolean segAndTrack = true;
        int[] frames = new int[]{422,422}; //{215, 237};
        //BacteriaClosedMicrochannelTrackerLocalCorrections.bactTestFrame=4;
        if (new Task(dbName).getDir()==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GUI.getInstance().setDBConnection(dbName, new Task(dbName).getDir(), true); // so that manual correction shortcuts work
        MasterDAO db = GUI.getDBConnection();
        ImageWindowManagerFactory.getImageManager().setDisplayImageLimit(1000);
        ProcessingScheme ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        ObjectDAO dao = db.getDao(db.getExperiment().getPosition(pIdx).getName());
        List<StructureObject> roots = Processor.getOrCreateRootTrack(dao);
        int parentSIdx = dao.getExperiment().getStructure(structureIdx).getParentStructure();
        List<StructureObject> parentTrack=null;
        if (parentSIdx==-1) {
            parentTrack = roots;
            roots.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
        }
        else {
            parentTrack = Utils.getFirst(StructureObjectUtils.getAllTracks(roots, parentSIdx), o->o.getIdx()==mcIdx&& o.getFrame()<=frames[1]);
            parentTrack.removeIf(o -> o.getFrame()<frames[0] || o.getFrame()>frames[1]);
        }
        
        Map<StructureObject, TestableProcessingPlugin.TestDataStore> stores = testImageProcessingPlugin(ps.getSegmenter(), db.getExperiment(), structureIdx, parentTrack, segAndTrack);
        if (stores!=null) displayIntermediateImages(stores, structureIdx);
    }
}