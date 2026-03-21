// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.lib.util;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class BatteryUsage {
    private static Map<String, List<Double>> deviceAmps = new TreeMap<String, List<Double>>();
    private static Map<String, List<Double>> deviceVolts = new TreeMap<String, List<Double>>();

    private static int loopSize = 5;

    public static void reportUsage(String name, double amps, double volts) {
        setValue(deviceAmps, name, amps);
        setValue(deviceVolts, name, volts);
    }

    private static void setValue(Map<String, List<Double>> map, String name, double value) {
        List<Double> list = map.get(name);
        if (list == null) {
            list = new ArrayList<Double>();
            map.put(name, list);
        }
        list.add(value);
    }

    public static double getTotalVoltage() {
        double total = 0;
        for (List<Double> c : deviceVolts.values()) {
            total += c.get(c.size() - 1);
        }

        return total;
    }

    public static double getVoltageAverage(String name) {
        double total = 0;
        int count = 0;
        List<Double> list = deviceVolts.get(name);

        for (int i = Math.max(list.size() - loopSize, 0); i < list.size(); i++, count++) {
            total += list.get(i);
        }

        return count == 0 ? 0 : total/count;
    }

    public static double getTotalAmps() {
        double total = 0;
        for (List<Double> c : deviceAmps.values()) {
            total += c.get(c.size() - 1);
        }

        return total;
    }

    public static double getAmpsAverage(String name) {
        double total = 0;
        int count = 0;
        List<Double> list = deviceAmps.get(name);

        for (int i = Math.max(list.size() - loopSize, 0); i < list.size(); i++, count++) {
            total += list.get(i);
        }

        return count == 0 ? 0 : total/count;
    }

    public static Set<String> getDeviceList() {
        return deviceVolts.keySet();
    }
}
