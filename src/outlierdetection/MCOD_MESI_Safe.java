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
public class MCOD_MESI_Safe { 

    public static ArrayList<Cluster> safeClusters = new ArrayList<>();
    public static ArrayList<Cluster> unsafeClusters = new ArrayList<>();

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int _currentTime, int W, int slide) {
        processExpiredSlide((_currentTime - W-1) / Constants.slide);
        data.stream().forEach((d) -> {
            processNewData(new MCData(d));
        });
        unsafeClusters.stream().forEach((cluster) -> {
            cluster.slide_members.values().stream().forEach((points) -> {
                points.stream().filter((d2) -> (d2.isOutlier())).forEach((d2) -> {
                    reProbe(d2, (_currentTime-1) / Constants.slide);
                });
            });
        });
        ArrayList<Data> outliers = scanToFindOutlier();
        return outliers;
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
            if (DistanceFunction.euclideanDistance(d, cluster.center) <= Constants.R / 2) {
                result = cluster;
                break;
            }
        }
        return result;
    }

    public void reProbe(MCData d, int newestSlideIndex) {
        int slideIndex = d.lastProbe + 1;
        while (slideIndex <= newestSlideIndex) {
            Cluster cluster = d.clusters.get(0);
            
            ArrayList<Cluster> candidates = new ArrayList<>();
            candidates.add(cluster);
            candidates.addAll(cluster.associateClusters);
            
            for (Cluster c : candidates) {
                if (DistanceFunction.euclideanDistance(d, c.center) <= Constants.R * 3 / 2) {
                    ArrayList<MCData> points = c.slide_members.get(slideIndex);
                    if (points != null) {
                        points.stream().filter((d2) -> (d.isNeighbor(d2))).forEach((_item) -> {
                            d.numSucceedingNeighbors++;
                        });
                    }
                }
            }
            if (!d.isOutlier()) {
                d.lastProbe = slideIndex;
                break;
            }
            slideIndex++;
        }
    }

    private void addPointToSafeCluster(MCData d, Cluster cluster) {
        //  cluster.members.add(d);
        cluster.addNewPointToSlideMember(d);
        d.clusters.add(cluster);

        //check with points in associate cluster
        cluster.associateClusters.stream().filter((c) -> !(c.isSafe())).forEach((c) -> {
            double distanceToCenter = DistanceFunction.euclideanDistance(d, c.center);
            if (distanceToCenter > Constants.R * 3.0 / 2) {

            } else if (distanceToCenter <= Constants.R / 2) {
                ArrayList<MCData> points = c.slide_members.get(d.getSlideIndex());
                points.stream().filter((d2) -> (d2.getSlideIndex() == d.getSlideIndex() )).forEach((d2) -> {
                    d2.numSucceedingNeighbors++;
                });
                
            } else {
                ArrayList<MCData> points = c.slide_members.get(d.getSlideIndex());
                points.stream().filter((d2) -> (d.isNeighbor(d2))).filter((d2) -> (d2.getSlideIndex() == d.getSlideIndex())).forEach((d2) -> {
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

        unsafeClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(cluster.center, c.center) <= Constants.R * 2)).map((c) -> {
            cluster.associateClusters.add(c);
            return c;
        }).forEach((c) -> {
            c.associateClusters.add(cluster);
        });
        unsafeClusters.stream().filter((c) -> (DistanceFunction.euclideanDistance(cluster.center, c.center) <= Constants.R * 2)).map((c) -> {
            cluster.associateClusters.add(c);
            return c;
        }).map((c) -> {
            c.associateClusters.add(cluster);
            return c;
        });
//            

        int slideIndex = d.getSlideIndex();
        while (slideIndex >= d.getSlideIndex() - Constants.W / Constants.slide) {
            for (Cluster c : cluster.associateClusters) {
                ArrayList<MCData> points = c.slide_members.get(slideIndex);
                if (points != null) {
                    for (MCData d2 : points) {
                        if (d.isNeighbor(d2)) {
                            if (d.countNeighborToSlide(slideIndex) < Constants.k) {
                                d.addPrecedingNeighbor(d2);
                            }
                            if (!c.isSafe()) {
                                if (d2.getSlideIndex() == d.getSlideIndex()) {
                                    d2.numSucceedingNeighbors++;
                                }
                            }
                        }
                    }
                }

            }
            slideIndex--;
        }

        //add to unsafe list
        unsafeClusters.add(cluster);
    }

    private void addPointToUnSafeCluster(MCData d, Cluster cluster) {

        for (ArrayList<MCData> points : cluster.slide_members.values()) {
            points.stream().map((d2) -> {
                if (d.getSlideIndex() > d2.getSlideIndex()) {
                    d.addPrecedingNeighbor(d2);
                } else {
                    d.numSucceedingNeighbors++;
                }
                return d2;
            }).filter((d2) -> (d2.getSlideIndex() == d.getSlideIndex())).forEach((d2) -> {
                d2.numSucceedingNeighbors++;
            });
        }
        // cluster.members.add(d);
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

        //check with associate clusters
        int slideIndex = d.getSlideIndex();
        while (slideIndex > d.getSlideIndex() - Constants.W / Constants.slide) {
            for (Cluster c : cluster.associateClusters) {
                double distanceToCenter = DistanceFunction.euclideanDistance(d, c.center);

                if (distanceToCenter > Constants.R * 3.0 / 2) {

                } else if (distanceToCenter <= Constants.R / 2) {

                    ArrayList<MCData> points = c.slide_members.get(slideIndex);
                    if (points != null) {
                        for (MCData d2 : points) {
                            if (!c.isSafe()) {
                                if (d2.getSlideIndex() == d.getSlideIndex()) {
                                    d2.numSucceedingNeighbors++;
                                }
                            }
                            if (!cluster.isSafe() && d.countNeighborToSlide(slideIndex) <= Constants.k) {
                                if (d.getSlideIndex() <= d2.getSlideIndex()) {
                                    d.numSucceedingNeighbors++;
                                } else {
                                    d.addPrecedingNeighbor(d2);
                                }
                            }
                        }
                    }

                } else {

                    ArrayList<MCData> points = c.slide_members.get(slideIndex);
                    if (points != null) {
                        for (MCData d2 : points) {
                            if (d.isNeighbor(d2)) {
                                if (!c.isSafe()) {
                                    if (d2.getSlideIndex() == d.getSlideIndex()) {
                                        d2.numSucceedingNeighbors++;
                                    }
                                }
                                if (!cluster.isSafe() && d.countNeighborToSlide(slideIndex) <= Constants.k) {
                                    if (d.getSlideIndex() <= d2.getSlideIndex()) {
                                        d.numSucceedingNeighbors++;
                                    } else {
                                        d.addPrecedingNeighbor(d2);
                                    }
                                }
                            }
                        }
                    }

                }
            }
            slideIndex--;
        }
    }
    private void processSafeBecomeUnsafeCluster(){
        unsafeClusters.stream().forEach((cluster) -> {
           if(!cluster.isSafe()){
               for (ArrayList<MCData> points : cluster.slide_members.values()) {

                    for (MCData d2 : points) {

                        d2.reset();
                        //check neighbors within cluster
                        for (ArrayList<MCData> ps : cluster.slide_members.values()) {

                            for (MCData d3 : ps) {
                                if (d2.arrivalTime != d3.arrivalTime) {
                                    if (d2.getSlideIndex() > d3.getSlideIndex()) {
                                        d2.addPrecedingNeighbor(d3);
                                    } else {
                                        d2.numSucceedingNeighbors++;
                                    }
                                }
                            }
                        }
                        //find neighbors in associate cluster
                        for (Cluster c : cluster.associateClusters) {
                            if (DistanceFunction.euclideanDistance(d2, c.center) <= Constants.R * 3.0 / 2) {
                                for (ArrayList<MCData> ps : c.slide_members.values()) {

                                    for (MCData d3 : ps) {
                                        if (d2.isNeighbor(d3)) {
                                            if (d2.getSlideIndex() > d3.getSlideIndex()) {
                                                d2.addPrecedingNeighbor(d3);
                                            } else {
                                                d2.numSucceedingNeighbors++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                //recompute neighbors for the rest

                safeClusters.remove(cluster);
                unsafeClusters.add(cluster);
           }
        });
    }

    private void processExpiredSlide(int slideIndex) {

        //remove expired data points in unsafe clusters. 
        unsafeClusters.stream().forEach((cluster) -> {
            cluster.slide_members.remove(slideIndex);
        });
        //remove expired data points in safe clusters. 
        for (int j = safeClusters.size() - 1; j >= 0; j--) {
            Cluster cluster = safeClusters.get(j);

            cluster.slide_members.remove(slideIndex);
            
            
        }
//if the cluster becomes unsafe, recompute neighbors for its members
        processSafeBecomeUnsafeCluster();
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
            if (unsafeClusters.get(i).slide_members.isEmpty()) {
                unsafeClusters.remove(i);
            }
        }
    }

    private class Cluster {

        public MCData center;
        public ArrayList<Cluster> associateClusters = new ArrayList<>();
        public HashMap<Integer, ArrayList<MCData>> slide_members = new HashMap<>();

        public int numMembers(){
            int count = 0;
            count = slide_members.values().stream().map((members) -> members.size()).reduce(count, Integer::sum);
            return count;
        }
        public boolean isSafe() {
            
            
            return numMembers() > Constants.k;
        }

        public Cluster() {
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
        public int lastProbe ;
        public int sIndex;

        public MCData(Data d) {
            super();
            this.arrivalTime = d.arrivalTime;
            this.values = d.values;
            this.hashCode = d.hashCode;
            clusters = new ArrayList<>();
            numPrecedingNeighbor = new HashMap<>();
            numSucceedingNeighbors = 0;
            sIndex = (int) Math.floor((arrivalTime - 1) / Constants.slide);
            lastProbe=sIndex;
        }

        public int countNeighborToSlide(int slideIndex) {
            int result = numSucceedingNeighbors;
            result = numPrecedingNeighbor.keySet().stream().filter((key) -> (key < getSlideIndex() && key >= slideIndex)).map((key) -> numPrecedingNeighbor.get(key)).reduce(result, Integer::sum);
            return result;
        }

        private boolean isNeighbor(MCData d2) {
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
            numNeighbor = numPrecedingNeighbor.keySet().stream().map((k) -> numPrecedingNeighbor.get(k)).reduce(numNeighbor, Integer::sum);
            return numNeighbor < Constants.k;
        }

        private void reset() {
            numPrecedingNeighbor.clear();
            numSucceedingNeighbors = 0;
            lastProbe = sIndex;
            
        }
    }

}
