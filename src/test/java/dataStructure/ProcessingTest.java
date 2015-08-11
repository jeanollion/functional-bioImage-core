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
package dataStructure;

import static TestUtils.Utils.showImageIJ;
import configuration.parameters.NumberParameter;
import testPlugins.dummyPlugins.DummySegmenter;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.Structure;
import dataStructure.containers.ImageDAO;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.objects.StructureObject;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObjectUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageFormat;
import image.ImageWriter;
import images.ImageIOTest;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.PluginFactory;
import plugins.Segmenter;
import plugins.plugins.trackers.TrackerObjectIdx;
import plugins.plugins.transformations.SimpleTranslation;
import utils.MorphuimUtils;

/**
 *
 * @author jollion
 */
public class ProcessingTest {
    public final static Logger logger = LoggerFactory.getLogger(ProcessingTest.class);
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    public static ImageByte[][] createDummyImagesTC(int sizeX, int sizeY, int sizeZ, int timePointNumber, int channelNumber) {
        ImageByte[][] images = new ImageByte[timePointNumber][channelNumber];
        for (int t = 0; t<timePointNumber; t++) {
            for (int c = 0; c<channelNumber;c++) {
                images[t][c] = new ImageByte("t"+t+"c"+c, sizeX, sizeY, sizeZ);
                images[t][c].setPixel(t, c, c, 1);
            }
        }
        return images;
    }
    
    //@Test
    public void importFieldTest() {
        // creation de l'image de test
        String title = "imageTestMultiple";
        ImageFormat format = ImageFormat.OMETIF;
        File folder = testFolder.newFolder("TestImages");
        int timePoint = 3;
        int channel = 2;
        ImageByte[][] images = createDummyImagesTC(6, 5 ,4,  timePoint, channel);
        ImageByte[][] images2 = createDummyImagesTC(6, 5 ,4, timePoint, channel+1);
        ImageByte[][] images3 = createDummyImagesTC(6, 5 ,4, timePoint+1, channel);
        
        ImageWriter.writeToFile(folder.getAbsolutePath(), title, format, images);
        File folder2 = new File(folder.getAbsolutePath()+File.separator+"subFolder");
        folder2.mkdir();
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title, format, images);
        ImageWriter.writeToFile(folder2.getAbsolutePath(), title+"2", format, images, images, images2, images3);
        
        Experiment xp = new Experiment("testXP", new Structure("structure"));
        xp.getChannelImages().insert(new ChannelImage("channel1"), new ChannelImage("channel2"));
        
