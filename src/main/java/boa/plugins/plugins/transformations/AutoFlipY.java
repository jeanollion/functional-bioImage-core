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
package boa.plugins.plugins.transformations;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import boa.image.ThresholdMask;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import boa.plugins.SimpleThresholder;
import boa.plugins.Transformation;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import static boa.plugins.plugins.transformations.AutoFlipY.AutoFlipMethod.FLUO;
import static boa.plugins.plugins.transformations.AutoFlipY.AutoFlipMethod.FLUO_HALF_IMAGE;
import boa.image.processing.ImageTransformation;
import boa.plugins.ToolTip;
import static boa.plugins.plugins.transformations.AutoFlipY.AutoFlipMethod.PHASE;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class AutoFlipY implements Transformation, ToolTip {

    
    public static enum AutoFlipMethod {
        FLUO("Bacteria Fluo", "Detects side where bacteria are more aligned -> should be the upper side"),
        FLUO_HALF_IMAGE("Bacteria Fluo: Upper Half of Image", "Bacteria should be present in upper half of the image"),
        PHASE("Phase Contrast Optical Aberration", "Optical Aberration is detected and side where variance along X axis is maximal is selected OR if the optical aberration is closer to one side on the image than microchannel height the other side is selected");
        final String name;
        final String toolTip;
        AutoFlipMethod(String name, String toolTip) {
            this.name=name;
            this.toolTip=toolTip;
        }
        public static AutoFlipMethod getMethod(String name) {
            for (AutoFlipMethod m : AutoFlipMethod.values()) if (m.name.equals(name)) return m;
            return null;
        }
    }
    String toolTip = "Methods for flipping image along Y-axis in order to set the close-end of channel at the top of the image. Should be set after the rotation";
    ChoiceParameter method = new ChoiceParameter("Method", Utils.transform(AutoFlipMethod.values(), new String[AutoFlipMethod.values().length], f->f.name), FLUO_HALF_IMAGE.name, false);
    PluginParameter<SimpleThresholder> fluoThld = new PluginParameter<>("Threshold for bacteria Segmentation", SimpleThresholder.class, new BackgroundThresholder(4, 5, 3), false); 
    NumberParameter minObjectSize = new BoundedNumberParameter("Minimal Object Size", 1, 100, 10, null).setToolTipText("Object under this size (in pixels) will be removed");
    NumberParameter microchannelLength = new BoundedNumberParameter("Microchannel Length", 0, 400, 100, null).setToolTipText("Typical Microchannel Length");
    ConditionalParameter cond = new ConditionalParameter(method).setActionParameters("Bacteria Fluo", new Parameter[]{fluoThld, minObjectSize}).setActionParameters("Phase Contrast Optical Aberration", new Parameter[]{microchannelLength});
    Boolean flip = null;
    public AutoFlipY() {
        cond.addListener(p->{ 
            AutoFlipMethod m = AutoFlipMethod.getMethod(method.getSelectedItem());
            if (m!=null) cond.setToolTipText(m.toolTip);
            else cond.setToolTipText("Choose autoflip algorithm");
        });
    }
    @Override
    public String getToolTipText() {
        return toolTip;
    }
    public AutoFlipY setMethod(AutoFlipMethod method) {
        this.method.setValue(method.name);
        return this;
    }
    List<Image> upperObjectsTest, lowerObjectsTest;
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) throws Exception {
        flip=null;
        if (method.getSelectedItem().equals(FLUO.name)) { 
            if (testMode) {
                upperObjectsTest=new ArrayList<>();
                lowerObjectsTest=new ArrayList<>();
            }
            // rough segmentation and get side where cells are better aligned
            List<Integer> frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, 5);
            int countFlip = 0;
            int countNoFlip = 0;
            for (int f: frames) {
                Image<? extends Image> image = inputImages.getImage(channelIdx, f);
                if (image.getSizeZ()>1) {
                    int plane = inputImages.getBestFocusPlane(f);
                    if (plane<0) throw new RuntimeException("AutoFlip can only be run on 2D images AND no autofocus algorithm was set");
                    image = image.splitZPlanes().get(plane);
                }
                Boolean flip = isFlipFluo(image);
                if (flip!=null) {
                    if (flip) ++countFlip;
                    else ++countNoFlip;
                }
            }
            if (testMode) {
                ImageWindowManagerFactory.showImage(Image.mergeZPlanes(upperObjectsTest).setName("Upper Objects"));
                ImageWindowManagerFactory.showImage(Image.mergeZPlanes(lowerObjectsTest).setName("Lower Objects"));
                upperObjectsTest.clear();
                lowerObjectsTest.clear();
            }
            flip = countFlip>countNoFlip;
            logger.debug("AutoFlipY: {} (flip:{} vs:{})", flip, countFlip, countNoFlip);
        } else if (method.getSelectedItem().equals(FLUO_HALF_IMAGE.name)) { 
            // compares signal in upper half & lower half -> signal should be in upper half
            List<Integer> frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, 1);
            int countFlip = 0;
            int countNoFlip = 0;
            for (int f: frames) {
                Image<? extends Image> image = inputImages.getImage(channelIdx, f);
                if (image.getSizeZ()>1) {
                    int plane = inputImages.getBestFocusPlane(f);
                    if (plane<0) throw new RuntimeException("AutoFlip can only be run on 2D images AND no autofocus algorithm was set");
                    image = image.splitZPlanes().get(plane);
                }
                Boolean flip = isFlipFluoUpperHalf(image);
                if (flip!=null) {
                    if (flip) ++countFlip;
                    else ++countNoFlip;
                }
            }
            flip = countFlip>countNoFlip;
            logger.debug("AutoFlipY: {} (flip:{} vs:{})", flip, countFlip, countNoFlip);
        } else if (method.getSelectedItem().equals(PHASE.name)) {
            int length = microchannelLength.getValue().intValue();
            Image image = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
            float[] yProj = ImageOperations.meanProjection(image, ImageOperations.Axis.Y, null);
            int maxIdx = ArrayUtil.max(yProj);
            int minIdx = ArrayUtil.min(yProj); //, maxIdx+1, yProj.length-1 // in case peak is touching edge of image no min after aberration: seach for min in whole y axis
            double peakHeight = yProj[maxIdx] - yProj[minIdx];
            float thld = (float)(peakHeight * 0.25 + yProj[minIdx] );
            int startOfPeakIdx = ArrayUtil.getFirstOccurence(yProj, maxIdx, 0, thld, true, true);
            if (startOfPeakIdx<length) {
                flip = true;
                return;
            }
            int endOfPeakIdx = ArrayUtil.getFirstOccurence(yProj, maxIdx, yProj.length-1, thld, true, true);
            if (yProj.length-1 - endOfPeakIdx<length) {
                flip = false;
                return;
            }
            // compare upper and lower side X-variances withing frame of microchannel length
            // TODO : TEST !!!!
            float[] xProjUpper = ImageOperations.meanProjection(image, ImageOperations.Axis.X, new BoundingBox(0, image.getSizeX()-1, startOfPeakIdx-length, startOfPeakIdx, 0, image.getSizeZ()));
            float[] xProjLower = ImageOperations.meanProjection(image, ImageOperations.Axis.X, new BoundingBox(0, image.getSizeX()-1, endOfPeakIdx, endOfPeakIdx+length, 0, image.getSizeZ()));
            double varUpper = ArrayUtil.meanSigma(xProjUpper, 0, xProjUpper.length, null)[1];
            double varLower = ArrayUtil.meanSigma(xProjLower, 0, xProjLower.length, null)[1];
            flip = varLower>varUpper;
            logger.debug("AutoFlipY: {} (var upper: {}, var lower: {} aberration: [{};{}]", flip, varLower, varUpper,startOfPeakIdx, endOfPeakIdx );
            
        }
    }
    private Boolean isFlipPhaseOpticalAberration(Image image) {
        /* 
        1) search for optical aberration
        2) get x variance for each line above and under aberration -> microchannels are where variance is maximal
        */
        ImageMask upper = new BlankMask( image.getSizeX(), image.getSizeY()/2, image.getSizeZ(), image.getOffsetX(), image.getOffsetY(), image.getOffsetZ(), image.getScaleXY(), image.getScaleZ());
        ImageMask lower = new BlankMask( image.getSizeX(), image.getSizeY()/2, image.getSizeZ(), image.getOffsetX(), image.getOffsetY()+image.getSizeY()/2, image.getOffsetZ(), image.getScaleXY(), image.getScaleZ());
        double upperMean = ImageOperations.getMeanAndSigmaWithOffset(image, upper, null)[0];
        double lowerMean = ImageOperations.getMeanAndSigmaWithOffset(image, lower, null)[0];
        if (testMode) logger.debug("AutoFlipY: upper half mean {} lower: {}", upperMean, lowerMean);
        if (upperMean>lowerMean) return false;
        else if (lowerMean>upperMean) return true;
        else return null;
    }
    private Boolean isFlipFluoUpperHalf(Image image) {
        ImageMask upper = new BlankMask( image.getSizeX(), image.getSizeY()/2, image.getSizeZ(), image.getOffsetX(), image.getOffsetY(), image.getOffsetZ(), image.getScaleXY(), image.getScaleZ());
        ImageMask lower = new BlankMask( image.getSizeX(), image.getSizeY()/2, image.getSizeZ(), image.getOffsetX(), image.getOffsetY()+image.getSizeY()/2, image.getOffsetZ(), image.getScaleXY(), image.getScaleZ());
        double upperMean = ImageOperations.getMeanAndSigmaWithOffset(image, upper, null)[0];
        double lowerMean = ImageOperations.getMeanAndSigmaWithOffset(image, lower, null)[0];
        if (testMode) logger.debug("AutoFlipY: upper half mean {} lower: {}", upperMean, lowerMean);
        if (upperMean>lowerMean) return false;
        else if (lowerMean>upperMean) return true;
        else return null;
    }
    private Boolean isFlipFluo(Image image) {
        int minSize = minObjectSize.getValue().intValue();
        SimpleThresholder thlder = fluoThld.instanciatePlugin();
        ImageMask mask = new ThresholdMask(image, thlder.runSimpleThresholder(image, null), true, true);
        List<Region> objects = ImageLabeller.labelImageList(mask);
        objects.removeIf(o->o.getSize()<minSize);
        // filter by median sizeY
        Map<Region, Integer> sizeY = objects.stream().collect(Collectors.toMap(o->o, o->o.getBounds().getSizeY()));
        double medianSizeY = ArrayUtil.medianInt(sizeY.values());
        objects.removeIf(o->sizeY.get(o)<medianSizeY/2);
        if (testMode) logger.debug("objects: {}, minSize: {}, minSizeY: {} (median sizeY: {})", objects.size(), minSize, medianSizeY/2, medianSizeY);
        if (objects.isEmpty() || objects.size()<=2) return null;
        Map<Region, BoundingBox> xBounds = objects.stream().collect(Collectors.toMap(o->o, o->new BoundingBox(o.getBounds().getxMin(), o.getBounds().getxMax(), 0, 1, 0, 1)));
        Iterator<Region> it = objects.iterator();
        List<Region> yMinOs = new ArrayList<>();
        List<Region> yMaxOs = new ArrayList<>();
        while(it.hasNext()) {
            Region o = it.next();
            List<Region> inter = new ArrayList<>(objects);
            inter.removeIf(oo->!xBounds.get(oo).intersect2D(xBounds.get(o)));
            yMinOs.add(Collections.min(inter, (o1, o2)->Integer.compare(o1.getBounds().getyMin(), o2.getBounds().getyMin())));
            yMaxOs.add(Collections.max(inter, (o1, o2)->Integer.compare(o1.getBounds().getyMax(), o2.getBounds().getyMax())));
            objects.removeAll(inter);
            it = objects.iterator();
        }
        // filter outliers with distance to median value
        double yMinMed = ArrayUtil.medianInt(Utils.transform(yMinOs, o->o.getBounds().getyMin()));
        yMinOs.removeIf(o->Math.abs(o.getBounds().getyMin()-yMinMed)>o.getBounds().getSizeY()/4);
        double yMaxMed = ArrayUtil.medianInt(Utils.transform(yMaxOs, o->o.getBounds().getyMax()));
        yMaxOs.removeIf(o->Math.abs(o.getBounds().getyMax()-yMaxMed)>o.getBounds().getSizeY()/4);
        
        if (testMode) {
            //ImageWindowManagerFactory.showImage(TypeConverter.toByteMask(mask, null, 1).setName("Segmentation mask"));
            this.upperObjectsTest.add(new RegionPopulation(yMinOs, image).getLabelMap().setName("Upper Objects"));
            this.lowerObjectsTest.add(new RegionPopulation(yMaxOs, image).getLabelMap().setName("Lower Objects"));
        }
        List<Pair<Integer, Integer>> yMins = Utils.transform(yMinOs, o->new Pair<>(o.getBounds().getyMin(), o.getBounds().getSizeY()));
        double sigmaMin = getSigma(yMins);
        List<Pair<Integer, Integer>> yMaxs = Utils.transform(yMaxOs, o->new Pair<>(o.getBounds().getyMax(), o.getBounds().getSizeY()));
        double sigmaMax = getSigma(yMaxs);
        if (testMode) {
            logger.debug("yMins sigma: {}: {}", sigmaMin, Utils.toStringList(yMins));
            logger.debug("yMaxs sigma {}: {}", sigmaMax, Utils.toStringList(yMaxs));
            logger.debug("flip: {}", sigmaMin>sigmaMax);
        }
        return sigmaMin>sigmaMax;
    }
    
    private static double getSigma(List<Pair<Integer, Integer>> l) {
        double mean = 0;
        for (Pair<Integer, Integer> p : l) mean +=p.key;
        mean/=(double)l.size();
        double mean2 = 0;
        double count = 0;
        for (Pair<Integer, Integer> p : l) {
            mean2 += Math.pow(p.key-mean, 2) * p.value;
            count+=p.value;
        }
        return mean2/count;
    }
    
    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return flip!=null;
    }
    
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) throws Exception {
        if (flip) {
            ///logger.debug("AutoFlipY: flipping (flip config: {} ({}))", flip, flip.getClass().getSimpleName());
            return ImageTransformation.flip(image, ImageTransformation.Axis.Y);
        } //else logger.debug("AutoFlipY: no flip");
        return image;
    }

    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    boolean testMode;
    @Override
    public void setTestMode(boolean testMode) {
        this.testMode=testMode;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{cond};
    }
    
}
