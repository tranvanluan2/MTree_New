/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.util.ArrayList;
import java.util.HashMap;
import mtree.tests.Data;
import mtree.utils.Constants;
import mtree.utils.Utils;

/**
 *
 * @author Luan
 */
public class MCOD_Safe_Version {

    public static ArrayList<Cluster> safeClusters = new ArrayList<>();
    public static ArrayList<Cluster> unsafeClusters = new ArrayList<>();
    public static int numWindows = 0;
    public static double avgPointsInSafeCluster = 0;
    public static double avgPointsInUnSafeCluster = 0;
    public static double avgNumberOfCluster = 0;
    public static double avgNumberOfSafeCluster = 0;
    public static double avgNumberOfUnSafeCluster = 0;
    public static double avgTimeForProcessExpiredData = 0;
    public static double avgTimeForProcessNewData = 0;
    public static double avgTimeForAddingToUnafeCluster = 0;
    public static double timeForAddingToUnsafeCluster = 0;
    public static double avgTimeForAddingToSafeCluster = 0;
    public static double timeForAddingToSafeCluster = 0;
    public static double avgTimeForFormingUnsafeCluster = 0;
    public static double timeForFormingUnSafeCluster = 0;
    public static int numberWindows = 0;
    public static int numberDistance = 0;
    public static int numDispersedCluster = 0;
    public static int numRecomputation= 0;
    public ArrayList<MCData> detectOutlier(ArrayList<Data> data, int _currentTime, int W, int slide) {
        numberWindows++;
        long start = Utils.getCPUTime();
        processExpiredSlide((_currentTime - W - 1) / Constants.slide);
        double elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;
        avgTimeForProcessExpiredData = (avgTimeForProcessExpiredData * (numberWindows - 1) + elapsedTimeInSec) / numberWindows;
        
        start = Utils.getCPUTime();
        timeForAddingToSafeCluster = 0;
        timeForFormingUnSafeCluster = 0;
        timeForAddingToUnsafeCluster = 0;
        data.stream().forEach((d) -> {
            processNewData(new MCData(d));
        });
        
        avgTimeForAddingToSafeCluster = (avgTimeForAddingToSafeCluster * (numberWindows-1)+ timeForAddingToSafeCluster)/numberWindows;
        avgTimeForFormingUnsafeCluster = (avgTimeForFormingUnsafeCluster * (numberWindows-1)+ timeForFormingUnSafeCluster)/numberWindows;
        avgTimeForAddingToUnafeCluster = (avgTimeForAddingToUnafeCluster * (numberWindows-1)+ timeForAddingToUnsafeCluster)/numberWindows;
        elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;
        avgTimeForProcessNewData = (avgTimeForProcessNewData * (numberWindows - 1) + elapsedTimeInSec) / numberWindows;
        
        ArrayList<MCData> outliers = scanToFindOutlier();

//        printStatistics();
        return outliers;
    }

    public static void printStatistics() {

        int numPointInSafeCluster = 0;
        int numPointInUnSafeCluster = 0;
        numPointInSafeCluster = safeClusters.stream().map((cluster) -> cluster.members.size()).reduce(numPointInSafeCluster, Integer::sum);
        numPointInUnSafeCluster = unsafeClusters.stream().map((cluster) -> cluster.members.size()).reduce(numPointInUnSafeCluster, Integer::sum);
        avgPointsInSafeCluster = (avgPointsInSafeCluster * numWindows + numPointInSafeCluster) * 1.0 / (numWindows + 1);
        avgPointsInUnSafeCluster = (avgPointsInUnSafeCluster * numWindows + numPointInUnSafeCluster) * 1.0 / (numWindows + 1);
        avgNumberOfCluster = (avgNumberOfCluster * numWindows + safeClusters.size() + unsafeClusters.size()) / (numWindows + 1);
        avgNumberOfSafeCluster = (avgNumberOfSafeCluster * numWindows + safeClusters.size()) / (numWindows + 1);
        avgNumberOfUnSafeCluster = (avgNumberOfUnSafeCluster * numWindows + unsafeClusters.size()) / (numWindows + 1);
        System.out.println("# avg number of clusters = " + avgNumberOfCluster);
        System.out.println("# avg number of safe clusters = " + avgNumberOfSafeCluster);
        System.out.println("# avg number of unsafe clusters = " + avgNumberOfUnSafeCluster);
        numWindows++;
        System.out.println("# avg points in safe clusters = " + avgPointsInSafeCluster);
        System.out.println("# avg points in unsafe clusters = " + avgPointsInUnSafeCluster);
        System.out.println("# avg time for adding to unsafe clusters = " + avgTimeForAddingToUnafeCluster);
        System.out.println("# avg time for adding to safe clusters = " + avgTimeForAddingToSafeCluster);
        System.out.println("# avg time for adding to forming clusters = " + avgTimeForFormingUnsafeCluster);
        System.out.println("# distance computation = "+ numberDistance);
        System.out.println("# dispersed clusters = " + numDispersedCluster);
        System.out.println("# recomputation = " + numRecomputation);
    }

