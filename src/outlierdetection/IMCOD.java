/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Utils;

/**
 * 
 * @author Luan
 */
public class IMCOD {

    public static HashMap<Integer, Double> averageOld = new HashMap<>();

    public static HashMap<Integer, MCO> dataList_set = new HashMap<>();
    public static HashMap<Integer, ArrayList<MCO>> micro_clusters = new HashMap<>();
    public static ArrayList<MCO> PD = new ArrayList<>();
    // store list ob in increasing time arrival order
    public static ArrayList<MCO> dataList = new ArrayList<>();
    public static MTreeClass mtree = new MTreeClass();
    public static ArrayList<MCO> outlierList = new ArrayList<>();
    public static PriorityQueue<MCO> eventQueue = new PriorityQueue<>(new MCComparator());

    public static double avgNumPointsInClusters = 0;
    public static double avgNumPointsInEventQueue = 0;
    public static double avgNeighborListLength = 0;

    public static double avgNumberOfClusters = 0;
    public static double timeForFindCluster = 0;
    public static double timeForAddToPD = 0;
    public static double timeForAddToCluster = 0;
    public static double timeForRemovePointFromCluster = 0;
    public static double timeForRemovePointFromPD = 0;
    public static double timeForUpdateAffectedPointInCluster = 0;
    public static double timeForUpdateAffectedPointInEventQueue = 0;
    public static double timeForFormNewCluster = 0;
    int numWindows = 0;
    public static int numberDispersedClusters = 0;
    
    public static HashMap<Integer, Integer> clusterScores = new HashMap<>();

    public static void updateClusterScore(){
        for(Integer cluster_id : micro_clusters.keySet()){
            int numExpires = 0;
            ArrayList<MCO> members = micro_clusters.get(cluster_id);
            for (MCO o : members) {
                if(o.arrivalTime < dataList.get(0).arrivalTime + Constants.slide )
                    numExpires++;
            }
            int numAlive = members.size() - numExpires;
            if(numAlive < Constants.k) {
                clusterScores.put(cluster_id, Constants.k - numAlive);
            }
            else clusterScores.put(cluster_id, 0);
        }
    }
    public static void updateClusterOld() {

        for (Integer cluster_id : micro_clusters.keySet()) {
            int sum = 0;
            int count = 0;
            ArrayList<MCO> members = micro_clusters.get(cluster_id);
            for (MCO o : members) {
                
                if(o.arrivalTime > dataList.get(0).arrivalTime + Constants.slide)
                {
                    sum += o.arrivalTime;
                    count ++;
                }
            }

            if(count != 0) 
                averageOld.put(cluster_id, sum * 1.0 / count);
            else averageOld.put(cluster_id, 0.0);

        }

    }

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {

        ArrayList<Data> result = new ArrayList<>();

        if (slide != W) {
            //purge expire object
            for (int i = dataList.size() - 1; i >= 0; i--) {
                MCO d = dataList.get(i);
                if (d.arrivalTime <= currentTime - W) {
                    //remove d from data List 
                    dataList.remove(i);

                    //if d is in cluster 
                    if (d.isInCluster) {
                        removeFromCluster(d);
                    }
                    //if d is PD 

                    removeFromPD(d);
                    //process event queue
                    long startTime3 = Utils.getCPUTime();
                    process_event_queue(currentTime);
                    timeForUpdateAffectedPointInEventQueue += Utils.getCPUTime() - startTime3;

                }
            }
        } else {
            long startTime3 = Utils.getCPUTime();
            micro_clusters.clear();
            timeForRemovePointFromCluster += Utils.getCPUTime() - startTime3;
            dataList.clear();
            dataList_set.clear();
            startTime3 = Utils.getCPUTime();
            eventQueue.clear();
            mtree = null;
            mtree = new MTreeClass();
            PD.clear();

            outlierList.clear();
            timeForRemovePointFromPD += Utils.getCPUTime() - startTime3;
        }
        //process new data
        data.stream().forEach((d) -> {
            processNewData(d);
        });

        //add result
        outlierList.stream().forEach((o) -> {
            result.add(o);
        });
        updateClusterOld();
        updateClusterScore();
        printStatistic();
        return result;
    }

