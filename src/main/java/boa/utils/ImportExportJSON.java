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
package boa.utils;

import boa.ui.GUI;
import boa.core.ProgressCallback;
import boa.configuration.experiment.Experiment;
import boa.data_structure.dao.ImageDAO;
import boa.data_structure.dao.DBMapObjectDAO;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.Measurements;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import static boa.data_structure.StructureObjectUtils.setAllChildren;
import boa.image.Image;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.utils.FileIO.ZipReader;
import boa.utils.FileIO.ZipWriter;
import static boa.utils.JSONUtils.parse;
import static boa.utils.JSONUtils.serialize;

/**
 *
 * @author Jean Ollion
 */
public class ImportExportJSON {
    public static final Logger logger = LoggerFactory.getLogger(ImportExportJSON.class);
    public static void writeObjects(ZipWriter writer, ObjectDAO dao, ProgressCallback pcb) {
        List<StructureObject> roots=dao.getRoots();
        if (roots.isEmpty()) return;
        List<StructureObject> allObjects = new ArrayList<>();
        allObjects.addAll(roots);
        for (int sIdx = 0; sIdx<dao.getExperiment().getStructureCount(); ++sIdx) {
            setAllChildren(roots, sIdx);
            for (StructureObject r : roots) allObjects.addAll(r.getChildren(sIdx));
        }
        if (pcb!=null) pcb.log(allObjects.size()+"# objects found");
        writer.write(dao.getPositionName()+"/objects.txt", allObjects, o -> serialize(o));
        allObjects.removeIf(o -> o.getMeasurements().getValues().isEmpty());
        if (pcb!=null) pcb.log(allObjects.size()+"# measurements found");
        writer.write(dao.getPositionName()+"/measurements.txt", allObjects, o -> serialize(o.getMeasurements()));
    }
    public static void exportPreProcessedImages(ZipWriter writer, ObjectDAO dao) {
        int ch = dao.getExperiment().getChannelImageCount();
        int fr = dao.getExperiment().getPosition(dao.getPositionName()).getFrameNumber(false);
        String dir = dao.getPositionName()+"/Images/";
        ImageDAO iDao = dao.getExperiment().getImageDAO();
        for (int c = 0; c<ch; ++c) {
            for (int f = 0; f<fr; ++f) {
                InputStream is = iDao.openPreProcessedImageAsStream(c, f, dao.getPositionName());
                if (is!=null) writer.appendFile(dir+f+"_"+c, is); //closes is
            }
        }
        // todo check all exported
    }
    private static Set<Triplet<StructureObject,Integer, Integer>> listAllTrackImages(ObjectDAO dao) {
        Set<Triplet<StructureObject,Integer, Integer>> res = new HashSet<>();
        for (int sIdx = 0; sIdx<dao.getExperiment().getStructureCount(); ++sIdx) {
            List<Integer> direct = dao.getExperiment().getAllDirectChildStructures(sIdx);
            direct = Utils.transform(direct, s->dao.getExperiment().getChannelImageIdx(s));
            Utils.removeDuplicates(direct, false);
            if (direct.isEmpty()) continue;
            List<StructureObject> ths = StructureObjectUtils.getAllObjects(dao, sIdx);
            ths.removeIf(o->!o.isTrackHead());
            logger.debug("exporting track images: structure: {}, child structures: {}, th: {}", sIdx, direct, ths.size());
            for (int childCIdx : direct) {
                for (StructureObject th : ths) {
                    res.add(new Triplet(th, sIdx, childCIdx));
                }
            }
        }
        return res;
    }
    public static void exportTrackImages(ZipWriter writer, ObjectDAO dao) {
        ImageDAO iDao = dao.getExperiment().getImageDAO();
        for (Triplet<StructureObject, Integer, Integer> p : listAllTrackImages(dao)) {
            InputStream is = iDao.openTrackImageAsStream(p.v1, p.v3);
            if (is!=null) writer.appendFile(dao.getPositionName()+"/TrackImages_"+p.v2+"/"+Selection.indicesString(p.v1)+"_"+p.v3, is);
        }
    }
    public static String importTrackImages(ZipReader reader, ObjectDAO dao) {
        ImageDAO iDao = dao.getExperiment().getImageDAO();
        Set<Triplet<StructureObject,Integer, Integer>> missingTrackImages = new HashSet<>();
        for (Triplet<StructureObject, Integer, Integer> p : listAllTrackImages(dao)) {
            String file = dao.getPositionName()+"/TrackImages_"+p.v2+"/"+Selection.indicesString(p.v1)+"_"+p.v3;
            InputStream is = reader.readFile(file);
            if (is!=null) iDao.writeTrackImage(p.v1, p.v3, is);
            else missingTrackImages.add(p);
        }
        if (!missingTrackImages.isEmpty()) {
            logger.info("trackImages Import @position: {} missing trackImages: {}", dao.getPositionName(), missingTrackImages);
            String message = "TrackImages Import @position: "+dao.getPositionName()+" missing trackImages: "+Utils.toStringList(missingTrackImages, t->t.v1);
            return message;
        }
        return null;
    }
    public static void importPreProcessedImages(ZipReader reader, ObjectDAO dao) {
        String dir = dao.getPositionName()+"/Images/";
        ImageDAO iDao = dao.getExperiment().getImageDAO();
        String pos = dao.getPositionName();
        List<String> files = reader.listsubFiles(dir);
        logger.debug("pos: {}, images: {}", pos, Utils.toStringList(files));
        for (String f : files) {
            File file = new File(f);
            String[] fc = file.getName().split("_");
            int frame = Integer.parseInt(fc[0]);
            int channel = Integer.parseInt(fc[1]);
            InputStream is = reader.readFile(f);
            if (is!=null) {
                //logger.debug("read images: f={}, c={} pos: {}", frame, channel, pos);
                iDao.writePreProcessedImage(is, channel, frame, pos);
            }
        }
        // todo check all imported
    }
    public static void importObjects(ZipReader reader, ObjectDAO dao) {
        logger.debug("reading objects..");
        List<StructureObject> allObjects = reader.readObjects(dao.getPositionName()+"/objects.txt", o->parse(StructureObject.class, o));
        logger.debug("{} objets read", allObjects.size());
        List<Measurements> allMeas = reader.readObjects(dao.getPositionName()+"/measurements.txt", o->new Measurements(parse(o), dao.getPositionName()));
        logger.debug("{} measurements read", allObjects.size());
        Map<String, StructureObject> objectsById = new HashMap<>(allObjects.size());
        
        List<StructureObject> roots = new ArrayList<>();
        Iterator<StructureObject> it = allObjects.iterator();
        while(it.hasNext()) {
            StructureObject n = it.next();
            if (n.isRoot()) {
                roots.add(n);
                it.remove();
            }
        }
        
        for (StructureObject o : allObjects) objectsById.put(o.getId(), o);
        for (StructureObject o : roots) objectsById.put(o.getId(), o);
        StructureObjectUtils.setRelatives(objectsById, true, false); // avoiding calls to dao getById when storing measurements: set parents
        
        for (Measurements m : allMeas) {
            StructureObject o = objectsById.get(m.getId());
            if (o!=null) o.setMeasurements(m);
        }
        logger.debug("storing roots");
        dao.store(roots);
        logger.debug("storing other objects");
        dao.store(allObjects);
        logger.debug("storing measurements");
        dao.upsertMeasurements(allObjects);
        if (dao instanceof DBMapObjectDAO) ((DBMapObjectDAO)dao).compactDBs(true);
    }
    
