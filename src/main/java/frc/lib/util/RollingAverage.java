package frc.lib.util;

public class RollingAverage {
    private double[] array;
    private int targetLength;
    private int index = 0;
    private int valuesAdded = 0;
    private double sum = 0;

    public RollingAverage(int length) {
        array = new double[length];
        targetLength = length;
    }

    public String toString() {
        return array.toString();
    }

    public double getAverage() {
        double denominator = 0;
        if (valuesAdded == 0){
            return 0;
        }
        if (valuesAdded >= targetLength) {
            denominator = targetLength;
        } else {
            denominator = index;
        }
        return sum/denominator;
    }

    public void add(double value) {
        double prevValue = array[index];
        array[index] = value;
        index++;
        valuesAdded++;
        if (index == 5){
            index = 0;
        }
        if (valuesAdded >= targetLength) {
            sum -= prevValue;
        }
        sum += value;
    }

    public double getSum() {
        return sum;
    }

    public double getLastAdded() {
        return array[index];
    }

    public int getLength() {
        return array.length;
    }

    public double getAtIndex(int index) {
        return array[index];
    }
}