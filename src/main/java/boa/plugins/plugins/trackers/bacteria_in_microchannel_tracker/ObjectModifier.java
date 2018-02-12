/*
 * Copyright (C) 2016 jollion
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
package boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import boa.plugins.legacy.BacteriaTrans;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;

/**
 *
 * @author jollion
 */
public abstract class ObjectModifier extends CorrectionScenario {
    protected final Map<Region, Split> splitMap = new HashMap<>();
    protected final Map<Pair<Region, Region>, Merge> mergeMap = new HashMap<>();
    protected final Map<Integer, List<Region>> objects = new HashMap<>();
    public ObjectModifier(int frameMin, int frameMax, BacteriaClosedMicrochannelTrackerLocalCorrections tracker) {
        super(frameMin, frameMax, tracker);
    }
    public List<Region> getObjects(int frame) {
        return objects.get(frame);
    }
    
    protected Split getSplit(int frame, Region o) {
        Split res = splitMap.get(o);
        if (res==null) {
            res = new Split(frame, o);
            splitMap.put(o, res);
            Pair<Region, Region> pair = res.pairValue();
            mergeMap.put(pair, new Merge(frame, pair, res.source, -res.cost));
        }
        return res;
    }
    protected Merge getMerge(int frame, Pair<Region, Region> o) {
        Merge res = mergeMap.get(o);
        if (res==null) {
            res = new Merge(frame, o);
            mergeMap.put(o, res);
            splitMap.put(res.value, new Split(frame, res.value, res.listSource(), -res.cost));
        }
        return res;
    }
    protected abstract class Correction implements Comparable<Correction> {
        final int frame;
        double cost = Double.NaN;
        Correction(int frame) {this.frame=frame;}
        protected double getCost() {return cost;}
        protected abstract void apply(List<Region> list);
        @Override public int compareTo(Correction other) {
            return Double.compare(cost, other.cost);
        }
    }
    protected class Split extends Correction {
        List<Region> values;
        final Region source;

        protected Split(int frame, Region source) {
            super(frame);
            this.source=source;
            values = new ArrayList(2);
            cost = tracker.getSegmenter(frame).split(tracker.getImage(frame), source, values);
            if (Double.isInfinite(cost) || Double.isNaN(cost) || values.size()!=2) {
                cost = Double.POSITIVE_INFINITY;
                values.clear();
            }
        }
        protected Split(int frame, Region source, List<Region> values, double cost) {
            super(frame);
            this.source=source;
            this.values = values;
            this.cost=cost;
        }

        @Override
        protected void apply(List<Region> currentObjects) {
            int i = currentObjects.indexOf(source);
            if (i<0) throw new RuntimeException("add split "+(frame)+" object not found");
            currentObjects.set(i, values.get(0));
            currentObjects.add(i+1, values.get(1)); 
        }
        
        public Pair<Region, Region> pairValue() {
            if (values.size()!=2) return new Pair(null, null);
            return new Pair(values.get(0), values.get(1));
        }
    }
    protected class Merge extends Correction {
        final Pair<Region, Region> source; 
        final Region value;

        public Merge(int frame, Pair<Region, Region> source) {
            super(frame);
            this.source = source;
            cost = tracker.getSegmenter(frame).computeMergeCost(tracker.getImage(frame), listSource());
            List<Voxel> vox = new ArrayList(source.key.getVoxels().size()+source.value.getVoxels().size());
            vox.addAll(source.key.getVoxels()); vox.addAll(source.value.getVoxels());
            value =new Region(vox, source.key.getLabel(), source.key.is2D(), source.key.getScaleXY(), source.key.getScaleZ());
        }
        public Merge(int frame, Pair<Region, Region> source, Region value, double cost) {
            super(frame);
            this.source=source;
            this.value= value;
            this.cost=cost;
        }
        private List<Region> listSource() {
            return new ArrayList<Region>(2){{add(source.key);add(source.value);}};
        }

        @Override
        protected void apply(List<Region> currentObjects) {
            int i = currentObjects.indexOf(source.key);
            int i2 = currentObjects.indexOf(source.value);
            if (i<0) throw new RuntimeException("ObjectModifier Error: frame:"+(frame)+" object 1 not found");
            if (i2<0) throw new RuntimeException("ObjectModifier Error: frame:"+(frame)+" object 2 not found");
            currentObjects.set(Math.min(i, i2), value);
            currentObjects.remove(Math.max(i, i2));
        }
    }
}