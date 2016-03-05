package outlierdetection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;

import mtree.tests.Data;
import mtree.tests.MesureMemoryThread;
import mtree.utils.Constants;
import mtree.utils.Utils;

public class AbstractC {

    public static MTreeClass mtree = new MTreeClass();

    // store list id in increasing time arrival order
    public static ArrayList<DataAbtractCObject> dataList = new ArrayList<>();

    public AbstractC() {

    }

    public ArrayList<Data> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {
        ArrayList<Data> outliers = new ArrayList<>();

        long startTime = Utils.getCPUTime();
        /**
         * remove expired data from dataList and mtree
         */
        if (slide != W) {
            int index = -1;
            for (int i = 0; i < dataList.size(); i++) {
                DataAbtractCObject d = dataList.get(i);
                if (d.arrivalTime <= currentTime - W) {
                    // mark here for removing data from datalist later
                    index = i;
                    // remove from mtree
                    long start4 = Utils.getCPUTime();
                    mtree.remove(d);
                    MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - start4;
                    // System.out.println(t);

                } else {
                    break;
                }
            }
            for (int i = index; i >= 0; i--) {

                dataList.remove(i);
            }
        }
        else{
            dataList.clear();
            mtree = null;
            mtree = new MTreeClass();
        }

        MesureMemoryThread.timeForExpireSlide += Utils.getCPUTime() - startTime;

        startTime = Utils.getCPUTime();

        data.stream().map((d) -> new DataAbtractCObject(d, currentTime)).map((DataAbtractCObject dac) -> {
            /**
             * do range query for ob
             */
            long startTime2 = Utils.getCPUTime();
            MTreeClass.Query query = mtree.getNearestByRange(dac, Constants.R);
            MesureMemoryThread.timeForQuerying += Utils.getCPUTime() - startTime2;
            for (MTreeClass.ResultItem ri : query) {
                if (ri.distance == 0) {
                    dac.values[0] += (new Random()).nextDouble() / 1000000;
                }
                /**
                 * update neighbor for new ob and its neighbor's
                 */
                DataAbtractCObject object = (DataAbtractCObject) ri.data;
                if (object.arrivalTime > currentTime - Constants.W) {

                    /**
                     * update lnt_count
                     */
                    for (int n = 0; object.lt_cnt.size() > n; n++) {
                        object.lt_cnt.set(n, object.lt_cnt.get(n) + 1);
                        dac.lt_cnt.set(n, dac.lt_cnt.get(n) + 1);
                    }

                }
            }

            return dac;
        }).map((dac) -> {
            /**
             * store object into mtree
             */
            long startTime3 = Utils.getCPUTime();
            mtree.add(dac);
            MesureMemoryThread.timeForIndexing += Utils.getCPUTime() - startTime3;
            return dac;
        }).forEach((dac) -> {
            dataList.add(dac);

//            Utils.computeUsedMemory();
        });

        // do outlier detection
        dataList.stream().map((d) -> {
            if (d.lt_cnt.get(0) < Constants.k) {

                outliers.add(d);

            }
            return d;
        }).forEach((d) -> {
//            Integer[] temp = new Integer[d.lt_cnt.length - 1];
//            System.arraycopy(d.lt_cnt, 1, temp, 0, d.lt_cnt.length - 1);
//            d.lt_cnt = temp;
            if (!d.lt_cnt.isEmpty()) {
                d.lt_cnt.remove(0);
            }
        });

        MesureMemoryThread.timeForNewSlide += Utils.getCPUTime() - startTime;
        return outliers;
    }

};

class DataAbtractCObject extends Data {

    public ArrayList<Integer> lt_cnt;

    public DataAbtractCObject(Data d, int currentTime) {
        super();
        this.arrivalTime = d.arrivalTime;
        this.values = d.values;
        int lifespan = Constants.W / Constants.slide;
        lt_cnt = new ArrayList<>(Collections.nCopies(lifespan, 0));
    }

}
