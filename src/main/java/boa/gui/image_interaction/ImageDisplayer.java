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
package boa.gui.image_interaction;

import boa.image.BoundingBox;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import static boa.image.IJImageWrapper.getStackIndex;
import boa.image.Image;
import static boa.image.Image.logger;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.processing.ImageOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 *
 * @author Jean Ollion
 * @param <T>
 */
public interface ImageDisplayer<T> {
    public static double zoomMagnitude=1;
    public boolean isDisplayed(T image);
    public T showImage(Image image, double... displayRange);
    public void close(Image image);
    public void close(T image);
    public T getImage(Image image);
    public Image getImage(T image);
    public void updateImageDisplay(Image image, double... displayRange);
    public void updateImageRoiDisplay(Image image);
    public T showImage5D(String title, Image[][] imageTC);
    public BoundingBox getDisplayRange(Image image);
    public void setDisplayRange(BoundingBox bounds, Image image);
    public T getCurrentImage();
    public Image getCurrentImage2();
    public Image[][] getCurrentImageCT();
    public void flush();
    public void addMouseWheelListener(Image image, Predicate<BoundingBox> movementCallBack);
    //public int[] getFCZCount(T image);
    //public boolean isVisible(Image image);
    //public Image[][] reslice(Image image, int[] FCZCount);
    
    static Image[][] reslice(Image image, int[] FCZCount, Function<int[], Integer> getStackIndex) {
        if (image.sizeZ()!=FCZCount[0]*FCZCount[1]*FCZCount[2]) {
            ImageWindowManagerFactory.showImage(image.setName("slices: "+image.sizeZ()));
            throw new IllegalArgumentException("Wrong number of images ("+image.sizeZ()+" instead of "+FCZCount[0]*FCZCount[1]*FCZCount[2]);
        }
        logger.debug("reslice: FCZ:{}", FCZCount);
        Image[][] resTC = new Image[FCZCount[1]][FCZCount[0]];
        for (int f = 0; f<FCZCount[0]; ++f) {
            for (int c = 0; c<FCZCount[1]; ++c) {
                List<Image> imageSlices = new ArrayList<>(FCZCount[2]);
                for (int z = 0; z<FCZCount[2]; ++z) {
                    imageSlices.add(image.getZPlane(getStackIndex.apply(new int[]{f, c, z})));
                }
                resTC[c][f] = Image.mergeZPlanes(imageSlices);
            }
        }
        return resTC;
    }
    public static double[] getDisplayRange(Image im, ImageMask mask) {
        Histogram hist = HistogramFactory.getHistogram(()->im.stream(mask, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
        if (hist.data.length==2) return new double[]{hist.min, hist.min+hist.binSize};
        hist.removeSaturatingValue(5, true);
        hist.removeSaturatingValue(5, false);
        double[] per =  hist.getQuantiles(0.01, 0.9999);
        if (per[0]==per[1]) {
            per[0] = hist.min;
            per[1] = hist.getMaxValue();
        }
        if (per[0]>0 &&  im instanceof ImageInteger) per[0] -= 1; // possibly a label image
        return per;
    }
}
