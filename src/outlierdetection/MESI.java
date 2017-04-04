package outlierdetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mtree.ComposedSplitFunction;
import mtree.DistanceFunction;
import mtree.DistanceFunctions;
import mtree.MTree;
import mtree.PartitionFunctions;
import mtree.PromotionFunction;

import mtree.tests.Data;
import mtree.utils.Constants;
import mtree.utils.Pair;
import mtree.utils.Utils;
import mtree.tests.MesureMemoryThread;

public class MESI {

    /**
     *
     */
    public static ArrayList<Data> outlierList;
    public static Window window = new Window();

    public static int count = 0;
    public static int totalTrigger = 0;

    public double avgTriggerListLength = 0;
    public static double avgAllWindowTriggerList = 0;

    public static double avgNeighborList = 0;
    public static double avgAllWindowNeighborList = 0;
    
    public static double avgNumberSafeInliers = 0;
    public static int count2 = 0;

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {

        //clear points in expiring slide
        Slide expiringSlide = window.getExpiringSlide();
        if (expiringSlide != null) {
            expiringSlide.points.clear();
            expiringSlide.mtree = null;
        }
        outlierList = new ArrayList<>();
        long startCPUTime = Utils.getCPUTime();
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
                Slide s = new Slide(d, currentTime);
                window.addNewSlide(s);

            }

        } else if (data.size() <= Constants.slide) {
            // add this slide to window
            Slide s = new Slide(data, currentTime);
            window.addNewSlide(s);

        }
        long currentCPUTime = Utils.getCPUTime();
        MesureMemoryThread.timeForNewSlide += currentCPUTime - startCPUTime;

        Thresh_LEAP(window);

        startCPUTime = Utils.getCPUTime();

        for (int i = window.startSlide; i < window.slides.size(); i++) {
            if (i >= 0) {
                for (MESIObject o : window.slides.get(i).points) {
                    if (o.isOutlier) {
                        outlierList.add(o);
                    }
                }
            }
        }
        currentCPUTime = Utils.getCPUTime();
        MesureMemoryThread.timeForNewSlide += currentCPUTime - startCPUTime;

        //compute # safe inliers
        int numSafeInlier = 0;
        for (int i = window.startSlide; i < window.slides.size(); i++) {
            if (i >= 0) {
                for (MESIObject o : window.slides.get(i).points) {
                    if(o.numSucEvidence >=Constants.k)
                        numSafeInlier++;
                }
            }
        }
        avgNumberSafeInliers = (avgNumberSafeInliers* count2+numSafeInlier)/(count2+1);
        System.out.println("Number of safe inliers = "+ avgNumberSafeInliers);
        //comput avg trigger list
        int count2 = 0;
        int count3 = 0;
        for (int i = window.startSlide; i < window.slides.size(); i++) {
            if (i >= 0) {
                count2++;
                avgTriggerListLength += window.slides.get(i).triggered.size();
                for (MESIObject o : window.slides.get(i).points) {
                    avgNeighborList += o.preEvidence.size();
                    count3++;
                }
            }
        }
        if (count3 > 0) {
            avgNeighborList = avgNeighborList / count3;
            avgAllWindowNeighborList += avgNeighborList;
        }
        if (count2 > 0) {
            avgTriggerListLength = avgTriggerListLength / count2;
            avgAllWindowTriggerList += avgTriggerListLength;
        }
