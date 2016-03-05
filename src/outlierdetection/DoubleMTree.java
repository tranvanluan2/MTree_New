/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import static java.lang.System.exit;
import java.util.ArrayList;
import java.util.Random;
import java.util.function.Consumer;
import mtree.tests.Data;
import static mtree.tests.MTTest.currentTime;
import static mtree.tests.MTTest.readArguments;
import mtree.tests.Stream;
import mtree.utils.Constants;
import mtree.utils.Utils;

/**
 *
 * @author Luan
 */
public class DoubleMTree {

    public static MTreeClass big_mtree = new MTreeClass();
    public static MTreeClass small_mtree = new MTreeClass();

    public static int numRealDistanceCall = 0;

    public static MTreeClass mtree = new MTreeClass();
    public static NewMTreeClass new_mtree = new NewMTreeClass();

    public static UpperBoundMTreeClass upperbound_mtree = new UpperBoundMTreeClass();
    public static double max_delta;
    public static int numCalculationInCompare = 0;

    public static int distanceCallInQuery = 0;
    public static double timeForRemovePoints = 0;
    public static double timeForQueryingUpperMTree = 0;
    public static double timeForQueryinglowerMtree = 0;
    public static double timeForComputingRealDistance =0;

    public DoubleMTree() {
    }

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, IOException {
        int dimensions = 0;
        readArguments(args);
        Stream s = Stream.getInstance("");

        //
        ///
        ArrayList<Data> incomingData;
        if (currentTime != 0) {
            incomingData = s.getIncomingData(currentTime, Constants.slide, Constants.dataFile, Constants.matrixType);
            currentTime = currentTime + Constants.slide;
        } else {
            incomingData = s.getIncomingData(currentTime, Constants.W, Constants.dataFile, Constants.matrixType);
            currentTime = currentTime + Constants.W;
        }
        
        //test compare distance lowerbound
        compareLowerAndMSL(incomingData.toArray(new Data[0]), 25);
        exit(0);
        //

        
        //test insert normal mtree
        long start = Utils.getCPUTime(); // requires java 1.5
        incomingData.stream().forEach((d) -> {
            mtree.add(d);

           //
            //      new_mtree.add(d);
        });
        System.out.println("#number of real distance call, insert= " + numRealDistanceCall);
        System.out.println("indexing time in normal mtree = " + (Utils.getCPUTime() - start) * 1.0 / 1000000000);

//        saveNearestDistanceToFile(incomingData);
//        
//        exit(0);
//        
        //find neighbors
        start = Utils.getCPUTime();
        findNeighborForAllData(incomingData);
        System.out.println("time for finding neighbors = " + (Utils.getCPUTime() - start) * 1.0 / 1000000000);
        System.out.println("#number of real distance call, insert+query= " + numRealDistanceCall);
        System.out.println("# real distance call in promotion = " + numCalculationInCompare);
        System.out.println("# real distance call in query = " + distanceCallInQuery);
        //insert into big and small mtree
        start = Utils.getCPUTime(); // requires java 1.5

        int count = 0;
        ArrayList<LowerData> lower_point_list = new ArrayList<>();
        for (int i =0; i < incomingData.size(); i++) {
            Data d = incomingData.get(i);
            count++;
            LowerData sd = new LowerData(d, 2);
            lower_point_list.add(sd);
            if (sd.values[1] / Math.sqrt(sd.backUpData.values.length) > max_delta) {
                max_delta = sd.values[1] / Math.sqrt(sd.backUpData.values.length);
            }
            //small_mtree.add(sd);
            upperbound_mtree.add(sd);
        }
        
        
//        ArrayList<TransformedData> transformed_list = new ArrayList<>();
//        for(int i = 0; i < incomingData.size(); i++){
//            Data d = incomingData.get(i);
//            TransformedData td = new TransformedData(d, 1);
//            transformed_list.add(td);
//            small_mtree.add(td);
//        }
        ArrayList<LowerData2> transformed_list = new ArrayList<>();
        for(int i = 0; i < incomingData.size(); i++){
            Data d = incomingData.get(i);
            LowerData2 td = new LowerData2(incomingData.toArray(new Data[0]),d, 1);
            transformed_list.add(td);
            small_mtree.add(td);
        }

        System.out.println("indexing time for two mtrees: " + (Utils.getCPUTime() - start) * 1.0 / 1000000000);
        //find all neighbor
        start = Utils.getCPUTime();

        findNeighborForAllData3(lower_point_list, transformed_list);
        System.out.println("time for finding neighbors using two mtree= " + (Utils.getCPUTime() - start) * 1.0 / 1000000000);
        System.out.println("max delta *2 * sqrt(N) /R " + (2 * Math.sqrt(incomingData.get(0).values.length) * max_delta) + "/" + Constants.R);
        System.out.println("Time for removing: " + timeForRemovePoints * 1.0 / 1000000000);
        System.out.println("Time for querying upper MTree = " + timeForQueryingUpperMTree * 1.0 / 1000000000);
        System.out.println("Time for queryung lower Mtree = "+ timeForQueryinglowerMtree* 1.0 / 1000000000);
        System.out.println("Time for computing real distance = "+ timeForComputingRealDistance* 1.0 / 1000000000);
    }

    
    public static void compareLowerAndMSL(Data[] dataList, int k){
        //compute error using LowerData
        double error1 = 0;
        LowerData2[] lowerdatas = new LowerData2[dataList.length];
        for(int i =0; i < dataList.length; i++){
            LowerData2 d2 = new LowerData2(dataList,dataList[i], k);
            lowerdatas[i] = d2;
        }
        for(int i = 0; i < lowerdatas.length; i++){
            for(int j = 0; j < lowerdatas.length; j++){
                double d1 = mtree.getDistanceFunction().calculate(lowerdatas[i], lowerdatas[j]);
                double d2 = mtree.getDistanceFunction().calculate(dataList[i], dataList[j]);
                error1 += Math.abs(d1 - d2);
            }
        }
        
        double error2 = 0;
        
        double[] means = new double[dataList.length];
        double[] stds = new double[dataList.length];
        for(int i = 0; i < dataList.length; i++){
            Data d = dataList[i];
            means[i] = 0;
            for(int j = 0; j < d.dimensions(); j++){
                means[i] += d.values[j];
            }
            means[i] = means[i]/d.dimensions();
            
            stds[i] = 0;
            for(int j = 0; j < d.dimensions(); j++){
                stds[i] += (d.values[j] - means[i])*(d.values[j] - means[i]);
                
            }
            stds[i] = Math.sqrt(stds[i]/d.dimensions());
        }
        for(int i = 0; i < dataList.length; i++){
            for(int j = 0; j < dataList.length; j++){
                double realDistance = mtree.getDistanceFunction().calculate(dataList[i], dataList[j]);
                double lowerDistance = computeMSLDistance(dataList[i], 
                        dataList[j], means[i], means[j], stds[i], stds[j], k);
                error2+= Math.abs(realDistance-lowerDistance);
            }
        }
        
        
        System.out.println("Error1 = " + error1);
        System.out.println("Error2 = " + error2);
        
        
        
        
        
    }
    public static double computeMSLDistance(Data d1, Data d2, double mean1, double mean2, 
            double std1, double std2, int k){
        
        double distance  = 0;
        
        int n = d1.dimensions();
        
        distance += n*((mean1-mean2)*(mean1-mean2) + (std1-std2)*(std1-std2));
        for(int i = 0; i < k; i++){
            distance += Math.pow((d2.values[i]-mean2)/Math.pow(std2,0.5)*Math.sqrt(std1)
                    -(d1.values[i]-mean1)/Math.pow(std1,0.5)*Math.sqrt(std2),2);
        }
        distance = Math.sqrt(distance);
        
        return distance;
    }
    
