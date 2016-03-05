package mtree.tests;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class DataGenerator {
    
    
    public static void main(String[] args) throws IOException{
        
        generateRandomData("C:\\Users\\Luan\\MTree\\randomData.txt");
    }
    
    public static void generateRandomData(String filename) throws IOException{
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filename)));
        ArrayList<Double> values = new ArrayList<Double>();
        Random r = new Random();
        for(int i = 0; i < 1000; i++){
            values.add(r.nextDouble()*100);
        }
        for(Double d: values){
            bw.write(d+"\n");
        }
        bw.close();
    }

}
