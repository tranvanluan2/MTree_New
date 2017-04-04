/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mtree.tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

import mtree.utils.Constants;
import static mtree.utils.Constants.readConfig;
import mtree.utils.Utils;
import outlierdetection.DistanceFunction;

/**
 *
 * @author Luan Tran
 */
public class CentralServer {

    public static int numWindows = 0;
    public static int numLocalNodes = 0;
    public static HashMap<String, Integer> neighborCounts = new HashMap<>();
    public static ArrayList<Data> clusters = new ArrayList<Data>();
    public static HashMap<Data, Integer> unsafeClusters = new HashMap<>();
    public static HashMap<Data, HashMap<Integer, Integer>> clusterCounts = new HashMap<>();
    public static HashMap<String, HashMap<Integer, Integer>> neigborCountsPerSlide = new HashMap<>();
    public static ArrayList<String> outlierResults = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException, IOException {
        readConfig();
        double totalTime = 0;

        for (int i = 0; i < 400; i++) {
            System.out.println("Window " + numWindows);
            long start = Utils.getCPUTime(); // requires java 1.5
            //clear outliers 
            outlierResults.clear();
            if (Constants.sendCluster) {
                waitForAllClusters();
                readAllCluster();
            }
            WaitForAllLocalOutliers();
            askNeighbors();
            aggregateResult();

            //send result back to local nodes
            if (Constants.sendBackResult) {
                sendResultBack();
            }
            sendFinishSignal();
            neigborCountsPerSlide.clear();
            neighborCounts.clear();
            unsafeClusters.clear();
            clusters.clear();
            numWindows++;
            double elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;

            totalTime += elapsedTimeInSec;
        }
        System.out.println("Average Running Time = " + totalTime / 400);
    }

    public static void WaitForAllLocalOutliers() throws InterruptedException, IOException {
        while (true) {
            int count = 0;
            for (int i = 0; i < Constants.nodeNames.length; i++) {
                if (check_file_exist(Constants.nodeNames[i] + "/localOutlier_" + numWindows + ".txt")) {
                    count++;
                }
            }
            if (count == Constants.nodeNames.length) {
                break;
            } else {
                Thread.sleep(1000);
            }
        }
    }