    public static ArrayList<Data> findNeighborForAllData(ArrayList<Data> incomingData) {

        int numOutlier = 0;
        numOutlier = incomingData.stream().map((d) -> {
            ArrayList<Data> results = new ArrayList<>();
            MTreeClass.Query query = mtree.getNearest(d, Constants.R, Constants.k);
            for (MTreeClass.ResultItem ri : query) {
                results.add(ri.data);
            }
            return results;
        }).filter((results) -> (results.size() < Constants.k)).map((_item) -> 1).reduce(numOutlier, Integer::sum);

        System.out.println("Number of outlier using normal tree = " + numOutlier);
        
       // System.out.println("Writing the distance to file...");
        
       
        
        return null;
    }
    
    public static void saveNearestDistanceToFile(ArrayList<Data> incomingData) throws FileNotFoundException, UnsupportedEncodingException{
        double[][] distances = new double[incomingData.size()][incomingData.get(0).values.length];
        int count = 0;
        for(Data d: incomingData){
//            MTreeClass.Query query = mtree.getNearestByLimit(d, Constants.k);
            MTreeClass.Query query = mtree.getNearest(d);
            Data nearest = null;
            for (MTreeClass.ResultItem ri : query) {
                nearest = ri.data;
            }
            double[] distance = new double[d.values.length];
            for(int  i = 0; i < d.values.length; i++){
                distance[i] = Math.abs(d.values[i] - nearest.values[i]);
            }
            distances[count] = distance;
            
            count++;
            
        }
        
        PrintWriter writer = new PrintWriter("distance_100dims.txt", "UTF-8");
        writer.write(distances.length+" "+distances[0].length+"\n");
        for(int i = 0; i < distances.length; i++){
            for(int j = 0 ; j < distances[i].length; j++){
                writer.write(String.valueOf(distances[i][j]));
                writer.write(" ");
                
            }
            writer.write("\n");
        }
        writer.close();
        
    }
     public static ArrayList<Data> findNeighborForAllData3(ArrayList<LowerData> upperPoints,
            ArrayList<LowerData2> lowerPoints) {
        int numOutlier = 0;
        for (int i = 0; i < upperPoints.size(); i++) {
            LowerData d = upperPoints.get(i);
          //  LowerData sOb = new LowerData(d,108);
            //query upper bound
            double start2 = Utils.getCPUTime();
            MTreeClass.Query query = upperbound_mtree.
                    getNearestByRange(d, Constants.R);
            ArrayList<Data> results = new ArrayList<>();
            for (MTreeClass.ResultItem ri : query) {
                results.add(((LowerData) ri.data).backUpData);
            }

            timeForQueryingUpperMTree += Utils.getCPUTime() - start2;
            if (results.size() < Constants.k) {

                start2 = Utils.getCPUTime();
               
                MTreeClass.Query query2 = small_mtree.getNearestByRange(lowerPoints.get(i), Constants.R);
                ArrayList<Data> results2 = new ArrayList<>();
                for (MTreeClass.ResultItem ri : query2) {
                    results2.add(((LowerData2) ri.data).backUpData);

                }
                
                timeForQueryinglowerMtree += Utils.getCPUTime() - start2;
                if (results2.size() < Constants.k) {
                    numOutlier++;
                }
                if (results2.size() >= Constants.k) {

                    ArrayList<Data> results3 = new ArrayList<>();

                    start2 = Utils.getCPUTime();
                    for (int k = results2.size() - 1; k >= 0; k--) {
                        if (results.contains(results2.get(k))) {
                            results2.remove(k);
                        }
                    }
                    timeForRemovePoints += Utils.getCPUTime() - start2;
                  //  System.out.println("Prune: "+(size-results2.size()));
                    // System.out.println("#results in lower mtree = "+ results2.size());
                    start2 = Utils.getCPUTime();
                    for (Data d2 : results2) {
                        if (mtree.getDistanceFunction().calculate(d2, d.backUpData) <= Constants.R) {
                            results3.add(d2);
                            if (results3.size() + results.size() >= Constants.k) {
                                break;
                            }
                        }
                    }
                    timeForComputingRealDistance += Utils.getCPUTime() - start2;
                    // if(!checkedPoints.contains(d2.arrivalTime))
                    if (results3.size() + results.size() < Constants.k) {
                        numOutlier++;
                    }
                }
            }

        }

        System.out.println("Number of outlier using 2 M-Trees = " + numOutlier);

        return null;
    }

    
    
    