    public void printStatistic() {
        numWindows++;
        int numPoints = computeNumberOfPointsInCluster();
        System.out.println("#points in clusters = " + numPoints);
        avgNumPointsInClusters += numPoints;

        System.out.println("#points in event queue = " + eventQueue.size());
        avgNumPointsInEventQueue += eventQueue.size();

        avgNeighborListLength += computeAvgNeighborList();
        System.out.println("avg neighborList length = " + computeAvgNeighborList());

        avgNumberOfClusters = (avgNumberOfClusters * (numWindows - 1) + micro_clusters.size()) / numWindows;
        System.out.println("avg number of clusters = " + avgNumberOfClusters);
        
        System.out.println("Number of dispersed clusters = "+ numberDispersedClusters);

    }

    public double computeAvgNeighborList() {
        double result = 0;
        for (MCO point : PD) {
            result += point.exps.size();
        }
        return result / PD.size();
    }

    public int computeNumberOfPointsInCluster() {
        int count = 0;
        for (ArrayList<MCO> points : micro_clusters.values()) {
            count += points.size();
        }
        return count;
    }

//    public void addToHashMap(Integer o1, Integer o2) {
//        ArrayList<Integer> values = checkedPoints.get(o1);
//        if (values != null) {
//            values.add(o2);
//            checkedPoints.put(o1, values);
//        } else {
//            values = new ArrayList<>();
//            values.add(o2);
//            checkedPoints.put(o1, values);
//        }
//    }
//    public boolean checkInHashMap(Integer key, Integer v) {
//        ArrayList<Integer> values = checkedPoints.get(key);
//        return values != null && values.contains(v);
//    }
    private void removeFromCluster(MCO d) {

        //get the cluster
        ArrayList<MCO> cluster = micro_clusters.get(d.center);
        if (cluster != null) {
            long startTime3 = Utils.getCPUTime();
            cluster.remove(d);
            micro_clusters.put(d.center, cluster);
            timeForRemovePointFromCluster += Utils.getCPUTime() - startTime3;

            //cluster is shrinked 
            startTime3 = Utils.getCPUTime();
            if (cluster.size() < Constants.k + 1) {
                //remove this cluster from micro cluster list 
                numberDispersedClusters++;
                micro_clusters.remove(d.center);
                dataList_set.remove(d.center);
                Collections.sort(cluster, new MCComparatorArrivalTime());
                //process the objects in clusters 
                for (int i = 0; i < cluster.size(); i++) {
                    MCO o = cluster.get(i);
                    //reset all objects 
                    resetObject(o);
                    //put into PD 

                    o.numberOfSucceeding = o.numberOfSucceeding + cluster.size() - 1 - i;
                    addToPD(o, true);

                }

            }
            timeForUpdateAffectedPointInCluster += Utils.getCPUTime() - startTime3;
        }

    }

    private void removeFromPD(MCO d) {
        long startTime3 = Utils.getCPUTime();
        
//remove from pd
        PD.remove(d);
//        mtree.remove(d);

        //if d is in outlier list 
        if (d.numberOfSucceeding + d.exps.size() < Constants.k) {
            outlierList.remove(d);
        }

        timeForRemovePointFromPD += Utils.getCPUTime() - startTime3;

        startTime3 = Utils.getCPUTime();
        outlierList.stream().forEach((data) -> {
            while (data.exps.size() > 0 && data.exps.get(0) <= d.arrivalTime + Constants.W) {
                data.exps.remove(0);
                if (data.exps.isEmpty()) {
                    data.ev = 0;
                } else {
                    data.ev = data.exps.get(0);
                }
            }
        });
        timeForUpdateAffectedPointInEventQueue += Utils.getCPUTime() - startTime3;
    }

