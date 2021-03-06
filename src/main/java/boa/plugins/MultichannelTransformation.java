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
package boa.plugins;

/**
 *
 * @author Jean Ollion
 */
public interface MultichannelTransformation extends Transformation {
    public static enum OUTPUT_SELECTION_MODE{SAME, SINGLE, MULTIPLE, ALL};
    /**
     * Selection mode for channels on which transformation will be applied
     * SINGLE: only one channel that can be configured
     * MULTIPLE : several channels that can be configured
     * ALL: all channels
     * SAME: same channel as input channels. Transformation must be configurable for this mode, if not error will be throwns
     * @return 
     */
    public OUTPUT_SELECTION_MODE getOutputChannelSelectionMode();
}
