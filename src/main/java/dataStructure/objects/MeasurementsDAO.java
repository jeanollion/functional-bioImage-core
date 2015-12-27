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
package dataStructure.objects;

import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import java.util.Collections;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class MeasurementsDAO {
    Morphium morphium;
    final String fieldName, collectionName;
    public MeasurementsDAO(Morphium morphium, String fieldName) {
        this.fieldName=fieldName;
        this.collectionName="objects_"+fieldName;
        morphium.ensureIndicesFor(Measurements.class);
        this.morphium=morphium;
    }
    
    protected Query<Measurements> getQuery() {
        Query<Measurements> res =  morphium.createQueryFor(Measurements.class); 
        res.setCollectionName(collectionName);
        return res;
    }
    
    public Measurements getObject(ObjectId id) {
        Measurements m =  getQuery().getById(id);
        m.fieldName=fieldName;
        return m;
    }
    
    public void store(Measurements o) {
        morphium.store(o);
    }
    
    public void delete(ObjectId id) {
        morphium.delete(getQuery().f("_id").eq(id));
    }
    
    public void delete(Measurements o) {
        if (o==null) return;
        if (o.getId()!=null) morphium.delete(o);
    }
    
    public void deleteAllObjects() {
        morphium.clearCollection(Measurements.class);
    }
    
    protected Query<Measurements> getQuery(int structureIdx, String... measurements) {
        Query<Measurements> q= getQuery().f("structure_idx").eq(structureIdx);
        if (measurements.length>0) q.setReturnedFields(Measurements.getReturnedFields(measurements));
        return q;
    }
    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        List<Measurements> res = getQuery(structureIdx, measurements).asList();
        Collections.sort(res);
        for (Measurements m : res) m.fieldName=fieldName;
        return res;
    }
}
