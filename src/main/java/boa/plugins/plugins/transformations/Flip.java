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
package boa.plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import java.util.ArrayList;
import boa.plugins.TransformationTimeIndependent;
import boa.image.processing.ImageTransformation;
import boa.image.processing.ImageTransformation.Axis;

/**
 *
 * @author jollion
 */
public class Flip implements TransformationTimeIndependent {
    
    ChoiceParameter direction = new ChoiceParameter("Flip Axis Direction", new String[]{Axis.X.toString(), Axis.Y.toString(), Axis.Z.toString()}, Axis.Y.toString(), false);
    Parameter[] p = new Parameter[]{direction};
    public Flip() {}
    
    public Flip(Axis axis) {
        direction.setSelectedItem(axis.toString());
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        Axis axis = Axis.valueOf(direction.getSelectedItem());
        //logger.debug("performing flip: axis: {}, channel: {}, timePoint: {}", axis, channelIdx, timePoint);
        ImageTransformation.flip(image, axis);
        return image;
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return p;
    }
    
    public ArrayList getConfigurationData() {
        return null;
    }
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return true;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        
    }
    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}