    public static ArrayList<Data> findNeighborForAllData2(ArrayList<LowerData> upperPoints,
            ArrayList<TransformedData> lowerPoints) {
        int numOutlier = 0;
        for (int i = 0; i < upperPoints.size(); i++) {
            LowerData d = upperPoints.get(i);
          //  LowerData sOb = new LowerData(d,108);
            //query upper bound
            double start2 = Utils.getCPUTime();
            MTreeClass.Query query = upperbound_mtree.
                    getNearestByRange(d, Constants.R);
            ArrayList<Data> results = new ArrayList<>();
            for (MTreeClass.ResultItem ri : query) {
                results.add(((LowerData) ri.data).backUpData);
            }

            timeForQueryingUpperMTree += Utils.getCPUTime() - start2;
            if (results.size() < Constants.k) {

                start2 = Utils.getCPUTime();
               
                MTreeClass.Query query2 = small_mtree.getNearestByRange(lowerPoints.get(i), Constants.R*TransformedData.operatorNorm);
                ArrayList<Data> results2 = new ArrayList<>();
                for (MTreeClass.ResultItem ri : query2) {
                    results2.add(((TransformedData) ri.data).backUpData);

                }
                
                timeForQueryinglowerMtree += Utils.getCPUTime() - start2;
                if (results2.size() < Constants.k) {
                    numOutlier++;
                }
                if (results2.size() >= Constants.k) {

                    ArrayList<Data> results3 = new ArrayList<>();

                    start2 = Utils.getCPUTime();
                    for (int k = results2.size() - 1; k >= 0; k--) {
                        if (results.contains(results2.get(k))) {
                            results2.remove(k);
                        }
                    }
                    timeForRemovePoints += Utils.getCPUTime() - start2;
                  //  System.out.println("Prune: "+(size-results2.size()));
                    // System.out.println("#results in lower mtree = "+ results2.size());
                    start2 = Utils.getCPUTime();
                    for (Data d2 : results2) {
                        if (mtree.getDistanceFunction().calculate(d2, d.backUpData) <= Constants.R) {
                            results3.add(d2);
                            if (results3.size() + results.size() >= Constants.k) {
                                break;
                            }
                        }
                    }
                    timeForComputingRealDistance += Utils.getCPUTime() - start2;
                    // if(!checkedPoints.contains(d2.arrivalTime))
                    if (results3.size() + results.size() < Constants.k) {
                        numOutlier++;
                    }
                }
            }

        }

        System.out.println("Number of outlier using 2 M-Trees = " + numOutlier);

        return null;
    }

}

