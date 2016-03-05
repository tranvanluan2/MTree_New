/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package outlierdetection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mtree.tests.Data;

/**
 *
 * @author Luan
 */
public class NewMTreeClass {

    public static NewMTreeClass root;

    private NewMTreeNode parent;

    private boolean isLeaf = true;

    private boolean isRoot = false;

    private ArrayList<NewMTreeNode> entries;

    private final int maxSize = 30;

    public NewMTreeClass() {
        entries = new ArrayList<>();
    }

    
    public void add(final Data data){
        insert(new NewMTreeNode(data));
    }
    public void insert(final NewMTreeNode n) {
        if (!isLeaf) {
            double min = Double.MAX_VALUE;
            NewMTreeNode best = null;
            double distance;
            for (final NewMTreeNode c : entries) {
                distance = n.distance(c);

                if (distance < min) {
                    best = c;
                    min = distance;
                }
            }
            if (min > best.getCoveringRadius()) {
                best.setCoveringRadius(min);
            }
            best.getSubtree().insert(n);
        } else {
            // is leaf
            if (entries.size() < maxSize) {
                // not full
                entries.add(n);
                n.setContainer(this);
                if (!isRoot) {
                    n.setParentDistance(n.distance(parent));
                }
            } else {
                // full
                split(n);
            }
        }
    }
    
    private void split(final NewMTreeNode n) {
        final ArrayList<NewMTreeNode> overfull = getEntries();
        overfull.add(n);

        // we need a new subtree
        final NewMTreeClass newTree = new NewMTreeClass();
        if (!isLeaf) {
            newTree.setLeaf(false);
        }

        // we partition nodes in overfull into these lists
        final ArrayList<NewMTreeNode> n1 = new ArrayList<>();
        final ArrayList<NewMTreeNode> n2 = new ArrayList<>();

        // promote two random nodes
        final Random rand = new Random();
        NewMTreeNode op1 = overfull.get(rand.nextInt(overfull.size()));
        NewMTreeNode op2 = null;
        while (op2 == null) {
            final NewMTreeNode temp = overfull.get(rand.nextInt(overfull.size()));
            if (op1 != temp) {
                op2 = temp;
            }
        }
        //?
        op1 = new NewMTreeNode(op1.data);
        op2 = new NewMTreeNode(op2.data);

        // partition
        for (final NewMTreeNode m : overfull) {
            final double dis1 = m.distance(op1);
            final double dis2 = m.distance(op2);
            if (dis1 < dis2) {
                if (dis1 > op1.getCoveringRadius()) {
                    op1.setCoveringRadius(dis1);
                }
                n1.add(m);
                m.setContainer(this);
                m.setParentDistance(dis1);
            } else {
                if (dis2 > op2.getCoveringRadius()) {
                    op2.setCoveringRadius(dis2);
                }
                n2.add(m);
                m.setContainer(newTree);
                m.setParentDistance(dis2);
            }
        }

        // store nodes
        setEntries(n1);
        newTree.setEntries(n2);

        // update subtrees
        op1.setSubtree(this);
        op2.setSubtree(newTree);

        if (isRoot) {
            // root splitted, need a new root
            final NewMTreeClass newRoot = new NewMTreeClass();
            NewMTreeClass.root = newRoot;
            newRoot.setLeaf(false);
            newRoot.setRoot(true);
            newRoot.addEntry(op1);
            newRoot.addEntry(op2);
            op1.setContainer(newRoot);
            op2.setContainer(newRoot);
        } else {
            // System.out.println("non-root splitted");
            // non-root node splitted
            final NewMTreeClass parCont = parent.getContainer();
            parCont.replaceEntry(parent, op1);
            op1.setContainer(parCont);
            if (!parCont.isRoot) {
                op1.setParentDistance(op1.distance(parCont.parent));
            }
            if (parent.getContainer().getEntries().size() >= maxSize) {
                // split propagates
                parent.getContainer().split(op2);
            } else {
                parCont.addEntry(op2);
                op2.setContainer(parCont);
                if (!parCont.isRoot()) {
                    op2.setParentDistance(op2.distance(parCont.parent));
                }
            }
        }
        parent = op1;
        isRoot = false;
        newTree.setParent(op2);
    }
    
      public void getRange(final NewMTreeNode q, final double radius,
            final List<Data> result, final double pqDist) {
        if (!isLeaf) {
            for (final NewMTreeNode n : entries) {
                if (Math.abs(pqDist - n.getParentDistance()) <= radius
                        + n.getCoveringRadius()) {
                    final double dist = n.distance(q);
                    if (dist <= radius + n.getCoveringRadius()) {
                        n.getSubtree()
                                .getRange(q, radius, result, dist);
                    }
                }
            }
        } else {
            for (final NewMTreeNode n : entries) {
                if (Math.abs(pqDist - n.getParentDistance()) <= radius) {
                    final double dist = n.distance(q);
                    if (dist <= radius) {
                        result.add(n.data);
                    }
                }
            }
        }
    }
    
    public ArrayList<NewMTreeNode> getEntries() {
        return entries;
    }

    public NewMTreeNode getParent() {
        return parent;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void setEntries(final ArrayList<NewMTreeNode> l) {
        entries = l;
    }

    public void setRoot(final boolean b) {
        isRoot = b;
    }

    public void setLeaf(final boolean b) {
        isLeaf = b;
    }

    public void setParent(final NewMTreeNode m) {
        parent = m;
    }

    public void addEntry(final NewMTreeNode n) {
        entries.add(n);
    }

    // used in split
    public void replaceEntry(final NewMTreeNode oldOne, final NewMTreeNode newOne) {
        entries.remove(oldOne);
        entries.add(newOne);
    }

    public void setRoot(final NewMTreeClass mTree) {
        root = mTree;
        mTree.setRoot(true);
    }

    public NewMTreeClass getRoot() {
        return root;
    }

}
