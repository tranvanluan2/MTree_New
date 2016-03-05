/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import be.tarsos.lsh.Index;
import be.tarsos.lsh.LSH;
import be.tarsos.lsh.Vector;
import be.tarsos.lsh.families.EuclidianHashFamily;
import be.tarsos.lsh.families.HashFamily;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import mtree.tests.Data;
import mtree.tests.MTTest;
import mtree.utils.Constants;

/**
 *
 * @author Luan
 */
public class MESIWithHash {

    public static LSH lsh;
    // store list id in increasing time arrival order
    public static ArrayList<Vector> dataList = new ArrayList<>();
    HashFamily family;

    public static HashMap<Integer, HashSet<Vector>> triggerList = new HashMap<>();

    public MESIWithHash() {
        if ((int) (1 * Constants.R) == 0) {
            this.family = new EuclidianHashFamily(4, Constants.dimensions);
        } else {
            this.family = new EuclidianHashFamily((int) (10*Constants.R), Constants.dimensions);
        }

    }

    public HashSet<Vector> detectOutlier(ArrayList<Data> data, int currentTime, int W, int slide) {

        HashSet<Vector> result = new HashSet<>();
        ArrayList<Vector> datas = new ArrayList<>();
        for (Data d : data) {
            datas.add(new Vector(d));
        }

        if (lsh == null) {
            lsh = new LSH(datas, family);
            lsh.index = new Index(family, 5, 10);

        }

        process_expired_object(currentTime);
        process_trigger_list(currentTime,result);
        dataList.addAll(datas);

        //index incoming data
        for (Vector d : datas) {
            lsh.index.index(d);
        }

        for (Vector d : datas) {
            CountNeighbors(d, result);
            
        }

        return result;
    }

    public void CountNeighbors(Vector d,HashSet<Vector> result  ){
         List<Vector> r = lsh.query(d, Constants.k);

            if (r.size() < Constants.k) {
                result.add(d);
            }
            //add to triggereList
            for (Vector v : r) {
                int slideIndex = (int) Math.floor((v.arrivalTime - 1) / Constants.slide);
                if (triggerList.get(slideIndex) == null) {
                    triggerList.put(slideIndex, new HashSet<>());
                }
                triggerList.get(slideIndex).add(v);
            }
    }
    private void process_expired_object(int currentTime) {
        int index = -1;
        for (int i = 0; i < dataList.size(); i++) {
            Vector v = dataList.get(i);
            if (v.arrivalTime + Constants.W <= currentTime) {
                if (i > index) {
                    index = i;
                }
                lsh.remove(v);
            } else {
                break;
            }
        }
        for (int i = index; i >= 0; i--) {
            dataList.remove(i);
        }

    }

    private void process_trigger_list(int currentTime, HashSet<Vector> result) {
        
        int maxExpiredIndex = (int) Math.floor((currentTime - Constants.W - 1) / Constants.slide);
        //get expired slide
        HashSet<Vector> triggered = new HashSet<>();
        for(Integer slideIndex:triggerList.keySet()){
            if(slideIndex <= maxExpiredIndex){
                HashSet<Vector> triggeredVectors = triggerList.get(slideIndex);
                for(Vector v: triggeredVectors)
                {
                    if(v.arrivalTime >= currentTime - Constants.W){
                        triggered.add(v);
                    }
                }
            }
        }
        
        for(Vector v: triggered){
            CountNeighbors(v, result);
        }
    }
}
