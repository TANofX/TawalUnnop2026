// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.lib.util;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class BatteryUsage {
    private static Map<String, RollingAverage> deviceAmps = new TreeMap<String, RollingAverage>();
    private static Map<String, RollingAverage> deviceVolts = new TreeMap<String, RollingAverage>();
    private static Map<Integer, Set<String>> devicePriority = new TreeMap<Integer, Set<String>>();
    private static Set<String> knownDevices = new HashSet<String>();

    private static int loopSize = 5;

    public static void registerDevice(String name, int priority) {
        Set<String> list = devicePriority.get(priority);
        if (list == null) {
            list = new HashSet<String>();
            devicePriority.put(priority, list);
        }
        list.add(name);
        knownDevices.add(name);
    }

    public static void reportUsage(String name, double amps, double volts) {
        if (knownDevices.contains(name)) {
            setValue(deviceAmps, name, amps);
            setValue(deviceVolts, name, volts);
        } else {
            throw new IllegalArgumentException(name + " is not a registered device. Please register before reporting battery usage.");
        }
    }

    private static void setValue(Map<String, RollingAverage> map, String name, double value) {
        RollingAverage list = map.get(name);
        if (list == null) {
            list = new RollingAverage(loopSize);
            map.put(name, list);
        }
        list.add(value);
    }

    public static double getTotalVoltage() {
        double total = 0;
        for (RollingAverage c : deviceVolts.values()) {
            total += c.getSum();
        }

        return total;
    }

    public static double getVoltageAverage(String name) {
        RollingAverage list = deviceVolts.get(name);

        return list.getAverage();
    }

    public static double getTotalAmps() {
        double total = 0;
        for (RollingAverage c : deviceAmps.values()) {
            total += c.getSum();
        }

        return total;
    }

    public static double getAmpsAverage(String name) {
        RollingAverage list = deviceAmps.get(name);

        return list.getAverage();
    }

    public static Set<String> getDeviceList() {
        return deviceVolts.keySet();
    }

    public static Map<Integer, Set<String>> getPriorityMap() {
        return devicePriority;
    }

    public static void reset() { // Reset utility to fix tests
        deviceAmps.clear();
        deviceVolts.clear();
        devicePriority.clear();
        knownDevices.clear();
    }
}
