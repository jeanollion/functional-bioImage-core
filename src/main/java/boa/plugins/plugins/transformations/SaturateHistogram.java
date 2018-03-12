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

import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.image.BoundingBox.LoopFunction;
import boa.image.Image;
import java.util.ArrayList;
import boa.plugins.Transformation;

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
