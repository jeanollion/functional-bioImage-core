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
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObjectProcessing;
import ij.process.AutoThresholder;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageInteger;
import image.ImageMask;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Collections;
import measurement.BasicMeasurements;
import plugins.Segmenter;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.trackers.ObjectIdxTracker;
import processing.ImageFeatures;
import processing.WatershedTransform;
import processing.mergeRegions.RegionCollection;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class BacteriaFluo implements Segmenter {
    public static boolean debug = false;
    NumberParameter splitThreshold = new BoundedNumberParameter("Split Threshold", 4, 0.15, 0, 1);
    NumberParameter minSize = new BoundedNumberParameter("Minimum size", 0, 50, 25, null);
    NumberParameter smoothScale = new BoundedNumberParameter("Smooth scale", 1, 3, 1, 5);
    NumberParameter dogScale = new BoundedNumberParameter("DoG scale", 0, 10, 5, null);
    NumberParameter hessianScale = new BoundedNumberParameter("Hessian scale", 1, 3, 1, 6);
    NumberParameter hessianThresholdFactor = new BoundedNumberParameter("Hessian threshold factor", 1, 1, 0, 5);
    Parameter[] parameters = new Parameter[]{splitThreshold, minSize, smoothScale, dogScale, hessianScale, hessianThresholdFactor};
    
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
    
    public ObjectPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        double fusionThreshold = splitThreshold.getValue().doubleValue()/10d;
        return run(input, parent.getMask(), fusionThreshold);
    }
    
    
    @Override
    public String toString() {
        return "Bacteria Fluo: " + Utils.toStringArray(parameters);
    }   
    
    public static ObjectPopulation run(Image input, ImageMask mask, double fusionThreshold) {
        double smoothScale = 3;
        double dogScale = 10; //15
        double hessianScale = 3;
        int minSize=100;
        double sigmaCoeff = 1;
        ImageFloat smoothed = ImageFeatures.gaussianSmooth(input, smoothScale, smoothScale, false).setName("smoothed");
        ImageFloat dog = ImageFeatures.differenceOfGaussians(smoothed, 0, dogScale, 1, false, false).setName("DoG");
        //Image hessian = ImageFeatures.getHessian(dog, hessianScale, false)[0].setName("hessian");
        
        
        double t0 = IJAutoThresholder.runThresholder(dog, mask, AutoThresholder.Method.Otsu, 0);
        ObjectPopulation pop1 = SimpleThresholder.run(dog, t0);
        pop1.filter(new ObjectPopulation.Thickness().setX(2).setY(2)); // remove thin objects
        pop1.filter(new ObjectPopulation.Size().setMin(minSize)); // remove small objects
        
        
        ImageOperations.trim(dog, 0, true, false);
        ImageOperations.normalize(dog, pop1.getLabelImage(), dog);
        //Image dogNorm = ImageOperations.multiply(dog, null, 100/BasicMeasurements.getPercentileValue(pop1.getObjects().get(0), 0.5, smoothed));
        Image hessian = ImageFeatures.getHessian(dog, hessianScale, false)[0].setName("hessian norm");
        ImageDisplayer disp=null;
        if (debug) {
            disp = new IJImageDisplayer();
            disp.showImage(hessian);
        }
        //pop1.keepOnlyLargestObject(); // for testing purpose -> TODO = loop
        ObjectPopulation res=null;
        ImageByte watershedMask = new ImageByte("", input);
        for (Object3D maskObject : pop1.getObjects()) {
            maskObject.draw(watershedMask, 1);
            double[] meanAndSigma = getMeanAndSigma(hessian, watershedMask, 0);
            //logger.debug("hessian mean: {}, sigma: {}, hessian thld: {}", meanAndSigma[0],meanAndSigma[1], sigmaCoeff * meanAndSigma[1]);
            ImageInteger seedMap = ImageOperations.threshold(hessian, sigmaCoeff * meanAndSigma[1], false, false, false, null);
            seedMap = ImageOperations.and(watershedMask, seedMap, seedMap).setName("seeds");
            //disp.showImage(seedMap);
            ObjectPopulation popWS = WatershedTransform.watershed(hessian, watershedMask, seedMap, false, null, new WatershedTransform.SizeFusionCriterion(minSize));
            popWS.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
            if (debug) disp.showImage(popWS.getLabelImage().duplicate("before merging"));
            
            ObjectPopulation localPop= RegionCollection.mergeHessianBacteria(popWS, dog, hessian, fusionThreshold);
            if (res==null) res= localPop;
            else res.addObjects(localPop.getObjects());
            maskObject.draw(watershedMask, 0);
        }
        res.sortBySpatialOrder(ObjectIdxTracker.IndexingOrder.YXZ);
        return res;
        
    }
    
    public static double[] getMeanAndSigma(Image hessian, ImageMask mask, double thld) {
        double mean = 0;
        double count = 0;
        double values2=0;
        double value;
        for (int z = 0; z<hessian.getSizeZ(); ++z) {
            for (int xy=0; xy<hessian.getSizeXY(); ++xy) {
                if (mask.insideMask(xy, z)) {
                    value = hessian.getPixel(xy, z);
                    if (value<=thld) {
                        mean+=value;
                        count++;
                        values2+=value*value;
                    }
                }
            }
        }
        if (count!=0) {
            mean/=count;
            values2/=count;
            return new double[]{mean, Math.sqrt(values2-mean*mean)};
        } else return new double[]{0, 0};
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
