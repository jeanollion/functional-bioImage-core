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
package boa.data_structure.dao;

import boa.ui.GUI;
import boa.configuration.experiment.Experiment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import org.json.simple.JSONObject;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.utils.DBMapUtils;
import boa.utils.FileIO;
import boa.utils.JSONUtils;
import boa.utils.Utils;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Jean Ollion
 */
public class DBMapMasterDAO implements MasterDAO {
    public static final Logger logger = LoggerFactory.getLogger(DBMapMasterDAO.class);
    protected final String configDir;
    
    protected final String dbName;
    final HashMap<String, DBMapObjectDAO> DAOs = new HashMap<>();
    final Set<String> positionLock = new HashSet<>();
    protected Experiment xp;
    java.nio.channels.FileLock xpFileLock;
    RandomAccessFile cfg;
    DBMapSelectionDAO selectionDAO;
    boolean readOnly = true; // default is read only
    public DBMapMasterDAO(String dir, String dbName) {
        if (dir==null) throw new IllegalArgumentException("Invalid directory: "+ dir);
        if (dbName==null) throw new IllegalArgumentException("Invalid DbName: "+ dbName);
        logger.debug("create DBMAPMASTERDAO: dir: {}, dbName: {}", dir, dbName);
        configDir = dir;
        File conf = new File(configDir);
        if (!conf.exists()) conf.mkdirs();
        this.dbName = dbName;
    }

    @Override
    public boolean lockPositions(String... positionNames) {
        if (positionNames==null || positionNames.length==0) {
            if (getExperiment()==null) return false;
            positionNames = getExperiment().getPositionsAsString();
        }
        positionLock.addAll(Arrays.asList(positionNames));
        boolean success = true;
        for (String p : positionNames) {
            DBMapObjectDAO dao = getDao(p);
            success = success && !dao.readOnly;
            if (dao.readOnly) logger.warn("Position: {} could not be locked. Another process already locks it? All changes won't be saved", p);
        }
        return success;
    }
    
    @Override 
    public void unlockPositions(String... positionNames) {
        if (positionNames==null || positionNames.length==0) {
            if (getExperiment()==null) return;
            positionNames = getExperiment().getPositionsAsString();
        }
        this.positionLock.removeAll(Arrays.asList(positionNames));
        for (String p : positionNames) {
            if (this.DAOs.containsKey(p)) {
                this.getDao(p).unlock();
                DAOs.remove(p);
            }
        }
    }
    
    public boolean xpFileLock() {
        return xpFileLock!=null;
    }
    
    @Override
    public boolean isConfigurationReadOnly() {
        if (cfg==null) this.getExperiment(); // try to get lock 
        return readOnly;
    }
    @Override 
    public boolean setConfigurationReadOnly(boolean readOnly) {
        if (readOnly) {
            this.readOnly=true;
            this.unlockXP();
            return true;
        } else {
            this.readOnly=false;
            this.getExperiment();
            if (xpFileLock!=null) {
                this.readOnly=false;
                return true;
            } else {
                this.readOnly = true;
                return false;
            }
        }
        
    }
    
    @Override
    public void eraseAll() {
        if (readOnly) return;
        String outputPath = getExperiment()!=null ? getExperiment().getOutputDirectory() : null;
        String outputImagePath = getExperiment()!=null ? getExperiment().getOutputImageDirectory() : null;
        unlockPositions();
        unlockConfiguration();
        Utils.deleteDirectory(outputPath);
        Utils.deleteDirectory(outputImagePath);
        deleteExperiment();
        new File(configDir).delete(); // deletes XP directory only if void. 
    }
    
    private File getConfigFile(String dbName) {
        return new File(configDir + File.separator + dbName + "_config.json");
    }
    
    @Override
    public DBMapObjectDAO getDao(String positionName) {
        DBMapObjectDAO res = this.DAOs.get(positionName);
        if (res==null) {
            String op = getOutputPath();
            if (op==null) return null;
            res = new DBMapObjectDAO(this, positionName, op, !positionLock.contains(positionName));
            //logger.debug("creating DAO: {} position lock: {}, read only: {}", positionName, positionLock.contains(positionName), res.isReadOnly());
            DAOs.put(positionName, res);
        }
        return res;
    }

