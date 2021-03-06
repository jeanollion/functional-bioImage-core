/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.processing.test;

import static boa.test_utils.TestUtils.logger;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.ImageDisplayer;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.core.Task;
import boa.configuration.experiment.Position;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.TrackPostFilterSequence;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.ImageJ;
import boa.image.Image;
import java.util.ArrayList;
import java.util.Arrays;
import boa.plugins.PluginFactory;
import boa.plugins.Segmenter;
import boa.plugins.TrackPostFilter;
import boa.plugins.plugins.segmenters.MicrochannelPhase2D;
import boa.plugins.plugins.track_post_filter.AverageMask;
import boa.plugins.plugins.track_post_filter.RemoveTracksStartingAfterFrame;
import boa.plugins.plugins.track_post_filter.TrackLengthFilter;
import boa.plugins.ProcessingPipeline;
import boa.plugins.ProcessingPipelineWithTracking;
import boa.plugins.plugins.track_post_filter.FitRegionsToEdges;

/**
 *
 * @author Jean Ollion
 */
public class TestProcessMicrochannelsPhase {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        int frame =1;
        int pos = 9;
        //String dbName = "TestThomasRawStacks";
        //String dbName = "AyaWT_mmglu";
        //String dbName = "170919_thomas";
        //String dbName = "MutH_150324";
        //String dbName = "MutH_140115";
        //String dbName = "WT_150616";
        //String dbName = "Aya_180315";
        //String dbName = "WT_180318_Fluo";
        String dbName = "WT_mopsara_180320";
        FitRegionsToEdges.debugLabel=-1;
        MicrochannelPhase2D.debugIdx=0;
        testSegMicrochannelsFromXP(dbName, pos, frame);
        //testPostProcessTracking(dbName, pos, frame);
    }
    
    public static void testSegMicrochannelsFromXP(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO =new Task(dbName).getDB();
        Position f = mDAO.getExperiment().getPosition(fieldNumber);
        
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        if (root==null) root = f.createRootObjects(mDAO.getDao(f.getName())).get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        root = StructureObjectUtils.duplicateRootTrackAndChangeDAO(false, root).get(root.getId());
        ArrayList<StructureObject> parentTrack = new ArrayList<>(); parentTrack.add(root);
        Image input = root.getRawImage(0);
        MicrochannelPhase2D.debug=true;
        //MicroChannelPhase2D seg = new MicroChannelPhase2D().setyStartAdjustWindow(5);
        Segmenter s = mDAO.getExperiment().getStructure(0).getProcessingScheme().getSegmenter();
        mDAO.getExperiment().getStructure(0).getProcessingScheme().getTrackPreFilters(true).filter(0, parentTrack);
        ImageWindowManagerFactory.showImage(root.getRawImage(0).duplicate("raw images"));
        ImageWindowManagerFactory.showImage(root.getPreFilteredImage(0).duplicate("pre-Filtered images"));
        RegionPopulation pop = s.runSegmenter(root.getPreFilteredImage(0), 0, root);
        root.setChildrenObjects(pop, 0);
        logger.debug("{} objects found", pop.getRegions().size());
        FitRegionsToEdges.debug=true;
        if (mDAO.getExperiment().getStructure(0).getProcessingScheme() instanceof ProcessingPipelineWithTracking) {
            TrackPostFilterSequence tpf = ((ProcessingPipelineWithTracking)mDAO.getExperiment().getStructure(0).getProcessingScheme()).getTrackPostFilters();
            for (PluginParameter<TrackPostFilter> pp : tpf.getChildren()) if (pp.instanciatePlugin() instanceof TrackLengthFilter || pp.instanciatePlugin() instanceof AverageMask || pp.instanciatePlugin() instanceof RemoveTracksStartingAfterFrame) pp.setActivated(false);
            tpf.filter(0, Arrays.asList(new StructureObject[]{root}));
            pop = root.getChildRegionPopulation(0);
        }
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        ImageWindowManagerFactory.showImage(input);
        ImageWindowManagerFactory.showImage(pop.getLabelMap());
        logger.debug("{} objects found after post-filters", pop.getRegions().size());
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
    
    public static void testPostProcessTracking(String dbName, int fieldNumber, int timePoint) {
        MasterDAO mDAO =new Task(dbName).getDB();
        Position f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoot(timePoint);
        if (root==null) root = f.createRootObjects(mDAO.getDao(f.getName())).get(timePoint);
        logger.debug("field name: {}, root==null? {}", f.getName(), root==null);
        Image input = root.getRawImage(0);
        ImageWindowManagerFactory.showImage(input);
        RegionPopulation pop = root.getChildRegionPopulation(0);
        ImageWindowManagerFactory.showImage(pop.getLabelMap());
        FitRegionsToEdges.debug=true;
        if (mDAO.getExperiment().getStructure(0).getProcessingScheme() instanceof ProcessingPipelineWithTracking) {
            ((ProcessingPipelineWithTracking)mDAO.getExperiment().getStructure(0).getProcessingScheme()).getTrackPostFilters().filter(0, Arrays.asList(new StructureObject[]{root}));
            pop = root.getChildRegionPopulation(0);
        } else return;
        //ObjectPopulation pop = MicroChannelFluo2D.run2(input, 355, 40, 20);
        
        ImageWindowManagerFactory.showImage(pop.getLabelMap());
        logger.debug("{} objects found", pop.getRegions().size());
        // test split
        //ObjectPopulation popSplit = testObjectSplitter(intensityMap, pop.getChildren().get(0));
        //disp.showImage(popSplit.getLabelImage());
    }
    
}
