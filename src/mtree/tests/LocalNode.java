/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mtree.tests;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import mtree.utils.Constants;
import static mtree.utils.Constants.readConfig;
import mtree.utils.Utils;
import outlierdetection.MCOD_MESI_Safe;
import outlierdetection.MCOD_Safe_Version;
import outlierdetection.MicroCluster_NewVersion;
import outlierdetection.MicroCluster_NewVersion.MCData;

/**
 *
 * @author Luan Tran
 */
public class LocalNode {

//    public static String nodeName = "Node1";
    public static int numWindows = 0;
    public static int currentTime = 0;
    public static MicroCluster_NewVersion mcod_new = new MicroCluster_NewVersion();
    public static MCOD_Safe_Version mcod_safe = new MCOD_Safe_Version();
    public static MCOD_MESI_Safe mcod_mesi_safe = new MCOD_MESI_Safe();

    public static void main(String[] args) throws IOException, MalformedURLException, SmbException, InterruptedException {

        readConfig();
        double totalTime = 0;

        for (int i = 0; i < 400; i++) {
            System.out.println("Window " + numWindows);
            long start = Utils.getCPUTime(); // requires java 1.5
            detectOutlier();
            waitForLocalOutlierFromCentral();

            if (Constants.sendBackResult) {
                waitForResultForLocalOutlierFromCentral();
            }

            waitForFinishSignalFromCentral();
            if (numWindows == 0) {
                currentTime += Constants.W;
            } else {
                currentTime += Constants.slide;
            }
            numWindows++;
            double elapsedTimeInSec = (Utils.getCPUTime() - start) * 1.0 / 1000000000;

            totalTime += elapsedTimeInSec;

        }
        System.out.println("Average Running Time = " + totalTime / 400);
    }

    public static void detectOutlier() throws SmbException, UnknownHostException, IOException {
        Stream s = Stream.getInstance("");
        ArrayList<Data> incomingData = s.getNewData(Constants.sharedFolder + "/Data/" + Constants.nodeName + "/newData_" + numWindows + ".txt");

        switch (Constants.algorithm) {

            case "mcod_new":

                ArrayList<MCData> outliers300 = mcod_new.detectOutlier(incomingData, currentTime, Constants.W, Constants.slide);
                if (Constants.sendCluster) {
                    sendCluster(MicroCluster_NewVersion.microClusters);
                }
                sendLocalOutlier(outliers300);
                break;
            case "mcod_safe":
                ArrayList<MCOD_Safe_Version.MCData> outliers = mcod_safe.detectOutlier(incomingData, currentTime, Constants.W, Constants.slide);
                if (Constants.sendCluster) {
                    ArrayList<MCOD_Safe_Version.Cluster> clusters = new ArrayList<>();
                    clusters.addAll(MCOD_Safe_Version.safeClusters);
                    clusters.addAll(MCOD_Safe_Version.unsafeClusters);

                    sendCluster2(clusters);
                }
                sendLocalOutlier(outliers);
                break;
            case "mcod_mesi_safe":
                ArrayList<MCOD_MESI_Safe.MCData> outliers500 = mcod_mesi_safe.detectOutlier(incomingData, currentTime, Constants.W, Constants.slide);
                if (Constants.sendCluster) {
                    ArrayList<MCOD_MESI_Safe.Cluster> clusters = new ArrayList<>();
                    clusters.addAll(MCOD_MESI_Safe.safeClusters);
                    clusters.addAll(MCOD_MESI_Safe.unsafeClusters);

                    sendCluster3(clusters);
                }
                sendLocalOutlier(outliers500);
        }

    }

