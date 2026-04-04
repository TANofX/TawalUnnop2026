// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.lib.util.BatteryUsage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PowerManagement extends SubsystemBase {
  private Map<String, AdvancedSubsystem> subsystemList = new HashMap<String, AdvancedSubsystem>();
  private static final double ALLOWED_CURRENT = 120;
  private static final Map<Integer, Set<String>> list = BatteryUsage.getPriorityMap();
  private double sum = 0; // Sum of the current being used
  private boolean limit = true;

  public PowerManagement(boolean limitPower) {
    limit = limitPower;
  }

  private double getSum(int priority) {
    sum = 0;
    double ask = 0;
    for (String n : list.get(priority)) { // Loop through all subsystems of a priority & add amp draw to sum
      ask = BatteryUsage.getAmpsAverage(n);
      sum += ask;
      SmartDashboard.putNumber("PowerManagment/" + n + "/AverageCurrent", ask);
    }
    return sum;
  }

  @Override
  public void periodic() {
    double remainingCurrent = ALLOWED_CURRENT;
    for (int i = 0; i < list.size(); i++) { // Loop by priority 
      double powerLimit;
      getSum(i);
      if (remainingCurrent > sum) { 
        powerLimit = 1;
        remainingCurrent -= sum;
      } else {
        powerLimit = remainingCurrent/sum;
        remainingCurrent = 0;
      }
      for (String n : list.get(i)) { // Loop through every subsystem within priority
        if (limit) { // Only limit power if enabled in constructor
          subsystemList.get(n).setPowerLimit(powerLimit);
        }
        SmartDashboard.putNumber("PowerManagment/" + n + "/PowerLimit", powerLimit);
      }
    }
  }

  public void addSubsystem(AdvancedSubsystem subsystem) {
    subsystemList.put(subsystem.getName(), subsystem);
  }

  public void removeSubsystem(AdvancedSubsystem subsystem) {
    subsystemList.remove(subsystem.getName());
  }

}