//         print_window();
//         print_outlier();

        return outlierList;

    }

    public void Thresh_LEAP(Window window) {

        long startTime = Utils.getCPUTime();

        if (window.slides.size() <= Math.ceil(Constants.W * 1.0 / Constants.slide)) {
            window.slides.stream().forEach((s) -> {
                s.points.stream().forEach((p) -> {
                    LEAP(p, window);
                });
            });
        } else {
            window.getNewestSlide().points.stream().forEach((p) -> {
                LEAP(p, window);
            });
        }

        MesureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;

        startTime = Utils.getCPUTime();
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

            expiredSlide.triggered.stream().filter((p) -> (!p.isSafe)).map((p) -> {
                p.expireEvidence(expiredSlide, window);
                return p;
            }).forEach((p) -> {
                // compute skipped slide for p
                LEAP(p, p.getSkippedPoints(window, expiredSlide));
            });

            expiredSlide.points.clear();
            expiredSlide.triggered.clear();
            expiredSlide.mtree = null;
//            expiredSlide = null;
        }

        MesureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;

    }

    public boolean LEAP(MESIObject p, Window window) {

        boolean isOutlier = false;
        if (p.preEvidence == null) {
            p.numSucEvidence = 0;
            p.preEvidence = new HashMap<>();
        }

        int currentSlideIndex = (int) Math.floor((p.arrivalTime - 1) / Constants.slide);

        for (int i = currentSlideIndex; i <= window.getNewestSlide().id; i++) {

            Slide s = window.slides.get(i);

            ArrayList<Data> neighbors = s.findNeighbors(p, Constants.k + 1);

            for (Data d : neighbors) {
                if (d.arrivalTime != p.arrivalTime) {
                    p.lastLEAPSlide = i;
                    p.updateSuccEvidence();
                    if (p.isMESIAquired()) {
                        p.isOutlier = false;

                        if (p.numSucEvidence >= Constants.k) {

                            p.isSafe = true;
                            return isOutlier;
                        }

                    }
                }
            }

        }

        List<Slide> precSlides = p.getPrecSlides(window);
        if (precSlides != null) {
            for (int i = 0; i < precSlides.size(); i++) {
                Slide slide = precSlides.get(i);
                // p.preEvidence.put(slide, 0);

                if (slide != null) {

//                    for (MESIObject o : slide.points) {
//                        if (true == p.isNeighborhood(o)) {
//                            p.updatePrecEvidence(slide);
//                            if (true == p.isMESIAquired()) {
//                                p.isOutlier = false;
//                                p.isSafe = false;
//                                slide.updateTriggeredList(p);
//
//                                return isOutlier;
//                            }
//                        }
//                    }
                    ArrayList<Data> neighbors = slide.findNeighbors(p, Constants.k + 1);
                    for (Data d : neighbors) {
                        if (d.arrivalTime != p.arrivalTime) {
                            p.updatePrecEvidence(slide);
                            if (p.isMESIAquired() == true) {
                                p.isOutlier = false;
                                if (p.numSucEvidence < Constants.k) {
                                    p.isSafe = false;
                                } else {
                                    p.isSafe = true;
                                }
                                slide.updateTriggeredList(p);
                                return isOutlier;
                            }
                        }
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
//
//        isOutlier = true;
//        p.isOutlier = isOutlier;
        if (!p.isMESIAquired()) {
            p.isOutlier = true;
        }
        return isOutlier;

    }

    public void print_window() {
        System.out.println("Dat points: ");
        for (int i = window.startSlide; i < window.slides.size(); i++) {
            if (i >= 0) {
                for (MESIObject o : window.slides.get(i).points) {
                    System.out.print(o.values[0] + " ; ");
                }
            }
        }
        System.out.println();

    }

    public void print_outlier() {
        System.out.println("Outliers: ");
        outlierList.stream().forEach((o) -> {
            System.out.print(o.values[0] + " ; ");
        });
        System.out.println();
    }
}

class Window {

    public ArrayList<Slide> slides = new ArrayList<>();
    public int startSlide;

    public Slide getNewestSlide() {
        if (slides.isEmpty()) {
            return null;
        }
        return slides.get(slides.size() - 1);
    }

    public Window() {

        startSlide = -1;

    }

    public Slide getExpiringSlide() {
        if (slides.size() < Constants.W / Constants.slide) {
            return null;
        } else {
            return slides.get(getNewestSlide().id - Constants.W / Constants.slide + 1);
        }
    }

    public Slide getExpiredSlide() {

        if (slides.size() <= Constants.W / Constants.slide) {
            return null;
        } else {
            return slides.get(getNewestSlide().id - Constants.W / Constants.slide);
        }
    }

    public void addNewSlide(Slide s) {
        Slide newestSlide = getNewestSlide();
        if (newestSlide != null) {
            s.id = newestSlide.id + 1;
        } else {
            s.id = 0;
        }
        s.window = this;
        slides.add(s);
        if (slides.size() >= Constants.W / Constants.slide) {
            startSlide++;
        }
    }

}

class Slide {

    public List<MESIObject> points = new ArrayList<>();
    public int id;

    public Window window;
    public HashSet<MESIObject> triggered = new HashSet<>();
    public MESIMTreeClass mtree = new MESIMTreeClass();

    public Slide(ArrayList<Data> data, int currentTime) {

        for (Data d : data) {

            MESIObject d2 = new MESIObject(d, currentTime);
            points.add(d2);
            long startTime = Utils.getCPUTime();
            mtree.add(d2);
            MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime;
        }
        System.out.println("");

    }

    public Slide(ArrayList<Data> data, int currentTime, int type) {
        if (type == 1) {
            for (Data d : data) {

                MESIMCODOBject d2 = new MESIMCODOBject(d, currentTime);
                points.add(d2);
               // mtree.add(d2);
            }

        }
    }
    
    
    public HashSet<MESIMCODOBject> findNeighbors2(Data d, int k){
        
        
        
        HashSet<MESIMCODOBject> result = new HashSet<>();

        if (mtree != null) {
            MTreeClass.Query query = mtree.getNearest(d, Constants.R, k);
            for (MTreeClass.ResultItem ri : query) {
                result.add((MESIMCODOBject)ri.data);
            }

        }
        
        if(result.size() >= k) return result; 
        
        //scan points in clusters
        
        
        for(MESIObject _p: this.points){
            MESIMCODOBject p = (MESIMCODOBject)_p;
            if(p.isInCluster == true){
                //check distance to center
                double distance_to_center = mtree.getDistanceFunction().calculate(d,p.cluster_center);
                if(distance_to_center <= 3*Constants.R/2){
                    double distance = mtree.getDistanceFunction().calculate(d,p);
                    if(distance <= Constants.R)
                        result.add(p);
                }
            }
        }
        return result;
    }

    public ArrayList<Data> findNeighbors(Data d, int k) {
        
        
        ArrayList<Data> result = new ArrayList<>();
        long startTime = Utils.getCPUTime();

        if (mtree != null) {
            MTreeClass.Query query = mtree.getNearest(d, Constants.R, k);
            MesureMemoryThread.timeForQuerying += Utils.getCPUTime() - startTime;
            for (MTreeClass.ResultItem ri : query) {
                result.add(ri.data);
            }

        }
        
        
        return result;
    }

    public void updateSkippedSlide() {
        triggered.stream().forEach((MESIObject p) -> {
            for (int i = window.startSlide; i < id; i++) {
                p.skippedSlide.add(window.slides.get(i));
            }
        });
    }

    public void updateTriggeredList(MESIObject p) {
//        if (!this.triggered.contains(p)) {
        this.triggered.add(p);
//        }
    }

}

class MESIMTreeClass extends MTree<Data> {

    private static final PromotionFunction<Data> nonRandomPromotion = new PromotionFunction<Data>() {
        @Override
        public Pair<Data> process(Set<Data> dataSet, DistanceFunction<? super Data> distanceFunction) {
            return Utils.minMax(dataSet);
        }
    };

    MESIMTreeClass() {
        super(25, DistanceFunctions.EUCLIDEAN, new ComposedSplitFunction<Data>(nonRandomPromotion,
                new PartitionFunctions.BalancedPartition<Data>()));
    }

    public void add(MESIObject data) {
        super.add(data);
        _check();
    }

    public boolean remove(MESIObject data) {
        boolean result = super.remove(data);
        _check();
        return result;
    }

    DistanceFunction<? super Data> getDistanceFunction() {
        return distanceFunction;
    }
};

class MESIObject extends Data {

    public boolean isOutlier;
    public boolean isSafe;
    public int lastLEAPSlide = 0;

    HashMap<Slide, Integer> preEvidence;
    public int numSucEvidence;
    public int numPreEvidence = -1;

    public ArrayList<Slide> skippedSlide = new ArrayList<>();

    public MESIObject(Data d, int currentTime) {
        super();
        this.arrivalTime = d.arrivalTime;
        this.values = d.values;

        numPreEvidence = -1;
    }

    public int getCurrentSlideIndex() {
        return (int) Math.floor((arrivalTime - 1) / Constants.slide);
    }

    public Window getSkippedPoints(Window window, Slide trigger) {
        Window result = new Window();
        result.slides = window.slides;
        // int currentSlideIndex = (int) Math.floor((arrivalTime - 1) / Constants.slide);
        // // if (currentSlideIndex >= Constants.W / Constants.slide) {
        // // if (lastLEAPSlide == 0) lastLEAPSlide = currentSlideIndex;
        // // } else lastLEAPSlide = Constants.W / Constants.slide - 1;
        result.startSlide = lastLEAPSlide + 1;
        // this.lastLEAPSlide = result.getNewestSlide().id;
        return result;
    }

    public void expireEvidence(Slide s, Window window) {
//        this.preEvidence.remove(s);
        if (preEvidence.get(s) != null) {
            this.numPreEvidence -= preEvidence.get(s);
            if (this.numPreEvidence < 0) {
                this.numPreEvidence = 0;
            }
        } else {
        }
        if (!this.isMESIAquired()) {
            this.isOutlier = true;
            this.isSafe = false;
            for (int i = s.id + 1; i < this.getCurrentSlideIndex(); i++) {
                window.slides.get(i).triggered.add(this);
            }
        }

    }

    public boolean isNeighborhood(MESIObject p) {
        // // if (test.contains(arrivalTime + "_" + p.arrivalTime)) {
        // // System.out.println("Chet!");
        // Runtime.getRuntime().exit(1);
        // }
        // test.add(arrivalTime + "_" + p.arrivalTime);

        MTreeClass mtree = new MTreeClass();
        return mtree.getDistanceFunction().calculate(this, p) <= Constants.R;
    }
    /*
     public ArrayList<MESIObject> getSuccPoint(Window window) {
     ArrayList<MESIObject> result = new ArrayList<>();
     int currentSlideIndex = (int) Math.floor((arrivalTime - 1) / Constants.slide);
     Slide currentSlide = window.slides.get(currentSlideIndex);
     currentSlide.points.stream().filter((o) -> (o.arrivalTime != this.arrivalTime && o.getCurrentSlideIndex() >= window.startSlide)).forEach((o) -> {
     result
     .add(o);
     });
     for (int i = currentSlideIndex + 1; i <= window.getNewestSlide().id; i++) {
     if (i >= window.startSlide) {
     result.addAll(window.slides.get(i).points);
     }
     }
     return result;
     }
     */

    public void updateSuccEvidence() {
        numSucEvidence++;
    }

    public void updatePrecEvidence(Slide s) {
        if (this.preEvidence.get(s) == null) {
            this.preEvidence.put(s, 1);
            numPreEvidence++;
        } else {
            this.preEvidence.put(s, this.preEvidence.get(s) + 1);
            numPreEvidence++;
        }
    }

    public boolean isMESIAquired() {

        if (numPreEvidence == -1) {
            int numPreceding = 0;

            numPreceding = preEvidence.keySet().stream().filter((s) -> (s.id >= MESI.window.startSlide)).map((s) -> preEvidence.get(s)).reduce(numPreceding, Integer::sum);

            numPreEvidence = numPreceding;
        }

        return numSucEvidence + numPreEvidence >= Constants.k;

    }

    List<Slide> getPrecSlides(Window window) {
        int currentSlideIndex = getCurrentSlideIndex();

        if (currentSlideIndex > window.startSlide) {
            return window.slides.subList(window.startSlide, currentSlideIndex);
        } else {
            return null;
        }
    }

}