    public static void sendLocalOutlier(Object localOutliers) throws MalformedURLException, SmbException, UnknownHostException, IOException {
        String content = "";
        switch (Constants.algorithm) {

            case "mcod_new":

                for (Data d : (ArrayList<MCData>) localOutliers) {
                    MCData data = (MCData) d;
                    content += data.arrivalTime + ":";
                    content += data.numSucceedingNeighbors + ":";
                    content += data.precedingNeighbors.size() + ":";
//                    for (MCData prevNeighbor : data.precedingNeighbors) {
//                        content += prevNeighbor.arrivalTime + ":";
//                    }
                    for (Double v : data.values) {
                        content += v + ",";
                    }
                    content += "\n";
                }

                break;
            case "mcod_safe":
                for (MCOD_Safe_Version.MCData data : (ArrayList<MCOD_Safe_Version.MCData>) localOutliers) {

                    content += data.arrivalTime + ":";
                    content += data.numSucceedingNeighbors + ":";
                    int count = 0;
                    for (Integer key : data.numPrecedingNeighbor.keySet()) {
                        count += data.numPrecedingNeighbor.get(key);
                    }
                    content += count + ":";
//                    for (MCData prevNeighbor : data.precedingNeighbors) {
//                        content += prevNeighbor.arrivalTime + ":";
//                    }
                    for (Double v : data.values) {
                        content += v + ",";
                    }
                    content += "\n";
                }

                break;
            case "mcod_mesi_safe":
                for (MCOD_MESI_Safe.MCData data : (ArrayList<MCOD_MESI_Safe.MCData>) localOutliers) {

                    content += data.arrivalTime + ":";
                    content += data.numSucceedingNeighbors + ":";
                    int count = 0;
                    for (Integer key : data.numPrecedingNeighbor.keySet()) {
                        count += data.numPrecedingNeighbor.get(key);
                    }
                    content += count + ":";
//                    for (MCData prevNeighbor : data.precedingNeighbors) {
//                        content += prevNeighbor.arrivalTime + ":";
//                    }
                    for (Double v : data.values) {
                        content += v + ",";
                    }
                    content += "\n";
                }

                break;

        }

        //write to file folder 
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);

        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/localOutlier_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }

    }

    public static void waitForLocalOutlierFromCentral() throws MalformedURLException, SmbException, InterruptedException, UnknownHostException, IOException {
        while (true) {
            //write to file folder 
            String user = "Luan Tran:Luan0991#";
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);

            SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/askingNeighbors_" + numWindows + ".txt", auth);
            if (sFile.exists()) {

                //read the local Outliers
                ArrayList<Data> askingData = new ArrayList<>();
                SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
                String line;
                while ((line = br.readLine()) != null) {
                    //form a Data object
                    String[] nodename_datavalues = line.split(":");
                    String[] data_values = nodename_datavalues[2].split(",");
                    double[] values = new double[data_values.length];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = Double.valueOf(data_values[i]);
                    }
                    Data d = new Data(values);
                    d.arrivalTime = Integer.valueOf(nodename_datavalues[1]);
                    d.nodeName = nodename_datavalues[0];
                    askingData.add(d);

                }
                findNeighbors(askingData);
                break;
            } else {
                Thread.sleep(1000);
            }
        }
    }

    private static void findNeighbors(ArrayList<Data> askingData) throws IOException {
        switch (Constants.algorithm) {

            case "mcod_new":
                ArrayList<MCData> results = new ArrayList<>();
                for (Data data : askingData) {
                    MCData d = mcod_new.new MCData(data);

                    mcod_new.fingNeighbors(d);
                    results.add(d);
                }
                sendResultToCentral(results);
                break;
            case "mcod_safe":
                ArrayList<MCOD_Safe_Version.MCData> results2 = new ArrayList<>();
                for (Data data : askingData) {
                    MCOD_Safe_Version.MCData d = mcod_safe.new MCData(data);
                    mcod_safe.findNeighbors(d);
                    results2.add(d);
                }
                sendResultToCentral2(results2);

                break;
            case "mcod_mesi_safe":
                ArrayList<MCOD_MESI_Safe.MCData> results3 = new ArrayList<>();
                for (Data data : askingData) {
                    MCOD_MESI_Safe.MCData d = mcod_mesi_safe.new MCData(data);
                    mcod_mesi_safe.findNeighbors(d);
                    results3.add(d);
                }
                if (Constants.sendBackResult) {
                    sendResultToCentral4(results3);
                } else {
                    sendResultToCentral3(results3);
                }

                break;
        }
    }

    private static void sendResultToCentral(ArrayList<MCData> results) throws MalformedURLException, IOException {
        //write to file folder 
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        String content = "";
        for (MCData d : results) {
            content += d.nodeName + ":" + d.arrivalTime + ":" + d.numSucceedingNeighbors + ":" + d.precedingNeighbors.size() + "\n";
//            for(MCData d2: d.precedingNeighbors){
//                
//            }

        }
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/resultNeighbors_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }
    }

    private static void sendCluster(ArrayList<MicroCluster_NewVersion.Micro_Cluster> microClusters) throws MalformedURLException, IOException {
        //only send center
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        String content = "";
        for (MicroCluster_NewVersion.Micro_Cluster c : microClusters) {
            double[] values = c.center.values;
            for (int i = 0; i < values.length; i++) {
                content += values[i] + ",";
            }
            content += "\n";

        }
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/localCluster_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }
    }

