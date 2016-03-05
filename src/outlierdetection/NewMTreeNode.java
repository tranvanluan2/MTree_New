/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import mtree.tests.Data;

/**
 *
 * @author Luan
 */
public class NewMTreeNode {
    
    private double parentDistance = 0;
    private double coveringRadius = 0;

    private NewMTreeClass subtree;

    private NewMTreeClass container;
    
    public Data data;
    
    public NewMTreeNode(){}
    
    public NewMTreeNode(Data d){
        data =d;
    }
    
    public double getCoveringRadius() {
        return coveringRadius;
    }

    public double getParentDistance() {
        return parentDistance;
    }

    public NewMTreeClass getSubtree() {
        return subtree;
    }

    
    public double distance(final NewMTreeNode n) {
        
        //not implemented
        
        //euclidean distance
        DistanceFunction.euclideanDistance(data, n.data);
        return 0;

    }
    
    public NewMTreeClass getContainer() {
        return container;
    }

    public void setCoveringRadius(final double r) {
        coveringRadius = r;
    }

    public void setParentDistance(final double d) {
        parentDistance = d;
    }

    public void setContainer(final NewMTreeClass m) {
        container = m;
    }

    public void setSubtree(final NewMTreeClass m) {
        subtree = m;
    }
    

    
}
