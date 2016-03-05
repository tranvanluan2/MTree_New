/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import mtree.tests.Data;
import mtree.utils.Constants;

/**
 *
 * @author Luan
 */
public class MicroCluster_NewVersion {

    public static ArrayList<Micro_Cluster> microClusters = new ArrayList<>();
    public static PriorityQueue<MCData> event_queue = new PriorityQueue(new MCDataComparator());
    public static ArrayList<MCData> PDList = new ArrayList<>();

    public static HashMap<Micro_Cluster, HashSet<MCData>> associates = new HashMap<>();
    public static int currentTime;
    public static int numDisperseCluster = 0;
    public static int numberOfRecomputation = 0;
    public static int totalPointsInCluster = 0;
    public static int totalCluster = 0;

    public static int numberWindows = 0;

    private ArrayList<Micro_Cluster> findListClusterToAdd(MCData d, int n) {
        ArrayList<Micro_Cluster> result = new ArrayList<>();
        for (Micro_Cluster cluster : microClusters) {
            double dis = DistanceFunction.euclideanDistance(d, cluster.center);
            if (dis <= Constants.R / 2) {
                result.add(cluster);
                if (result.size() >= n) {
                    break;
                }
            }
        }
        return result;
    }

    private Micro_Cluster findClusterToAdd(MCData d) {
        Micro_Cluster result = null;
        int bestScore = -1;
        double bestDistance = Double.MAX_VALUE;
        for (Micro_Cluster cluster : microClusters) {
            double dis = DistanceFunction.euclideanDistance(d, cluster.center);
            if (dis <= Constants.R / 2) {

                if (Constants.weightedCluster == 2) {
                    if (bestDistance > dis) {
                        bestDistance = dis;
                        result = cluster;
                    }
                } else {
                    if (cluster.score > bestScore) {
                        result = cluster;
                    }
                }
            }
        }
        return result;
    }

    private void addToPDList(MCData d) {

        HashSet<MCData> neighbors = new HashSet<>();
        HashSet<MCData> neighborInClusters = findNeighborInCluster(d);

        microClusters.stream().filter((cluster) -> (DistanceFunction.euclideanDistance(d, cluster.center)
                <= 3 * Constants.R / 2)).forEach((cluster) -> {
                    HashSet<MCData> associatePoints = associates.get(cluster);
                    associatePoints.add(d);
                    //   associates.put(cluster, associatePoints);
                });

        HashSet<MCData> neighborInPD = findNeighborInPD(d);
        neighbors.addAll(neighborInPD);
        neighbors.addAll(neighborInClusters);

        if (isComeFromNewSlide(d)) {
            neighbors.stream().forEach((d2) -> {
                updateNeighborList(d, d2, true);
            });

        } else {
            neighbors.stream().forEach((d2) -> {
                updateNeighborList(d, d2, false);
            });
        }
        if (d.isUnsafeInlier()) {
            event_queue.add(d);
        }

        PDList.add(d);

    }

    private boolean isComeFromNewSlide(MCData d) {
        return d.arrivalTime > currentTime - Constants.slide || currentTime <= Constants.W;
    }

    private HashSet<MCData> findNeighborToFormCluster(MCData d) {
        HashSet<MCData> result = new HashSet<>();
        switch (Constants.type_MCOD) {
            case 0:

            case 2:
                PDList.stream().filter((d2) -> (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2 && d.arrivalTime != d2.arrivalTime)).forEach((d2) -> {

                    result.add(d2);

                });

                break;
            case 1:
                PDList.stream().filter((d2) -> (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2 && d.arrivalTime != d2.arrivalTime)).forEach((d2) -> {
                    if (d2.arrivalTime != d.arrivalTime) {
                        result.add(d2);
                    }
                });
                //find in clusters 
                //find points in clusters that in range R
                microClusters.stream().filter((cluster) -> DistanceFunction.euclideanDistance(d, cluster.center) <= Constants.R).forEach((cluster) -> {
                    cluster.members.stream().filter((d2) -> (DistanceFunction.euclideanDistance(d2, d) <= Constants.R / 2)).forEach((d2) -> {
                        if (d2.arrivalTime != d.arrivalTime && d2.clusters.size() < Constants.maxClusterEachPoint) {
                            result.add(d2);
                        }
                    });
                });

                break;
        }
        return result;
    }

