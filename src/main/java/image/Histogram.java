/*
 * Copyright (C) 2017 jollion
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
package image;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jollion
 */
public class Histogram {

    public final int[] data;
    public final boolean byteHisto;
    public final double[] minAndMax;

    public Histogram(int[] data, boolean byteHisto, double[] minAndMax) {
        this.data = data;
        this.byteHisto = byteHisto;
        this.minAndMax = minAndMax;
    }
    public Histogram duplicate() {
        int[] dataC = new int[256];
        System.arraycopy(data, 0, dataC, 0, 256);
        return new Histogram(dataC, byteHisto, new double[]{minAndMax[0], minAndMax[1]});
    }
    public double getHistoMinBreak() {
        if (byteHisto) return 0;
        else return minAndMax[0];
    }
    public double getBinSize() {
        return byteHisto ? 1 : (minAndMax[1] - minAndMax[0]) / 256d;
    }
    public void add(Histogram other) {
        for (int i = 0; i < 256; ++i) data[i] += other.data[i];
    }
    public void remove(Histogram other) {
        for (int i = 0; i < 256; ++i) data[i] -= other.data[i];
    }
    
    public double getValueFromIdx(double thld256) {
        if (byteHisto) return thld256;
        return convertHisto256Threshold(thld256, minAndMax);
    }
    public double getIdxFromValue(double thld) {
        if (byteHisto) {
            int idx = (int)Math.round(thld);
            if (idx<0) idx =0;
            if (idx>255) idx = 255;
            return idx;
        }
        return convertTo256Threshold(thld, minAndMax);
    }
    public static double convertHisto256Threshold(double threshold256, double[] minAndMax) {
        return threshold256 * (minAndMax[1] - minAndMax[0]) / 256.0 + minAndMax[0];
    }

    public static int convertTo256Threshold(double threshold, double[] minAndMax) {
        int res = (int) Math.round((threshold - minAndMax[0]) * 256 / ((minAndMax[1] - minAndMax[0])));
        if (res >= 256) {
            res = 255;
        }
        return res;
    }

    public static double convertHisto256Threshold(double threshold256, Image input, ImageMask mask, BoundingBox limits) {
        if (mask == null) {
            mask = new BlankMask("", input);
        }
        double[] mm = input.getMinAndMax(mask, limits);
        if (input instanceof ImageByte) {
            return threshold256;
        } else {
            return Histogram.convertHisto256Threshold(threshold256, mm);
        }
    }
    /**
     *
     * @param images
     * @param minAndMax the method will output min and max values in this array, except if minAndMax[0]<minAndMax[1] -> in this case will use these values for histogram
     * @return
     */
    public static Histogram getHisto256(List<Image> images, double[] minAndMax) {
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        Histogram histo = null;
        for (Image im : images) {
            Histogram h = im.getHisto256(minAndMax[0], minAndMax[1], null, null);
            if (histo == null) {
                histo = h;
            } else {
                histo.add(h);
            }
        }
        return histo;
    }
    public static List<Histogram> getHisto256AsList(List<Image> images, double[] minAndMax) {
        if (!(minAndMax[0] < minAndMax[1])) {
            double[] mm = ImageOperations.getMinAndMax(images);
            minAndMax[0] = mm[0];
            minAndMax[1] = mm[1];
        }
        List<Histogram> res = new ArrayList<>(images.size());
        for (Image im : images) res.add(im.getHisto256(minAndMax[0], minAndMax[1], null, null));
        return res;
    }
}
