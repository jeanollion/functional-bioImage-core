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
package boa.image.processing;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import static boa.image.BoundingBox.loop;
import boa.image.MutableBoundingBox;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.SimpleImageProperties;
import boa.image.TypeConverter;
import boa.utils.HashMapGetCreate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 *
 * @author Jean Ollion
 */
public class RegionFactory {
    public static Region[] getRegions(ImageInteger labelImage, boolean ensureContinuousLabels) {
        HashMapGetCreate<Integer, Set<Voxel>> objects = new HashMapGetCreate<>(new HashMapGetCreate.SetFactory<>());
        int label;
        int sizeX = labelImage.sizeX();
        for (int z = 0; z < labelImage.sizeZ(); ++z) {
            for (int xy = 0; xy < labelImage.sizeXY(); ++xy) {
                label = labelImage.getPixelInt(xy, z);
                if (label != 0) objects.getAndCreateIfNecessary(label).add(new Voxel(xy % sizeX, xy / sizeX, z));
            }
        }
        TreeMap<Integer, Set<Voxel>> tm = new TreeMap(objects);
        Region[] res = new Region[tm.size()];
        int i = 0;
        for (Entry<Integer, Set<Voxel>> e : tm.entrySet()) {
            res[i] = new Region(e.getValue(), ensureContinuousLabels?(i + 1):e.getKey(), labelImage.sizeZ()==1, labelImage.getScaleXY(), labelImage.getScaleZ());
            ++i;
        }
        return res;
    }
  
    public static TreeMap<Integer, BoundingBox> getBounds(ImageInteger labelImage) {
        HashMapGetCreate<Integer, MutableBoundingBox> bounds = new HashMapGetCreate<>(i->new MutableBoundingBox());
        loop(labelImage.getBoundingBox().resetOffset(), (x, y, z)-> {
            int label = labelImage.getPixelInt(x, y, z);
            if (label>0) bounds.getAndCreateIfNecessary(label).union(x, y, z);
        });
        return new TreeMap<>(bounds);
    }
    public static BoundingBox getBounds(ImageMask mask) {
        MutableBoundingBox bounds = new MutableBoundingBox();
        ImageMask.loop(mask, (x, y, z)->{bounds.union(x, y, z);});
        return bounds;
    }
    
    public static Region[] getObjectsImage(ImageInteger labelImage, boolean ensureContinuousLabels) {
        return getObjectsImage(labelImage, null, ensureContinuousLabels);
    }
    
    public static Region[] getObjectsImage(ImageInteger labelImage, TreeMap<Integer, BoundingBox> bounds,  boolean ensureContinuousLabels) {
        if (bounds==null) bounds = getBounds(labelImage);
        Region[] res = new Region[bounds.size()];
        int i = 0;
        
        for (Entry<Integer, BoundingBox> e : bounds.entrySet()) {
            ImageByte label = labelImage.cropLabel(e.getKey(), e.getValue());
            res[i] = new Region(label, ensureContinuousLabels?(i + 1):e.getKey(), labelImage.sizeZ()==1);
            ++i;
        }
        return res;
    }
    /**
     * 
     * @param mask
     * @return region contained within {@param mask}
     * the returned region has the same landmark as the mask
     */
    public static Region getObjectImage(ImageMask mask) {
        if (mask instanceof BlankMask) return new Region(mask, 1, mask.sizeZ()==1);
        BoundingBox bounds = getBounds(mask);
        if (bounds.sameDimensions(mask)) {
            return  new Region(mask, 1, mask.sizeZ()==1);
        } else {
            ImageByte newMask = new ImageByte("", new SimpleImageProperties(bounds,mask.getScaleXY(), mask.getScaleZ()));
            loop(bounds, (x, y, z)->{
                if (mask.insideMask(x, y, z)) newMask.setPixelWithOffset(x, y, z, 1); // bounds has for landmask mask
            });
            newMask.translate(mask); 
            return new Region(newMask, 1, mask.sizeZ()==1);
        }
    }
    public static void relabelImage(ImageInteger labelImage){
        relabelImage(labelImage, null);
    }
    
    public static void relabelImage(ImageInteger labelImage, TreeMap<Integer, BoundingBox> bounds) {
        if (bounds==null) bounds = getBounds(labelImage);
        int newLabel = 1;
        int currentLabel;
        for (Entry<Integer, BoundingBox> e : bounds.entrySet()) {
            currentLabel = e.getKey();
            if (currentLabel!=newLabel) {
                BoundingBox b= e.getValue();
                for (int z = b.zMin(); z<=b.zMax(); ++z) {
                    for (int y = b.yMin(); y<=b.yMax(); ++y) {
                        for (int x = b.xMin(); x<=b.xMax(); ++x) {
                            if (labelImage.getPixelInt(x, y, z)==currentLabel) labelImage.setPixel(x, y, z, newLabel);
                        }
                    }
                }
            }
            ++newLabel;
        }
    }
    public static List<Region> createSeedObjectsFromSeeds(List<int[]> seedsXYZ, boolean is2D, double scaleXY, double scaleZ) {
        List<Region> seedObjects = new ArrayList<>(seedsXYZ.size());
        int label = 0;
        for (int[] seed : seedsXYZ) seedObjects.add(new Region(new Voxel(seed), ++label, is2D, (float)scaleXY, (float)scaleZ));
        return seedObjects;
    }
    
}
