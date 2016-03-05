package outlierdetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import mtree.MTree;

import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.FibonacciHeap.Node;
import mtree.utils.Utils;

public class Direct_Update_Event extends Lazy_Update_Event {

    public static double numberPointsInEventQueue = 0;
    public static double avgAllWindowNumberPoints = 0;

    public static boolean isSameSlide(DataLUEObject d1, DataLUEObject d2) {
        return (d1.arrivalTime - 1) / Constants.slide == (d2.arrivalTime - 1) / Constants.slide;
    }

    @Override
    public HashSet<DataLUEObject> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {

//        int minArrivalTime = currentTime-slide;
        /**
         * remove expired data from dataList and mtree
         */
        long startTime = Utils.getCPUTime();

        if (slide != W) {
            int index = -1;
            for (int i = 0; i < dataList.size(); i++) {
                DataLUEObject d = dataList.get(i);
                if (d.arrivalTime <= currentTime - W) {

                    outlierList.remove(d);
                    // mark here for removing data from datalist later
                    index = i;
                    // remove from mtree
                    long start3 = Utils.getCPUTime();
                    mtree.remove(d);
                    d.p_neighbors.clear();
                    if (links.containsKey(d)) {
                        eventQueue.delete(links.get(d));
                        links.remove(d);
                    }
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - start3;

                } else {
                    break;
                }
            }
            outlierList.stream().map((outlierList1) -> (DataLUEObject) outlierList1).forEach((d2) -> {
                while (d2.p_neighbors.size() > 0 && d2.p_neighbors.get(0).expireTime <= currentTime) {
                    d2.p_neighbors.remove(0);
                }
            });

            process_event_queue(null, currentTime);

            for (int i = index; i >= 0; i--) {

                dataList.remove(i);
            }
        } else {
            dataList.clear();
            mtree = null;
            mtree = new MTreeClassLUE();

            links.clear();
            eventQueue.clear();
            outlierList.clear();
        }
        MesureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;
        startTime = Utils.getCPUTime();
        // Runtime.getRuntime().gc();
        data.stream().map((d) -> new DataLUEObject(d, currentTime)).map((p) -> {
            /**
             * do range query for ob
             */

            MTreeClassLUE.Query query = mtree.getNearestByRange(p, Constants.R);
            ArrayList<DataLUEObject> queryResult = new ArrayList<>();

            for (MTree.ResultItem ri : query) {
                queryResult.add((DataLUEObject) ri.data);
            }

            Collections.sort(queryResult, new DataDUEComparator());
            for (DataLUEObject q : queryResult) {

                if (q.arrivalTime >= currentTime - Constants.W) {
                    q.numberSuccedingNeighbors = q.numberSuccedingNeighbors + 1;
                    if (isSameSlide(q, p)) {
                        p.numberSuccedingNeighbors++;
                    } else {
                        if (p.p_neighbors.size() < Constants.k - p.numberSuccedingNeighbors) {
                            p.p_neighbors.add(q);
                        }
                    }
                    if (q.p_neighbors.size() + q.numberSuccedingNeighbors <= Constants.k) {
                        if (q.p_neighbors.size() + q.numberSuccedingNeighbors == Constants.k) {
                            outlierList.remove(q);
                            if (q.numberSuccedingNeighbors < Constants.k) {
                                q.ev = q.p_neighbors.get(0).expireTime;

                                Node<DataLUEObject> node = eventQueue.insert(q);
                                links.put(q, node);
                            }
                        }
                    } else if (q.p_neighbors.size() + q.numberSuccedingNeighbors > Constants.k
                            && q.p_neighbors.size() > 0 && q.numberSuccedingNeighbors - 1 < Constants.k) {

                        Node<DataLUEObject> node = links.get(q);
                        q.p_neighbors.remove(0);
                        if (q.p_neighbors.size() > 0) {
                            q.ev = q.p_neighbors.get(0).expireTime;

                            eventQueue.increaseKey(node, q);
                        }

                    }

                }
            }
            return p;
        }).map((p) -> {
            Collections.sort(p.p_neighbors, new DataExpireTimeLUEComparator());
            return p;
        }).map((p) -> {
            while (p.p_neighbors.size() > Constants.k - p.numberSuccedingNeighbors && p.p_neighbors.size() > 0) {
                p.p_neighbors.remove(0);
            }
            return p;
        }).map((p) -> {
            if (p.p_neighbors.size() + p.numberSuccedingNeighbors < Constants.k) {
                outlierList.add(p);
            } else if (p.numberSuccedingNeighbors < Constants.k
                    && p.p_neighbors.size() + p.numberSuccedingNeighbors >= Constants.k) {
                p.ev = p.p_neighbors.get(0).expireTime;
                Node<DataLUEObject> node = eventQueue.insert(p);
                links.put(p, node);
            }
            return p;
        }).map((p) -> {
            long startTime2 = Utils.getCPUTime();
            try {
                mtree.add(p);
            } catch (Exception e) {
            }
            MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;
            return p;
        }).forEach((p) -> {
            dataList.add(p);
        });

        MesureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;

        //compute number of points in trigger list
        if (eventQueue.size > numberPointsInEventQueue) {
            numberPointsInEventQueue = eventQueue.size;
        }

        System.out.println("Size of outlier list = " + outlierList.size());
        return outlierList;
    }

}

class DataDUEComparator implements Comparator<DataLUEObject> {

    @Override
    public int compare(DataLUEObject o1, DataLUEObject o2) {
        if (o1.arrivalTime < o2.arrivalTime) {
            return 1;
        } else if (o1.arrivalTime == o2.arrivalTime) {
            return 0;
        } else {
            return -1;
        }
    }
};