//    private static void sendCluster3WithCount(ArrayList<MCOD_Safe_Version.Cluster> clusters) throws MalformedURLException, IOException {
//        String user = "Luan Tran:Luan0991#";
//        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
//        String content = "";
//        for (MCOD_Safe_Version.Cluster c : clusters) {
//            content += c.members.size() + ":";
//            
//            double[] values = c.center.values;
//            for (int i = 0; i < values.length; i++) {
//                content += values[i] + ",";
//            }
//            content += "\n";
//
//        }
//        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/localCluster_" + numWindows + ".txt", auth);
//        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
//            sfos.write(content.getBytes());
//        }
//    
//    }
    private static void sendCluster2(ArrayList<MCOD_Safe_Version.Cluster> clusters) throws MalformedURLException, IOException {
        //only send center
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        String content = "";
        for (MCOD_Safe_Version.Cluster c : clusters) {
            content += c.members.size() + ":";
            double[] values = c.center.values;
            for (int i = 0; i < values.length; i++) {
                content += values[i] + ",";
            }
            content += "\n";

        }
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/localCluster_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }
    }

    private static void sendResultToCentral2(ArrayList<MCOD_Safe_Version.MCData> results) throws MalformedURLException, IOException {
        //write to file folder 
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        String content = "";
        for (MCOD_Safe_Version.MCData d : results) {

            //compute #preceding neighbors
            int prev = 0;
            for (Integer key : d.numPrecedingNeighbor.keySet()) {
                prev += d.numPrecedingNeighbor.get(key);
            }
            content += d.nodeName + ":" + d.arrivalTime + ":" + d.numSucceedingNeighbors + ":" + prev + "\n";
//            for(MCData d2: d.precedingNeighbors){
//                
//            }

        }
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/resultNeighbors_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }
    }

    private static void waitForFinishSignalFromCentral() throws MalformedURLException, SmbException, InterruptedException {
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/finish_" + numWindows + ".txt", auth);
        while (true) {
            if (sFile.exists()) {
                break;
            } else {
                Thread.sleep(1000);
            }
        }
    }

    private static void sendCluster3(ArrayList<MCOD_MESI_Safe.Cluster> clusters) throws MalformedURLException, IOException {
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        String content = "";
        for (MCOD_MESI_Safe.Cluster c : clusters) {
            int count = 0;
            List sortedKeys = new ArrayList(c.slide_members.keySet());
            Collections.sort(sortedKeys);
            for (int i = sortedKeys.size() - 1; i >= 0; i--) {
                int num = c.slide_members.get(sortedKeys.get(i)).size();
                content += sortedKeys.get(i) + "," + num + ";";
                count += num;
                if (count >= Constants.k) {
                    break;
                }
            }
            content += ":";
            double[] values = c.center.values;
            for (int i = 0; i < values.length; i++) {
                content += values[i] + ",";
            }
            content += "\n";

        }
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/localCluster_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }

    }

    private static void sendResultToCentral3(ArrayList<MCOD_MESI_Safe.MCData> results2) throws MalformedURLException, IOException {
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        String content = "";
        for (MCOD_MESI_Safe.MCData d : results2) {

            //compute #preceding neighbors
            int prev = 0;
            for (Integer key : d.numPrecedingNeighbor.keySet()) {
                prev += d.numPrecedingNeighbor.get(key);
            }
            content += d.nodeName + ":" + d.arrivalTime + ":" + d.numSucceedingNeighbors + ":" + prev + "\n";
//            for(MCData d2: d.precedingNeighbors){
//                
//            }

        }
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/resultNeighbors_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }
    }

    private static void sendResultToCentral4(ArrayList<MCOD_MESI_Safe.MCData> results2) throws MalformedURLException, IOException {
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        String content = "";
        for (MCOD_MESI_Safe.MCData d : results2) {

            //compute #preceding neighbors
            content += d.nodeName + ":" + d.arrivalTime + ":" + d.numSucceedingNeighbors + ":";
            int count = d.numSucceedingNeighbors;
            List<Integer> sortedKeys = new ArrayList(d.numPrecedingNeighbor.keySet());
            Collections.sort(sortedKeys);
            for (int i = sortedKeys.size() - 1; i >= 0; i--) {
                int key = sortedKeys.get(i);
                if (count >= Constants.k) {
                    break;
                }
                content += key + "," + d.numPrecedingNeighbor.get(key) + ";";
                count += d.numPrecedingNeighbor.get(key);
                
                
            }
            content += "\n";
//            for(MCData d2: d.precedingNeighbors){
//                
//            }

        }
        SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/resultNeighbors_" + numWindows + ".txt", auth);
        try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
            sfos.write(content.getBytes());
        }
    }

    private static void waitForResultForLocalOutlierFromCentral() throws MalformedURLException, SmbException, UnknownHostException, IOException, InterruptedException {
        while (true) {
            //write to file folder 
            String user = "Luan Tran:Luan0991#";
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);

            SmbFile sFile = new SmbFile(Constants.sharedFolder + "/" + Constants.nodeName + "/result_For_Local_Outlier_" + numWindows + ".txt", auth);
            if (sFile.exists()) {
                HashMap<Integer, HashMap<Integer, Integer>> neighborCountPerSlide = new HashMap<>();
                SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] arrivalTime_neighbors = line.split(":");
                    Integer arrivalTime = Integer.valueOf(arrivalTime_neighbors[0]);
                    String[] neighbors= arrivalTime_neighbors[1].split(";");
                    HashMap<Integer, Integer> neighborPerSlide = new HashMap<>();
                    for(String neighbor: neighbors){
                        String[] slide_values = neighbor.split(",");
                        neighborPerSlide.put(Integer.valueOf(slide_values[0]), Integer.valueOf(slide_values[1]));
                    }
                    neighborCountPerSlide.put(arrivalTime, neighborPerSlide);
                    
                }
                //update to local data
                if(Constants.algorithm.equals("mcod_mesi_safe")){
                    mcod_mesi_safe.updateNeighbors(neighborCountPerSlide);
                }
              
                break;
                
            } else {
                Thread.sleep(1000);
            }
        }
        
    }

}
