package outlierdetection;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

import mtree.ComposedSplitFunction;
import mtree.DistanceFunction;
import mtree.DistanceFunctions;
import mtree.MTree;
import mtree.PartitionFunctions;
import mtree.PromotionFunction;
import mtree.DistanceFunctions.EuclideanCoordinate;
import mtree.tests.Data;
import mtree.utils.Constants;
import mtree.utils.Pair;
import mtree.utils.Utils;

public class DataLUEObject implements EuclideanCoordinate, Comparable<DataLUEObject> {

    public int expireTime;
    public int numberSuccedingNeighbors;
    public int ev;
    public ArrayList<DataLUEObject> p_neighbors = new ArrayList<>();
    public int arrivalTime;
    public double[] values;
    public final int hashCode;

    public DataLUEObject(Data d, int currentTime) {
        super();
        this.expireTime = d.arrivalTime + Constants.W;
        this.values = d.values;
        this.arrivalTime = d.arrivalTime;
        int hashcode2 = 1;
        for (double value : values) {
            hashcode2 = 31 * hashcode2 + (int) value + (new Random()).nextInt(100000);
        }
        this.hashCode = hashcode2;

    }

    @Override
    public int compareTo(DataLUEObject that) {
        if (this.ev < that.ev) return -1;
        return 1;
    }

    @Override
    public int dimensions() {
        return values.length;
    }

    @Override
    public double get(int index) {
        return values[index];
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DataLUEObject) {
            DataLUEObject that = (DataLUEObject) obj;
            if (this.arrivalTime != that.arrivalTime) return false;
            if (this.dimensions() != that.dimensions()) {
                return false;
            }
            for (int i = 0; i < this.dimensions(); i++) {
                if (this.values[i] != that.values[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

}

class MTreeClassLUE extends MTree<DataLUEObject> {

    private static final PromotionFunction<DataLUEObject> nonRandomPromotion = new PromotionFunction<DataLUEObject>() {
        @Override
        public Pair<DataLUEObject> process(Set<DataLUEObject> dataSet,
                                           DistanceFunction<? super DataLUEObject> distanceFunction) {
            return Utils.minMax(dataSet);
        }
    };

    MTreeClassLUE() {
        super(25, DistanceFunctions.EUCLIDEAN, new ComposedSplitFunction<DataLUEObject>(nonRandomPromotion,
                new PartitionFunctions.BalancedPartition<DataLUEObject>()));
    }

    public void add(DataLUEObject data) {
        super.add(data);
        _check();
    }

    public boolean remove(DataLUEObject data) {
        boolean result = super.remove(data);
        _check();
        return result;
    }

    DistanceFunction<? super DataLUEObject> getDistanceFunction() {
        return distanceFunction;
    }
};