    public static <T> List<T> readObjects(String path, Class<T> clazz) {
        return FileIO.readFromFile(path, s-> parse(clazz, s));
    }
    
    public static void exportPositions(ZipWriter w, MasterDAO dao, boolean objects, boolean preProcessedImages, boolean trackImages, ProgressCallback pcb) {exportPositions(w, dao, objects, preProcessedImages, trackImages, null, pcb);}
    public static void exportPositions(ZipWriter w, MasterDAO dao, boolean objects, boolean preProcessedImages, boolean trackImages, List<String> positions, ProgressCallback pcb) {
        if (!w.isValid()) return;
        if (positions==null) positions = Arrays.asList(dao.getExperiment().getPositionsAsString());
        int count = 0;
        //if (pcb!=null) pcb.incrementTaskNumber(positions.size());
        for (String p : positions) {
            count++;
            logger.info("Exporting: {}/{}", count, positions.size());
            if (pcb!=null) pcb.log("Exporting position: "+p+ " ("+count+"/"+positions.size()+")");
            ObjectDAO oDAO = dao.getDao(p);
            if (objects) {
                writeObjects(w, oDAO, pcb);
                logger.info("objects exported");
            }
            if (preProcessedImages) {
                logger.info("Writing pp images");
                exportPreProcessedImages(w, oDAO);
            }
            if (trackImages) {
                logger.info("Writing track images");
                exportTrackImages(w, oDAO);
            }
            oDAO.clearCache();
            if (pcb!=null) pcb.incrementProgress();
            if (pcb!=null) pcb.log("Position: "+p+" exported!");
        }
        logger.info("Exporting position done!");
    }
    public static void exportConfig(ZipWriter w, MasterDAO dao) {
        if (!w.isValid()) return;
        w.write("config.json", new ArrayList<Experiment>(1){{add(dao.getExperiment());}}, o->JSONUtils.serialize(o));
    }
    