    public void processNewData(MCData d) {
        long start = Utils.getCPUTime();
        Cluster cluster = findSafeClusterToAdd(d);
        if (cluster != null) {
            addPointToSafeCluster(d, cluster);
        } 
        double elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;
        timeForAddingToSafeCluster = timeForAddingToSafeCluster + elapsedTimeInSec;
        if(cluster == null) {
            //find unsafe cluster to add
            start = Utils.getCPUTime();
            cluster = findUnsafeClusterToAdd(d);
            if (cluster != null) {
                addPointToUnSafeCluster(d, cluster);
            } 
            elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;
            timeForAddingToUnsafeCluster = timeForAddingToUnsafeCluster + elapsedTimeInSec;
            
            start = Utils.getCPUTime();
            if(cluster == null) {
                //form new cluster 
                formNewUnSafeCluster(d);
            }
            elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;
            timeForFormingUnSafeCluster = timeForFormingUnSafeCluster+ elapsedTimeInSec;

        }
    }

    public void scanToRemoveExpiredNeighbor(int slideIndex) {

        unsafeClusters.stream().forEach((cluster) -> {
            cluster.members.stream().forEach((d) -> {
                d.numPrecedingNeighbor.remove(slideIndex);

            });
        });
    }

//    public void processExpiredData(MCData d) {
//
//        for (Cluster cluster : d.clusters) {
//            cluster.members.remove(d);
//            if (cluster.members.size() == Constants.k) {
//                safeClusters.remove(cluster);
//                //re-compute neighbors for other points
//                for (MCData d2 : cluster.members) {
//                    d2.reset();
//                    for (MCData d3 : cluster.members) {
//                        if (d2.arrivalTime != d3.arrivalTime) {
//                            if (d2.getSlideIndex() > d3.getSlideIndex()) {
//                                d2.addPrecedingNeighbor(d3);
//                            } else {
//                                d2.numSucceedingNeighbors++;
//                            }
//                        }
//                    }
//                    for (Cluster c : cluster.associateClusters) {
//                        if (DistanceFunction.euclideanDistance(d2, c.center) <= Constants.R * 3.0 / 2) {
//                            for (MCData d3 : c.members) {
//                                if (d2.isNeighbor(d3)) {
//                                    if (d2.getSlideIndex() > d3.getSlideIndex()) {
//                                        d2.addPrecedingNeighbor(d3);
//                                    } else {
//                                        d2.numSucceedingNeighbors++;
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//                unsafeClusters.add(cluster);
//            }
//        }
//
//    }
    private Cluster findSafeClusterToAdd(MCData d) {
        Cluster result = null;
        for (Cluster cluster : safeClusters) {
            if (DistanceFunction.euclideanDistance(d, cluster.center) <= Constants.R / 2) {
                result = cluster;
                break;
            }
            numberDistance++;
        }
        return result;
    }

    private void addPointToSafeCluster(MCData d, Cluster cluster) {
        cluster.members.add(d);
        d.clusters.add(cluster);

        //check with points in associate cluster
        cluster.associateClusters.stream().filter((c) -> !(c.isSafe())).forEach((c) -> {
            double distanceToCenter = DistanceFunction.euclideanDistance(d, c.center);
            numberDistance++;
            if (distanceToCenter > Constants.R * 3.0 / 2) {

            } else if (distanceToCenter <= Constants.R / 2) {
                c.members.stream().forEach((d2) -> {
                    d2.numSucceedingNeighbors++;
                });
            } else {
                c.members.stream().filter((d2) -> (d.isNeighbor(d2))).forEach((d2) -> {
                    d2.numSucceedingNeighbors++;
                });
            }
        });
    }

    private Cluster findUnsafeClusterToAdd(MCData d) {
        Cluster result = null;
        for (Cluster cluster : unsafeClusters) {
            if (DistanceFunction.euclideanDistance(d, cluster.center) <= Constants.R / 2) {
                
                result = cluster;
                break;
            }
            numberDistance++;
        }
        return result;
    }

