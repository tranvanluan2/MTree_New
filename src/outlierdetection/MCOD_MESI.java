/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import mtree.tests.Data;
import mtree.utils.Constants;

/**
 *
 * @author Luan Tran
 */
public class MCOD_MESI {

//    public static HashMap<Cluster, HashSet<MCData>> associates = new HashMap<>();
    public static ArrayList<Cluster> microClusters = new ArrayList<>();
    public static PriorityQueue<MCData> event_queue = new PriorityQueue(new MCDataNeighborComparator());
    public static HashMap<Integer, ArrayList<MCData>> PDList = new HashMap<>();

    // public static int numberWindows = 0;
    public static int currentTime;

    public static ArrayList<MCData> dispersedList = new ArrayList<>();

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int _currentTime, int W, int slide) {
        ArrayList<Data> result = new ArrayList<>();
        currentTime = _currentTime;
        int expiredSlideIndex = (currentTime - Constants.W) / slide;

        processExpiredData(expiredSlideIndex);

//        System.out.println("After processing expired data");
        //  printStatistic();
        data.stream().map((o) -> new MCData(o)).forEach((d) -> {
            processData(d, false);
        });

        //find outlier     
        for (Integer slideIndex : PDList.keySet()) {
            ArrayList<MCData> datas = PDList.get(slideIndex);

            datas.stream().filter((d) -> (d.isOutlier())).map((d) -> {
                int newestSlide = (currentTime - 1) / Constants.slide;
                reProbe(d, newestSlide);
                return d;
            }).filter((d) -> (d.isOutlier())).forEach((d) -> {
                result.add(d);
            });
        }
        dispersedList.clear();
        return result;

    }

    public void reProbe(MCData d, int newestSlideIndex) {
        int slideIndex = d.lastProbe + 1;
        while (slideIndex <= newestSlideIndex) {

            HashSet<MCData> checked = new HashSet<>();
            for (Cluster c : microClusters) {
                if (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R * 3 / 2) {
                    ArrayList<MCData> points = c.slide_members.get(slideIndex);
                    if (points != null) {

                        points.stream().filter((d2) -> ((d2.clusters.size() > 1
                                && !checked.contains(d2)) || d2.clusters.size() <= 1)).map((d2) -> {
                            if (d2.clusters.size() > 1) {
                                checked.add(d2);
                            }
                            return d2;
                        }).filter((d2) -> (d.isNeighbor(d2))).forEach((_item) -> {
                            d.numSucceedingNeighbors++;
                        });//                        points.stream().filter((d2) -> (d.isNeighbor(d2))).forEach((_item) -> {
//                            d.numSucceedingNeighbors++;
//                        });
                    }
                }
            }

            //probe in PD list
            ArrayList<MCData> points = PDList.get(slideIndex);
            if (points != null) {
                points.stream().filter((d2) -> (d.isNeighbor(d2))).forEach((_item) -> {
                    d.numSucceedingNeighbors++;
                });
            }

            if (!d.isOutlier()) {
                d.lastProbe = slideIndex;
                return;
            }
            d.lastProbe = slideIndex;
            slideIndex++;
        }
    }

    private Cluster findClusterToAdd(MCData d) {

        Cluster result = null;
        double bestDistance = Double.MAX_VALUE;
        for (Cluster cluster : microClusters) {
            double dis = DistanceFunction.euclideanDistance(d, cluster.center);
            if (dis <= Constants.R / 2) {
                if (dis < bestDistance) {
                    bestDistance = dis;
                    result = cluster;
                }
            }
        }
        return result;

    }

    private void processExpiredData(int expiredSlide) {
        if (PDList.get(expiredSlide) != null) {
            ArrayList<MCData> points = PDList.get(expiredSlide);
            for(MCData p: points) {
                p.clean();
                p.clusters.clear();
                p = null;
            }
            points.clear();
        }

        PDList.remove(expiredSlide);

        processEventQueue(expiredSlide);
        PDList.values().stream().forEach((datas) -> {
            datas.stream().forEach((d) -> {
                if (d.numPrecedingNeighbor.containsKey(expiredSlide)) {
                    HashSet<MCData> points = d.numPrecedingNeighbor.get(expiredSlide);
                    if(points !=null) {
                        for(MCData p: points) p.clean();
                        points.clear();
                    }
                    d.numPrecedingNeighbor.remove(expiredSlide);
                }
            });
        });
        //update neighbor list
        dispersedList.clear();
        //remove from clusters 
        for (int i = microClusters.size() - 1; i >= 0; i--) {
            Cluster c = microClusters.get(i);
            //c.slide_members
            c.removeExpiredSlide(expiredSlide);
            //check if c has less than K+1 memebers 
            if (c.notEnoughMembers()) {
                //proces dispersed clusters
                for (ArrayList<MCData> members : c.slide_members.values()) {

                    members.stream().map((d) -> {
                        d.clusters.remove(c);
                        return d;
                    }).filter((d) -> (d.clusters.isEmpty())).map((d) -> {
                        d.clean();
                        return d;
                    }).forEach((d) -> {
                        dispersedList.add(d);
                    });

                }
                c.slide_members.clear();
                microClusters.remove(c);
            }
        }
        //process dispersed list
        dispersedList.stream().forEach((d) -> {
            processData(d, true);
        });
        dispersedList.clear();
        //remove from PD 

    }

    private void processEventQueue(int expiredSlide) {
        while (true) {
            if (event_queue.isEmpty()) {
                break;
            }
            MCData d = event_queue.peek();
            if (d.getOldestSlide() <= expiredSlide || d.getSlideIndex() <= expiredSlide
                    || d.numPrecedingNeighbor.isEmpty()) {
                event_queue.poll();
                if (d.numPrecedingNeighbor != null) {
                    //d.updateEarliestNeighbor();
                    for (int i = d.getOldestSlide(); i <= expiredSlide; i++) {
                        if (d.numPrecedingNeighbor.get(i) != null) {
                            d.numPrecedingNeighbor.get(i).clear();

                            d.numPrecedingNeighbor.remove(i);
                        }
                    }
//                    d.updateEarliestNeighbor();
                }
                if (d.isUnsafeInlier() && !d.numPrecedingNeighbor.isEmpty() && d.getSlideIndex() > expiredSlide) {
                    event_queue.add(d);
                } else if (d.getSlideIndex() <= expiredSlide) {
                    d.clean();
                }
            } else {
                break;
            }
        }
    }

    private void processData(MCData d, boolean isFromDispersedList) {
        //find cluster to add
        Cluster cluster = findClusterToAdd(d);
        //add to cluster
        if (cluster != null) {
            cluster.addNewPointToSlideMember(d);
        } //add to PD list/event queue
        else {
            //find neighbor for d 
            probe(d, isFromDispersedList);
            if (d.clusters.isEmpty()) {
                ArrayList<MCData> datas = PDList.get(d.getSlideIndex());
                if (datas == null) {
                    datas = new ArrayList<>();

                    datas.add(d);

                    PDList.put(d.getSlideIndex(), datas);
                } else {
                    datas.add(d);
                }
                if (d.isUnsafeInlier()) {
                    event_queue.add(d);
                }
            }

        }
    }

    public void probe(MCData d, boolean isFromDispersedList) {

        int slideIndex = d.lastProbe;
        ArrayList<MCData> closeNeighbors = new ArrayList<>();

        HashMap<Integer, HashSet<MCData>> slide_neighbors = new HashMap<>();
        if (Constants.type_MCOD == 0) {

            if (d.lastProbe == d.getSlideIndex()) {
                while ((slideIndex > d.getSlideIndex() - Constants.W / Constants.slide)
                        && (closeNeighbors.size() < Constants.minSizeOfCluster)) {
                    HashSet<MCData> neighbors = new HashSet<>();
                    //find neighbor in PD list
                    ArrayList<MCData> datas = PDList.get(slideIndex);
                    if (datas != null) {
                        datas.stream().filter((d2) -> (DistanceFunction.euclideanDistance(d, d2) <= Constants.R)).map((d2) -> {
                            neighbors.add(d2);
                            return d2;
                        }).filter((d2) -> (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2)).forEach((d2) -> {
                            closeNeighbors.add(d2);
                        });
                    }
                    slide_neighbors.put(slideIndex, neighbors);

                    if (closeNeighbors.size() >= Constants.minSizeOfCluster) {
                        //form new cluster
                        formNewCluster(d, closeNeighbors);
                        break;
                    }

                    slideIndex--;
                }

                if (d.clusters.isEmpty()) {

                    slideIndex = d.getSlideIndex();
                    int countNeighbor = 0;
                    //find neighbors in clusters and join with neighbors in PD
                    while ((slideIndex > d.getSlideIndex() - Constants.W / Constants.slide)
                            && (countNeighbor < Constants.k)) {

                        HashSet<MCData> neighbors = slide_neighbors.get(slideIndex);

                        //find neighbor in clusters 
                        for (Cluster c : microClusters) {
                            if (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R * 3 / 2) {
                                ArrayList<MCData> dataList = c.slide_members.get(slideIndex);
                                if (dataList != null) {
                                    for (MCData d2 : dataList) {
                                        if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R) {

                                            //add to neighbor list of d
                                            neighbors.add(d2);
                                            countNeighbor++;
                                            if (countNeighbor >= Constants.k) {

                                                break;

                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // countNeighbor += neighbors.size();
                        if (slideIndex == d.getSlideIndex()) {
                            d.numSucceedingNeighbors += neighbors.size();
                        } else {
                            d.numPrecedingNeighbor.put(slideIndex, neighbors);
                        }

                        slideIndex--;

                    }

                }
            }
        } else if (Constants.type_MCOD == 1) {

            if (d.lastProbe == d.getSlideIndex()) {
                while ((slideIndex > d.getSlideIndex() - Constants.W / Constants.slide)
                        && (closeNeighbors.size() < Constants.minSizeOfCluster)) {
                    HashSet<MCData> neighbors = new HashSet<>();
                    //find neighbor in PD list
                    ArrayList<MCData> datas = PDList.get(slideIndex);
                    if (datas != null) {
                        for (MCData d2 : datas) {
                            if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R) {

                                neighbors.add(d2);

                                if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2) {
                                    closeNeighbors.add(d2);
                                }

                            }
                        }
                    }
                    slide_neighbors.put(slideIndex, neighbors);

                    if (closeNeighbors.size() >= Constants.minSizeOfCluster) {
                        //form new cluster
                        formNewCluster(d, closeNeighbors);
                        break;
                    }

                    slideIndex--;
                }

                if (d.clusters.isEmpty()) {

                    slideIndex = d.getSlideIndex();
                    int countNeighbor = 0;
                    //find neighbors in clusters and join with neighbors in PD
                    while ((slideIndex > d.getSlideIndex() - Constants.W / Constants.slide)
                            && (countNeighbor < Constants.k)
                            && closeNeighbors.size() < Constants.minSizeOfCluster) {

                        HashSet<MCData> neighbors = slide_neighbors.get(slideIndex);

                        HashSet<Integer> checked = new HashSet<>();
                        //find neighbor in clusters 
                        for (Cluster c : microClusters) {
                            if (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R * 3 / 2) {
                                ArrayList<MCData> dataList = c.slide_members.get(slideIndex);
                                if (dataList != null) {
                                    for (MCData d2 : dataList) {
                                        if (!checked.contains(d2.arrivalTime)) {
                                            if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R) {
                                                checked.add(d2.arrivalTime);
                                                //add to neighbor list of d
                                                neighbors.add(d2);

                                                if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2
                                                        && d2.clusters.size() < Constants.maxClusterEachPoint) {
                                                    closeNeighbors.add(d2);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        countNeighbor += neighbors.size();
                        if (slideIndex == d.getSlideIndex()) {
                            d.numSucceedingNeighbors += neighbors.size();
                        } else {
                            d.numPrecedingNeighbor.put(slideIndex, neighbors);
                        }
                        if (countNeighbor >= Constants.k) {

                            break;

                        };

                        slideIndex--;

                    }
                    if (closeNeighbors.size() >= Constants.minSizeOfCluster) {
                        formNewCluster(d, closeNeighbors);
                    }

                }
            }

        }

    }

    private void formNewCluster(MCData d, ArrayList<MCData> closeNeighbors) {
        closeNeighbors.add(d);
        Cluster cluster = new Cluster();
        cluster.center = d;
        closeNeighbors.stream().map((d2) -> {
            if (d2.arrivalTime != d.arrivalTime && d2.clusters.isEmpty()) {

                PDList.get(d2.getSlideIndex()).remove(d2);
            }
            return d2;
        }).map((d2) -> {
            if (d2.isUnsafeInlier() && d2.clusters.isEmpty()) {
                event_queue.remove(d2);
            }
            return d2;
        }).map((d2) -> {
            d2.clean();
            return d2;
        }).forEach((d2) -> {
            cluster.addNewPointToSlideMember(d2);
        });
        microClusters.add(cluster);
    }

    private ArrayList<MCData> findNeighborInPD(MCData d, double R, int k) {

        int slideIndex = d.getSlideIndex();
        ArrayList<MCData> result = new ArrayList<>();
        while (result.size() < k && slideIndex > d.getSlideIndex() - Constants.W / Constants.slide
                && slideIndex >= 0) {
            ArrayList<MCData> datas = PDList.get(slideIndex);
            if (datas != null) {
                datas.stream().filter((d2) -> (DistanceFunction.euclideanDistance(d, d2) <= R)).forEach((d2) -> {
                    result.add(d2);
                });
            }
            slideIndex--;
        }
        return result;
    }

    private ArrayList<MCData> findNeighborInCluster(MCData d, double R, int k, boolean useMaxCluster) {
        ArrayList<MCData> result = new ArrayList<>();
        int slideIndex = d.getSlideIndex();
        while (slideIndex >= d.getSlideIndex() - Constants.W / Constants.slide
                && slideIndex >= 0 && result.size() <= k) {
//            microClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R / 2 + R)).map((c) -> c.slide_members.get(slideIndex)).filter((datas) -> (datas != null)).forEach((datas) -> {
//                datas.stream().filter((d2) -> (DistanceFunction.euclideanDistance(d, d2) <= R
//                        && (!useMaxCluster || d2.clusters.size() < Constants.maxClusterEachPoint))).forEach((d2) -> {
//                    result.add(d2);
//                });
//            });
            for (Cluster c : microClusters) {
                if (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R / 2 + R) {
                    for (MCData d2 : c.slide_members.get(slideIndex)) {
                        if (DistanceFunction.euclideanDistance(d, d2) <= R
                                && (!useMaxCluster || d2.clusters.size() < Constants.maxClusterEachPoint)) {
                            result.add(d2);
                        }
                    }
                }
            }
            slideIndex--;
        }

        return result;
    }

    private static class MCDataNeighborComparator implements Comparator<MCData> {

        @Override
        public int compare(MCData t, MCData t1) {
            if (t.getOldestSlide() > t1.getOldestSlide()) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    private class Cluster {

        public MCData center;
        public HashMap<Integer, ArrayList<MCData>> slide_members = new HashMap<>();

        public int numMembers() {
            int count = 0;
            count = slide_members.values().stream().map((members) -> members.size()).reduce(count, Integer::sum);
            return count;
        }

//        public boolean isSafe() {
//
//            return numMembers() > Constants.k;
//        }
        public Cluster() {
        }

        public void addNewPointToSlideMember(MCData d) {
            d.clusters.add(this);
            ArrayList<MCData> points = slide_members.get(d.getSlideIndex());
            if (points == null) {
                points = new ArrayList<>();
                points.add(d);
                slide_members.put(d.getSlideIndex(), points);
            } else {
                points.add(d);
            }

        }

        private void removeExpiredSlide(int expiredSlide) {
            if (this.slide_members.containsKey(expiredSlide)) {
                ArrayList<MCData> points = this.slide_members.get(expiredSlide);
                if(points!=null){
                    for(MCData p: points) {
                        p.clean();
                    }
                    points.clear();
                    
                }
                
                this.slide_members.remove(expiredSlide);
            }

        }

        private int getTotalMembers() {
            int count = 0;
            count = slide_members.keySet().stream().map((key) -> slide_members.get(key).size()).reduce(count, Integer::sum);
            return count;
        }

        private boolean notEnoughMembers() {
            return this.getTotalMembers() < Constants.minSizeOfCluster;
        }
    }

    private class MCData extends Data {

        public ArrayList<Cluster> clusters = new ArrayList<>();
        public int numSucceedingNeighbors;
        public HashMap<Integer, HashSet<MCData>> numPrecedingNeighbor = new HashMap<>();
        public int lastProbe;
        public int sIndex;
//        public int earliestExpireTime;

        public int getOldestSlide() {
            int result = getSlideIndex();
            for (Integer slide : numPrecedingNeighbor.keySet()) {
                if (slide < result) {
                    result = slide;
                }
            }
            return result;
        }

        public MCData(Data d) {
            super();
            this.arrivalTime = d.arrivalTime;
            this.values = d.values;
            this.hashCode = d.hashCode;
            clusters = new ArrayList<>();
            numPrecedingNeighbor = new HashMap<>();
            numSucceedingNeighbors = 0;
            sIndex = (int) Math.floor((arrivalTime - 1) / Constants.slide);
            lastProbe = sIndex;
        }

        public int countNeighborToSlide(int slideIndex) {
            int result = numSucceedingNeighbors;
            for (Integer slide : numPrecedingNeighbor.keySet()) {
                HashSet<MCData> datas = numPrecedingNeighbor.get(slide);
                result = result + datas.size();
            }

            return result;
        }

        private boolean isNeighbor(MCData d2) {
            return DistanceFunction.euclideanDistance(this, d2) <= Constants.R && this.arrivalTime != d2.arrivalTime;
        }

        private void addPrecedingNeighbor(MCData d2) {

            if (this.isOutlier()) {
                int slideIndex = d2.getSlideIndex();
                if (numPrecedingNeighbor.get(slideIndex) != null) {
                    HashSet<MCData> datas = numPrecedingNeighbor.get(slideIndex);
                    datas.add(d2);
                    // numPrecedingNeighbor.put(slideIndex, );
                } else {
                    HashSet<MCData> datas = new HashSet<>();
                    datas.add(d2);
                    numPrecedingNeighbor.put(slideIndex, datas);
                }
            }

        }

        private int getSlideIndex() {
            return sIndex;
        }

        private boolean isOutlier() {

            int numNeighbor = numSucceedingNeighbors;
            numNeighbor = numPrecedingNeighbor.keySet().stream().map((slideIndex) -> numPrecedingNeighbor.get(slideIndex)).map((datas) -> datas.size()).reduce(numNeighbor, Integer::sum);
            return numNeighbor < Constants.k;
        }

//        private void reset() {
//            numPrecedingNeighbor.clear();
//            numSucceedingNeighbors = 0;
//            lastProbe = sIndex;
//
//        }
//        private void updateEarliestNeighbor() {
//            
//        }
        private boolean isUnsafeInlier() {

            return !isOutlier() && numSucceedingNeighbors < Constants.k;
        }

        private void clean() {

            numSucceedingNeighbors = 0;
            numPrecedingNeighbor.clear();
//            clusters.clear();
        }

        private boolean isInCluster() {
            return clusters.isEmpty();
        }
    }
}
