/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mtree.tests;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author Luan Tran
 */
public class NormalizeData {
    
    public static String filename = "tao.txt";
    public static String output = "normalized_tao.txt";
    
    public static void main(String[] args) throws FileNotFoundException, IOException{
        
//        ArrayList<Double> test=  new ArrayList<>();
//        test.add(-5.0);
//        test.add(6.0);
//        test.add(9.0);
//        test.add(2.0);
//        test.add(4.0);
//        ArrayList<Double> norm = normalize(test);
        
    
        BufferedReader bfr = new BufferedReader(new FileReader(new File(filename)));
        ArrayList<Double> att1 = new ArrayList<>();
        ArrayList<Double> att2 = new ArrayList<>();
        ArrayList<Double> att3 = new ArrayList<>();
        String line = "";
        while((line = bfr.readLine())!=null){
            String[] elements = line.trim().split(",");
            att1.add(Double.valueOf(elements[0]));
            att2.add(Double.valueOf(elements[1]));
            att3.add(Double.valueOf(elements[2]));
            
        }
        
        ArrayList<Double> normalized_att1 = normalize(att1);
        ArrayList<Double> normalized_att2 = normalize(att2);
        ArrayList<Double> normalized_att3 = normalize(att3);
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File(output)));
        for(int i = 0; i < normalized_att1.size(); i++){
            bw.write(att1.get(i)+","+att2.get(i)+","+att3.get(i)+"\n");
        }
        bw.close();
    }
    

    private static ArrayList<Double> normalize(ArrayList<Double> att1) {
        ArrayList<Double> normalized_att = new ArrayList<>();
        double sum = 0;
        for (Double d: att1){
            sum += d;
        }
        double mean = sum/att1.size();
        double squared_error = 0;
        for(Double d: att1){
            squared_error += (mean-d)*(mean-d);
        }
        double deviation =  Math.sqrt(squared_error/att1.size());
        for(int i = 0; i < att1.size(); i++){
            Double d = att1.get(i);
            normalized_att.add((d-mean)/deviation);
        }
        return normalized_att;
    }
    
}