    private Micro_Cluster formNewCluster(MCData d, HashSet<MCData> neighborsToFormCluster) {

        neighborsToFormCluster.add(d);

        Micro_Cluster cluster = new Micro_Cluster(Constants.type_MCOD);

        cluster.center = d;

        neighborsToFormCluster.stream().map((MCData d2) -> {
            //remove from PD and event queue

            PDList.remove(d2);
            if (d2.isUnsafeInlier()) {
                event_queue.remove(d2);
            }
            return d2;
        }).forEach((d2) -> {
            d2.reset();
            d2.clusters.add(cluster);
            cluster.members.add(d2);
        });
        cluster.computeScore();

        return cluster;
    }

    private boolean isNeighbor(MCData d, MCData d2) {
        return DistanceFunction.euclideanDistance(d, d2) <= Constants.R
                && !d.expired() && !d2.expired() && d.arrivalTime != d2.arrivalTime;
    }

    private void updateNeighborList(MCData d, MCData d2, boolean dComeFromNewSlide) {

        if (isNeighbor(d, d2)) {
            if (d.getSlideIndex() == d2.getSlideIndex()) {
                if (d.clusters.isEmpty()) {
                    d.numSucceedingNeighbors++;
                }
                if (d2.clusters.isEmpty() && dComeFromNewSlide) {
                    d2.numSucceedingNeighbors++;
                }
            } else if (d.getSlideIndex() < d2.getSlideIndex()) {
                if (d.clusters.isEmpty()) {
                    d.numSucceedingNeighbors++;
                }
                if (d2.clusters.isEmpty() && dComeFromNewSlide) {
                    d2.precedingNeighbors.add(d);
                    //  d2.updateEarliestNeighbor();
                }
            } else {
                if (d2.clusters.isEmpty() && dComeFromNewSlide) {
                    d2.numSucceedingNeighbors++;
                }
                if (d.clusters.isEmpty()) {
                    d.precedingNeighbors.add(d2);
                    // d.updateEarliestNeighbor();
                }
            }

        }
    }

//    private void updateForNeighborInPD(MCData d, HashSet<MCData> neighborsInPD) {
//        neighborsInPD.stream().forEach((d2) -> {
//            if (isNeighbor(d, d2) && d.arrivalTime != d2.arrivalTime) {
//                if (isComeFromNewSlide(d)) {
//                    updateNeighborList(d, d2, true);
//                } else {
//                    updateNeighborList(d, d2, false);
//                }
//            }
//        });
//    }
    private void printStatistic() {
//        System.out.println("Add to cluster : " + addToCluster);
//        System.out.println("Add to PD  : " + addToPD);

        System.out.println("Numer of cluster: " + microClusters.size());
        //calculate number of points in clusters
        HashSet<Integer> points = new HashSet<>();
        HashSet<Integer> pointsInPD = new HashSet<>();
        microClusters.stream().forEach((cluster) -> {
            cluster.members.stream().forEach((d) -> {
                points.add(d.arrivalTime);
            });
        });
        PDList.stream().forEach((d) -> {
            pointsInPD.add(d.arrivalTime);
        });
        totalPointsInCluster += points.size();
        totalCluster += microClusters.size();
        System.out.println("Total Number of cluster = " + totalCluster);
        //  System.out.println("Number of points in cluster: " + points.size());
        //   System.out.println("Number of points in PD: " + pointsInPD.size());
        System.out.println("Number of dispersed cluster = " + numDisperseCluster);
        System.out.println("Number of recompuatation = " + numberOfRecomputation);
        System.out.println("Total Number of points in cluster = " + totalPointsInCluster);
    }

    private HashSet<MCData> findNeighborInCluster(MCData d) {
        HashSet<MCData> result = new HashSet<>();
        microClusters.stream().filter((cluster) -> (DistanceFunction.euclideanDistance(d, cluster.center) <= Constants.R * 3.0 / 2)).forEach((cluster) -> {
            //check with member
            cluster.members.stream().filter((d2) -> (isNeighbor(d, d2))).forEach((d2) -> {
                if (d2.arrivalTime != d.arrivalTime) {
                    result.add(d2);
                }
            });
        });
        return result;
    }

