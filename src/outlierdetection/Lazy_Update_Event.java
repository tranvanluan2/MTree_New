package outlierdetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import mtree.tests.Data;
import mtree.utils.Constants;
import mtree.utils.FibonacciHeap;
import mtree.utils.FibonacciHeap.Node;

public class Lazy_Update_Event {

    public static FibonacciHeap<DataLUEObject> eventQueue = new FibonacciHeap<>();
    public static HashMap<DataLUEObject, Node<DataLUEObject>> links = new HashMap<>();

    public static HashSet<DataLUEObject> outlierList = new HashSet<>();
    public static MTreeClassLUE mtree = new MTreeClassLUE();
    // store list id in increasing time arrival order
    public static ArrayList<DataLUEObject> dataList = new ArrayList<>();

    public HashSet<DataLUEObject> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {

        /**
         * remove expired data from dataList and mtree
         */
//        long startTime = System.currentTimeMillis();
        int index = -1;
        for (int i = 0; i < dataList.size(); i++) {
            DataLUEObject d = dataList.get(i);
            if (d.arrivalTime <= currentTime - W) {
                // mark here for removing data from datalist later
                index = i;
                // remove from mtree
                mtree.remove(d);
                
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

        // Runtime.getRuntime().gc();
        data.stream().map((d) -> new DataLUEObject(d, currentTime)).map((p) -> {
            /**
             * do range query for ob
             */
            MTreeClassLUE.Query query = mtree.getNearestByRange(p, Constants.R);
            for (MTreeClassLUE.ResultItem ri : query) {
                if (ri.distance == 0) {
                    p.values[0] += (new Random()).nextDouble() / 1000000;
                }

                DataLUEObject q = (DataLUEObject) ri.data;
                if (q.arrivalTime >= currentTime - Constants.W) {
                    q.numberSuccedingNeighbors = q.numberSuccedingNeighbors + 1;
                    p.p_neighbors.add(q);
                    if (q.p_neighbors.size() + q.numberSuccedingNeighbors <= Constants.k) {
                        if (q.p_neighbors.size() + q.numberSuccedingNeighbors == Constants.k) {
                            outlierList.remove(q);
                            if (q.numberSuccedingNeighbors < Constants.k) {
                                q.ev = q.p_neighbors.get(0).expireTime;

                                Node<DataLUEObject> node = eventQueue.insert(q);
                                links.put(q, node);
                            }
                        }
                    }
                  
                }
            }
            return p;
        }).map((p) -> {
            Collections.sort(p.p_neighbors, new DataExpireTimeLUEComparator());
            return p;
        }).map((p) -> {
            while (p.p_neighbors.size() > Constants.k) {
                p.p_neighbors.remove(0);
            }
            return p;
        }).map((p) -> {
            if (p.p_neighbors.size() < Constants.k) {
                outlierList.add(p);
            } else {
                p.ev = p.p_neighbors.get(0).expireTime;
                Node<DataLUEObject> node = eventQueue.insert(p);
                links.put(p, node);
            }
            return p;
        }).map((p) -> {
            mtree.add(p);
            return p;
        }).forEach((p) -> {
            dataList.add(p);
        });

        return outlierList;
    }

    public Integer minIndex(ArrayList<Integer> objects) {
        if (objects.isEmpty()) {
            return null;
        }
        int result = 0;
        Integer min = objects.get(0);
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) < min) {
                result = i;
            }
        }
        return result;
    }

    public int min(ArrayList<Integer> objects) {
        int min = objects.get(0);
        for (Integer d : objects) {
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    public void process_event_queue(ArrayList<DataLUEObject> expireData, int currentTime) {
        DataLUEObject x = null;
        if (eventQueue.findMinimum() != null) {
            x = eventQueue.findMinimum().getKey();
        }
        
      
        while (x != null && (x.ev <= currentTime || x.arrivalTime <= currentTime-Constants.W)) {
            x = eventQueue.extractMin().getKey();
            
            links.remove(x);
            
            
            if (x.p_neighbors.size() > 0 && x.expireTime > currentTime) {
                while (x.p_neighbors.size() > 0 && x.p_neighbors.get(0).expireTime <= currentTime) {
                    x.p_neighbors.remove(0);
                    if (x.p_neighbors.isEmpty()) {
                        x.ev = Integer.MAX_VALUE;
                    } else x.ev = x.p_neighbors.get(0).expireTime;
                }

                if (x.p_neighbors.size() + x.numberSuccedingNeighbors < Constants.k && x.arrivalTime > currentTime-Constants.W) {
                    outlierList.add(x);

                } else if (x.p_neighbors.size() + x.numberSuccedingNeighbors >= Constants.k
                        && x.numberSuccedingNeighbors < Constants.k && x.p_neighbors.size() > 0 && x.arrivalTime > currentTime-Constants.W) {
                    x.ev = x.p_neighbors.get(0).expireTime;
                    if (x.ev > currentTime  && x.p_neighbors.size() > 0 && x.arrivalTime > currentTime-Constants.W ) {
                        Node<DataLUEObject> node = eventQueue.insert(x);
                        links.put(x, node);
                    }
                }

            }

            
            // }
            if (eventQueue.findMinimum() != null) {
                x = eventQueue.findMinimum().getKey();
            } else {
                x = null;
            }

        }

    }

}

class DataLUEComparator implements Comparator<DataLUEObject> {

    @Override
    public int compare(DataLUEObject o1, DataLUEObject o2) {
        if (o1.ev < o2.ev) {
            return -1;
        } else if (o1.ev == o2.ev) {
            return 0;
        } else {
            return 1;
        }
    }

};

class DataExpireTimeLUEComparator implements Comparator<DataLUEObject> {

    @Override
    public int compare(DataLUEObject o1, DataLUEObject o2) {
        if (o1.expireTime < o2.expireTime) {
            return -1;
        } else if (o1.expireTime == o2.expireTime) {
            return 0;
        } else {
            return 1;
        }
    }
};
