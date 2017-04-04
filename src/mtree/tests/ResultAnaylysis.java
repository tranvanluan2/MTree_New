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
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import static mtree.tests.LocalNode.numWindows;
import mtree.utils.Constants;
import static mtree.utils.Constants.readConfig;

/**
 *
 * @author Luan Tran
 */
public class ResultAnaylysis {

    public static void main(String[] args) throws IOException {
        readConfig();
        compareSendAndNotSendCluster(400);
    }

    public static void compareSendAndNotSendCluster(int numberWindow) throws MalformedURLException, IOException {
        double avgPrune = 0;
        //read all local outliers file
        int[] countLocalOutliers = new int[numberWindow];
        for (int i = 0; i < countLocalOutliers.length; i++) {
            countLocalOutliers[i] = 0;
        }

        //read asking neighbors 
        int[] countAskingData = new int[numberWindow];
        for (int i = 0; i < countAskingData.length; i++) {
            countAskingData[i] = 0;
        }

        double[] numberPrunedData = new double[numberWindow];

     //   String folder = "Stock_W10k_S5k_R0.2_K150SendCluster";
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);

        for (String nodeName : Constants.nodeNames) {
            for (int i = 0; i < numberWindow; i++) {
                SmbFile sFile = new SmbFile(Constants.sharedFolder +"/"+ nodeName + "/localOutlier_" + i + ".txt", auth);
                SmbFileInputStream smbStream = new SmbFileInputStream(sFile);
                BufferedReader br = new BufferedReader(new InputStreamReader(smbStream));
                String line;

                while ((line = br.readLine()) != null) {
                    countLocalOutliers[i]++;
                }

                SmbFile sFile2 = new SmbFile(Constants.sharedFolder +"/"  + nodeName + "/askingNeighbors_" + i + ".txt", auth);
                SmbFileInputStream smbStream2 = new SmbFileInputStream(sFile2);
                BufferedReader br2 = new BufferedReader(new InputStreamReader(smbStream2));
                String line2;

                while ((line2 = br2.readLine()) != null) {
                    countAskingData[i]++;
                }
            }
        }

        for (int i = 0; i < numberPrunedData.length; i++) {
            numberPrunedData[i] = (countLocalOutliers[i] - countAskingData[i]) * 1.0 / countLocalOutliers[i] * 100;
        }

        for (int i = 0; i < numberPrunedData.length; i++) {
            System.out.println(numberPrunedData[i]);
            avgPrune += numberPrunedData[i];
        }
        System.out.println("avg prune rate = "+ avgPrune/numberPrunedData.length);
        //compute outlier rate 
        int sum = 0;
        for (int i = 0; i < 400; i++) {
            SmbFile sFile2 = new SmbFile(Constants.sharedFolder + "/Result/" + "/" + i + "_outlier.txt", auth);
            SmbFileInputStream smbStream2 = new SmbFileInputStream(sFile2);
            BufferedReader br2 = new BufferedReader(new InputStreamReader(smbStream2));
            String line2;
            while ((line2 = br2.readLine()) != null) {
                sum++;
            }
        }
        double outlierRate = sum*100.0/(Constants.W*Constants.nodeNames.length*400);
        System.out.println("Outlier rate = "+ outlierRate);
    }

}