    private HashSet<MCData> findAssociatesInPD(MCData d) {
        HashSet<MCData> result = new HashSet<>();
        PDList.stream().filter((d2) -> (DistanceFunction.euclideanDistance(d, d2)
                <= Constants.R * 3 / 2)).forEach((d2) -> {
                    if (!d2.expired()) {
                        result.add(d2);
                    }

                });
        return result;
    }

    private HashSet<MCData> findNeighborInPD(MCData d) {
        HashSet<MCData> result = new HashSet<>();
        PDList.stream().filter((d2) -> (isNeighbor(d, d2))).forEach((d2) -> {

            result.add(d2);

        });
        return result;
    }

    public static boolean dispersed = false;

    private void processExpiredData() {

        associates.values().stream().map((points) -> points.iterator()).forEach((iterator) -> {
            while (iterator.hasNext()) {
                MCData p = iterator.next();
                if (p.expired()) {
                    iterator.remove();
                }
            }
        });

        for (int j = microClusters.size() - 1; j >= 0; j--) {

            Micro_Cluster cluster = microClusters.get(j);
            for (int i = cluster.members.size() - 1; i >= 0; i--) {
                if (cluster.members.get(i).expired()) {
                    MCData d2 = cluster.members.get(i);
                    d2.clusters.remove(cluster);
                    d2.reset();
                    //     d2 = null;
                    cluster.members.remove(i);
                }
            }

            cluster.computeScore();
            //   disperseList = needToProcess;
            if (cluster.members.size() <= Constants.k) {
                numDisperseCluster++;

                associates.remove(cluster);
                microClusters.remove(j);

                cluster.members.stream().filter((d) -> (!d.expired())).forEach((d) -> {

                    d.reset();
                    d.clusters.remove(cluster);
                    if (d.clusters.isEmpty()) {
                        disperseList.add(d);
                    }
                });

                cluster.members.clear();

            }

        }

        processEventQueue();
        for (int i = PDList.size() - 1; i >= 0; i--) {
            MCData d = PDList.get(i);
            if (d == null || d.expired()) {
                if (d != null) {
                    d.clean();
                }

                PDList.remove(i);

            }
        }
        PDList.addAll(disperseList);

        numberOfRecomputation += disperseList.size();
        processDisperseData();

    }

    private void processDisperseData() {

        disperseList.stream().forEach((d) -> {

            Micro_Cluster cluster = findClusterToAdd(d);

            if (cluster != null) {
                //add to cluster
                PDList.remove(d);
                d.reset();
                cluster.members.add(d);
                d.clusters.add(cluster);

            } else {

                HashSet<MCData> neighbors = new HashSet<>();
                neighbors.addAll(findNeighborInCluster(d));
                neighbors.addAll(findNeighborInPD(d));
                neighbors.stream().forEach((d2) -> {
                    updateNeighborList(d, d2, false);
                });
                //  d.updateEarliestNeighbor();
                if (d.isUnsafeInlier()) {
                    event_queue.add(d);
                }

            }
        });
        disperseList.clear();

    }

    private void processEventQueue() {
        while (true) {
            if (event_queue.isEmpty()) {
                break;
            }
            MCData d = event_queue.peek();
            if (d.earliestExpireTime <= currentTime - Constants.W || d.expired() || d.precedingNeighbors.isEmpty()) {
                event_queue.poll();
                if (d.precedingNeighbors != null) {
                    //d.updateEarliestNeighbor();
                    for (int i = d.precedingNeighbors.size() - 1; i >= 0; i--) {
                        MCData d2 = d.precedingNeighbors.get(i);
                        if (d2.expired()) {
                            d.precedingNeighbors.remove(i);

                        }
                    }
                    d.updateEarliestNeighbor();
                }
                if (d.isUnsafeInlier() && !d.precedingNeighbors.isEmpty() && !d.expired()) {
                    event_queue.add(d);
                } else if (d.expired()) {
                    d.clean();
                }
            } else {
                break;
            }
        }
    }

    private static class MCDataComparator implements Comparator<MCData> {

