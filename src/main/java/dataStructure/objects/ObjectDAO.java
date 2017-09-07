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

import dataStructure.configuration.Experiment;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author jollion
 */
public interface ObjectDAO {
    public MasterDAO getMasterDAO();
    public Experiment getExperiment();
    public String getPositionName();
    public void clearCache();
    StructureObject getById(String parentTrackHeadId, int structureIdx, int frame, String id);
    public List<StructureObject> getChildren(StructureObject parent, int structureIdx); // needs indicies: structureIdx & parent
    /**
     * Sets children for each parent in parent Track
     * @param parentTrack object with same trackHead id
     * @param structureIdx direct child of parent
     */
    public void setAllChildren(List<StructureObject> parentTrack, int structureIdx);
    /**
     * Deletes the children of {@param parent} of structure {@param structureIdx}
     * @param parent
     * @param structureIdx 
     */
    public void deleteChildren(final StructureObject parent, int structureIdx);
    public void deleteChildren(Collection<StructureObject> parents, int structureIdx);
    /**
     * Deletes all objects from the given structure index  plus all objects from direct or indirect children structures
     * @param structures 
     */
    public void deleteObjectsByStructureIdx(int... structures);
    public void deleteAllObjects();
    /**
     * 
     * @param o object to delete
     * @param deleteChildren if true, deletes all direct or indirect chilren
     */
    public void delete(StructureObject o, boolean deleteChildren, boolean deleteFromParent, boolean relabelParent);
    public void delete(Collection<StructureObject> list, boolean deleteChildren, boolean deleteFromParent, boolean relabelParent);
    //revoir les fonctions deletes avec la gestions des enfant directs et indirects.. la fonction delete doit elle appeller deleteChildren?
    public void store(StructureObject object);
    public void store(final Collection<StructureObject> objects);
    
    public List<StructureObject> getRoots();
    public void setRoots(List<StructureObject> roots);
    public StructureObject getRoot(int timePoint);
    
    public List<StructureObject> getTrack(StructureObject trackHead);
    public List<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx);
    
    public void upsertMeasurements(Collection<StructureObject> objects);
    public void upsertMeasurement(StructureObject o);
    public Measurements getMeasurements(StructureObject o);
    public List<Measurements> getMeasurements(int structureIdx, String... measurements);
    public void deleteAllMeasurements();
}