    private void resetObject(MCO o) {
        o.exps.clear();
        o.Rmc.clear();
        o.isCenter = false;
        o.isInCluster = false;
        o.ev = 0;
        o.center = -1;
        o.numberOfSucceeding = 0;

    }

    public void appendToFile(String filename, String str) throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename, true)))) {
            out.println(str);
        } catch (IOException e) {
            //exception handling left as an exercise for the reader
        }
    }

    private void addToPD(MCO o, boolean fromCluster) {

        PD.stream().forEach((inPD) -> {
            //compute distance
            double distance = mtree.getDistanceFunction().calculate(o, inPD);
            if (distance <= Constants.R) {
                //check inPD is succeeding or preceding neighbor
                if (isSameSlide(inPD, o) == -1) {
                    //is preceeding neighbor
                    o.exps.add(inPD.arrivalTime + Constants.W);
                    if (!fromCluster) {
                        inPD.numberOfSucceeding++;
                    }
                } else if (isSameSlide(inPD, o) == 0) {
                    o.numberOfSucceeding++;
                    if (!fromCluster) {
                        inPD.numberOfSucceeding++;
                    }
                } else {
                    o.numberOfSucceeding++;
                    if (!fromCluster) {
                        inPD.exps.add(o.arrivalTime + Constants.W);
                    }

                }
                //just keep k-numberofSucceedingNeighbor
                if (!fromCluster) {
                    checkInlier(inPD);
                }

            }
        });

        //find neighbors in clusters (3R/2)
        ArrayList<Integer> clusters = findClusterIn3_2Range(o);
        clusters.stream().map((center_id) -> micro_clusters.get(center_id)).forEach((points) -> {
            points.stream().filter((p) -> (isNeighbor(p, o))).forEach((p) -> {
                if (isSameSlide(o, p) <= 0) {
                    //o is preceeding neighbor
                    o.numberOfSucceeding++;
                } else {
                    //p is preceeding neighbor
                    o.exps.add(p.arrivalTime + Constants.W);
                }
            });
        });

        //keep k-numberofSucceedingNeighbor of o
        checkInlier(o);

        PD.add(o);
//        mtree.add(o);

    }

    public int isSameSlide(MCO o1, MCO o2) {
        if ((o1.arrivalTime - 1) / Constants.slide == (o2.arrivalTime - 1) / Constants.slide) {
            return 0;
        } else if ((o1.arrivalTime - 1) / Constants.slide < (o2.arrivalTime - 1) / Constants.slide) {
            return -1;
        } else {
            return 1;
        }
    }

    public int findNearestCenter(MCO d) {

        double min_distance = Double.MAX_VALUE;
        int min_center_id = -1;
        for (Integer center_id : micro_clusters.keySet()) {
            //get the center object
            MCO center = dataList_set.get(center_id);
            //compute the distance
            double distance = mtree.getDistanceFunction().calculate(center, d);

            if (distance < min_distance) {
                min_distance = distance;
                min_center_id = center_id;
            }
        }
        return min_center_id;

    }

    public int findNearestAndOldestCenter(MCO d) {

        double min_distance = Double.MAX_VALUE;
        int min_center_id = -1;
        double minAge = Double.MAX_VALUE;
        for (Integer center_id : micro_clusters.keySet()) {
            //get the center object
            MCO center = dataList_set.get(center_id);
            //compute the distance
            double distance = mtree.getDistanceFunction().calculate(center, d);

            if (distance <= Constants.R / 2) {
                if (averageOld.get(center_id) == null) {
                    min_distance = distance;
                    min_center_id = center_id;
                } else if (averageOld.get(center_id) != null) {
                    if (averageOld.get(center_id) < minAge) {
                        minAge = averageOld.get(center_id);
                        min_distance = distance;
                        min_center_id = center_id;
                    }
                }
            }
        }
        return min_center_id;

    }

    public int findNearestAndNewestCenter(MCO d) {

        double min_distance = Double.MAX_VALUE;
        int min_center_id = -1;
        double maxAge = Double.MIN_VALUE;
        for (Integer center_id : micro_clusters.keySet()) {
            //get the center object
            MCO center = dataList_set.get(center_id);
            //compute the distance
            double distance = mtree.getDistanceFunction().calculate(center, d);

            if (distance <= Constants.R / 2) {
                if (averageOld.get(center_id) == null) {
                    min_distance = distance;
                    min_center_id = center_id;
                } else if (averageOld.get(center_id) != null) {
                    if (averageOld.get(center_id) > maxAge) {
                        maxAge = averageOld.get(center_id);
                        min_distance = distance;
                        min_center_id = center_id;
                    }
                }
            }
        }
        return min_center_id;

    }
    
    public int findNearestAndHighestScore(MCO d){
        double min_distance = Double.MAX_VALUE;
        int min_center_id = -1;
        double minScore = Double.MIN_VALUE;
        for (Integer center_id : micro_clusters.keySet()) {
            //get the center object
            MCO center = dataList_set.get(center_id);
            //compute the distance
            double distance = mtree.getDistanceFunction().calculate(center, d);

            if (distance <= Constants.R / 2) {
                if (clusterScores.get(center_id) == null) {
                    min_distance = distance;
                    min_center_id = center_id;
                } else if (clusterScores.get(center_id) != null) {
                    if (clusterScores.get(center_id) > minScore &&( clusterScores.get(center_id) > 0 
                            || min_center_id == -1)) {
                        minScore = clusterScores.get(center_id);
                        min_distance = distance;
                        min_center_id = center_id;
                    }
                }
            }
        }
        return min_center_id;
    }

    public int findNearestAndLowestScore(MCO d){
        double min_distance = Double.MAX_VALUE;
        int min_center_id = -1;
        double minScore = Double.MAX_VALUE;
        for (Integer center_id : micro_clusters.keySet()) {
            //get the center object
            MCO center = dataList_set.get(center_id);
            //compute the distance
            double distance = mtree.getDistanceFunction().calculate(center, d);

            if (distance <= Constants.R / 2) {
                if (clusterScores.get(center_id) == null) {
                    min_distance = distance;
                    min_center_id = center_id;
                } else if (clusterScores.get(center_id) != null) {
                    if (clusterScores.get(center_id) < minScore &&( clusterScores.get(center_id) > 0 
                            || min_center_id == -1)) {
                        minScore = clusterScores.get(center_id);
                        min_distance = distance;
                        min_center_id = center_id;
                    }
                }
            }
        }
        return min_center_id;
    }

    public ArrayList<Integer> findClusterIn3_2Range(MCO d) {
        ArrayList<Integer> result = new ArrayList<>();
        micro_clusters.keySet().stream().forEach((center_id) -> {
            //get the center object
            MCO center = dataList_set.get(center_id);
            //compute the distance
            double distance = mtree.getDistanceFunction().calculate(center, d);
            if (distance <= Constants.R * 3.0 / 2) {
                result.add(center_id);
            }
        });
        return result;
    }

    private void processNewData(Data data) {

        MCO d = new MCO(data);

        //add to datalist
        dataList.add(d);

        long startTime3 = Utils.getCPUTime();
        int nearest_center_id = findNearestAndNewestCenter(d);
        double min_distance = Double.MAX_VALUE;
        if (nearest_center_id > -1) { //found neareast cluster
            min_distance = mtree.getDistanceFunction().
                    calculate(dataList_set.get(nearest_center_id), d);
        }
        MesureMemoryThread.timeForQuerying += Utils.getCPUTime() - startTime3;

        //assign to cluster if min distance <= R/2
        if (min_distance <= Constants.R / 2) {
            startTime3 = Utils.getCPUTime();
            addToCluster(nearest_center_id, d);
            timeForAddToCluster += Utils.getCPUTime() - startTime3;

        } else {
            //find all neighbors for d in PD that can  form a cluster
            startTime3 = Utils.getCPUTime();
            ArrayList<MCO> neighborsInR2Distance = findNeighborR2InPD(d);
            MesureMemoryThread.timeForQuerying += Utils.getCPUTime() - startTime3;

            if (neighborsInR2Distance.size() >= Constants.k * 1.1) {
                //form new cluster
                startTime3 = Utils.getCPUTime();
                formNewCluster(d, neighborsInR2Distance);
                timeForFormNewCluster += Utils.getCPUTime() - startTime3;

            } else {
                //cannot form a new cluster
                startTime3 = Utils.getCPUTime();
                addToPD(d, false);
                timeForAddToPD += Utils.getCPUTime() - startTime3;
            }
        }
        if (d.isCenter) {
            dataList_set.put(d.arrivalTime, d);
        }

    }

    private void addToCluster(int nearest_center_id, MCO d) {

        long startTime3 = Utils.getCPUTime();
        //update for points in cluster
        d.isCenter = false;
        d.isInCluster = true;
        d.center = nearest_center_id;
        ArrayList<MCO> cluster = micro_clusters.get(nearest_center_id);
        cluster.add(d);
        micro_clusters.put(nearest_center_id, cluster);
        
        
        //recompute cluster_score
        if(clusterScores.get(nearest_center_id)!=null && clusterScores.get(nearest_center_id)!=0)
            clusterScores.put(nearest_center_id, clusterScores.get(nearest_center_id)-1);
        //end recomputation
        
        MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime3;

        //update for points in PD that has Rmc list contains center
        PD.stream().filter((inPD) -> (inPD.Rmc.contains(nearest_center_id))).forEach((inPD) -> {
            //check if inPD is neighbor of d
            double distance = mtree.getDistanceFunction().
                    calculate(d, inPD);
            if (distance <= Constants.R) {
                if (isSameSlide(d, inPD) == -1) {
                    inPD.exps.add(d.arrivalTime + Constants.W);

                } else if (isSameSlide(d, inPD) >= 0) {
                    inPD.numberOfSucceeding++;
                }
                //mark inPD has checked with d
//                    addToHashMap(inPD.arrivalTime,d.arrivalTime);
                //check if inPD become inlier
                checkInlier(inPD);
            }
        });

    }

    public ArrayList<MCO> findNeighborR2InPD(MCO d) {
        ArrayList<MCO> results = new ArrayList<>();
        PD.stream().filter((o) -> (mtree.getDistanceFunction().calculate(o, d) <= Constants.R * 1.0 / 2)).forEach((o) -> {
            results.add(o);
        });
        return results;
    }

    public boolean isOutlier(MCO d) {
        return d.numberOfSucceeding + d.exps.size() < Constants.k;
    }

    private void formNewCluster(MCO d, ArrayList<MCO> neighborsInR2Distance) {

        long startTime3 = Utils.getCPUTime();
        d.isCenter = true;
        d.isInCluster = true;
        d.center = d.arrivalTime;
        neighborsInR2Distance.stream().map((data) -> {
            PD.remove(data);
            return data;
        }).map((data) -> {
            if (isOutlier(data)) {
                outlierList.remove(data);
            }
            return data;
        }).map((data) -> {
            if (!isOutlier(data)) {
                eventQueue.remove(data);
            }
            return data;
        }).map((data) -> {
            resetObject(data);
            return data;
        }).map((data) -> {
            data.isInCluster = true;
            return data;
        }).map((data) -> {
            data.center = d.arrivalTime;
            return data;
        }).forEach((data) -> {
            data.isCenter = false;
        });

        //add center to neighbor list
        Collections.sort(neighborsInR2Distance, new MCComparatorArrivalTime());
        neighborsInR2Distance.add(d);
        micro_clusters.put(d.arrivalTime, neighborsInR2Distance);
        MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime3;

        //update Rmc list
        ArrayList<MCO> list_rmc = findNeighborInR3_2InPD(d);
        list_rmc.stream().map((o) -> {
            if (isNeighbor(o, d)) {
                if (isSameSlide(o, d) <= 0) {
                    o.numberOfSucceeding++;
                } else {
                    o.exps.add(d.arrivalTime + Constants.W);
                }
//                addToHashMap(o.arrivalTime,d.arrivalTime);
                checkInlier(o);

            }
            return o;
        }).forEach((o) -> {
            o.Rmc.add(d.arrivalTime);
        });

    }

    private ArrayList<MCO> findNeighborInRInPD(MCO d) {

        ArrayList<MCO> result = new ArrayList<>();

        PD.stream().filter((o) -> (mtree.getDistanceFunction().calculate(o, d) <= Constants.R)).forEach((o) -> {
            result.add(o);
        });
        return result;
    }

    private ArrayList<MCO> findNeighborInR3_2InPD(MCO d) {

        ArrayList<MCO> result = new ArrayList<>();

        PD.stream().forEach((p) -> {
            double distance = mtree.getDistanceFunction().calculate(p, d);
            if (distance <= Constants.R * 3.0 / 2) {
                result.add(p);
            }
        });
        return result;
    }

    private void checkInlier(MCO inPD) {
        Collections.sort(inPD.exps);

        while (inPD.exps.size() > Constants.k - inPD.numberOfSucceeding && inPD.exps.size() > 0) {
            inPD.exps.remove(0);
        }
        if (inPD.exps.size() > 0) {
            inPD.ev = inPD.exps.get(0);
        } else {
            inPD.ev = 0;
        }

        if (inPD.exps.size() + inPD.numberOfSucceeding >= Constants.k) {
            if (inPD.numberOfSucceeding >= Constants.k) {

                eventQueue.remove(inPD);

                outlierList.remove(inPD);
            } else {

                outlierList.remove(inPD);
                if (!eventQueue.contains(inPD)) {
                    long startTime3 = Utils.getCPUTime();
                    eventQueue.add(inPD);
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime3;
                }
            }

        } else {
            eventQueue.remove(inPD);
            if (!outlierList.contains(inPD)) {
                outlierList.add(inPD);
            }
        }
    }

    private boolean isNeighbor(MCO p, MCO o) {
        double d = mtree.getDistanceFunction().calculate(p, o);
        return d <= Constants.R;
    }

    private void process_event_queue(int currentTime) {
        MCO x = eventQueue.peek();

        while (x != null && x.ev <= currentTime) {

            x = eventQueue.poll();
            while (x.exps.get(0) <= currentTime) {
                x.exps.remove(0);
                if (x.exps.isEmpty()) {
                    x.ev = 0;
                    break;
                } else {
                    x.ev = x.exps.get(0);
                }
            }
            if (x.exps.size() + x.numberOfSucceeding < Constants.k) {

                outlierList.add(x);

            } else if (x.numberOfSucceeding < Constants.k
                    && x.exps.size() + x.numberOfSucceeding >= Constants.k) {
                eventQueue.add(x);
            }

            x = eventQueue.peek();

        }
    }

    static class MCComparator implements Comparator<MCO> {

        @Override
        public int compare(MCO o1, MCO o2) {
            if (o1.ev < o2.ev) {
                return -1;
            } else if (o1.ev == o2.ev) {
                return 0;
            } else {
                return 1;
            }

        }

    }

    static class MCComparatorArrivalTime implements Comparator<MCO> {

        @Override
        public int compare(MCO o1, MCO o2) {
            if (o1.arrivalTime < o2.arrivalTime) {
                return -1;
            } else if (o1.arrivalTime == o2.arrivalTime) {
                return 0;
            } else {
                return 1;
            }

        }

    }

    static class MCO extends Data {

        public int center;
        public ArrayList<Integer> exps;
        public ArrayList<Integer> Rmc;

        public int ev;
        public boolean isInCluster;
        public boolean isCenter;

        public int numberOfSucceeding;

        public MCO(Data d) {
            super();
            this.arrivalTime = d.arrivalTime;
            this.values = d.values;

            exps = new ArrayList<>();
            Rmc = new ArrayList<>();
            isCenter = false;
            isInCluster = false;
        }

    }
}
