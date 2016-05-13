/*
 * Copyright (C) 2015 jollion
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
package plugins.plugins.segmenters;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Voxel;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import image.ObjectFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import measurement.BasicMeasurements;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import plugins.Segmenter;
import plugins.SegmenterSplitAndMerge;
import plugins.plugins.manualSegmentation.WatershedObjectSplitter;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import processing.Filters;
import processing.ImageFeatures;
import processing.WatershedTransform;
import processing.mergeRegions.InterfaceCollection;
import processing.mergeRegions.RegionCollection;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriaFluo implements SegmenterSplitAndMerge, ManualSegmenter, ObjectSplitter {
    public static boolean debug = false;
    
    // configuration-related attributes
    NumberParameter openRadius = new BoundedNumberParameter("Open Radius", 1, 0, 0, null);
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.12, 0, 1);
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 100, 50, null);
    NumberParameter contactLimit = new BoundedNumberParameter("Contact Threshold with X border", 0, 10, 0, null);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 3, 1, 5);
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 40, 5, null);
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 4, 1, 6);
    NumberParameter hessianThresholdFactor = new BoundedNumberParameter("Hessian threshold factor", 1, 1, 0, 5);
    NumberParameter thresholdForEmptyChannel = new BoundedNumberParameter("Threshold for empty channel", 1, 2, 0, null);
    NumberParameter manualSegPropagationHessianThreshold = new BoundedNumberParameter("Manual Segmentation: Propagation NormedHessian Threshold", 3, 0.2, 0, null);
    Parameter[] parameters = new Parameter[]{splitThreshold, minSize, contactLimit, smoothScale, dogScale, hessianScale, hessianThresholdFactor, thresholdForEmptyChannel, openRadius, manualSegPropagationHessianThreshold};
    
    //segmentation-related attributes (kept for split and merge methods)
    ProcessingMaps maps;
    
    public BacteriaFluo setSplitThreshold(double splitThreshold) {
        this.splitThreshold.setValue(splitThreshold);
        return this;
    }
    public BacteriaFluo setMinSize(int minSize) {
        this.minSize.setValue(minSize);
        return this;
    }
    public BacteriaFluo setSmoothScale(double smoothScale) {
        this.smoothScale.setValue(smoothScale);
        return this;
    }
    public BacteriaFluo setDogScale(int dogScale) {
        this.dogScale.setValue(dogScale);
        return this;
    }
    public BacteriaFluo setHessianScale(double hessianScale) {
        this.hessianScale.setValue(hessianScale);
        return this;
    }
    public BacteriaFluo setHessianThresholdFactor(double hessianThresholdFactor) {
        this.hessianThresholdFactor.setValue(hessianThresholdFactor);
        return this;
    }
    public BacteriaFluo setOpenRadius(double openRadius) {
        this.openRadius.setValue(openRadius);
        return this;
    }
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double fusionThreshold = splitThreshold.getValue().doubleValue();
        return run(input, parent.getMask(), fusionThreshold, minSize.getValue().intValue(), contactLimit.getValue().intValue(), smoothScale.getValue().doubleValue(), dogScale.getValue().doubleValue(), hessianScale.getValue().doubleValue(), hessianThresholdFactor.getValue().doubleValue(), thresholdForEmptyChannel.getValue().doubleValue(), openRadius.getValue().doubleValue(), this);
    }
    
    @Override
    public String toString() {
        return "Bacteria Fluo: " + Utils.toStringArray(parameters);
    }   
    private void initializeMaps(Image input) {
        maps = new ProcessingMaps(input, this.splitThreshold.getValue().doubleValue(), dogScale.getValue().doubleValue(), smoothScale.getValue().doubleValue(), hessianScale.getValue().doubleValue());
    }
    
    
    public static ObjectPopulation run(Image input, ImageMask mask, double fusionThreshold, int minSize, int contactLimit, double smoothScale, double dogScale, double hessianScale, double hessianThresholdFactor, double thresholdForEmptyChannel, double openRadius, BacteriaFluo instance) {
        ImageDisplayer disp=debug?new IJImageDisplayer():null;
        //double hessianThresholdFacto = 1;
        ProcessingMaps maps = new ProcessingMaps(input, fusionThreshold, dogScale, smoothScale, hessianScale);
        if (instance!=null) instance.maps=maps;
        Image smoothed = maps.intensityMap;
        //Image hessian = ImageFeatures.getHessian(intensityMap, hessianScale, false)[0].setName("hessian");

        //double t0 = IJAutoThresholder.runThresholder(intensityMap, mask, null, AutoThresholder.Method.Otsu, 0);
        double threshold = IJAutoThresholder.runThresholder(smoothed, mask, null, AutoThresholder.Method.Otsu, 0);
        
        // criterion for empty channel: 
        double[] musigmaOver = getMeanAndSigma(smoothed, mask, 0, true);
        double[] musigmaUnder = getMeanAndSigma(smoothed, mask, 0, false);
        if (musigmaOver[2]==0 || musigmaUnder[2]==0) return new ObjectPopulation(input);
        else {            
            if (musigmaOver[0] - musigmaUnder[0]<thresholdForEmptyChannel) return new ObjectPopulation(input);
        }
        ObjectPopulation pop1 = SimpleThresholder.run(smoothed, 0);
        if (openRadius>=1) {
            for (Object3D o : pop1.getObjects()) {
                ImageInteger m = Filters.binaryOpen(o.getMask(), null, Filters.getNeighborhood(openRadius, openRadius, o.getMask()));
                o.setMask(m);
            }
            pop1.relabel();
            pop1 = new ObjectPopulation(pop1.getLabelMap(), false);
        }
        pop1.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        pop1.filter(new ObjectPopulation.Size().setMin(minSize)); // remove small objects
        
        
        if (debug) logger.debug("threhsold: {}", threshold);
        pop1.filter(new ObjectPopulation.MeanIntensity(threshold, true, smoothed));
        if (debug) disp.showImage(pop1.getLabelMap().duplicate("first seg"));
        
        Image hessian = maps.hessian;
        
        if (debug) {
            disp.showImage(smoothed);
            //disp.showImage(log);
            disp.showImage(hessian);
        }
        //pop1.keepOnlyLargestObject(); // for testing purpose -> TODO = loop
        ObjectPopulation res=null;
        ImageByte watershedMask = new ImageByte("", input);
        for (Object3D maskObject : pop1.getObjects()) {
            maskObject.draw(watershedMask, 1);
            double[] meanAndSigma = getMeanAndSigma(hessian, watershedMask, 0, false); // mean & sigma < 0
            //logger.debug("hessian mean: {}, sigma: {}, hessian thld: {}", meanAndSigma[0],meanAndSigma[1], sigmaCoeff * meanAndSigma[1]);
            ImageInteger seedMap = ImageOperations.threshold(hessian, hessianThresholdFactor * meanAndSigma[1], false, false, false, null);
            seedMap = ImageOperations.and(watershedMask, seedMap, seedMap).setName("seeds");
            //disp.showImage(seedMap);
            ObjectPopulation popWS = WatershedTransform.watershed(hessian, watershedMask, seedMap, false, null, new WatershedTransform.SizeFusionCriterion(minSize));
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            if (debug) disp.showImage(popWS.getLabelMap().duplicate("before local threshold & merging"));
            //popWS.localThreshold(dogNoTrim, 0, localThresholdMargin, 0);
            //if (debug) disp.showImage(popWS.getLabelImage().duplicate("after local threhsold / before merging"));
            RegionCollection.verbose=debug;
            ObjectPopulation localPop= RegionCollection.mergeHessianBacteria(popWS, input, hessian, fusionThreshold, true);
            if (res==null) res= localPop;
            else res.addObjects(localPop.getObjects());
            //if (debug) disp.showImage(localPop.getLabelImage().setName("after merging"));
            maskObject.draw(watershedMask, 0);
        }
        if (res!=null) {
            if (contactLimit>0) res.filter(new ObjectPopulation.ContactBorder(contactLimit, mask, ObjectPopulation.ContactBorder.Border.YDown));
            res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        }
        return res;
    }
    
    public static double[] getMeanAndSigma(Image image, ImageMask mask, double thld, boolean overThreshold) {
        double mean = 0;
        double count = 0;
        double values2 = 0;
        double value;
        for (int z = 0; z < image.getSizeZ(); ++z) {
            for (int xy = 0; xy < image.getSizeXY(); ++xy) {
                if (mask.insideMask(xy, z)) {
                    value = image.getPixel(xy, z);
                    if ((overThreshold && value>=thld) || (!overThreshold && value <= thld)) {
                        mean += value;
                        count++;
                        values2 += value * value;
                    }
                }
            }
        }
        if (count != 0) {
            mean /= count;
            values2 /= count;
            return new double[]{mean, Math.sqrt(values2 - mean * mean), count};
        } else {
            return new double[]{0, 0, 0};
        }
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    @Override public double split(Object3D o, List<Object3D> result) {
        if (maps==null) throw new Error("Segment method have to be called before split method in order to initialize maps");
        if (maps.splitMask==null) maps.splitMask = new ImageByte("split mask", maps.intensityMap);
        o.draw(maps.splitMask, 1);
        ObjectPopulation pop = WatershedObjectSplitter.splitInTwo(maps.intensityMap, maps.splitMask, true);
        o.draw(maps.splitMask, 0);
        if (pop==null || pop.getObjects().isEmpty() || pop.getObjects().size()==1) return Double.NaN;
        ArrayList<Object3D> remove = new ArrayList<Object3D>(pop.getObjects().size());
        pop.filter(new ObjectPopulation.Thickness().setX(2).setY(2), remove); // remove thin objects
        pop.filter(new ObjectPopulation.Size().setMin(minSize.getValue().intValue()), remove); // remove small objects
        if (pop.getObjects().size()<=1) return Double.NaN;
        else {
            if (!remove.isEmpty()) pop.mergeWithConnected(remove);
            Object3D o1 = pop.getObjects().get(0);
            Object3D o2 = pop.getObjects().get(1);
            result.add(o1);
            result.add(o2);
            return maps.splitThresholdValue-getInterfaceValue(getInterface(o1, o2));
        }
    }

    @Override public double computeMergeCost(List<Object3D> objects) {
        if (maps==null || maps.intensityMap==null || maps.hessian==null || maps.rawIntensityMap==null) throw new Error("Segment method have to be called before merge method in order to initialize images");
        if (maps.splitMask==null) maps.splitMask = new ImageByte("split mask", maps.intensityMap);
        if (objects.isEmpty() || objects.size()==1) return 0;
        Iterator<Object3D> it = objects.iterator();
        Object3D ref  = objects.get(0);
        double maxCost = Double.MIN_VALUE;
        while (it.hasNext()) { //first round : remove objects not connected with ref & compute interactions with ref objects
            Object3D n = it.next();
            if (n!=ref) {
                ArrayList<Voxel> inter = getInterface(ref, n);
                if (inter.isEmpty()) it.remove();
                else {
                    double c = getInterfaceValue(inter);
                    if (c>maxCost) maxCost = c;
                }
            }
        }
        for (int i = 2; i<objects.size()-1; ++i) { // second round compute other interactions
            for (int j = i+1; j<objects.size(); ++j) {
                ArrayList<Voxel> inter = getInterface(objects.get(i), objects.get(j));
                if (!inter.isEmpty()) {
                    double c = getInterfaceValue(inter);
                    if (c>maxCost) maxCost = c;
                }
            }
        }
        if (maxCost==Double.MIN_VALUE) return Double.NaN;
        return maxCost-maps.splitThresholdValue;
    }
    private double getInterfaceValue(ArrayList<Voxel> inter) {
        double meanHess = BasicMeasurements.getMeanValue(inter, maps.hessian, false);
        double meanDOG = BasicMeasurements.getMeanValue(inter, maps.rawIntensityMap, false);
        return meanHess / meanDOG;
    }
    
    private ArrayList<Voxel> getInterface(Object3D o1, Object3D o2) {
        o1.draw(maps.splitMask, o1.getLabel());
        o2.draw(maps.splitMask, o2.getLabel());
        ArrayList<Voxel> inter = InterfaceCollection.getInteface(o1, o2, maps.splitMask);
        o1.draw(maps.splitMask, 0);
        o2.draw(maps.splitMask, 0);
        return inter;
    }
    
    // manual correction implementations
    private boolean verboseManualSeg;
    public void setManualSegmentationVerboseMode(boolean verbose) {
        this.verboseManualSeg=verbose;
    }

    public ObjectPopulation manualSegment(Image input, StructureObject parent, ImageMask segmentationMask, int structureIdx, List<int[]> seedsXYZ) {
        if (maps==null) initializeMaps(input);
        List<Object3D> seedObjects = ObjectFactory.createObjectsFromSeeds(seedsXYZ, input.getScaleXY(), input.getScaleZ());
        ObjectPopulation pop =  WatershedTransform.watershed(maps.hessian, segmentationMask, seedObjects, false, new WatershedTransform.ThresholdPropagation(maps.getNormalizedHessian(), this.manualSegPropagationHessianThreshold.getValue().doubleValue(), false), new WatershedTransform.SizeFusionCriterion(this.minSize.getValue().intValue()));
        
        if (verboseManualSeg) {
            Image seedMap = new ImageByte("seeds from: "+input.getName(), input);
            for (int[] seed : seedsXYZ) seedMap.setPixel(seed[0], seed[1], seed[2], 1);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(seedMap);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(maps.hessian);
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(maps.getNormalizedHessian().setName("NormalizedHessian: for propagation limit"));
            ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(pop.getLabelMap().setName("segmented from: "+input.getName()));
        }
        
        return pop;
    }

    public ObjectPopulation splitObject(Image input, Object3D object) {
        Image hessian=ImageFeatures.getHessian(input, hessianScale.getValue().doubleValue(), false)[0].setName("hessian");
        return WatershedObjectSplitter.splitInTwo(hessian, object.getMask(), true);
    }
    private static class ProcessingMaps {
        Image hessian;
        Image rawIntensityMap;
        Image intensityMap;
        Image normalizedHessian;
        ImageByte splitMask;
        double splitThresholdValue, smoothScale;
        private ProcessingMaps(Image input, double splitThreshold, double dogScale, double smoothScale, double hessianScale) {
            rawIntensityMap=input;
            splitThresholdValue=splitThreshold;
            this.smoothScale=smoothScale;
            ImageFloat dog = ImageFeatures.differenceOfGaussians(input, 0, dogScale, 1, false).setName("DoG");
            intensityMap= Filters.median(dog, dog, Filters.getNeighborhood(smoothScale, smoothScale, input)).setName("DoG+Smoothed");
            hessian=ImageFeatures.getHessian(input, hessianScale, false)[0].setName("hessian");
        }
        
        private Image getNormalizedHessian() {
            if (normalizedHessian==null) {
                Image gauss = ImageFeatures.gaussianSmooth(rawIntensityMap, smoothScale, smoothScale*rawIntensityMap.getScaleXY()/rawIntensityMap.getScaleZ(), false);
                normalizedHessian=ImageOperations.divide(hessian, gauss, null).setName("NormalizedHessian");
            } 
            return normalizedHessian;
        }
    }
}