    public static void exportSelections(ZipWriter w, MasterDAO dao) {
        if (!w.isValid()) return;
        if (dao.getSelectionDAO()!=null) w.write("selections.json", dao.getSelectionDAO().getSelections(), o -> JSONUtils.serialize(o));
    }
    public static Experiment readConfig(File f) {
        if (f.getName().endsWith(".json")||f.getName().endsWith(".txt")) {
            List<Experiment> xp = FileIO.readFromFile(f.getAbsolutePath(), o->JSONUtils.parse(Experiment.class, o));
            if (xp.size()==1) return xp.get(0);
        } else if (f.getName().endsWith(".zip")) {
            ZipReader r = new ZipReader(f.getAbsolutePath());
            if (r.valid()) {
                List<Experiment> xp = r.readObjects("config.json", o->JSONUtils.parse(Experiment.class, o));
                if (xp.size()==1) return xp.get(0);
            }
        }
        return null;
    }
    public static void importConfigurationFromFile(String path, MasterDAO dao, boolean structures, boolean preProcessingTemplate) {
        File f = new File(path);
        if (f.getName().endsWith(".json")||f.getName().endsWith(".txt")) { //FIJI allows only to upload .txt
            List<Experiment> xp = FileIO.readFromFile(path, o->JSONUtils.parse(Experiment.class, o));
            if (xp.size()==1) {
                Experiment source = xp.get(0);
                if (source.getStructureCount()!=dao.getExperiment().getStructureCount()) {
                    GUI.log("Source has: "+source.getStructureCount()+" instead of "+dao.getExperiment().getStructureCount());
                    return;
                }
                // set structures
                dao.getExperiment().getStructures().setContentFrom(source.getStructures());
                // set preprocessing template
                dao.getExperiment().getPreProcessingTemplate().setContentFrom(source.getPreProcessingTemplate());
                // set measurements
                dao.getExperiment().getMeasurements().setContentFrom(source.getMeasurements());
                dao.updateExperiment();
                logger.debug("Dataset: {} from file: {} set to db: {}", dao.getExperiment().getName(), path, dao.getDBName());
            }
            
        }
    }
    public static void importFromFile(String path, MasterDAO dao, boolean config, boolean selections, boolean objects, boolean preProcessedImages, boolean trackImages, ProgressCallback pcb) {
        File f = new File(path);
        if (f.getName().endsWith(".json")||f.getName().endsWith(".txt")) {
            if (config) {
                dao.setConfigurationReadOnly(false);
                if (dao.isConfigurationReadOnly()) {
                    if (pcb!=null) pcb.log("Cannot import configuration: experiment is in read only");
                    return;
                }
                List<Experiment> xp = FileIO.readFromFile(path, o->JSONUtils.parse(Experiment.class, o));
                if (xp.size()==1) {
                    xp.get(0).setOutputDirectory(dao.getDir()+File.separator+"Output");
                    xp.get(0).setOutputImageDirectory(xp.get(0).getOutputDirectory());
                    dao.setExperiment(xp.get(0));
                    logger.debug("Dataset: {} from file: {} set to db: {}", dao.getExperiment().getName(), path, dao.getDBName());
                    dao.clearCache(); // avoid lock issues
                }
            }
        } else if (f.getName().endsWith(".zip")) importFromZip(path, dao, config, selections, objects, preProcessedImages, trackImages, pcb);
    }
    
