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
package boa.gui.imageInteraction;

import boa.gui.GUI;
import static boa.gui.GUI.logger;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.plugin.filter.MaximumFinder;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.processing.ImageOperations;
import static boa.image.processing.ImageOperations.pasteImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public class TrackMaskY extends TrackMask {
    int maxParentX, maxParentZ;
    public TrackMaskY(List<StructureObject> parentTrack, int childStructureIdx) {
        this(parentTrack, childStructureIdx, false);
    }
    public TrackMaskY(List<StructureObject> parentTrack, int childStructureIdx, boolean middleXZ) {
        super(parentTrack, childStructureIdx);
        int maxX=0, maxZ=0;
        for (int i = 0; i<parentTrack.size(); ++i) { // compute global Y and Z max to center parent masks
            if (maxX<parentTrack.get(i).getObject().getBounds().getSizeX()) maxX=parentTrack.get(i).getObject().getBounds().getSizeX();
            if (maxZ<parentTrack.get(i).getObject().getBounds().getSizeZ()) maxZ=parentTrack.get(i).getObject().getBounds().getSizeZ();
        }
        maxParentX=maxX;
        maxParentZ=maxZ;
        logger.trace("track mask image object: max parent X-size: {} z-size: {}", maxParentX, maxParentZ);
        int currentOffsetY=0;
        for (int i = 0; i<parentTrack.size(); ++i) {
            trackOffset[i] = parentTrack.get(i).getBounds().duplicate().translateToOrigin(); 
            if (middleXZ) trackOffset[i].translate((int)(0.5+maxParentX/2.0-trackOffset[i].getSizeX()/2.0), currentOffsetY , (int)(0.5+maxParentZ/2.0-trackOffset[i].getSizeZ()/2.0)); // Y & Z middle of parent track
            else trackOffset[i].translate(0, currentOffsetY, 0); // X & Z up of parent track
            trackObjects[i] = new StructureObjectMask(parentTrack.get(i), childStructureIdx, trackOffset[i]);
            currentOffsetY+=interval+trackOffset[i].getSizeY();
            logger.trace("current index: {}, current bounds: {} current offsetX: {}", i, trackOffset[i], currentOffsetY);
        }
        for (StructureObjectMask m : trackObjects) m.getObjects();
    }
    
    
    @Override
    public Pair<StructureObject, BoundingBox> getClickedObject(int x, int y, int z) {
        if (is2D()) z=0; //do not take in account z in 2D case.
        // recherche du parent: 
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(0, 0, y, y, 0, 0), new bbComparatorY());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("getClicked object: index: {}, parent: {}, #children: {}", i, i>=0?trackObjects[i]:"", i>=0? trackObjects[i].getObjects().size():"");
        if (i>=0 && trackOffset[i].containsWithOffset(x, y, z)) return trackObjects[i].getClickedObject(x, y, z);
        else return null;
    }
    
    @Override
    public void addClickedObjects(BoundingBox selection, List<Pair<StructureObject, BoundingBox>> list) {
        if (is2D() && selection.getSizeZ()>0) selection=new BoundingBox(selection.getxMin(), selection.getxMax(), selection.getyMin(), selection.getyMax(), 0, 0);
        int iMin = Arrays.binarySearch(trackOffset, new BoundingBox(0, 0, selection.getyMin(), selection.getyMin(), 0, 0), new bbComparatorY());
        if (iMin<0) iMin=-iMin-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        int iMax = Arrays.binarySearch(trackOffset, new BoundingBox(0, 0, selection.getyMax(), selection.getyMax(), 0, 0), new bbComparatorY());
        if (iMax<0) iMax=-iMax-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        //logger.debug("looking for objects from time: {} to time: {}", iMin, iMax);
        for (int i = iMin; i<=iMax; ++i) trackObjects[i].addClickedObjects(selection, list);
    }
    
    @Override
    public int getClosestFrame(int x, int y) {
        int i = Arrays.binarySearch(trackOffset, new BoundingBox(0, 0, y, y, 0, 0), new bbComparatorY());
        if (i<0) i=-i-2; // element inférieur à x puisqu'on compare les xmin des bounding box
        return trackObjects[i].parent.getFrame();
    }

    @Override
    public ImageInteger generateLabelImage() {
        int maxLabel = 0; 
        for (StructureObjectMask o : trackObjects) {
            int label = o.getMaxLabel();
            if (label>maxLabel) maxLabel = label;
        }
        String structureName;
        if (GUI.hasInstance() && GUI.getDBConnection()!=null && GUI.getDBConnection().getExperiment()!=null) structureName = GUI.getDBConnection().getExperiment().getStructure(childStructureIdx).getName(); 
        else structureName= childStructureIdx+"";
        final ImageInteger displayImage = ImageInteger.createEmptyLabelImage("Track: Parent:"+parents+" Segmented Image of: "+structureName, maxLabel, new BlankMask( this.maxParentX, trackOffset[trackOffset.length-1].getyMax()+1, this.maxParentZ).setCalibration(parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
        drawObjects(displayImage);
        return displayImage;
    }
    @Override 
    public Image generateEmptyImage(String name, Image type) {
        return Image.createEmptyImage(name, type, new BlankMask( this.maxParentX, trackOffset[trackOffset.length-1].getyMax()+1, Math.max(type.getSizeZ(), this.maxParentZ)).setCalibration(parents.get(0).getMaskProperties().getScaleXY(), parents.get(0).getMaskProperties().getScaleZ()));
    }
    
    
    class bbComparatorY implements Comparator<BoundingBox>{
        @Override
        public int compare(BoundingBox arg0, BoundingBox arg1) {
            return Integer.compare(arg0.getyMin(), arg1.getyMin());
        }
        
    }
}