        @Override
        public int compare(MCData t, MCData t1) {
            if (t.earliestExpireTime > t1.earliestExpireTime) {
                return 1;
            } else {
                return -1;
            }
        }

    }

    public void processData(MCData d) {

        //  if(d.clusters.size()>0) return;
        //find cluster for d 
        if (Constants.type_MCOD < 2) {
            Micro_Cluster cluster = findClusterToAdd(d);

            if (cluster != null) {
                //add to cluster
                cluster.addData(d);
                return;

            }
        } else if (Constants.type_MCOD == 2) {
            ArrayList<Micro_Cluster> clusters = findListClusterToAdd(d, Constants.numClusterToAdd);
            clusters.stream().map((cluster) -> {
                d.reset();
                cluster.members.add(d);
                return cluster;
            }).map((cluster) -> {
                d.clusters.add(cluster);
                return cluster;
            }).forEach((cluster) -> {
                cluster.computeScore();
            });
            if (!clusters.isEmpty()) {
                clusters.get(0).checkWithAssociatePoints(d, clusters.get(0));

                return;
            }

        }
        HashSet<MCData> neighborsToFormCluster = findNeighborToFormCluster(d);

        if (neighborsToFormCluster.size() >= Constants.minSizeOfCluster) {
            Micro_Cluster cluster = formNewCluster(d, neighborsToFormCluster);
            microClusters.add(cluster);
            HashSet<MCData> associateInPD = findAssociatesInPD(d);
            associates.put(cluster, associateInPD);

        } else {

            addToPDList(d);
        }

    }
    public static int addToCluster = 0;
    public static int addToPD = 0;

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int _currentTime, int W, int slide) {
        numberWindows++;
        currentTime = _currentTime;
        ArrayList<Data> result = new ArrayList<>();

        processExpiredData();
        System.out.println("After processing expired data");
        //  printStatistic();
        data.stream().map((o) -> new MCData(o)).forEach((d) -> {
            processData(d);

        });

        //find outlier
        PDList.stream().filter((d) -> (d.checkIsOutlier())).forEach((d) -> {
            result.add(d);
        });

        //  printStatistic();
        return result;

    }

    private class MCData extends Data {

        public ArrayList<Micro_Cluster> clusters;
        public ArrayList<MCData> precedingNeighbors;
        public int numSucceedingNeighbors;
        public int earliestExpireTime;

        public void clean() {
            clusters = null;
            if (precedingNeighbors != null) {
                for (MCData d : precedingNeighbors) {
                    d = null;
                }
            }
            precedingNeighbors = null;
        }

        public MCData(Data d) {
            super();
            this.arrivalTime = d.arrivalTime;
            this.values = d.values;
            this.hashCode = d.hashCode;
            clusters = new ArrayList<>();
            precedingNeighbors = new ArrayList<>();
            numSucceedingNeighbors = 0;
            earliestExpireTime = 0;
        }

        private class MCDataComparator implements Comparator<MCData> {

            @Override
            public int compare(MCData t, MCData t1) {
                if (t.arrivalTime > t1.arrivalTime) {
                    return 1;
                } else {
                    return -1;
                }
            }

        }

        public int updateEarliestNeighbor() {

            Collections.sort(precedingNeighbors, (new MCDataComparator()));
            while (precedingNeighbors.size() > Constants.k + 1) {
                precedingNeighbors.remove(0);
            }
            if (!precedingNeighbors.isEmpty()) {
                earliestExpireTime = precedingNeighbors.get(0).arrivalTime;
            } else {
                earliestExpireTime = 0;
            }
            return earliestExpireTime;
        }

        private boolean isUnsafeInlier() {
            return precedingNeighbors != null && numSucceedingNeighbors + precedingNeighbors.size() >= Constants.k
                    && numSucceedingNeighbors < Constants.k;
        }

        private int getSlideIndex() {
            return (int) Math.floor((arrivalTime - 1) / Constants.slide);
        }

        private void reset() {
            this.earliestExpireTime = 0;
            this.precedingNeighbors.clear();
            this.numSucceedingNeighbors = 0;
        }

        private boolean expired() {
            return this.arrivalTime <= currentTime - Constants.W;
        }

        private boolean checkIsOutlier() {
            return this.clusters.isEmpty() && this.numSucceedingNeighbors + this.precedingNeighbors.size() < Constants.k;
        }
    }

    private class Micro_Cluster {

        public MCData center;
        public ArrayList<MCData> members = new ArrayList<>();
        public double score;
        int type = 0;

        public Micro_Cluster(int _type) {
            type = _type;
        }

        public double computeScore() {
            if (Constants.type_MCOD == 0) {
                return score;
            }
            switch (Constants.weightedCluster) {
                case 0:
                    int expireSlideIndex = (currentTime - Constants.W) / Constants.slide;
                    HashMap<Integer, Integer> slideScoreMap = new HashMap<>();
                    members.stream().forEach((d) -> {
                        if (slideScoreMap.get(d.getSlideIndex()) != null) {
                            slideScoreMap.put(d.getSlideIndex(), slideScoreMap.get(d.getSlideIndex()) + 1);
                        } else {
                            slideScoreMap.put(d.getSlideIndex(), 1);
                        }
                    });

                    for (Integer slideIndex : slideScoreMap.keySet()) {
                        if (slideIndex > expireSlideIndex) {
                            if (slideScoreMap.get(slideIndex) < Constants.k) {
                                score += (Constants.k - slideScoreMap.get(slideIndex)) * 1.0 / (slideIndex - expireSlideIndex);
                            }
                        }
                    }
                    break;
                case 1:
                    expireSlideIndex = (currentTime - Constants.W) / Constants.slide;
                    int count = 0;
                    count = members.stream().filter((d) -> (d.getSlideIndex() > expireSlideIndex)).map((_item) -> 1).reduce(count, Integer::sum);
                    if (count >= Constants.k) {
                        score = 0;
                    } else {
                        score = Constants.k - count;
                    }
                    break;

                case 3:
                    expireSlideIndex = (currentTime - Constants.W) / Constants.slide;
                    int c = 0;
                    for (MCData d : members) {
                        if (d.getSlideIndex() > expireSlideIndex) {
                            c++;
                            score += d.arrivalTime;
                        }
                    }
                    if (c > 0) {
                        score = score * 1.0 / c;
                    }
                    break;

            }
            return score;
        }

        public void addData(MCData d) {
            d.reset();
            members.add(d);

            d.clusters.add(this);
            //check associate points 
            checkWithAssociatePoints(d, this);

            //recompute score
            computeScore();
        }

        public void removeData(MCData d) {
            members.remove(d);
            d.clusters.remove(this);
            //check if cluster is dispersed 
            if (members.size() < Constants.k + 1) {
                dispersed = true;
            }
            //recompute Score
            // computeScore();

        }

        private void checkWithAssociatePoints(MCData d, Micro_Cluster cluster) {
            if ((d.arrivalTime > currentTime - Constants.slide)
                    || currentTime <= Constants.W) {
                associates.get(cluster).stream().filter((d2) -> (isNeighbor(d, d2))).forEach((d2) -> {
                    if (d.getSlideIndex() >= d2.getSlideIndex()) {
                        d2.numSucceedingNeighbors++;
                    } else {
                        d2.precedingNeighbors.add(d);
                        //  d2.updateEarliestNeighbor();
                    }
                });
            }
        }
    }

    public static ArrayList<MCData> disperseList = new ArrayList<>();
    public static ArrayList<Micro_Cluster> listNewCluster = new ArrayList<>();

    public void disperseCluster(Micro_Cluster cluster) {
        disperseList.clear();
        listNewCluster.clear();
        //remove from the list 
        microClusters.remove(cluster);
        associates.remove(cluster);
        //remove points from cluster
        cluster.members.stream().map((d) -> {
            d.clusters.remove(cluster);
            d.reset();
            if (d.clusters.isEmpty() && !d.expired()) {
                d.reset();
                disperseList.add(d);
            }
            return d;
        });
        cluster.members.clear();

        disperseList.stream().forEach((d) -> {
            processData(d);
//            addToPDList(d);
        }); //?
        listNewCluster.stream().forEach((c) -> {
            HashSet<MCData> associatesNeighbor = findAssociatesInPD(c.center);
            associates.put(c, associatesNeighbor);
        });
        disperseList.clear();
        listNewCluster.clear();
    }
}