    private void formNewUnSafeCluster(MCData d) {

        Cluster cluster = new Cluster();
        cluster.center = d;
        d.clusters.add(cluster);
        cluster.members.add(d);
        //find associate clusters
        safeClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(cluster.center, c.center) <= Constants.R * 2)).map((c) -> {
            cluster.associateClusters.add(c);
            return c;
        }).map((c) -> {
            c.associateClusters.add(cluster);
            return c;
        }).forEach((c) -> {
            //find neighbors for d
            c.members.stream().filter((d2) -> (d.isNeighbor(d2))).forEach((d2) -> {
                if (d.getSlideIndex() > d2.getSlideIndex()) {
                    d.addPrecedingNeighbor(d2);
                } else {
                    d.numSucceedingNeighbors++;
                }
            });
        });
        
        numberDistance+= safeClusters.size();

        unsafeClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(cluster.center, c.center) <= Constants.R * 2)).map((c) -> {
            cluster.associateClusters.add(c);
            return c;
        }).map((c) -> {
            c.associateClusters.add(cluster);
            return c;
        }).forEach((c) -> {
            //find neighbors for c
            c.members.stream().filter((d2) -> (d.isNeighbor(d2))).map((d2) -> {
                if (d.getSlideIndex() > d2.getSlideIndex()) {
                    d.addPrecedingNeighbor(d2);
                } else {
                    d.numSucceedingNeighbors++;
                }
                return d2;
            }).forEach((d2) -> {

                d2.numSucceedingNeighbors++;
            });
        });
        numberDistance+= unsafeClusters.size();
        //add to unsafe list
        unsafeClusters.add(cluster);
    }

    private void addPointToUnSafeCluster(MCData d, Cluster cluster) {
        cluster.members.stream().forEach((d2) -> {
            if (d.getSlideIndex() > d2.getSlideIndex()) {
                d.addPrecedingNeighbor(d2);
            } else {
                d.numSucceedingNeighbors++;
            }
            d2.numSucceedingNeighbors++;
        });

        cluster.members.add(d);
        d.clusters.add(cluster);

        if (cluster.isSafe()) {
            unsafeClusters.remove(cluster);
            safeClusters.add(cluster);
            cluster.members.stream().forEach((d3) -> {
                d3.reset();
            });

        }

        //check with associate clusters
        cluster.associateClusters.stream().forEach((c) -> {
            double distanceToCenter = DistanceFunction.euclideanDistance(d, c.center);
            numberDistance++;
            if (distanceToCenter > Constants.R * 3.0 / 2) {

            } else if (distanceToCenter <= Constants.R / 2) {
                c.members.stream().map((d2) -> {
                    if (!c.isSafe()) {
                        d2.numSucceedingNeighbors++;
                    }
                    return d2;
                }).forEach((d2) -> {
                    if (!cluster.isSafe()) {
                        if (d.getSlideIndex() <= d2.getSlideIndex()) {
                            d.numSucceedingNeighbors++;
                        } else {
                            d.addPrecedingNeighbor(d2);
                        }
                    }
                });
            } else {
                c.members.stream().filter((d2) -> (d.isNeighbor(d2))).map((d2) -> {
                    if (!c.isSafe());
                    d2.numSucceedingNeighbors++;
                    return d2;
                }).forEach((d2) -> {
                    if (!cluster.isSafe()) {
                        if (d.getSlideIndex() <= d2.getSlideIndex()) {
                            d.numSucceedingNeighbors++;
                        } else {
                            d.addPrecedingNeighbor(d2);
                        }
                    }
                });
            }
        });
    }

    private void processExpiredSlide(int slideIndex) {
        unsafeClusters.stream().forEach((cluster) -> {
            for (int i = cluster.members.size() - 1; i >= 0; i--) {
                MCData d = cluster.members.get(i);
                if (d.getSlideIndex() == slideIndex) {
                    cluster.members.remove(i);
                    d.clean();
//                    d = null;
                }
            }
        });
        for (int j = safeClusters.size() - 1; j >= 0; j--) {
            Cluster cluster = safeClusters.get(j);
            for (int i = cluster.members.size() - 1; i >= 0; i--) {
                MCData d = cluster.members.get(i);
                if (d.getSlideIndex() == slideIndex) {
                    cluster.members.remove(i);
                    d.clean();
                    d = null;
                }

            }
        }
        for (int j = safeClusters.size() - 1; j >= 0; j--) {
            Cluster cluster = safeClusters.get(j);
            if (!cluster.isSafe()) {
                
                numDispersedCluster ++;
                numRecomputation += cluster.members.size();
                //recompute neighbors for the rest
                cluster.members.stream().map((d2) -> {
                    d2.reset();
                    return d2;
                }).map((d2) -> {
                    cluster.members.stream().filter((d3) -> (d2.arrivalTime != d3.arrivalTime)).forEach((d3) -> {
                        if (d2.getSlideIndex() > d3.getSlideIndex()) {
                            d2.addPrecedingNeighbor(d3);
                        } else {
                            d2.numSucceedingNeighbors++;
                        }
                    });
                    return d2;
                }).forEach((d2) -> {
                    cluster.associateClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(d2, c.center) <= Constants.R * 3.0 / 2)).forEach((c) -> {
                        numberDistance++;
                        c.members.stream().filter((d3) -> (d2.isNeighbor(d3))).forEach((d3) -> {
                            if (d2.getSlideIndex() > d3.getSlideIndex()) {
                                d2.addPrecedingNeighbor(d3);
                            } else {
                                d2.numSucceedingNeighbors++;
                            }
                        });
                    });
                });
                safeClusters.remove(j);
                unsafeClusters.add(cluster);
            }
        }

        scanToRemoveExpiredNeighbor(slideIndex);

        removeZeroCluster();

    }

    private ArrayList<MCData> scanToFindOutlier() {

        ArrayList<MCData> result = new ArrayList<>();
        unsafeClusters.stream().forEach((c) -> {
            c.members.stream().filter((d) -> (d.isOutlier())).forEach((d) -> {
                result.add(d);
            });
        });
        return result;
    }

    private void removeZeroCluster() {

        for (int i = unsafeClusters.size() - 1; i >= 0; i--) {
            Cluster cluster = unsafeClusters.get(i);
            if (cluster.members.isEmpty()) {
                unsafeClusters.remove(i);
                cluster.associateClusters.clear();
                cluster.associateClusters = null;
                //  cluster.members = null;
            }

            //  cluster.center = null;
        }
    }

    public void findNeighbors(MCData d) {
        ArrayList<Cluster> clusters=  new ArrayList<>();
        clusters.addAll(safeClusters);
        clusters.addAll(unsafeClusters);
        for(Cluster c: clusters){
            if(DistanceFunction.euclideanDistance(d, c.center) <= Constants.R*3/2){
                for(MCData d2: c.members){
                    if(DistanceFunction.euclideanDistance(d, d2) <= Constants.R){
                        if(d2.getSlideIndex() >= d.getSlideIndex())
                            d.numSucceedingNeighbors++;
                        else {
                            if(d.numPrecedingNeighbor.get(d2.getSlideIndex())!=null){
                                d.numPrecedingNeighbor.put(d2.getSlideIndex(), d.numPrecedingNeighbor.get(d2.getSlideIndex())+1);
                            }
                            
                        }
                    }
                }
            }
        }
        
    }

    public class Cluster {

        public MCData center;
        public ArrayList<MCData> members = new ArrayList<>();
        public ArrayList<Cluster> associateClusters = new ArrayList<>();

        public boolean isSafe() {
            return members.size() > Constants.k;
        }

        public Cluster() {
        }
    }

    public class MCData extends Data {

        public ArrayList<Cluster> clusters = new ArrayList<>();
        public int numSucceedingNeighbors;
        public HashMap<Integer, Integer> numPrecedingNeighbor = new HashMap<>();

        public void clean() {

            clusters.clear();
            clusters = null;
            numPrecedingNeighbor.clear();
            numPrecedingNeighbor = null;
        }

        public MCData(Data d) {
            super();
            this.arrivalTime = d.arrivalTime;
            this.values = d.values;
            this.hashCode = d.hashCode;
            clusters = new ArrayList<>();
            numPrecedingNeighbor = new HashMap<>();
            numSucceedingNeighbors = 0;
            this.nodeName = d.nodeName;
        }

        private boolean isNeighbor(MCData d2) {
            numberDistance++;
            return DistanceFunction.euclideanDistance(this, d2) <= Constants.R
                    && this.arrivalTime != d2.arrivalTime;
        }

        private void addPrecedingNeighbor(MCData d2) {
            int slideIndex = d2.getSlideIndex();
            if (numPrecedingNeighbor.get(slideIndex) != null) {
                numPrecedingNeighbor.put(slideIndex, numPrecedingNeighbor.get(slideIndex) + 1);
            } else {
                numPrecedingNeighbor.put(slideIndex, 1);
            }
        }

        private int getSlideIndex() {
            return (int) Math.floor((arrivalTime - 1) / Constants.slide);
        }

        private boolean isOutlier() {

            int numNeighbor = numSucceedingNeighbors;
            numNeighbor = numPrecedingNeighbor.keySet().stream().map((k) -> numPrecedingNeighbor.get(k)).reduce(numNeighbor, Integer::sum);
            return numNeighbor < Constants.k;
        }

        private void reset() {
            numPrecedingNeighbor.clear();
            numSucceedingNeighbors = 0;

        }
    }

}