    public static void askNeighbors() throws MalformedURLException, SmbException, UnknownHostException, IOException {

        String[] contents = new String[Constants.nodeNames.length];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = "";
        }
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        for (int i = 0; i < Constants.nodeNames.length; i++) {
            //read local outlier 
            String nodeName = Constants.nodeNames[i];
            SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/localOutlier_" + numWindows + ".txt", auth);
            SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
            String line;
            while ((line = br.readLine()) != null) {

                String[] elements = line.split(":");
                String arrivalTime = elements[0];
                int numSuccNeighbor = Integer.valueOf(elements[1]);
                int numPrevNeighbor = Integer.valueOf(elements[2]);

                //check with cluster center 
                String[] string_values = elements[3].split(",");
                double[] values = new double[string_values.length];
                for (int j = 0; j < values.length; j++) {
                    values[j] = Double.valueOf(string_values[j]);
                }
                Data d = new Data(values);
                //find  cluster
                if (Constants.algorithm.equals("mcod_new")) {
                    int c = findCluster(d);
                    if (c <= 0) {

                        //if does not belong to any cluster
                        String content = nodeName + ":" + arrivalTime + ":" + elements[3] + "\n";
                        neighborCounts.put(nodeName + "_" + arrivalTime, numSuccNeighbor + numPrevNeighbor);
                        for (int j = 0; j < i; j++) {
                            contents[j] += content;
                        }
                        for (int j = i + 1; j < contents.length; j++) {
                            contents[j] += content;
                        }
                    }
//                    else if(c == -1){
//                        outlierResults.add(nodeName + "_" + arrivalTime);
//                    }
                } else if (Constants.algorithm.equals("mcod_safe")) {

                    int[] neighbors = countNeighborsInCluster(d, unsafeClusters);
                    if (neighbors[0] < Constants.k - numSuccNeighbor - numPrevNeighbor
                            && neighbors[1] >= Constants.k - numSuccNeighbor - numPrevNeighbor) {
                        //if (!findNeighborsInCluster(d, unsafeClusters)) {
                        //if does not belong to any cluster
                        String content = nodeName + ":" + arrivalTime + ":" + elements[3] + "\n";
                        neighborCounts.put(nodeName + "_" + arrivalTime, numSuccNeighbor + numPrevNeighbor + neighbors[0]);
                        for (int j = 0; j < i; j++) {
                            contents[j] += content;
                        }
                        for (int j = i + 1; j < contents.length; j++) {
                            contents[j] += content;
                        }
                    } else if (neighbors[0] < Constants.k - numSuccNeighbor - numPrevNeighbor
                            && neighbors[1] < Constants.k - numSuccNeighbor - numPrevNeighbor) {
                        outlierResults.add(nodeName + "_" + arrivalTime);
                    }
                } else if (Constants.algorithm.equals("mcod_mesi_safe")) {
                    HashMap<Integer, Integer> slideNeighbors = countNeighborsInCluster2(d, clusterCounts);
                    if (Constants.sendBackResult) {
                        neigborCountsPerSlide.put(d.nodeName + "_" + d.arrivalTime, slideNeighbors);
                    }

                    int numNeighbor = 0;
                    int potential = 0;
                    if (slideNeighbors.get(-1) != null) {
                        potential = slideNeighbors.get(-1);
                    }
                    for (Integer slide : slideNeighbors.keySet()) {
                        if (slide != -1) {
                            numNeighbor += slideNeighbors.get(slide);
                        }
                    }

                    if (numNeighbor < Constants.k - numSuccNeighbor - numPrevNeighbor
                            && potential >= Constants.k - numSuccNeighbor - numPrevNeighbor) {
                        //if (!findNeighborsInCluster(d, unsafeClusters)) {
                        //if does not belong to any cluster
                        String content = nodeName + ":" + arrivalTime + ":" + elements[3] + "\n";
                        neighborCounts.put(nodeName + "_" + arrivalTime, numSuccNeighbor + numPrevNeighbor + numNeighbor);

                        for (int j = 0; j < i; j++) {
                            contents[j] += content;
                        }
                        for (int j = i + 1; j < contents.length; j++) {
                            contents[j] += content;
                        }
                    } else if (numNeighbor < Constants.k - numSuccNeighbor - numPrevNeighbor
                            && potential < Constants.k - numSuccNeighbor - numPrevNeighbor) {
                        outlierResults.add(nodeName + "_" + arrivalTime);
                    }
                }
            }

        }
        //write asking neighbors to file 
        for (int i = 0; i < contents.length; i++) {
            SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeNames[i] + "/askingNeighbors_" + numWindows + ".txt", auth);
            try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
                sfos.write(contents[i].getBytes());
            }
        }
    }

    private static boolean check_file_exist(String fileName) throws MalformedURLException, IOException {
        //write to file folder 
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);

        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + fileName, auth);
        return sFile.exists();
    }

    public static void aggregateResult() throws IOException, InterruptedException {
        //check all file exist
        while (true) {
            int count = 0;
            for (int i = 0; i < Constants.nodeNames.length; i++) {
                if (check_file_exist(Constants.nodeNames[i] + "/resultNeighbors_" + numWindows + ".txt")) {
                    count++;
                }
            }
            if (count == Constants.nodeNames.length) {
                break;
            } else {
                Thread.sleep(1000);
            }
        }
        //all file exist
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);

        for (int i = 0; i < Constants.nodeNames.length; i++) {
            String nodeName = Constants.nodeNames[i];
            SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/resultNeighbors_" + numWindows + ".txt", auth);
            SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
            BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
            String line;
            while ((line = br.readLine()) != null) {
                if (!Constants.sendBackResult) {
                    String[] elements = line.split(":");
                    int newSucc = Integer.valueOf(elements[2]);
                    int newPrev = Integer.valueOf(elements[3]);
                    int currentNeighbors = neighborCounts.get(elements[0] + "_" + elements[1]);
                    neighborCounts.put(elements[0] + "_" + elements[1], currentNeighbors + newSucc + newPrev);
                } else {
                    String[] elements = line.split(":");
                    int newSucc = Integer.valueOf(elements[2]);
                    HashMap<Integer, Integer> prev = new HashMap<>();
                    if (elements.length >= 4) {
                        String newPrevs = elements[3];

                        String[] slidePrevs = newPrevs.split(";");
                        for (String slideValue : slidePrevs) {
                            String[] slide_value = slideValue.split(",");
                            prev.put(Integer.valueOf(slide_value[0]), Integer.valueOf(slide_value[1]));
                        }
                    }
                    prev.put(-1, newSucc);
                    neigborCountsPerSlide.put(elements[0] + "_" + elements[1], prev);

                }
            }
        }

        //ArrayList<String> outlierResults = new ArrayList<>();
        for (String key : neighborCounts.keySet()) {
            if (neighborCounts.get(key) < Constants.k) {
                outlierResults.add(key);
            }
        }

        //write result to file
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/Result/" + numWindows + "_outlier.txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            for (String s : outlierResults) {
                sfos.write((s + "\n").getBytes());
            }
        }

        //write finish signal
        for (int i = 0; i < Constants.nodeNames.length; i++) {
            String nodeName = Constants.nodeNames[i];
            SmbFile sFile2 = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/finish_" + numWindows + ".txt", auth);
            try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile2)) {
                sfos.write(" ".getBytes());
            }
        }

    }

    private static void waitForAllClusters() throws IOException, InterruptedException {
        while (true) {
            int count = 0;
            for (int i = 0; i < Constants.nodeNames.length; i++) {
                if (check_file_exist(Constants.nodeNames[i] + "/localCluster_" + numWindows + ".txt")) {
                    count++;
                }
            }
            if (count == Constants.nodeNames.length) {
                //read all cluster
                readAllCluster();
                break;
            } else {
                Thread.sleep(1000);
            }
        }
    }

    private static void readAllCluster() throws MalformedURLException, SmbException, UnknownHostException, IOException {
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);

        if (Constants.algorithm.equals("mcod_new")) {
            clusters.clear();

            for (String nodeName : Constants.nodeNames) {
                SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/localCluster_" + numWindows + ".txt", auth);
                SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] string_values = line.split(",");
                    double[] values = new double[string_values.length];
                    for (int j = 0; j < string_values.length; j++) {
                        values[j] = Double.valueOf(string_values[j]);
                    }
                    Data d = new Data(values);
                    clusters.add(d);

                }

            }
        } else if (Constants.algorithm.equals("mcod_safe")) {

            unsafeClusters.clear();

            for (String nodeName : Constants.nodeNames) {
                SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/localCluster_" + numWindows + ".txt", auth);
                SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] count_datavalues = line.split(":");
                    int count = Integer.valueOf(count_datavalues[0]);

                    String[] string_values = count_datavalues[1].split(",");
                    double[] values = new double[string_values.length];
                    for (int j = 0; j < string_values.length; j++) {
                        values[j] = Double.valueOf(string_values[j]);
                    }
                    Data d = new Data(values);
                    unsafeClusters.put(d, count);

                }

            }
        } else if (Constants.algorithm.equals("mcod_mesi_safe")) {
            clusterCounts.clear();
            for (String nodeName : Constants.nodeNames) {
                SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/localCluster_" + numWindows + ".txt", auth);
                SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] count_datavalues = line.split(":");

                    String[] slide_counts = count_datavalues[0].split(";");
                    HashMap<Integer, Integer> slide_count_hm = new HashMap<>();
                    for (String slide_count : slide_counts) {
                        String[] sc = slide_count.split(",");
                        slide_count_hm.put(Integer.valueOf(sc[0]), Integer.valueOf(sc[1]));

                    }
                    String[] string_values = count_datavalues[1].split(",");
                    double[] values = new double[string_values.length];
                    for (int j = 0; j < string_values.length; j++) {
                        values[j] = Double.valueOf(string_values[j]);
                    }
                    Data d = new Data(values);
                    clusterCounts.put(d, slide_count_hm);

                }

            }
        }

    }

    private static int findCluster(Data d) {
        int result = -1;
        for (Data d2 : clusters) {
            if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2) {
                return 1;
            } else if (DistanceFunction.euclideanDistance(d, d2) > Constants.R / 2
                    && DistanceFunction.euclideanDistance(d, d2) <= Constants.R * 3 / 2) {
                result = 0;
            }
        }
        return result;
    }

    private static int[] countNeighborsInCluster(Data d, HashMap<Data, Integer> clusters) {
        int count = 0;
        int potential = 0;
        for (Data d2 : clusters.keySet()) {
            if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2) {
                count += clusters.get(d2);
                potential += clusters.get(d2);
            } else if (DistanceFunction.euclideanDistance(d, d2) > Constants.R / 2
                    && DistanceFunction.euclideanDistance(d, d2) <= Constants.R * 3 / 2) {
                potential += clusters.get(d2);
            }
            if (count >= Constants.k) {
                return new int[]{count, potential};
            }
        }
        return new int[]{count, potential};
    }

    private static HashMap<Integer, Integer> countNeighborsInCluster2(Data d, HashMap<Data, HashMap<Integer, Integer>> clusters) {
        HashMap<Integer, Integer> results = new HashMap<>();

        for (Data d2 : clusters.keySet()) {
            int totalMember = 0;
            for (Integer slide : clusters.get(d2).keySet()) {
                if (slide != -1) {
                    totalMember += clusters.get(d2).get(slide);
                }
            }
            if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2) {
                HashMap<Integer, Integer> slideCounts = clusters.get(d2);
                for (Integer slide : slideCounts.keySet()) {
                    if (results.get(slide) != null) {
                        results.put(slide, results.get(slide) + slideCounts.get(slide));
                    } else {
                        results.put(slide, slideCounts.get(slide));
                    }
                }
            } else if (DistanceFunction.euclideanDistance(d, d2) > Constants.R / 2
                    && DistanceFunction.euclideanDistance(d, d2) <= Constants.R * 3 / 2) {
                if (results.get(-1) != null) {
                    results.put(-1, results.get(-1) + totalMember);
                } else {
                    results.put(-1, totalMember);
                }
            }

        }
        return results;
    }

    private static boolean findNeighborsInCluster(Data d, HashMap<Data, Integer> clusters) {
        int count = 0;
        for (Data d2 : clusters.keySet()) {
            if (DistanceFunction.euclideanDistance(d, d2) <= Constants.R / 2) {
                count += clusters.get(d2);
            }
            if (count > Constants.k) {
                return true;
            }
        }
        return false;

    }

    private static void sendFinishSignal() throws MalformedURLException, IOException {
        //all file exist
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        //write finish signal
        for (int i = 0; i < Constants.nodeNames.length; i++) {
            String nodeName = Constants.nodeNames[i];
            SmbFile sFile2 = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/finish_" + numWindows + ".txt", auth);
            try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile2)) {
                sfos.write(" ".getBytes());
            }
        }

    }

    private static void sendResultBack() throws MalformedURLException, IOException {
        HashMap<String, String> node_content = new HashMap<>();

        for (String localData : neigborCountsPerSlide.keySet()) {

            String[] nodeName_time = localData.split("_");
            String localNodeName = nodeName_time[0];
            String content = node_content.get(localNodeName);
            if (content == null) {
                content = "";
            }
            String arrivalTime = nodeName_time[1];
            HashMap<Integer, Integer> neighbormap = neigborCountsPerSlide.get(localData);
            content += arrivalTime + ":";
            for (Integer slide : neighbormap.keySet()) {
                content += slide + "," + neighbormap.get(slide) + ";";

            }
            content += "\n";

            node_content.put(localNodeName, content);
        }
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        //write finish signal
        for (int i = 0; i < Constants.nodeNames.length; i++) {
            String nodeName = Constants.nodeNames[i];
            SmbFile sFile2 = new SmbFile(Constants.sharedFolder + "/" + nodeName + "/result_For_Local_Outlier_" + numWindows + ".txt", auth);
            try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile2)) {
                if (node_content.get(nodeName) != null) {
                    sfos.write(node_content.get(nodeName).getBytes());
                   
                }
                else {
                    sfos.write("".getBytes());
                }
            }
        }

    }

}
