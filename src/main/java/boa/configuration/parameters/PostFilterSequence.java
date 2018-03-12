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
import boa.image.ImageProperties;
import java.util.Collection;
import boa.plugins.PostFilter;

/**
 *
 * @author jollion
 */
public class PostFilterSequence extends PluginParameterList<PostFilter> {
    Boolean configured = false;
    
    public PostFilterSequence(String name) {
        super(name, "Post-Filter", PostFilter.class);
    }
    
    public RegionPopulation filter(RegionPopulation objectPopulation, int structureIdx, StructureObject parent) {
        if (objectPopulation == null) return null;
        if (!configured) {
            synchronized(configured) {
                if (!configured) ParameterUtils.configureStructureParameters(structureIdx, this);
            }
        }
        ImageProperties prop = objectPopulation.getImageProperties().getProperties();
        for (PostFilter p : this.get()) objectPopulation = p.runPostFilter(parent, structureIdx, objectPopulation);
        objectPopulation.setProperties(prop, true);
        return objectPopulation;
    }
    @Override public PostFilterSequence add(PostFilter... instances) {
        super.add(instances);
        return this;
    }
    
    @Override public PostFilterSequence add(Collection<PostFilter> instances) {
        super.add(instances);
        return this;
    }
}
