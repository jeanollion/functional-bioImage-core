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
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.TypeConverter;
import java.util.ArrayList;
import boa.image.processing.ImageTransformation;
import boa.plugins.MultichannelTransformation;
import boa.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class SimpleTranslation implements MultichannelTransformation {
    NumberParameter X = new NumberParameter("dX", 3, 0);
    NumberParameter Y = new NumberParameter("dY", 3, 0);
    NumberParameter Z = new NumberParameter("dZ", 3, 0);
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", Utils.toStringArray(ImageTransformation.InterpolationScheme.values()), ImageTransformation.InterpolationScheme.BSPLINE5.toString(), false);
    Parameter[] parameters = new Parameter[]{X, Y, Z, interpolation};

    public SimpleTranslation() {}
    
    public SimpleTranslation(double dX, double dY, double dZ) {
        X.setValue(dX);
        Y.setValue(dY);
        Z.setValue(dZ);
    }
    
    public SimpleTranslation setInterpolationScheme(ImageTransformation.InterpolationScheme scheme) {
        this.interpolation.setSelectedItem(scheme.toString());
        return this;
    }
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (!X.hasIntegerValue() || !Y.hasIntegerValue() || !Z.hasIntegerValue()) image = TypeConverter.toFloat(image, null);
        return ImageTransformation.translate(image, X.getValue().doubleValue(), Y.getValue().doubleValue(), Z.getValue().doubleValue(), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()));
    }
    @Override 
    public Parameter[] getParameters() {
        return parameters;
    }
    
    @Override
    public OUTPUT_SELECTION_MODE getOutputChannelSelectionMode() {
        return OUTPUT_SELECTION_MODE.MULTIPLE;
    }

    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
