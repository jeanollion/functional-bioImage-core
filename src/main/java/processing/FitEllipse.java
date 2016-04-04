/*
From ImageJ source code
Best-fitting ellipse routines by:

  Bob Rodieck
  Department of Ophthalmology, RJ-10
  University of Washington, 
  Seattle, WA, 98195
 */
package processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.IJImageWindowManager;
import static core.Processor.logger;
import dataStructure.objects.Object3D;
import dataStructure.objects.Voxel;
import ij.ImagePlus;
import ij.gui.EllipseRoi;
import ij.gui.Roi;
import ij.process.EllipseFitter;
import ij.process.ImageProcessor;
import image.BoundingBox;
import image.IJImageWrapper;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import java.awt.Rectangle;
import plugins.plugins.measurements.SimpleObjectFeature;

/**
 *
 * @author jollion
 */
public class FitEllipse {
    static final double HALFPI = 1.5707963267949;
    
    /** X centroid */
    public double xCenter;

    /** X centroid */
    public double  yCenter;
    
    /** Length of major axis */
    public double major;
    
    /** Length of minor axis */
    public double minor;
    
    /** Angle in degrees */
    public double angle;
    
    /** Angle in radians */
    public double theta;
    
    /** Initialized by makeRoi() */
    public int[] xCoordinates;
    /** Initialized by makeRoi() */
    public int[] yCoordinates;
    /** Initialized by makeRoi() */
    public int nCoordinates = 0;

    
    private int bitCount;
    private double  xsum, ysum, x2sum, y2sum, xysum;
    private ImageMask mask;
    private int left, top, width, height;
    private double   n;
    private double   xm, ym;   //mean values
    private double   u20, u02, u11;  //central moments

    //private double pw, ph;
    private boolean record;
        public void fit(ImageMask mask) {
        this.mask = mask;
        left = 0;
        top = 0;
        width = mask.getSizeX();
        height = mask.getSizeY();
        getEllipseParam();
    }
    
    void getEllipseParam() {
        double    sqrtPi = 1.772453851;
        double    a11, a12, a22, m4, z, scale, tmp, xoffset, yoffset;
        double    RealAngle;

        if (mask==null) {
            major = (width*2) / sqrtPi;
            minor = (height*2) / sqrtPi; // * Info->PixelAspectRatio;
            angle = 0.0;
            theta = 0.0;
            if (major < minor) {
                tmp = major;
                major = minor;
                minor = tmp;
                angle = 90.0;
                theta = Math.PI/2.0;
            }
            xCenter = left + width / 2.0;
            yCenter = top + height / 2.0;
            return;
        }

        computeSums();
        getMoments();
        m4 = 4.0 * Math.abs(u02 * u20 - u11 * u11);
        if (m4 < 0.000001)
            m4 = 0.000001;
        a11 = u02 / m4;
        a12 = u11 / m4;
        a22 = u20 / m4;
        xoffset = xm;
        yoffset = ym;

        tmp = a11 - a22;
        if (tmp == 0.0)
            tmp = 0.000001;
        theta = 0.5 * Math.atan(2.0 * a12 / tmp);
        if (theta < 0.0)
            theta += HALFPI;
        if (a12 > 0.0)
            theta += HALFPI;
        else if (a12 == 0.0) {
            if (a22 > a11) {
                theta = 0.0;
                tmp = a22;
                a22 = a11;
                a11 = tmp;
            } else if (a11 != a22)
                theta = HALFPI;
        }
        tmp = Math.sin(theta);
        if (tmp == 0.0)
            tmp = 0.000001;
        z = a12 * Math.cos(theta) / tmp;
        major = Math.sqrt (1.0 / Math.abs(a22 + z));
        minor = Math.sqrt (1.0 / Math.abs(a11 - z));
        scale = Math.sqrt (bitCount / (Math.PI * major * minor)); //equalize areas
        major = major*scale*2.0;
        minor = minor*scale*2.0;
        angle = 180.0 * theta / Math.PI;
        if (angle == 180.0)
            angle = 0.0;
        if (major < minor) {
            tmp = major;
            major = minor;
            minor = tmp;
        }
        xCenter = left + xoffset + 0.5;
        yCenter = top + yoffset + 0.5;
    }

    void computeSums () {
        xsum = 0.0;
        ysum = 0.0;
        x2sum = 0.0;
        y2sum = 0.0;
        xysum = 0.0;
        int bitcountOfLine;
        double   xe, ye;
        int xSumOfLine;
        for (int y=0; y<height; y++) {
            bitcountOfLine = 0;
            xSumOfLine = 0;
            int offset = y*width;
            for (int x=0; x<width; x++) {
                if (mask.insideMask(offset+x, 0)) {
                    bitcountOfLine++;
                    xSumOfLine += x;
                    x2sum += x * x;
                }
            } 
            xsum += xSumOfLine;
            ysum += bitcountOfLine * y;
            ye = y;
            xe = xSumOfLine;
            xysum += xe*ye;
            y2sum += ye*ye*bitcountOfLine;
            bitCount += bitcountOfLine;
        }
    }

    void getMoments () {
        double   x1, y1, x2, y2, xy;

        if (bitCount == 0)
            return;

        x2sum += 0.08333333 * bitCount;
        y2sum += 0.08333333 * bitCount;
        n = bitCount;
        x1 = xsum/n;
        y1 = ysum / n;
        x2 = x2sum / n;
        y2 = y2sum / n;
        xy = xysum / n;
        xm = x1;
        ym = y1;
        u20 = x2 - (x1 * x1);
        u02 = y2 - (y1 * y1);
        u11 = xy - x1 * y1;
    }
    
    public ImageByte getEllipseMask() {
        double dx = major*Math.cos(theta)/2.0;
        double dy = - major*Math.sin(theta)/2.0;
        double x1 = xCenter - dx;
        double x2 = xCenter + dx;
        double y1 = yCenter - dy;
        double y2 = yCenter + dy;
        double aspectRatio = minor/major;
        Roi roi = new EllipseRoi(x1,y1,x2,y2,aspectRatio);
        ImageProcessor ip  = roi.getMask();
        ImagePlus imp = new ImagePlus("Ellipse Mask", ip);
        ImageByte res=  (ImageByte)IJImageWrapper.wrap(imp);    
        res.addOffset(left-(int)((double)res.getSizeX()/2d-xCenter), top-(int)((double)res.getSizeY()/2d-yCenter), 0);
        // a point in this mask needs to be shifted by the offset value to correspond to the original mask
        //logger.debug("offsetX: {}, offsetY: {}", res.getBoundingBox().getxMin(), res.getBoundingBox().getyMin());
        return res;
    }

    
    public static EllipseFit2D fitEllipse2D(Object3D object) {
        FitEllipse fitter = new FitEllipse();
        ImageInteger mask = object.getMask();
        if (mask.getSizeZ()>1) mask = mask.getZPlane(mask.getSizeZ()/2);
        
        fitter.fit(mask);
        // compute the error = nPixels outside the ROI / total pixels count
        ImageByte b = fitter.getEllipseMask();
        new IJImageDisplayer().showImage(b);
        new IJImageDisplayer().showImage(mask);
        logger.debug("ellipse fit: angle: {}, major: {}, minor: {}, xCenter: {}, yCenter: {}", fitter.angle, fitter.major, fitter.minor, fitter.xCenter, fitter.yCenter);
        return null;
    }
    
    public static class EllipseFit2D {
        double error;
        Voxel center;
        public EllipseFit2D() {
            
        }
    }
    }