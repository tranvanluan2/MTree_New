package outlierdetection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.Set;

import mtree.ComposedSplitFunction;
import mtree.DistanceFunction;
import mtree.DistanceFunctions;
import mtree.MTree;
import mtree.PartitionFunctions;
import mtree.PromotionFunction;
import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Pair;
import mtree.utils.Utils;

public class ExactStorm {

    public static MTreeClass mtree = new MTreeClass();
    // store list id in increasing time arrival order
    public static ArrayList<DataStormObject> dataList = new ArrayList<>();

    public double avgCurrentNeighbor = 0;
    public static double avgAllWindowNeighbor = 0;
    public static double averageSafeInlier = 0;
    public static double numWindows = 0;

    public ExactStorm() {

    }
    
    public static double computeSafeInliers(){
        int count = 0;
        for(int i = 0; i < dataList.size(); i++){
            if(dataList.get(i).count_after >= Constants.k){
                count++;
            }
        }
        return count*1.0/dataList.size();
    }

    public static boolean isSameSlide(DataStormObject d1, DataStormObject d2) {
        return (d1.arrivalTime - 1) / Constants.slide == (d2.arrivalTime - 1) / Constants.slide;
    }

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {

        numWindows ++;
        ArrayList<Data> outliers = new ArrayList<>();

        long startTime = Utils.getCPUTime();
        /**
         * remove expired data from dataList and mtree
         */
        if (slide != W) {
            int index = -1;
            for (int i = 0; i < dataList.size(); i++) {
                DataStormObject d = dataList.get(i);
                if (d.arrivalTime <= currentTime - W) {
                    // mark here for removing data from datalist later
                    index = i;
                    // remove from mtree
//                    dataList.stream().filter((d2) -> (d2.arrivalTime > d.arrivalTime)).filter((d2) -> (d2.nn_before.contains(d))).forEach((d2) -> {
//                        d2.nn_before.remove(d);
//                    });
                    long startTime3 = Utils.getCPUTime();
                    mtree.remove(d);
                    d.nn_before.clear();
                //    d=null;
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime3;
                } else {
                    break;
                }
            }
            for (int i = index; i >= 0; i--) {

                dataList.remove(i);
            }
        } else {
            dataList.clear();
            mtree = null;
            mtree = new MTreeClass();

        }
        MesureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;

        startTime = Utils.getCPUTime();

        for (Data d : data) {

            DataStormObject ob = new DataStormObject(d);
            /**
             * do range query for ob
             */
            long startTime4 = Utils.getCPUTime();
            MTreeClass.Query query = mtree.getNearestByRange(ob, Constants.R);
            MesureMemoryThread.timeForQuerying += Utils.getCPUTime() - startTime4;

            ArrayList<DataStormObject> queryResult = new ArrayList<>();
            for (MTreeClass.ResultItem ri : query) {
                queryResult.add((DataStormObject) ri.data);
                if (ri.distance == 0) {
                    ob.values[0] += (new Random()).nextDouble() / 1000000;
                }
            }

            Collections.sort(queryResult, new DataStormComparator());

            queryResult.stream().filter((dod) -> (dod.arrivalTime > currentTime - Constants.W)).filter((dod) -> (dod != null)).map((dod) -> {
                if (isSameSlide(dod, ob)) {
                    ob.count_after++;
                } else {

                    if (ob.nn_before.size() < Constants.k) {
                        ob.nn_before.add(0, dod);
                    }
                }
                return dod;
            }).forEach((dod) -> {
                dod.count_after++;
            });//                Utils.computeUsedMemory();

            /**
             * store object into mtree
             */
//            Utils.computeUsedMemory();
            long startTime2 = Utils.getCPUTime();
            mtree.add(ob);
            MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime2;

            dataList.add(ob);

        }

        // do outlier detection
        dataList.stream().forEach((d) -> {
            /**
             * Count preceeding neighbors
             */
            // System.out.println(d.values[0]);
     
            int pre = 0;
            pre = d.nn_before.stream().filter((nn_before) -> (nn_before.arrivalTime > currentTime - W)).map((_item) -> 1).reduce(pre, Integer::sum);
            if (pre + d.count_after < Constants.k) {
                outliers.add(d);
            }
        }); // System.out.println("#outliers: "+count_outlier);

        //count the avg length of neighbors list
        for (DataStormObject d : dataList) {
            avgCurrentNeighbor += d.nn_before.size();
        }
        avgCurrentNeighbor = avgCurrentNeighbor / dataList.size();
        avgAllWindowNeighbor += avgCurrentNeighbor;

        MesureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;
        
        averageSafeInlier = (averageSafeInlier * (numWindows-1)+computeSafeInliers())/numWindows;
        System.out.println(" Number of safe inliers = "+ averageSafeInlier*100+"%");
        
        return outliers;

    }

