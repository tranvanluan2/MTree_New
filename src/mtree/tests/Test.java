package mtree.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import mtree.utils.FibonacciHeap;

public class Test {
    
    public static ArrayList<FibonacciHeap.Node<Integer>> l = new ArrayList<>();
    public static void main(String[] args){
        
        for(int i= 1; i< 100; i++){
            if(Math.pow(0.019,i)+i*0.981*Math.pow(0.019, i-1) <=0.9){
                System.out.println("i= "+i);
                break;
            }
        }
        
        
//        FibonacciHeap<Integer> heaps = new FibonacciHeap<Integer>();
//        
//        
//        
//        FibonacciHeap.Node<Integer> node1 = heaps.insert(1);
//        l.add(node1);
//        FibonacciHeap.Node<Integer> node3 = heaps.insert(3);
//         l.add(node3);
//        FibonacciHeap.Node<Integer> node5 = heaps.insert(5);
//         l.add(node5);
//        FibonacciHeap.Node<Integer> node7 = heaps.insert(7);
//         l.add(node7);
//        FibonacciHeap.Node<Integer> node9 = heaps.insert(9);
//         l.add(node9);
//        FibonacciHeap.Node<Integer> node10 = heaps.insert(10);
//         l.add(node10);
//        FibonacciHeap.Node<Integer> node11 = heaps.insert(19);
//         l.add(node11);
//        FibonacciHeap.Node<Integer> node12 = heaps.insert(29);
//         l.add(node12);
//        FibonacciHeap.Node<Integer> node13 = heaps.insert(39);
//         l.add(node13);
//        FibonacciHeap.Node<Integer> node14 = heaps.insert(14);
//         l.add(node14);
//        FibonacciHeap.Node<Integer> node15 = heaps.insert(50);
//         l.add(node15);
//        FibonacciHeap.Node<Integer> node17 = heaps.insert(37);
//         l.add(node17);
//        FibonacciHeap.Node<Integer> node20 = heaps.insert(200);
//         l.add(node20);
//        FibonacciHeap.Node<Integer> node21 = heaps.insert(12);
//         l.add(node21);
//        FibonacciHeap.Node<Integer> node23 = heaps.insert(390);
//         l.add(node23);
//        FibonacciHeap.Node<Integer> node24 = heaps.insert(32);
//         l.add(node24);
//        FibonacciHeap.Node<Integer> node25 = heaps.insert(79);
//         l.add(node25);
//        FibonacciHeap.Node<Integer> node26 = heaps.insert(139);
//         l.add(node26);
//        FibonacciHeap.Node<Integer> node27 = heaps.insert(90);
//         l.add(node27);
//        Integer min = heaps.extractMin().getKey();
//        printHeaps();
//        heaps.increaseKey(node12, 101);
//        heaps.extractMin();
//        printHeaps();
//        heaps.increaseKey(node3, 1011);
//        printHeaps();
//        heaps.increaseKey(node5, 100);
//        printHeaps();
//        heaps.increaseKey(node7, 103);
//        printHeaps();
//        heaps.increaseKey(node9, 104);
//        printHeaps();
//        heaps.increaseKey(node10, 104);
//        printHeaps();
//        heaps.increaseKey(node11, 204);
//        printHeaps();
//         min = heaps.extractMin().getKey();
//        System.out.println("Min = "+min);
        
        
        
}
    
    public static void readFromFile(String filename){
        try {
            FileReader fr = new FileReader(new File(filename));
            BufferedReader bfr = new BufferedReader(fr);
            String line="";
            while((line=bfr.readLine())!=null){
                String[] numbers= line.split(" ");
                for(int i=0; i < 3; i++){
                    Double number0 = Double.valueOf(numbers[0]);
                    Double number1 = Double.valueOf(numbers[1]);
                    Double number2 = Double.valueOf(numbers[2]);
                }
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public static void printHeaps(){
        System.out.println("------------------------------------");
        for(FibonacciHeap.Node<Integer> node: l){
            
            System.out.print("key="+node.getKey() +";next="+node.next.getKey()+";prev="+node.prev.getKey());
            if(node.parent!=null)System.out.print(";parent="+node.parent.getKey());
            else System.out.print(";parent="+node.parent);
            if(node.child!=null)System.out.println(";child="+node.child.getKey());
            else System.out.println(";child="+node.child);
        }
    }
}