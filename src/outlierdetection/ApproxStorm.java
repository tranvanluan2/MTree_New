package outlierdetection;

import java.util.ArrayList;
import java.util.Collections;

import java.util.Random;

import mtree.tests.Data;
import mtree.utils.Constants;

public class ApproxStorm {

    public static MTreeClass mtree = new MTreeClass();
    // store list id in increasing time arrival order
    public static ArrayList<DataStormObject> dataList = new ArrayList<>();

    public double avgCurrentNeighbor = 0;
    public static double avgAllWindowNeighbor = 0;
    public static double averageSafeInlier = 0;
    public static double numWindows = 0;

    public static double p = 0.01;

    public static ArrayList<DataStormObject> safeInlierList = new ArrayList<>();

    public ApproxStorm(double _p) {
        super();
        p = _p;

    }

    public ApproxStorm() {
        super();
    }

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {
        ArrayList<Data> outliers = new ArrayList<>();

        /**
         * remove expired data from dataList and mtree
         */
        int index = -1;
        for (int i = 0; i < dataList.size(); i++) {
            Data d = dataList.get(i);
            if (d.arrivalTime <= currentTime - W) {
                // mark here for removing data from datalist later
                index = i;
                // remove from mtree
                mtree.remove(d);
            } else {
                break;
            }
        }
        for (int i = index; i >= 0; i--) {
            safeInlierList.remove(dataList.get(i));
            dataList.remove(i);
        }

        data.stream().map((d) -> new DataStormObject(d)).map((ob) -> {
            /**
             * do range query for ob
             */
            MTreeClass.Query query = mtree.getNearestByRange(ob, Constants.R);
            ArrayList<DataStormObject> queryResult = new ArrayList<>();
            for (MTreeClass.ResultItem ri : query) {
                queryResult.add((DataStormObject) ri.data);
//                if (ri.distance == 0) {
//                    ob.values[0] += (new Random()).nextDouble() / 1000000;
//                }
            }
//            Collections.sort(queryResult, new DataStormComparator());
            ob.count_before = 0;
            for (int i = 0; i < queryResult.size(); i++) {

                /**
                 * update neighbor for new ob and its neighbor's
                 */
                DataStormObject dod = queryResult.get(i);
                if (dod != null) {

                    if (ExactStorm.isSameSlide(dod, ob)) {
                        ob.count_after++;
                    }
//                    else {
//                        ob.count_before++;
//                    }
                    else if (dod.count_after >= Constants.k) {
                        ob.count_before++;
                    }

                    dod.count_after++;
                    /**
                     * check dod is safe inliers
                     */
                    if (dod.count_after == Constants.k) {
                        // check if # of safe inliers > pW

                        while (safeInlierList.size() >= (int) Constants.W * p) {
                            // remove randomly a safe inliers
                            int r_index = (new Random()).nextInt(safeInlierList.size());
                            DataStormObject remove = safeInlierList.get(r_index);
                            safeInlierList.remove(r_index);
                            dataList.remove(remove);
                            mtree.remove(remove);
                            remove = null;
                        }

                        safeInlierList.add(dod);

                    }
                }

            }
            return ob;
        }).map((ob) -> {
            if (currentTime > W) {
                ob.frac_before = ob.count_before * 1.0 / safeInlierList.size();
            }
            return ob;
        }).map((ob) -> {
            /**
             * store object into mtree
             */
            if (ob.count_after >= Constants.k) {
                        // check if # of safe inliers > pW

                        while (safeInlierList.size() >= (int) Constants.W * p) {
                            // remove randomly a safe inliers
                            int r_index = (new Random()).nextInt(safeInlierList.size());
                            DataStormObject remove = safeInlierList.get(r_index);
                            safeInlierList.remove(r_index);
                            dataList.remove(remove);
                            mtree.remove(remove);
                            remove = null;
                        }

                        
                        safeInlierList.add(ob);

                    }
            return ob;
            
        }).forEach((ob) -> {
            
                        dataList.add(ob);
                        mtree.add(ob);
        });

        /**
         * Compute number of safe inliers for the first window
         */
        if (currentTime == W) {
//            dataList.stream().filter((d) -> (d.count_after >= Constants.k)).forEach((d) -> {
//                safeInlierList.add(d);
//            });
//           

            // update frac_before for all object in window
            dataList.stream().forEach((d) -> {
                d.frac_before = d.count_before * 1.0 / safeInlierList.size();
            });
        }

        // do outlier detection
        dataList.stream().forEach((d) -> {
            /**
             * Count preceeding neighbors
             */
            // System.out.println(d.values[0]);
            int pre = (int) (d.frac_before * (Constants.W - currentTime + (d.arrivalTime / Constants.slide)* Constants.slide ));
            if (pre + d.count_after < Constants.k) {
                // System.out.println("Outlier: "+d.values[0]);
                outliers.add(d);
            }
        }); // System.out.println("#outliers: "+count_outlier);

//        Utils.computeUsedMemory();
        return outliers;
    }

}