class TransformedData extends Data{
    public Data backUpData;
    public int dimensions;
    public static double[] transformMatrix;
    public static double operatorNorm;
    public TransformedData(Data d, int dim) throws FileNotFoundException, IOException{
        super(d.values);
        this.values = new double[dim];
        dimensions = dim;
        // read matrix from file 
        BufferedReader bfr = new BufferedReader(new FileReader("E:\\liblbfgs-1.10\\Optimazation2\\result_100dims.txt"));
        String line="";
        while((line = bfr.readLine())!=null){
            String[] a_values = line.split(" ");
            if(transformMatrix ==null){
                transformMatrix = new double[a_values.length - 1];
                for(int i =0; i < transformMatrix.length; i++){
                    transformMatrix[i] = Double.valueOf(a_values[i]);
                }
                operatorNorm = Double.valueOf(a_values[a_values.length -1]);
            }
            
        }
        
        // transform matrix is 1
        
        
        
        
        double value= 0;
        for(int i = 0 ; i < transformMatrix.length; i++)
            value += transformMatrix[i]*d.values[i];
        
        values[0] = value;
        
        backUpData = d;
    }
}

class LowerData2 extends Data{
    public static double[] stds = null;
    public int dimensions; 
    public Data backUpData;
    
    
    public LowerData2(Data[] dataList, Data d, int dim){
        if(stds == null)
            computeStds(dataList);
        backUpData = d;
        
        values = new double[dim];
        dimensions = dim;
        int t = d.dimensions()/dim;
        for(int i =0; i < dimensions; i++){
        
            //sum from i*dimension -> (i+1)* dimensions;
            //sumSquare of std 
            double std_i = 0;
            values[i]= 0;
            for(int j = i*t;  j < (i+1)*t; j++){
                values[i]+=d.values[j]*stds[j];
                
                std_i +=stds[j]*stds[j];
            }
            values[i] = values[i]/Math.sqrt(std_i);
            
            
            
        }
    }
    
    
    