        String[] files = new String[]{folder.getAbsolutePath()};
        Processor.importFiles(files, xp);
        assertEquals("number of fields detected", 6-1-1, xp.getMicroscopyFields().getChildCount()); // 6 - 1 (unique title) - 1 (channel number)
        MultipleImageContainer c = xp.getMicroscopyField(title).getImages();
        ImageIOTest.assertImageByte(images[0][0], (ImageByte)c.getImage(0, 0));
    }
    
    //@Test
    public void preProcessingTest() {
        // set-up XP
        File daoFolder = testFolder.newFolder("TestPreProcessingDAOFolder");
        Experiment xp = new Experiment("test");
        ChannelImage ci1 = xp.getChannelImages().createChildInstance();
        ChannelImage ci2 = xp.getChannelImages().createChildInstance();
        xp.getChannelImages().insert(ci1, ci2);
        xp.setOutputImageDirectory(daoFolder.getAbsolutePath());
        //xp.setOutputImageDirectory("/tmp");
        xp.setImageDAOType(Experiment.ImageDAOTypes.LocalFileSystem);
        
        // import fields
        ImageByte[][] images = createDummyImagesTC(6, 5 ,4, 3, 2);
        images[0][0].setPixel(0, 0, 0, 1);
        File folder = testFolder.newFolder("TestImagesPreProcessing");
        ImageWriter.writeToFile(folder.getAbsolutePath(), "field1", ImageFormat.OMETIF, images);
        Processor.importFiles(new String[]{folder.getAbsolutePath()}, xp);
        MicroscopyField f = xp.getMicroscopyField(0);
        assertEquals("number of fields detected", 1, xp.getMicroscopyFields().getChildCount());
        
        //set-up pre-processing chains
        PluginFactory.findPlugins("plugins.plugins.transformations");
        SimpleTranslation t = new SimpleTranslation(1, 0, 0);
        f.getPreProcessingChain().addTransformation(0, null, t);
        SimpleTranslation t2 = new SimpleTranslation(0, 1, 0);
        f.getPreProcessingChain().addTransformation(0, null, t2);
        
        //pre-process
        Processor.preProcessImages(xp);
        
        // passage through morphium
        MorphiumConfig cfg = new MorphiumConfig();
        cfg.setDatabase("testdb");
        try {
            cfg.addHost("localhost", 27017);
        } catch (UnknownHostException ex) {
            logger.error("create morphium", ex);
        }
        Morphium m=new Morphium(cfg);
        m.clearCollection(Experiment.class);
        m.store(xp);
        m=new Morphium(cfg);
        xp = m.createQueryFor(Experiment.class).getById(xp.getId());
        
        // test 
        ImageDAO dao = xp.getImageDAO();
        Image image = dao.openPreProcessedImage(0, 0, "field1");
        assertTrue("Image saved in DAO", image!=null);
        SimpleTranslation tInv = new SimpleTranslation(-1, -1, 0);
        Image imageInv = tInv.applyTransformation(0, 0, image);
        ImageIOTest.assertImageByte(images[0][0], (ImageByte)imageInv);
    }
    
    private static ImageByte getMask(StructureObject root, int[] pathToRoot) {
        ImageByte mask = new ImageByte("mask", root.getMask());
        int startLabel = 1;
        for (StructureObject o : StructureObjectUtils.getAllObjects(root, pathToRoot)) mask.appendBinaryMasks(startLabel++, o.getMask().addOffset(o.getRelativeBoundingBox(null)));
        return mask;
    }
    
    //@Test
    public void StructureObjectTestStore() {
        MorphiumConfig cfg = new MorphiumConfig();
        cfg.setDatabase("testdb");
        try {
            cfg.addHost("localhost", 27017);
        } catch (UnknownHostException ex) {
            logger.error("create morphium", ex);
        }
        Morphium m=new Morphium(cfg);
        m.clearCollection(StructureObject.class);
        m.clearCollection(Experiment.class);
        Experiment xp = new Experiment("test");
        m.store(xp);
        StructureObject r = new StructureObject("test", 0, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), xp);
        StructureObject r2 = new StructureObject("test", 1, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), xp);
        StructureObject r3 = new StructureObject("test", 3, new BlankMask("", 1, 2, 3, 0, 0, 0, 1, 1), xp);
        ObjectDAO dao = new ObjectDAO(m);
        dao.store(r, r2, r3);
        r2.setPreviousInTrack(r, true);
        r3.setPreviousInTrack(r2, true);
        dao.store(r, r2, r3);
        MorphuimUtils.waitForWrites(m);
        MorphuimUtils.addDereferencingListeners(m);
        r2 = dao.getObject(r2.getId());
        r = dao.getObject(r.getId());
        assertTrue("r2 retrieved", r!=null);
        assertEquals("r unique instanciation", r, r2.getPrevious());
        assertEquals("xp unique instanciation", r.getExperiment(), r2.getExperiment());
        m=new Morphium(cfg);
        MorphuimUtils.addDereferencingListeners(m);
        dao = new ObjectDAO(m);
        r2 = dao.getObject(r2.getId());
        assertTrue("r2 retrieved", r!=null);
        assertEquals("r retrieved 2", "test", r2.getFieldName());
        assertEquals("r previous ", r.getId(), r2.getPrevious().getId());
    }
    
    @Test
    public void StructureObjectTest() {
        try {
            // set-up experiment structure
            Experiment xp = new Experiment("test");
            ChannelImage image = new ChannelImage("ChannelImage");
            xp.getChannelImages().insert(image);
            xp.getStructures().removeAllElements();
            Structure microChannel = new Structure("MicroChannel", -1, 0);
            Structure bacteries = new Structure("Bacteries", 0, 0);
            xp.getStructures().insert(microChannel, bacteries);
            bacteries.setParentStructure(0);
            
            // set-up processing chain
            PluginFactory.findPlugins("testPlugins.dummyPlugins");
            
            microChannel.getProcessingChain().setSegmenter(new DummySegmenter(true, 2));
            bacteries.getProcessingChain().setSegmenter(new DummySegmenter(false, 3));
            assertTrue("segmenter set", microChannel.getProcessingChain().getSegmenter() instanceof DummySegmenter);
            assertEquals("segmenter set (2)", 2, ((NumberParameter)microChannel.getProcessingChain().getSegmenter().getParameters()[0]).getValue().intValue());
            // set-up traking
            PluginFactory.findPlugins("plugins.plugins.trackers");
            microChannel.setTracker(new TrackerObjectIdx());
            bacteries.setTracker(new TrackerObjectIdx());
            
            // set up fields
            ImageByte[][] images = createDummyImagesTC(50, 50, 1, 3, 1);
            File folder = testFolder.newFolder("TestInputImagesStructureObject");
            ImageWriter.writeToFile(folder.getAbsolutePath(), "field1", ImageFormat.OMETIF, images);
            Processor.importFiles(new String[]{folder.getAbsolutePath()}, xp);
            File outputFolder = testFolder.newFolder("TestOutputImagesStructureObject");
            xp.setOutputImageDirectory(outputFolder.getAbsolutePath());
            xp.setOutputImageDirectory("/tmp");
            //save to morphium
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testdb");
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            m.clearCollection(Experiment.class);
            m.clearCollection(StructureObject.class);
            m.store(xp);
            /*m=new Morphium(cfg);
            ExperimentDAO xpDAO = new ExperimentDAO(m);
            xp=xpDAO.getExperiment();*/
            ObjectDAO dao = new ObjectDAO(m);
            
            Processor.preProcessImages(xp);
            StructureObject[] root = xp.getMicroscopyField(0).createRootObjects();
            dao.store(root); 
            Processor.trackRoot(xp, root, dao);
            
            for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
                for (int t = 0; t<root.length; ++t) Processor.processStructure(s, root[t], dao); // process
                for (StructureObject o : StructureObjectUtils.getAllParentObjects(root[0], xp.getPathToRoot(s))) Processor.track(xp, xp.getStructure(s).getTracker(), o, s, dao); // structure
            }
            MorphuimUtils.waitForWrites(m);
            
            StructureObject rootFetch = dao.getObject(root[0].getId());
            assertEquals("root fetch @t=0", root[0].getId(), rootFetch.getId());
            // retrieve
            cfg = new MorphiumConfig();
            cfg.setDatabase("testdb");
            cfg.addHost("localhost", 27017);
            m=new Morphium(cfg);
            MorphuimUtils.addDereferencingListeners(m);
            dao = new ObjectDAO(m);
            
            rootFetch = dao.getObject(root[0].getId());
            assertEquals("root fetch @t=0 (2)", root[0].getId(), rootFetch.getId());
            
            for (int t = 0; t<root.length; ++t) {
                root[t]=dao.getObject(root[t].getId());
                for (int s : xp.getStructuresInHierarchicalOrderAsArray()) {
                    for (StructureObject parent : StructureObjectUtils.getAllParentObjects(root[t], xp.getPathToRoot(s))) parent.setChildObjects(dao.getObjects(parent.getId(), s), s);
                }
            }
            
            for (int t = 1; t<root.length; ++t) {
                logger.trace("root track: {}->{} / expected: {} / actual: {}", t-1, t, root[t], root[t-1].getNext());
                assertEquals("root track:"+(t-1)+"->"+t, root[t], root[t-1].getNext());
                assertEquals("root track:"+(t)+"->"+(t-1), root[t-1], root[t].getPrevious());
            }
            StructureObject[][] microChannels = new StructureObject[root.length][];
            for (int t = 0; t<root.length; ++t) microChannels[t] = root[t].getChildObjects(0);
            for (int t = 0; t<root.length; ++t) assertEquals("number of microchannels @t:"+t, 2, microChannels[t].length);
            for (int i = 0; i<microChannels[0].length; ++i) {
                for (int t = 1; t<root.length; ++t) {
                    assertEquals("mc:"+i+" track:"+(t-1)+"->"+t, microChannels[t][i],  microChannels[t-1][i].getNext());
                    assertEquals("mc:"+i+" track:"+(t)+"->"+(t-1), microChannels[t-1][i], microChannels[t][i].getPrevious());
                }
            }
            for (int i = 0; i<microChannels[0].length; ++i) {
                StructureObject[][] bactos = new StructureObject[root.length][];
                for (int t = 0; t<root.length; ++t) bactos[t] = microChannels[t][i].getChildObjects(1);
                for (int t = 0; t<root.length; ++t) assertEquals("number of bacteries @t:"+t+" @mc:"+i, 3, bactos[t].length);
                for (int b = 0; b<bactos[0].length; ++b) {
                    for (int t = 1; t<root.length; ++t) {
                        assertEquals("mc: "+i+ " bact:"+b+" track:"+(t-1)+"->"+t, bactos[t][i],  bactos[t-1][i].getNext());
                        assertEquals("mc: "+i+ " bact:"+b+" track:"+(t)+"->"+(t-1), bactos[t-1][i], bactos[t][i].getPrevious());
                    }
                }
            }
            // creation des images @t0: 
            //ImageByte maskMC = getMask(root[0], xp.getPathToRoot(0));
            //ImageByte maskBactos = getMask(root[0], xp.getPathToRoot(1));
            //return new ImageByte[]{maskMC, maskBactos};
            
        } catch (UnknownHostException ex) {
            logger.error("create morphium", ex);
        } //return null;
    }
    /*public static void main(String[] args) throws IOException {
        ProcessingTest t = new ProcessingTest();
        t.testFolder.create();
        Image[] images = t.StructureObjectTest();
        for (Image i : images) showImageIJ(i);
    }*/
}
