package mtree.tests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Random;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;
import static mtree.tests.CentralServer.numWindows;
import mtree.utils.Constants;
import static mtree.utils.Constants.readConfig;

public class DataGenerator {

    public static int numberWindows = 0;
    public static int currentTime = 0;

    public static void main(String[] args) throws IOException {
        for(int i = 0; i < 400; i++){
            
        generateDataNextWindow(i);
        }
    }
    
//    public static ArrayList<Data> generateGaussianData(double[]) throws IOException{
//        readConfig();
//        Stream s = Stream.getInstance("");
//
//        ArrayList<Data> incomingData;
//        if (numberWindows != 0) {
//            incomingData = s.getIncomingData(currentTime, Constants.nodeNames.length * Constants.slide, Constants.dataFile, Constants.matrixType);
//            currentTime = currentTime + Constants.nodeNames.length * Constants.slide;
//        } else {
//            incomingData = s.getIncomingData(currentTime, Constants.nodeNames.length * Constants.W, Constants.dataFile, Constants.matrixType);
//            currentTime = currentTime + Constants.nodeNames.length * Constants.W;
//        }
//        
//    }

    public static void generateDataNextWindow(int slideSize) throws MalformedURLException, IOException {
        //read data from data file
        readConfig();
        Stream s = Stream.getInstance("");

        ArrayList<Data> incomingData;
        if (numberWindows != 0) {
            incomingData = s.getIncomingData(currentTime, Constants.nodeNames.length * Constants.slide, Constants.dataFile, Constants.matrixType);
            currentTime = currentTime + Constants.nodeNames.length * Constants.slide;
        } else {
            incomingData = s.getIncomingData(currentTime, Constants.nodeNames.length * Constants.W, Constants.dataFile, Constants.matrixType);
            currentTime = currentTime + Constants.nodeNames.length * Constants.W;
        }
        

        //distribute value to local nodes
        String[] contents = new String[Constants.nodeNames.length];
        for(int i = 0 ; i < contents.length; i++) contents[i] = "";
        Random r = new Random();
        for (int i = 0; i < incomingData.size(); i=i+Constants.nodeNames.length) {
            
            
            for(int j = 0 ; j < Constants.nodeNames.length; j++){
                Data d = incomingData.get(i+j);
                double ran = r.nextDouble();
                if(ran <1)
                    contents[j] += d.toStringValue()+"\n";
                else contents[j] +="-\n";
            }
            
        }

        //write content to each local Node
        String user = "Luan Tran:Luan0991#";
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
        for (int i = 0; i < Constants.nodeNames.length; i++) {
            SmbFile sFile = new SmbFile(Constants.sharedFolder + "/Data/" + Constants.nodeNames[i] + "/newData_" + numberWindows + ".txt", auth);
            try (SmbFileOutputStream sfos = new SmbFileOutputStream(sFile)) {
                sfos.write(contents[i].getBytes());
            }
        }
        numberWindows++;
    }

    public static void generateRandomData(String filename) throws IOException {

    }

}