    @Override
    public String getDBName() {
        return dbName;
    }
    @Override
    public String getDir() {
        return this.configDir;
    }

    @Override
    public void deleteAllObjects() {
        if (readOnly) return;
        if (getExperiment()==null) return;
        for (String s : getExperiment().getPositionsAsString()) {
            getDao(s).deleteAllObjects(); // also deletes measurements
        }
    }
    
    @Override
    public void deleteExperiment() {
        if (readOnly) return;
        unlockXP();
        File cfg = getConfigFile(dbName);
        if (cfg.isFile()) cfg.delete();
    }

    private synchronized void lockXP() {
        if (xpFileLock!=null) return;
        try {
            logger.debug("locking file: {} (cfg null? {})", getConfigFile(dbName), xp==null);
            File f = getConfigFile(dbName);
            if (!f.exists()) f.createNewFile();
            cfg = new RandomAccessFile(f, readOnly?"r":"rw");
            if (!readOnly) xpFileLock = cfg.getChannel().tryLock();
            //logger.debug("lock at creation: {}, for file: {}", xpFileLock, getConfigFile(dbName, false));
        } catch (FileNotFoundException ex) {
            logger.debug("no config file found!");
        } catch (OverlappingFileLockException e) {
            logger.debug("file already locked", e);
        } catch (IOException ex) {
            logger.debug("File could not be locked", ex);
        }
    }
    private synchronized void unlockXP() {
        if (this.xpFileLock!=null) {
            try {
                logger.debug("realising lock: {}", xpFileLock);
                xpFileLock.release();
                logger.debug("lock realeased: {} ", !xpFileLock.isValid());
                xpFileLock = null;
            } catch (IOException ex) {
                logger.debug("error realeasing cfg lock", ex);
            }
        }
        if (cfg!=null) {
            try {
                cfg.close();
                cfg=null;
            } catch (IOException ex) {
                logger.debug("could not close config file", ex);
            }
        }
        
    }
    
    @Override
    public void clearCache() {
        logger.debug("clearing cache...");
        clearCache(true, true , true);
    }
    @Override
    public void unlockConfiguration() {
        unlockXP();
        if (selectionDAO!=null) {
            getSelectionDAO().clearCache();
            this.selectionDAO=null;
        }
        
    }
    @Override 
    public void clearCache(String position) {
        if (getExperiment().getPosition(position)!=null) getExperiment().getPosition(position).flushImages(true, true); // input images
        DBMapObjectDAO dao = DAOs.get(position);
        if (dao!=null) dao.clearCache();
    }
    public synchronized void clearCache(boolean xpDAO, boolean objectDAO, boolean selectionDAO) {
        if (objectDAO) {
            for (DBMapObjectDAO dao : DAOs.values()) clearCache(dao.getPositionName());
        }
        
        if (selectionDAO && this.selectionDAO!=null) {
            getSelectionDAO().clearCache();
            this.selectionDAO=null;
        }
        if (xpDAO) {
            //this.unlockXP();
            this.xp=null;
        }
    }
    
    public void compact() {
        for (String s : getExperiment().getPositionsAsString()) {
            GUI.log("Compacting position: "+s);
            getDao(s).compactDBs(true);
        }
        if (getSelectionDAO()!=null) getSelectionDAO().compact(true);
    }

    @Override
    public Experiment getExperiment() {
        if (this.xp==null) {
            synchronized(this) {
                if (xp==null) {
                    if (xpFileLock==null) this.lockXP();
                    if (!readOnly && xpFileLock==null) {
                        logger.warn(dbName+ ": Config file could not be locked. Dataset already opened ? Dataset will be opened in ReadOnly mode");
                        readOnly = true;
                        lockXP(); // will try to access the xp file in readonly mode
                    }
                    xp = getXPFromFile();

                    // check output dir & set default if necessary
                    boolean modified = checkOutputDirectories(true);
                    modified = checkOutputDirectories(false) || modified;
                    if (modified) updateExperiment();
                } else return xp;
            }
        }
        return xp;
    }