    public void processFirstWindow(ArrayList<Data> data, int currentTime, int W, int slide) {
        for (Data d : data) {
            DataStormObject ob = new DataStormObject(d);
            mtree.add(ob);
            dataList.add(ob);
        }
        for (DataStormObject d : dataList) {
            MTreeClass.Query query = mtree.getNearestByRange(d, Constants.R);

            ArrayList<DataStormObject> queryResult = new ArrayList<>();
            for (MTreeClass.ResultItem ri : query) {
                queryResult.add((DataStormObject) ri.data);
                if (ri.distance == 0) {
                    d.values[0] += (new Random()).nextDouble() / 1000000;
                }
            }

            Collections.sort(queryResult, new DataStormComparator());
            for (DataStormObject dob : queryResult) {

                if (dob.arrivalTime >= d.arrivalTime) {
                    d.count_after++;
                } else if (dob.arrivalTime >= currentTime - Constants.W && dob.arrivalTime < d.arrivalTime) {
                    if (d.nn_before.size() < Constants.k) {
                        d.nn_before.add(dob);
                    }
                }
            }

        }
    }
}

class MTreeClass extends MTree<Data> {

    private static final PromotionFunction<Data> nonRandomPromotion = (Set<Data> dataSet, DistanceFunction<? super Data> distanceFunction1) -> Utils.minMax(dataSet);

    MTreeClass() {
        super(25, DistanceFunctions.EUCLIDEAN, new ComposedSplitFunction<>(nonRandomPromotion,
                new PartitionFunctions.BalancedPartition<>()));
    }

    @Override
    public void add(Data data) {
        super.add(data);
        _check();
    }

    @Override
    public boolean remove(Data data) {
        boolean result = super.remove(data);
        _check();
        return result;
    }

    DistanceFunction<? super Data> getDistanceFunction() {
        return distanceFunction;
    }
};

class UpperBoundMTreeClass extends MTree<Data> {

    private static final PromotionFunction<Data> nonRandomPromotion = new PromotionFunction<Data>() {
        @Override
        public Pair<Data> process(Set<Data> dataSet, DistanceFunction<? super Data> distanceFunction) {
            return Utils.minMax(dataSet);
        }
    };

    UpperBoundMTreeClass() {
        super(100, DistanceFunctions.UPPERBOUND, new ComposedSplitFunction<Data>(nonRandomPromotion,
                new PartitionFunctions.BalancedPartition<Data>()));
    }

    public void add(Data data) {
        super.add(data);
        _check();
    }

    public boolean remove(Data data) {
        boolean result = super.remove(data);
        _check();
        return result;
    }

    DistanceFunction<? super Data> getDistanceFunction() {
        return distanceFunction;
    }
};

class DataStormComparator implements Comparator<DataStormObject> {

    @Override
    public int compare(DataStormObject o1, DataStormObject o2) {
        if (o1.arrivalTime < o2.arrivalTime) {
            return 1;
        } else if (o1.arrivalTime == o2.arrivalTime) {
            return 0;
        } else {
            return -1;
        }
    }
};

class DataStormObject extends Data {

    public int count_after;
    public ArrayList<DataStormObject> nn_before;

    public double frac_before = 0;

    public DataStormObject(Data d) {
        super();
        nn_before = new ArrayList<>();
        this.arrivalTime = d.arrivalTime;
        this.values = d.values;

    }

}
