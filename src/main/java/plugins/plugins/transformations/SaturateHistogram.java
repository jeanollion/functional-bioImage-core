/*
 * Copyright (C) 2016 jollion
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
package plugins.plugins.transformations;

import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import image.BoundingBox.LoopFunction;
import image.Image;
import java.util.ArrayList;
import plugins.Transformation;

/**
 *
 * @author jollion
 */
public class SaturateHistogram implements Transformation {
    NumberParameter threshold = new NumberParameter("Saturation initiation value", 4, 400);
    NumberParameter maxValue = new NumberParameter("Maximum value", 3, 500);
    Parameter[] parameters = new Parameter[]{threshold, maxValue};
    
    public SaturateHistogram(){}
    public SaturateHistogram(double saturationThreshold, double maxValue){
        threshold.setValue(saturationThreshold);
        this.maxValue.setValue(maxValue);
    }
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        
    }

    public Image applyTransformation(int channelIdx, int timePoint, final Image image) {
        saturate(threshold.getValue().doubleValue(), maxValue.getValue().doubleValue(), image);
        return image;
    }
    public static void saturate(double thld, double thldMax, Image image) {
        if (thldMax<thld) throw new IllegalArgumentException("Saturate histogram transformation: configuration error: Maximum value should be superior to threhsold value");
        double maxObs = image.getMinAndMax(null)[1];
        if (maxObs<=thldMax || maxObs<=thld) return;
        
        if (thldMax>thld) {
            final double factor = (thldMax - thld) / (maxObs - thld);
            final double add = thld * (1 - factor);

            image.getBoundingBox().translateToOrigin().loop(new LoopFunction() {
                public void loop(int x, int y, int z) {
                    float value = image.getPixel(x, y, z);
                    if (value>thld) image.setPixel(x, y, z, value * factor + add);
                }
            });
        } else {
            image.getBoundingBox().translateToOrigin().loop((int x, int y, int z) -> {
                float value = image.getPixel(x, y, z);
                if (value>thld) image.setPixel(x, y, z, thld);
            });
        }
    }

    public ArrayList getConfigurationData() {
        return null;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
