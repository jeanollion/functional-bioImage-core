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
import boa.core.DefaultWorker;
import boa.core.DefaultWorker.WorkerTask;
import boa.core.ProgressCallback;
import boa.image.Image;
import boa.image.ImageFloat;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingWorker;
import boa.image.processing.Filters;
import boa.ui.logger.ProgressLogger;

/**
 *
 * @author Jean Ollion
 */
public class TestThreadExecutorFrameWork {
    static ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    public static void test() {
        
        
        Worker w = new Worker("A", 10);
        w.execute();
        Worker w2 = new Worker("B", 10);
        w2.execute();
    }
    public static Callable<String> getTask(String name, int taskNumber) {
        return () -> {
            return runTask(name, taskNumber, null);
        };
    }
    public static String runTask(String name, int taskNumber, ProgressCallback pcb) {
        long t0 = System.currentTimeMillis();
        Image im = new ImageFloat("", 500, 500, 10);
        Filters.open(im, im, Filters.getNeighborhood(3, 2, im), false);
        long t1 = System.currentTimeMillis();
        String message = name+":"+taskNumber+":"+(t1-t0)+"ms";
        if (pcb!=null) pcb.log(message);
        return message;
    }
    static class Worker extends SwingWorker<Integer, String> implements ProgressCallback {
        final ProgressLogger gui = GUI.getInstance();
        final int maxTaskIdx;
        final String name;
        int totalTasks = 0;
        int currentTask= 0;
        public Worker(String name, int maxTaskIdx) {
            this.maxTaskIdx=maxTaskIdx;
            this.name = name;
            if (gui!=null) {
            addPropertyChangeListener(new PropertyChangeListener() {
                    @Override    
                    public void propertyChange(PropertyChangeEvent evt) {
                        if ("progress".equals(evt.getPropertyName())) {
                            int progress = (Integer) evt.getNewValue();
                            gui.setProgress(progress);
                        }
                    }
                });
            }
        }
        
        @Override
        protected Integer doInBackground() throws Exception {
            List<Integer> tasks = Utils.toList(ArrayUtil.generateIntegerArray(maxTaskIdx));
            ThreadRunner.execute(tasks, false, (taskIdx, idx)->{runTask(name, taskIdx, this);}, executor, this);
            publish(name+" end of first round");
            ThreadRunner.execute(tasks, false, (taskIdx, idx)->{runTask(name, taskIdx, this);}, executor, this);
            publish(name+" end of second round");
            
            return 0;
        }
        
        @Override
        protected void process(List<String> strings) {
            if (gui!=null) {
                for (String s : strings) gui.setMessage(s);
            } 
        }

        @Override 
        public void done() {
            gui.setMessage(name + " all processes done!");
        }

        @Override
        public void incrementTaskNumber(int subtask) {
            this.totalTasks+=subtask;
        }

        @Override
        public void incrementProgress() {
            setProgress(100*(++this.currentTask)/totalTasks);
        }

        @Override
        public void log(String message) {
            publish(message);
        }
    }
}
