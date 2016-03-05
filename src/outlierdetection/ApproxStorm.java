package outlierdetection;

import java.util.ArrayList;
import java.util.Collections;

import java.util.Random;

import mtree.tests.Data;
import mtree.utils.Constants;

public class ApproxStorm extends ExactStorm {

    public static double p = 0.01;

    public static ArrayList<DataStormObject> safeInlierList = new ArrayList<DataStormObject>();

    public ApproxStorm(double _p) {
        super();
        p = _p;

    }

    public ApproxStorm() {
        super();
    }

    @Override
    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {
        ArrayList<Data> outliers = new ArrayList<Data>();

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

            dataList.remove(i);
        }

        for (Data d : data) {

            DataStormObject ob = new DataStormObject(d);
            /**
             * do range query for ob
             */
            MTreeClass.Query query = mtree.getNearestByRange(ob, Constants.R);

            ArrayList<DataStormObject> queryResult = new ArrayList<DataStormObject>();
            for (MTreeClass.ResultItem ri : query) {
                queryResult.add((DataStormObject) ri.data);
                if (ri.distance == 0) ob.values[0] += (new Random()).nextDouble() / 1000000;
            }

            Collections.sort(queryResult, new DataStormComparator());
            int count_before = 0;
            for (int i = 0; i < queryResult.size(); i++) {

                /**
                 * update neighbor for new ob and its neighbor's
                 */
                DataStormObject dod = queryResult.get(i);
                if (dod != null) {

                    if (currentTime <= W) {
                        if (ob.nn_before.size() < Constants.k) ob.nn_before.add(dod);

                    } else {
                        count_before++;
                    }

                    dod.count_after++;
                    /**
                     * check dod is safe inliers
                     */
                    if (currentTime > W && dod.count_after >= Constants.k) {
                        // check if # of safe inliers > pW
                        
                        if (safeInlierList.size() >= (int) Constants.W * p) {
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

            if (currentTime > W) ob.frac_before = count_before * 1.0 / safeInlierList.size();
            /**
             * store object into mtree
             */
            mtree.add(ob);

            dataList.add(ob);

        }

        /**
         * Compute number of safe inliers for the first window
         */
        if (currentTime <= W) {
            for (DataStormObject d : dataList) {
                if (d.count_after >= Constants.k) {
                    safeInlierList.add(d);
                }

            }
            /**
             * contrains safeInlerList <=pW
             */

            // check if # of safe inliers > pW
            /**
            while (safeInlierList.size() >= (int) Constants.W * p) {
                // remove randomly a safe inliers
                int r_index = (new Random()).nextInt(safeInlierList.size());
                DataStormObject remove = safeInlierList.get(r_index);
                safeInlierList.remove(r_index);
                dataList.remove(remove);
                mtree.remove(remove);
            }
*/
            // update frac_before for all object in window
            for (DataStormObject d : dataList) {
                d.frac_before = d.nn_before.size() * 1.0 / safeInlierList.size();
            }
        }

        // do outlier detection
        for (DataStormObject d : dataList) {
            /**
             * Count preceeding neighbors
             */
            // System.out.println(d.values[0]);
            int pre = (int) (d.frac_before * (Constants.W - currentTime + d.arrivalTime));

            if (pre + d.count_after < Constants.k) {

                // System.out.println("Outlier: "+d.values[0]);
                outliers.add(d);
            }
        }
        // System.out.println("#outliers: "+count_outlier);

//        Utils.computeUsedMemory();
        return outliers;
    }

}