    public static boolean importFromZip(String path, MasterDAO dao, boolean config, boolean selections, boolean objects, boolean preProcessedImages, boolean trackImages, ProgressCallback pcb) {
        ZipReader r = new ZipReader(path);
        boolean ok = true;
        if (r.valid()) {
            if (config) { 
                dao.setConfigurationReadOnly(false);
                if (dao.isConfigurationReadOnly()) {
                    if (pcb!=null) pcb.log("Cannot import configuration: dataset is in read only");
                    ok = false;
                } else {
                    Experiment xp = r.readFirstObject("config.json", o->JSONUtils.parse(Experiment.class, o));
                    if (xp!=null) {
                        if (dao.getDir()!=null) {
                            xp.setOutputDirectory(dao.getDir()+File.separator+"Output");
                            xp.setOutputImageDirectory(xp.getOutputDirectory());
                        }
                        dao.setExperiment(xp);
                        logger.debug("XP: {} from file: {} set to db: {}", dao.getExperiment().getName(), path, dao.getDBName());
                    } else {
                        ok = false;
                    }
                }
            }
            if (objects || preProcessedImages || trackImages) {
                Collection<String> dirs = r.listRootDirectories();
                dirs = new ArrayList<>(dirs);
                Collections.sort((List)dirs);
                logger.info("directories: {}", dirs);
                if (pcb!=null) {
                    pcb.incrementTaskNumber(dirs.size());
                    pcb.log("positions: "+dirs.size());
                }
                int count = 0;
                dao.lockPositions(dirs.toArray(new String[dirs.size()]));
                for (String position : dirs) {
                    count++;
                    if (pcb!=null) pcb.log("Importing: Position: "+position + " ("+ count+"/"+dirs.size()+")");
                    ObjectDAO oDAO = dao.getDao(position);
                    if (oDAO.isReadOnly()) {
                        if (pcb!=null) pcb.log("Cannot import position: "+position+" (cannot be locked)");
                        ok = false;
                    } else {
                        try {
                            if (objects) {
                                logger.debug("deleting all objects");
                                oDAO.deleteAllObjects();
                                logger.debug("all objects deleted");
                                importObjects(r, oDAO);
                            }
                            if (preProcessedImages) importPreProcessedImages(r, oDAO);
                            if (trackImages) {
                                String importTI = importTrackImages(r, oDAO);
                                if (importTI!=null && pcb!=null) pcb.log(importTI);
                                else if (pcb!=null) pcb.log("Import track images ok");
                            }
                        } catch (Exception e) {
                            if (pcb!=null) pcb.log("Error! xp could not be undumped! "+e.getMessage());
                            e.printStackTrace();
                            dao.deleteExperiment();
                            throw e;
                        }
                        oDAO.clearCache();
                        if (pcb!=null) pcb.incrementProgress();
                        if (pcb!=null) pcb.log("Position: "+position+" imported!");
                    }
                }
            }
            if (selections) {
                dao.setConfigurationReadOnly(false);
                if (dao.isConfigurationReadOnly()) {
                    if (pcb!=null) pcb.log("Cannot import selection: dataset is in read only");
                    ok = false;
                } else {
                    logger.debug("importing selections....");
                    List<Selection> sels = r.readObjects("selections.json", o->JSONUtils.parse(Selection.class, o));
                    logger.debug("selections: {}", sels.size());
                    if (sels.size()>0 && dao.getSelectionDAO()!=null) {
                        for (Selection s: sels ) if (dao.getSelectionDAO()!=null) dao.getSelectionDAO().store(s);
                        logger.debug("Stored: #{} selections from file: {} set to db: {}", sels.size(), path, dao.getDBName());
                    }
                }
            }
            r.close();
            return ok;
        } else return false;
    }
    
}
