/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mtree.tests;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import org.apache.commons.math3.distribution.NormalDistribution;

/**
 *
 * @author Luan
 */
public class DataGeneration {
    
    
    public static void main(String[] args) throws IOException{
        generateNormalDistribution(20000, 100, "E:\\Data\\testNormalDistribution_100.txt");
    }

    public static void generateNormalDistribution(int number, int dim, String outputFile) throws IOException {
       // NormalDistribution norm = new NormalDistribution();
        NormalDistribution[] norm = new NormalDistribution[dim];
        for(int i = 0; i < dim; i++){
            norm[i] = new NormalDistribution(((new Random()).nextDouble()), ((new Random()).nextDouble()));
        }
        FileWriter fw = new FileWriter(new File(outputFile));
        for (int i = 0; i < number; i++) {
            String row = "";
            for (int j = 0; j < dim - 1; j++) {
                row = row + norm[j].sample() + ",";
            }
            row += norm[dim-1].sample() + "\n";
            fw.append(row);

        }
        fw.close();
    }

}
