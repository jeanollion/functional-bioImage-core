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
package boa.plugins.plugins.measurements.objectFeatures.object_feature;

import boa.plugins.object_feature.IntensityMeasurement;
import boa.data_structure.Region;
import boa.image.MutableBoundingBox;
import boa.plugins.ToolTip;

/**
 *
 * @author Jean Ollion
 */
public class Min extends IntensityMeasurement implements ToolTip {

    @Override public double performMeasurement(Region object) {
        return core.getIntensityMeasurements(object).min;
    }

    @Override public String getDefaultName() {
        return "Min";
    }

    @Override
    public String getToolTipText() {
        return "Minimal intensity value within object";
    }
    
}
