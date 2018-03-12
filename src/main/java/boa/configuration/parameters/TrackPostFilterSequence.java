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
package boa.configuration.parameters;

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import boa.plugins.MultiThreaded;
import boa.plugins.PostFilter;
import boa.plugins.PreFilter;
import boa.plugins.TrackPostFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class TrackPostFilterSequence extends PluginParameterList<TrackPostFilter> {
    
    public TrackPostFilterSequence(String name) {
        super(name, "Track Post-Filter", TrackPostFilter.class);
    }
    
    public void filter(int structureIdx, List<StructureObject> parentTrack, ExecutorService executor) throws MultipleException {
        if (parentTrack.isEmpty()) return;
        int count=0;
        for (TrackPostFilter p : this.get()) {
            if (p instanceof MultiThreaded) ((MultiThreaded)p).setExecutor(executor);
            p.filter(structureIdx, parentTrack);
            logger.debug("track post-filter: {}/{} done", ++count, this.getChildCount());
        }
    }
    @Override public TrackPostFilterSequence add(TrackPostFilter... instances) {
        super.add(instances);
        return this;
    }
    
    @Override public TrackPostFilterSequence add(Collection<TrackPostFilter> instances) {
        super.add(instances);
        return this;
    }
}
