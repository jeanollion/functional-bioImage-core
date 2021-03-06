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
package boa.plugins.plugins.processing_pipeline;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ParentObjectClassParameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import static boa.plugins.Plugin.logger;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.Segmenter;
import boa.plugins.ToolTip;
import boa.plugins.TrackPostFilter;
import boa.plugins.TrackPreFilter;
import boa.plugins.Tracker;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import boa.plugins.ProcessingPipeline;
import boa.plugins.ProcessingPipelineWithTracking;

/**
 *
 * @author Jean Ollion
 */
public class Duplicate extends SegmentationAndTrackingProcessingPipeline<Duplicate> implements  ToolTip {
    protected PluginParameter<Tracker> tracker = new PluginParameter<>("Tracker", Tracker.class, true);
    ParentObjectClassParameter dup = new ParentObjectClassParameter("Duplicate From").setAllowNoSelection(false);
    protected Parameter[] parameters = new Parameter[]{dup, preFilters, trackPreFilters, tracker, trackPostFilters};

    @Override
    public String getToolTipText() {
        return "Duplicates the segmented objects of another Structure. Tracker and post-filter can be applied. If no tracker is set, source lineage is also duplicated";
    }
    public Tracker getTracker() {return tracker.instanciatePlugin();}
    @Override
    public Duplicate addPostFilters(PostFilter... postFilter) {
        throw new IllegalArgumentException("No post filters allowed for duplicate processing scheme");
    }
    @Override public Duplicate addPostFilters(Collection<PostFilter> postFilter){
        throw new IllegalArgumentException("No post filters allowed for duplicate processing scheme");
    }
    @Override
    public Segmenter getSegmenter() {
        return null;
    }
    @Override
    public void segmentAndTrack(final int structureIdx, final List<StructureObject> parentTrack) {
        segmentOnly(structureIdx, parentTrack);
        trackOnly(structureIdx, parentTrack);
        trackPostFilters.filter(structureIdx, parentTrack);
    }
    @Override
    public void trackOnly(final int structureIdx, List<StructureObject> parentTrack) {
        if (!tracker.isOnePluginSet()) {
            logger.info("No tracker set for structure: {}", structureIdx);
            return;
        }
        for (StructureObject parent : parentTrack) {
            for (StructureObject c : parent.getChildren(structureIdx)) c.resetTrackLinks(true, true);
        }
        Tracker t = tracker.instanciatePlugin();
        t.track(structureIdx, parentTrack);
        
    }
    public void segmentOnly(final int structureIdx, final List<StructureObject> parentTrack) {
        if (parentTrack.isEmpty()) return;
        int parentStorage = parentTrack.get(0).getExperiment().getStructure(structureIdx).getParentStructure();
        if (dup.getSelectedClassIdx()<0) throw new IllegalArgumentException("No selected structure to duplicate");
        logger.debug("dup: {} dup parent: {}, parentTrack: {}", dup.getSelectedClassIdx(), dup.getParentObjectClassIdx(), parentTrack.get(0).getStructureIdx());
        //if (dup.getParentStructureIdx()!=parentTrack.get(0).getStructureIdx() && dup.getSelectedStructureIdx()!=parentTrack.get(0).getStructureIdx()) throw new IllegalArgumentException("Parent Structure should be the same as duplicated's parent strucutre");
        Stream<StructureObject> dupStream = dup.getSelectedClassIdx() == parentTrack.get(0).getStructureIdx() ? parentTrack.stream() : StructureObjectUtils.getAllChildrenAsStream(parentTrack.stream(), dup.getSelectedClassIdx());
        dupStream = dupStream.parallel();
        Map<StructureObject, StructureObject> dupMap = dupStream.collect(Collectors.toMap(s->s, s->s.duplicate(true, true, false)));
        logger.debug("duplicate for parentTrack: {} structure: {}: #{}objects", parentTrack.get(0), structureIdx, dupMap.size());
        // set trackHead, next & prev ids + structureIdx
        Field sIdx;
        try {
            sIdx = StructureObject.class.getDeclaredField("structureIdx");
            sIdx.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            throw new RuntimeException(e);
            
        }
        dupMap.entrySet().stream().forEach(e->{
            if (e.getKey().getNext()!=null) e.getValue().setNext(dupMap.get(e.getKey().getNext()));
            if (e.getKey().getPrevious()!=null) e.getValue().setPrevious(dupMap.get(e.getKey().getPrevious()));
            if (e.getKey().getTrackHead()!=null) e.getValue().setTrackHead(dupMap.get(e.getKey().getTrackHead()), false);
            // set structureIdx using reflexion
            try {
                sIdx.set(e.getValue(), structureIdx);
            } catch(IllegalAccessException | IllegalArgumentException ex) {
                throw new RuntimeException(ex);
            }
        });
        // set to parents : collect by parent and set to parent. set parent will also set parent && parent track head Id to each object
        if (parentStorage == parentTrack.get(0).getStructureIdx() && dup.getSelectedClassIdx() == parentStorage) { // simply store each object into parent
            dupMap.entrySet().forEach(e->e.getKey().setChildren(new ArrayList<StructureObject>(1){{add(e.getValue());}}, structureIdx));
        } else { // group by parent & store
            dupMap.entrySet().stream().collect(Collectors.groupingBy(e->e.getKey().getParent(parentStorage))).entrySet().stream().forEach(p->{
                p.getKey().setChildren(p.getValue().stream().map(e->e.getValue()).collect(Collectors.toList()), structureIdx); 
            });
        }
        getTrackPreFilters(true).filter(structureIdx, parentTrack);
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
}