    public static void computeStds(Data[] dataList){
        //compute std for each attribute
        
        stds = new double[dataList[0].values.length];
        for(int i = 0; i < stds.length; i++){
            //compute mean for attribute i
            double mean = 0;
            for(int j = 0; j < dataList.length; j++){
                mean += dataList[j].values[i];
            }
            mean = mean/dataList.length;
            
            //compute std
            double std = 0;
            for(int j = 0; j < dataList.length; j++){
                std+= (dataList[j].values[i] - mean)*(dataList[j].values[i] - mean);
            }
            stds[i] = Math.sqrt(std/dataList.length);
            
        }
    }

}

class LowerData extends Data {

    public Data backUpData;
    public int dimensions;

    public LowerData(Data d, int dim) {

        super(d.values);
        this.dimensions = dim;
        this.values = new double[dimensions];
        this.backUpData = d;
        int segmentLength = d.values.length * 2 / dimensions;
        int numSegment = dimensions / 2;
        for (int i = 1; i <= numSegment; i++) {
            values[(i - 1) * 2] = Math.sqrt(segmentLength) * mean(d.values, (i - 1) * segmentLength, segmentLength);
            values[(i - 1) * 2 + 1] = Math.sqrt(segmentLength) * deviant(d.values, (i - 1) * segmentLength, segmentLength);
        }

    }

    private double mean(double[] values, int start, int segmentLength) {
        double sum = 0;
        for (int i = start; i < start + segmentLength; i++) {
            sum += values[i];
        }
        return sum / segmentLength;
    }

    private double deviant(double[] values, int start, int segmentLength) {
        double mean = mean(values, start, segmentLength);
        double sumSquare = 0;

        for (int i = start; i < start + segmentLength; i++) {
            sumSquare += (values[i] - mean) * (values[i] - mean);
        }
        return Math.sqrt(sumSquare / segmentLength);

    }

}

class SmallDistanceObject extends Data {

    public Data backUpData;
    public double sumSquareError = 0;

    SmallDistanceObject(Data d) {
        super(d.values);
        this.backUpData = d;
        this.values = new double[2];

        this.values[0] = Math.sqrt(d.values.length) * computeMeanValue(d);
        this.values[1] = Math.sqrt(d.values.length) * computeSumSquareError(d);
    }

    public final double computeMeanValue(Data d) {
        double sum = 0;
        for (Double value : d.values) {
            sum += value;
        }
        return sum / d.values.length;
    }

    public final double computeSumSquareError(Data d) {
        double sumSquare = 0;
        double mean = computeMeanValue(d);
        for (Double value : d.values) {
            sumSquare += (mean - value) * (mean - value);
        }
        return Math.sqrt(sumSquare / d.values.length);
    }

    public Data getDataObject() {
        return backUpData;
    }
}
