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
package utils;

import configuration.parameters.FileChooser;
import de.caluga.morphium.Morphium;
import ij.gui.Plot;
import image.Image;
import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

/**
 *
 * @author jollion
 */
public class Utils {
    private final static Pattern p = Pattern.compile("[^a-z0-9_-]", Pattern.CASE_INSENSITIVE);
    public static String getStringArrayAsString(String... stringArray) {
        if (stringArray==null) return "[]";
        String res="[";
        for (int i = 0; i<stringArray.length; ++i) {
            if (i!=0) res+="; ";
            res+=stringArray[i];
        }
        res+="]";
        return res;
    }
    
    public static String getStringArrayAsStringTrim(int maxSize, String... stringArray) {
        String array = getStringArrayAsString(stringArray);
        if (maxSize<4) maxSize=5;
        if (array.length()>=maxSize) {
            return array.substring(0, maxSize-4)+"...]";
        } else return array;
    }
    
    public static int getIndex(String[] array, String key) {
        if (key==null) return -1;
        for (int i = 0; i<array.length; i++) if (key.equals(array[i])) return i;
        return -1;
    }
    
    public static boolean isValid(String s, boolean allowSpecialCharacters) {
        if (s==null || s.length()==0) return false;
        if (allowSpecialCharacters) return true;
        Matcher m = p.matcher(s);
        return !m.find();
    }
    
    
    public static String formatInteger(int paddingSize, int number) {
        return String.format(Locale.US, "%0" + paddingSize + "d", number);
    }
    
    public static int[] toArray(ArrayList<Integer> arrayList, boolean reverseOrder) {
        int[] res=new int[arrayList.size()];
        if (reverseOrder) {
            int idx = res.length-1;
            for (int s : arrayList) res[idx--] = s;
        } else for (int i = 0; i<res.length; ++i) res[i] = arrayList.get(i);
        return res;
    }
    
    public static String[] toStringArray(Enum[] array) {
        String[] res = new String[array.length];
        for (int i = 0;i<res.length;++i) res[i]=array[i].toString();
        return res;
    }
    
    public static<T> ArrayList<T> reverseOrder(ArrayList<T> arrayList) {
        ArrayList<T> res = new ArrayList<T>(arrayList.size());
        for (int i = arrayList.size()-1; i>=0; --i) res.add(arrayList.get(i));
        return res;
    }
    
    public static void addHorizontalScrollBar(JComboBox box) {
        if (box.getItemCount() == 0) return;
        Object comp = box.getUI().getAccessibleChild(box, 0);
        if (!(comp instanceof JPopupMenu)) {
            return;
        }
        JPopupMenu popup = (JPopupMenu) comp;
        int n = popup.getComponentCount();
        int i = 0;
        while (i<n) {
            if (popup.getComponent(i) instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) popup.getComponent(i);
                scrollPane.setHorizontalScrollBar(new JScrollBar(JScrollBar.HORIZONTAL));
                scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            }
            i++;
        }
    }
    
    public static Color[] generatePalette(int colorNb, boolean excludeReds) {
        Color[] res = new Color[colorNb];
        double goldenRatioConjugate = 0.618033988749895;
        double h = 0.33;
        for(int i = 0; i <colorNb; ++i) {
            res[i] = Color.getHSBColor((float)h, 0.99f, 0.99f);
            if (excludeReds) {  // pure red = 0;
                while(h<0.05) h=incrementColor(h, goldenRatioConjugate);
            } else h=incrementColor(h, goldenRatioConjugate);
            
        }
        return res;
    }
    
    private static double incrementColor(double h, double goldenRatioConjugate) {return (h+goldenRatioConjugate)%1;}
    
    public static void plotProfile(Image image, int z, int coord, boolean alongX) {
        double[] x;
        double[] y;
        if (alongX) {
            x=new double[image.getSizeX()];
            y=new double[image.getSizeX()];
            for (int i = 0; i<x.length; ++i) {
                x[i]=i;
                y[i]=image.getPixel(i, coord, z);
            }
        } else {
            x=new double[image.getSizeY()];
            y=new double[image.getSizeY()];
            for (int i = 0; i<x.length; ++i) {
                x[i]=i;
                y[i]=image.getPixel(coord, i, z);
            }
        }
        new Plot(image.getName(), "coord", "value", x, y).show();
    }
    
    public static void plotProfile(String title, float[] values) {
        float[] x=new float[values.length];
        for (int i = 0; i<x.length; ++i) x[i]=i;
        new Plot(title, "coord", "value", x, values).show();
    }
    
    public static void deleteDirectory(File dir) { //recursive delete, because java's native function wants the dir to be empty to delete it
        if (dir==null || !dir.exists()) return;
        if (dir.isFile()) dir.delete();
        else {
            for (File f : dir.listFiles()) deleteDirectory(f);
            dir.delete();
        }
    }
    
    public static File[] chooseFile(String dialogTitle, String directory, FileChooser.FileChooserOption option, Component parent) {
        final JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(option.getOption());
        //fc.setFileHidingEnabled(false);
        fc.setMultiSelectionEnabled(option.getMultipleSelectionEnabled());
        if (directory != null) fc.setCurrentDirectory(new File(directory));
        if (dialogTitle!=null) fc.setDialogTitle(dialogTitle);
        else fc.setDialogTitle("Choose Files");
        int returnval = fc.showOpenDialog(parent);
        System.out.println("return val"+returnval);
        if (returnval == JFileChooser.APPROVE_OPTION) {
            File[] res;
            if (option.getMultipleSelectionEnabled()) res = fc.getSelectedFiles();
            else res = new File[]{fc.getSelectedFile()};
            return res;
        } else return null;
    }
    
    public static String[] convertFilesToString(File[] files) {
        if (files ==null) return null;
        String[] res = new String[files.length];
        for (int i = 0; i<files.length; ++i) res[i] = files[i].getAbsolutePath();
        return res;
    }
    
    public static int[] copyArray(int[] source) {
        int[] res = new int[source.length];
        System.arraycopy(source, 0, res, 0, source.length);
        return res;
    }
}
