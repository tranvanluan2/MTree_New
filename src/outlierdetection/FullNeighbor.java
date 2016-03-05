/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.util.ArrayList;
import mtree.tests.Data;

/**
 *
 * @author Luan
 */
public class FullNeighbor {
    
    public static MTreeClass mtree = new MTreeClass();
     
     
    
    
    
}

class DataObject extends Data{
    public ArrayList<DataObject> precedings = new ArrayList<>();
    public ArrayList<DataObject> succeedings = new ArrayList<>();
    
   // public 
}
