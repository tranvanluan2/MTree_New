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

/**
 *
 * @author Luan
 */
public class MCOD_Safe_Wait {

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
    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int _currentTime, int W, int slide) {
        processExpiredSlide((_currentTime - W - 1) / Constants.slide);
        data.stream().forEach((d) -> {
            processNewData(new MCData(d));
        });
        unsafeClusters.stream().forEach((cluster) -> {
            cluster.slide_members.values().stream().forEach((points) -> {
                points.stream().filter((d2) -> (d2.isOutlier())).forEach((d2) -> {
                    reProbe(d2, (_currentTime - 1) / Constants.slide);
                });
            });
        });
        ArrayList<Data> outliers = scanToFindOutlier();
        
        printStatistics();
        return outliers;
    }

    public static void printStatistics() {

        int numPointInSafeCluster = 0;
        int numPointInUnSafeCluster = 0;
        numPointInSafeCluster = safeClusters.stream().map((c) -> c.numMembers()).reduce(numPointInSafeCluster, Integer::sum);
        numPointInUnSafeCluster = unsafeClusters.stream().map((c) -> c.numMembers()).reduce(numPointInUnSafeCluster, Integer::sum);
        
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
        Cluster cluster = findSafeClusterToAdd(d);
        if (cluster != null) {
            addPointToSafeCluster(d, cluster);
        } else {
            //find unsafe cluster to add
            cluster = findUnsafeClusterToAdd(d);
            if (cluster != null) {
                addPointToUnSafeCluster(d, cluster);
            } else {
                //form new cluster 
                formNewUnSafeCluster(d);
            }

        }
    }

    public void scanToRemoveExpiredNeighbor(int slideIndex) {

        unsafeClusters.stream().forEach((cluster) -> {

            cluster.slide_members.values().stream().forEach((points) -> {
                points.stream().forEach((d) -> {
                    d.numPrecedingNeighbor.remove(slideIndex);
                });
            });
        });
    }

    private Cluster findSafeClusterToAdd(MCData d) {
        Cluster result = null;
        for (Cluster cluster : safeClusters) {
            numberDistance++;
            if (DistanceFunction.euclideanDistance(d, cluster.center) <= Constants.R / 2) {
                result = cluster;
                break;
            }
            
        }
        return result;
    }

    public void reProbe(MCData d, int newestSlideIndex) {
        numRecomputation++;
        int slideIndex = d.lastProbe + 1;
        Cluster cluster = d.clusters.get(0);

        ArrayList<Cluster> candidates = new ArrayList<>();
        candidates.add(cluster);
        candidates.addAll(cluster.associateClusters);
        while (slideIndex <= newestSlideIndex) {

            for (Cluster c : candidates) {
                numberDistance++;
                if (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R * 3 / 2) {
                    
                    ArrayList<MCData> points = c.slide_members.get(slideIndex);
                    if (points != null) {

                        for (MCData d2 : points) {
                            if ((d.clusters.get(0).center.arrivalTime == d2.clusters.get(0).center.arrivalTime
                                    && d.arrivalTime != d2.arrivalTime) || d.isNeighbor(d2)) {
                                d.numSucceedingNeighbors++;
                            }
                            if (d.numSucceedingNeighbors >= Constants.k) {
                                d.lastProbe = slideIndex;
                                break;
                            }
                        }
                    }
                    if (d.numSucceedingNeighbors >= Constants.k) {
                        d.lastProbe = slideIndex;
                        return;
                    }
                }

            }
            d.lastProbe = slideIndex;
            if (!d.isOutlier()) {
                return;
            }
            slideIndex++;
        }

        //  slideIndex = d.lastProbe - 1;
        slideIndex = d.getSlideIndex() - 1;
        while (slideIndex >= newestSlideIndex - Constants.W / Constants.slide + 1
                && !d.probePrecedingNeighbor) {

            for (Cluster c : candidates) {
                numberDistance++;
                if (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R * 3 / 2) {
                    ArrayList<MCData> points = c.slide_members.get(slideIndex);
                    if (points != null) {

                        for (MCData d2 : points) {
                            if ((d.clusters.get(0).center.arrivalTime == d2.clusters.get(0).center.arrivalTime
                                    && d.arrivalTime != d2.arrivalTime) || d.isNeighbor(d2)) {
                                d.addPrecedingNeighbor(d2);
                            }
                            if (!d.isOutlier()) {
                                break;
                            }
                        }
                    }
                }
            }
            if (!d.isOutlier()) {
                //d.lastProbe = slideIndex;
                break;
            }
            slideIndex--;
        }

        d.probePrecedingNeighbor = true;
    }

    private void addPointToSafeCluster(MCData d, Cluster cluster) {
        //  cluster.members.add(d);
        cluster.addNewPointToSlideMember(d);
        d.clusters.add(cluster);

    }

    private Cluster findUnsafeClusterToAdd(MCData d) {
        Cluster result = null;
        for (Cluster cluster : unsafeClusters) {
            numberDistance++;
            if (DistanceFunction.euclideanDistance(d, cluster.center) <= Constants.R / 2) {
                result = cluster;
                break;
            }
        }
        return result;
    }

    private void formNewUnSafeCluster(MCData d) {

        Cluster cluster = new Cluster();
        cluster.center = d;
        d.clusters.add(cluster);
        //  cluster.members.add(d);
        cluster.addNewPointToSlideMember(d);
        //find associate clusters
        safeClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(cluster.center, c.center) <= Constants.R * 2)).map((c) -> {
            cluster.associateClusters.add(c);
            return c;
        }).forEach((c) -> {
            c.associateClusters.add(cluster);
        });
        
        numberDistance += safeClusters.size();

        unsafeClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(cluster.center, c.center) <= Constants.R * 2)).map((c) -> {
            cluster.associateClusters.add(c);
            return c;
        }).forEach((c) -> {
            c.associateClusters.add(cluster);
        });
        numberDistance += unsafeClusters.size();

        //add to unsafe list
        unsafeClusters.add(cluster);
    }

    private void addPointToUnSafeCluster(MCData d, Cluster cluster) {

        cluster.addNewPointToSlideMember(d);
        d.clusters.add(cluster);

        if (cluster.isSafe()) {
            unsafeClusters.remove(cluster);
            safeClusters.add(cluster);

            cluster.slide_members.values().stream().forEach((points) -> {
                points.stream().forEach((d3) -> {
                    d3.reset();
                });
            });

        }
    }

    private void processExpiredSlide(int slideIndex) {

        //remove expired data points in unsafe cluster
        unsafeClusters.stream().forEach((cluster) -> {
            cluster.slide_members.remove(slideIndex);
        });

        //remove expired data points in safe cluster
        for (int j = safeClusters.size() - 1; j >= 0; j--) {
            Cluster cluster = safeClusters.get(j);

            cluster.slide_members.remove(slideIndex);

            if (!cluster.isSafe()) {
                numDispersedCluster++;
               
                cluster.slide_members.values().stream().forEach((points) -> {
                    points.stream().forEach((d2) -> {
                        d2.reset();
                    });
                }); //recompute neighbors for the rest

                safeClusters.remove(j);
                unsafeClusters.add(cluster);
            }
        }

        removeZeroCluster();
        scanToRemoveExpiredNeighbor(slideIndex);

    }

    private ArrayList<Data> scanToFindOutlier() {

        ArrayList<Data> result = new ArrayList<>();
        unsafeClusters.stream().forEach((c) -> {

            c.slide_members.values().stream().forEach((points) -> {
                points.stream().filter((d) -> (d.isOutlier())).forEach((d) -> {
                    result.add(d);
                });
            });

        });
        return result;
    }

    private void removeZeroCluster() {

        for (int i = unsafeClusters.size() - 1; i >= 0; i--) {
            Cluster cluster = unsafeClusters.get(i);
            if (cluster.slide_members.isEmpty()) {
                unsafeClusters.remove(i);
                cluster.associateClusters.clear();
            }

        }
    }

    private class Cluster {

        public MCData center;
//        public ArrayList<MCData> members = new ArrayList<>();
        public ArrayList<Cluster> associateClusters = new ArrayList<>();
        public HashMap<Integer, ArrayList<MCData>> slide_members = new HashMap<>();

        public boolean isSafe() {
            //return members.size() > Constants.k;
            int numMember = 0;
            numMember = slide_members.keySet().stream().map((slideIndex) -> slide_members.get(slideIndex).size()).reduce(numMember, Integer::sum);
            return numMember > Constants.k;
        }

        public Cluster() {
        }
         public int numMembers() {
            int count = 0;
            count = slide_members.values().stream().map((members) -> members.size()).reduce(count, Integer::sum);
            return count;
        }

        public void addNewPointToSlideMember(MCData d) {
            ArrayList<MCData> points = slide_members.get(d.getSlideIndex());
            if (points == null) {
                points = new ArrayList<>();
                points.add(d);
                slide_members.put(d.getSlideIndex(), points);
            } else {
                points.add(d);
            }

        }
    }

    private class MCData extends Data {

        public ArrayList<Cluster> clusters = new ArrayList<>();
        public int numSucceedingNeighbors;
        public HashMap<Integer, Integer> numPrecedingNeighbor = new HashMap<>();
        public int lastProbe;
        public int sIndex;
        public boolean probePrecedingNeighbor = false;

        public void clean() {
            clusters.clear();
            numPrecedingNeighbor.clear();
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
            lastProbe = sIndex - 1;
        }

        public int countNeighborToSlide(int slideIndex) {
            int result = numSucceedingNeighbors;
            result = numPrecedingNeighbor.keySet().stream().filter((key) -> (key < getSlideIndex() && key >= slideIndex)).map((key) -> numPrecedingNeighbor.get(key)).reduce(result, Integer::sum);
            return result;
        }

        private boolean isNeighbor(MCData d2) {
            numberDistance++;
            return DistanceFunction.euclideanDistance(this, d2) <= Constants.R && this.arrivalTime != d2.arrivalTime;
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
            return sIndex;
        }

        private boolean isOutlier() {

            int numNeighbor = numSucceedingNeighbors;
            //numNeighbor += numPrecedingNeighbor.keySet().stream().map((k) -> numPrecedingNeighbor.get(k)).reduce(numNeighbor, Integer::sum);
            for (Integer k : numPrecedingNeighbor.keySet()) {
                numNeighbor += numPrecedingNeighbor.get(k);
            }
            return numNeighbor < Constants.k;
        }

        private void reset() {
            numPrecedingNeighbor.clear();
            numSucceedingNeighbors = 0;
            probePrecedingNeighbor = false;
            lastProbe = getSlideIndex() - 1;

        }
    }

}
