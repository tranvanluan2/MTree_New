/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Utils;
import static outlierdetection.MESI.avgAllWindowNeighborList;
import static outlierdetection.MESI.avgAllWindowTriggerList;
import static outlierdetection.MESI.avgNeighborList;
import static outlierdetection.MESI.count;
import static outlierdetection.MESI.outlierList;
import static outlierdetection.MESI.totalTrigger;

/**
 *
 * @author Luan
 */
public class MesiAndCluster {

    public static ArrayList<Data> outlierList;
    public static Window window = new Window();

    public static HashMap<MESIMCODOBject, ArrayList<MESIMCODOBject>> micro_clusters = new HashMap<>();
    private double theta = 1.5;

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime,
            int W, int slide) {

        //clear points in expiring slide
        Slide expiringSlide = window.getExpiringSlide();
        if (expiringSlide != null) {
            expiringSlide.points.clear();
            expiringSlide.mtree = null;
        }
        outlierList = new ArrayList<>();
        if (data.size() == Constants.W) {
            // split into slides
            int numSlide = (int) Math.ceil(Constants.W * 1.0 / Constants.slide);
            for (int i = 0; i < numSlide; i++) {

                ArrayList<Data> d = new ArrayList<>();
                for (int j = 0; j < Constants.slide; j++) {
                    if (i * Constants.slide + j < data.size()) {
                        d.add(data.get(i * Constants.slide + j));
                    }
                }
                Slide s = new Slide(d, currentTime, 1);
                window.addNewSlide(s);

            }

        } else if (data.size() <= Constants.slide) {
            // add this slide to window
            Slide s = new Slide(data, currentTime, 1);
            window.addNewSlide(s);

        }

        Thresh_LEAP(window);

        for (int i = window.startSlide; i < window.slides.size(); i++) {
            if (i >= 0) {
                for (Data o : window.slides.get(i).points) {
                    MESIMCODOBject _o = (MESIMCODOBject)o;
                    if (_o.isOutlier && !_o.isInCluster) {
                        outlierList.add(o);
                    }
                }
            }
        }

        return outlierList;

    }

    public void Thresh_LEAP(Window window) {

        if (window.slides.size() <= Math.ceil(Constants.W * 1.0 / Constants.slide)) {
            window.slides.stream().forEach((s) -> {
                s.points.stream().forEach((p) -> {
                    LEAP2((MESIMCODOBject) p, window);
                });
            });
        } else {
            window.getNewestSlide().points.stream().forEach((p) -> {
                LEAP2((MESIMCODOBject) p, window);
            });
        }

        Slide expiredSlide = window.getExpiredSlide();

        if (expiredSlide != null) {

            count++;
            totalTrigger += expiredSlide.triggered.size();
            /**
             * clear expired slides
             */
            for (int i = 0; i < expiredSlide.id; i++) {
                if (window.slides.get(i) != null) {
                    window.slides.set(i, null);
                }
            }
            for(MESIObject ob: expiredSlide.points){
                MESIMCODOBject p = (MESIMCODOBject)ob;
                if(p.isInCluster == true){
                    ArrayList<MESIMCODOBject> points = micro_clusters.get(p.cluster_center);
                    points.remove(p);
                    if(points.size() < Constants.k){
                        processDestroyedCluster(p.cluster_center);
                    }
                }
            }

            expiredSlide.triggered.stream().filter((p) -> (!p.isSafe)).map((p) -> {
                p.expireEvidence(expiredSlide, window);
                return p;
            }).forEach((p) -> {
                // compute skipped slide for p
                if(!((MESIMCODOBject)p).isInCluster)
                    LEAP2((MESIMCODOBject) p, p.getSkippedPoints(window, expiredSlide));
            });

            expiredSlide.points.clear();
            expiredSlide.triggered.clear();
            expiredSlide.mtree = null;
            
//            expiredSlide = null;
        }

    }

    public MESIMCODOBject findClusterInRangeR2(Data d) {
        ArrayList<MESIMCODOBject> results = new ArrayList<>();
        MESIMTreeClass mtree = new MESIMTreeClass();
        micro_clusters.keySet().stream().map((center) -> {
            if (mtree.getDistanceFunction().calculate(d, center) <= Constants.R / 2) {
                results.add(center);
            }
            return center;
        });
        if (results.isEmpty()) {
            return null;
        } else {
            return results.get(0);
        }
    }

    public int countNeighborNotInCluster(HashSet<MESIMCODOBject> neighbors) {
        int n = 0;
        for (MESIMCODOBject d : neighbors) {

            if (d.isInCluster != true) {
                n++;
            }
        }
        return n;
    }

    public boolean LEAP2(MESIMCODOBject p, Window window) {

        boolean isOutlier = true;
        if (p.preEvidence == null) {
            p.numSucEvidence = 0;
            p.preEvidence = new HashMap<>();
        }

        //find cluster for point p
        MESIMCODOBject cluster = findClusterInRangeR2(p);
        if (cluster != null) {
            ArrayList<MESIMCODOBject> points = micro_clusters.get(cluster);
            points.add(p);
            p.isOutlier = false;
            p.isInCluster = true;
            p.cluster_center = cluster;
            micro_clusters.put(cluster, points);

            return p.isOutlier;
        }

        //cannot find any cluster 
        int currentSlideIndex = p.getCurrentSlideIndex();

        HashSet<MESIMCODOBject> succeedingNeighbors = new HashSet<>();
        int newestSlideIndex = window.getNewestSlide().id;
        for (int i = currentSlideIndex; i <=  newestSlideIndex; i++) {

            Slide s = window.slides.get(i);

            HashSet<MESIMCODOBject> neighbors = s.findNeighbors2(p, (int) (Constants.k * theta));
            succeedingNeighbors.addAll(neighbors);
            if (succeedingNeighbors.size() > Constants.k * theta) {
                break;
            }

        }
        int numNeighborNotInClusters = countNeighborNotInCluster(succeedingNeighbors);
        if (numNeighborNotInClusters > Constants.k * 1.1) {
            //form cluster
            formNewCluster(p, succeedingNeighbors);
        }
        for (MESIMCODOBject d : succeedingNeighbors) {
            if (d.arrivalTime != p.arrivalTime) {
                p.lastLEAPSlide = d.getCurrentSlideIndex();
                p.updateSuccEvidence();
                if (p.isMESIAquired() || p.isInCluster) {
                    p.isOutlier = false;

                    if (p.numSucEvidence >= Constants.k) {

                        p.isSafe = true;
                        return isOutlier;
                    }

                }
            }
        }

        HashSet<MESIMCODOBject> preceedingNeighbors = new HashSet<>();

        List<Slide> precSlides = p.getPrecSlides(window);
        if (precSlides != null) {
            for (int i = 0; i < precSlides.size(); i++) {
                Slide slide = precSlides.get(i);
                // p.preEvidence.put(slide, 0);

                if (slide != null) {

                    HashSet<MESIMCODOBject> neighbors = slide.findNeighbors2(p, (int) (Constants.k * theta));
                    preceedingNeighbors.addAll(neighbors);
                    if (preceedingNeighbors.size() > Constants.k * theta) {
                        break;
                    }
                    /**
                    * update triggerlist if p is outlier
                     */
                    if (i == precSlides.size() - 1) {
                        slide.updateTriggeredList(p);
                    }
                    
                    

                }
            }
        }
        
        numNeighborNotInClusters = countNeighborNotInCluster(preceedingNeighbors);
        if (numNeighborNotInClusters > Constants.k * 1.1) {
            //form cluster
            formNewCluster(p, preceedingNeighbors);
        }
        
        //add to m-tree
        if(window.slides.get(p.getCurrentSlideIndex()).mtree!=null && !p.isInCluster){
            window.slides.get(p.getCurrentSlideIndex()).mtree.add(p);
        }
        for (Data d : preceedingNeighbors) {
            if (d.arrivalTime != p.arrivalTime) {
                p.updatePrecEvidence(window.slides.get(p.getCurrentSlideIndex()));
                if (p.isMESIAquired() == true || p.isInCluster) {
                    p.isOutlier = false;
                    if (p.numSucEvidence < Constants.k) {
                        p.isSafe = false;
                    } else {
                        p.isSafe = true;
                    }
                    window.slides.get(p.getCurrentSlideIndex()).updateTriggeredList(p);
                    return isOutlier;
                }
            }
        }
        
//
//        isOutlier = true;
//        p.isOutlier = isOutlier;
        if (!p.isMESIAquired()) {
            p.isOutlier = true;
        }
        return isOutlier;

    }

    private void formNewCluster(MESIMCODOBject p, HashSet<MESIMCODOBject> neighbors) {
        ArrayList<MESIMCODOBject> pointsNotInCluster = new ArrayList<>();
        for (MESIMCODOBject n : neighbors) {
            if (!n.isInCluster) {
                pointsNotInCluster.add(n);
                n.isInCluster = true;
                n.cluster_center = p;
                n.isOutlier = false;
            }
        }
        p.cluster_center = p;
        p.isInCluster = true;
        p.isOutlier = false;
        pointsNotInCluster.add(p);
        micro_clusters.put(p, pointsNotInCluster);

    }

    private void processDestroyedCluster(MESIMCODOBject cluster_center) {
        ArrayList<MESIMCODOBject> points = micro_clusters.get(cluster_center);
        micro_clusters.remove(cluster_center);
        
        for(MESIMCODOBject p: points){
            p.reset();
            LEAP2(p, window);
        }
        
    
    }

}

class SlideMESI_MCOD extends Slide {

    public SlideMESI_MCOD(ArrayList<Data> data, int currentTime) {
        super(data, currentTime);
    }

}

class MESIMCODOBject extends MESIObject {

    public boolean isInCluster = false;
    public MESIMCODOBject cluster_center;

    public MESIMCODOBject(Data d, int currentTime) {
        super(d, currentTime);
        this.arrivalTime = d.arrivalTime;
        this.values = d.values;
        numPreEvidence = -1;
    }
    
    public void reset(){
        isInCluster = false;
        cluster_center = null;
        numPreEvidence = 0;
        numSucEvidence  = 0;
        preEvidence.clear();
        
        
    }

}
