/*
 * Copyright (C) 2018 jollion
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
package boa.utils.geom;

import boa.data_structure.Voxel;
import boa.image.Offset;
import boa.utils.Utils;
import java.util.Arrays;
import net.imglib2.RealLocalizable;

/**
 *
 * @author jollion
 */
public class Point<T extends Point> implements Offset<T>, RealLocalizable{
    protected float[] coords;
    public Point(float... coords) {
        this.coords=coords;
    }
    public float getWithDimCheck(int dim) {
        if (dim>=coords.length) return 0;
        return coords[dim];
    }
    public float get(int dim) {
        return coords[dim];
    }
    public T setData(float... coords) {
        //System.arraycopy(coords, 0, this.coords, 0, Math.min(coords.length, this.coords.length));
        this.coords=coords;
        return (T)this;
    }
    
    public T setData(Point other) {
        this.coords=other.coords;
        return (T)this;
    }
    
    
    public T translate(Vector other) {
        for (int i = 0; i<coords.length; ++i) coords[i]+=other.coords[i];
        return (T)this;
    }
    public T translateRev(Vector other) {
        for (int i = 0; i<coords.length; ++i) coords[i]-=other.coords[i];
        return (T)this;
    }
    public T averageWith(Point other) {
        for (int i = 0; i<coords.length; ++i) coords[i] = (coords[i]+other.coords[i])/2f;
        return (T)this;
    }
    public Point duplicate() {
        return new Point(Arrays.copyOf(coords, coords.length));
    }
    public static Point middle(Offset o1, Offset o2) {
        return new Point((o1.xMin()+o2.xMin())/2f, (o1.yMin()+o2.yMin())/2f, (o1.zMin()+o2.zMin())/2f);
    }
    public static Point middle2D(Offset o1, Offset o2) {
        return new Point((o1.xMin()+o2.xMin())/2f, (o1.yMin()+o2.yMin())/2f);
    }
    public T weightedSum(Point other, double weight, double weightOther) {
        for (int i = 0; i<coords.length; ++i) coords[i] = (float)(coords[i] * weight + other.coords[i]*weightOther);
        return (T) this;
    }
    public static Point asPoint2D(Offset off) {
        return new Point(off.xMin(), off.yMin());
    }
    public static Point asPoint(Offset off) {
        return new Point(off.xMin(), off.yMin(), off.zMin());
    }
    public T toMiddlePoint() {
        for (int i = 0; i<coords.length; ++i) coords[i]/=2f;
        return (T)this;
    }
    public double distSq(Point other) {
        double d = 0;
        for (int i = 0; i<coords.length; ++i) d+=Math.pow(coords[i]-other.coords[i], 2);
        return d;
    }
    public double dist(Point other) {
        double d = 0;
        for (int i = 0; i<coords.length; ++i) d+=Math.pow(coords[i]-other.coords[i], 2);
        return Math.sqrt(d);
    }
    /**
     * Coordinates are not copies any modification on the will impact this instance 
     * @return a vector with same coordinates as this point
     */
    public Vector asVector() {
        return new Vector(coords);
    }
    public Voxel asVoxel() {
        return new Voxel(xMin(), yMin(), zMin());
    }
    // offset implementation
    @Override
    public int xMin() {
        return (int)(coords[0]+0.5);
    }

    @Override
    public int yMin() {
        return coords.length<=1 ? 0 :(int)(coords[1]+0.5);
    }

    @Override
    public int zMin() {
        return coords.length<=2 ? 0 : (int)(coords[2]+0.5);
    }

    @Override
    public T resetOffset() {
        for (int i = 0; i<coords.length; ++i) coords[i]=0;
        return (T)this;
    }

    @Override
    public T reverseOffset() {
        for (int i = 0; i<coords.length; ++i) coords[i]=-coords[i];
        return (T)this;
    }

    @Override
    public T translate(Offset other) {
        coords[0] +=other.xMin();
        if (coords.length>1) coords[1]+=other.yMin();
        if (coords.length>2) coords[2]+=other.zMin();
        return (T)this;
    }
    public T translateRev(Offset other) {
        coords[0] -=other.xMin();
        if (coords.length>1) coords[1]-=other.yMin();
        if (coords.length>2) coords[2]-=other.zMin();
        return (T)this;
    }
    // RealLocalizable implementation
    @Override
    public void localize(float[] floats) {
        System.arraycopy(coords, 0, floats, 0, Math.min(floats.length, coords.length));
    }

    @Override
    public void localize(double[] doubles) {
        for (int i = 0; i<Math.min(doubles.length, coords.length); ++i) doubles[i] = coords[i];
    }

    @Override
    public float getFloatPosition(int i) {
        return coords[i];
    }

    @Override
    public double getDoublePosition(int i) {
        return coords[i];
    }

    @Override
    public int numDimensions() {
        return coords.length;
    }
    // object methods
    @Override public String toString() {
        return Utils.toStringArray(coords);
    }
    @Override
    public int hashCode() {
        return Arrays.hashCode(coords);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Point<?> other = (Point<?>) obj;
        if (!Arrays.equals(this.coords, other.coords)) {
            return false;
        }
        return true;
    }
    public static Point intersect2D(Point line1Point1, Point line1Point2, Point line2Point1, Point line2Point2) {
        double d = (line1Point1.coords[0]-line1Point2.coords[0])*(line2Point1.coords[1]-line2Point2.coords[1]) - (line1Point1.coords[1]-line1Point2.coords[1])*(line2Point1.coords[0]-line2Point2.coords[0]);
        if (d == 0) return null;
        double xi = ((line2Point1.coords[0]-line2Point2.coords[0])*(line1Point1.coords[0]*line1Point2.coords[1]-line1Point1.coords[1]*line1Point2.coords[0])-(line1Point1.coords[0]-line1Point2.coords[0])*(line2Point1.coords[0]*line2Point2.coords[1]-line2Point1.coords[1]*line2Point2.coords[0]))/d;
        double yi = ((line2Point1.coords[1]-line2Point2.coords[1])*(line1Point1.coords[0]*line1Point2.coords[1]-line1Point1.coords[1]*line1Point2.coords[0])-(line1Point1.coords[1]-line1Point2.coords[1])*(line2Point1.coords[0]*line2Point2.coords[1]-line2Point1.coords[1]*line2Point2.coords[0]))/d;
        return new Point((float)xi, (float)yi);
    }
}