    private Experiment getXPFromFile() {
        if (cfg==null) return null;
        String xpString;
        try {
            cfg.seek(0);
            xpString = cfg.readLine();
        } catch (IOException ex) {
            logger.debug("couldnot read config file: ", ex);
            return null;
        }
        if (xpString==null || xpString.length()==0) return null;
        Experiment xp = new Experiment();
        xp.initFromJSONEntry(JSONUtils.parse(xpString));
        return xp;
    }
    
    public boolean checkOutputDirectories(boolean image) {
        if (xp==null) return false;
        String outS = image ? xp.getOutputImageDirectory() : xp.getOutputDirectory();
        File out = outS!=null ? new File(outS) : null;
        if (out==null || !out.exists() || !out.isDirectory()) { // look for default output dir and set it up if exists
            out = new File(configDir + File.separator + "Output");
            out.mkdirs();
            if (out.isDirectory()) {
                if (image) xp.setOutputImageDirectory(out.getAbsolutePath());
                else xp.setOutputDirectory(out.getAbsolutePath());
                logger.info("Output {} directory was: {} is now : {}", image? "Image" : "",  outS, out.getAbsolutePath());
                return true;
            }
            logger.debug("default output dir: {}, exists: {}, is Dir: {}", out.getAbsolutePath(), out.exists(), out.isDirectory());
        } 
        if (!out.exists() || !out.isDirectory()) { // warn
            String message = "No "+(image?"Image":"")+" Output Directory Found, Please configure it";
            logger.warn(message);
            GUI.log(message);
        }
        return false;
    }
    
    
    @Override
    public void updateExperiment() {
        if (xp==null) {
            GUI.log("Could not update XP -> XP NULL ERROR");
            logger.error("Cannot update XP -> XP NULL");
        }
        if (readOnly) {
            GUI.log("Could not update XP -> READ ONLY");
            logger.error("Cannot update XP -> READ ONLY");
            return;
        }
        if (this.xpFileLock==null) lockXP();
        updateXPFile();
    }
    private void updateXPFile() {
        logger.debug("Updating xp file..");
        if (xp!=null && cfg!=null) {
            try {
                FileIO.write(cfg, xp.toJSONEntry().toJSONString(), false);
                if (this.experimentChangedFromFile()) {
                    GUI.log("Could not save XP");
                    logger.debug("update not done!");
                }
                else logger.debug("Update done!");
            } catch (IOException ex) {
                GUI.log("Could not update XP");
                logger.error("Could not update experiment", ex);
            }
        } else {
            GUI.log("Could not update XP -> xp null ?" + (xp==null)+ " file read error "+(cfg==null));
            logger.error("Could not update experiment");
        }
        
        //FileIO.writeToFile(getConfigFile(dbName, false), Arrays.asList(new Experiment[]{xp}), o->o.toJSONEntry().toJSONString());
    }

    @Override
    public void setExperiment(Experiment xp) {
        this.xp=xp;
        updateExperiment();
    }
    
    @Override 
    public boolean experimentChangedFromFile() {
        Experiment xpFile = getXPFromFile();
        return xpFile==null || !xpFile.sameContent(xp);
    }

    protected String getOutputPath() {
        getExperiment();
        if (xp==null) return null;
        String res = xp.getOutputDirectory();
        if (res==null) return null;
        File f = new File(res);
        if (f.exists() && f.isDirectory()) return res;
        else {
            return null;
        }
        
    }
    
    @Override
    public DBMapSelectionDAO getSelectionDAO() {
        if (this.selectionDAO==null) {
            String op = getOutputPath();
            if (op!=null) {
                selectionDAO = new DBMapSelectionDAO(this, op, readOnly);
            }
        }
        return selectionDAO;
    }
    
}
