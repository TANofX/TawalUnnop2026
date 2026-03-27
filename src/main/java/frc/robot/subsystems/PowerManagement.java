// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.lib.util.BatteryUsage;

import java.util.HashMap;
import java.util.Map;

public class PowerManagement extends SubsystemBase {
  private Map<String, AdvancedSubsystem> subsystemList = new HashMap<String, AdvancedSubsystem>();
  private static final double ALLOWED_CURRENT = 120;

  public PowerManagement() {

  }

  @Override
  public void periodic() {
    double totalCurrent;
    totalCurrent = BatteryUsage.getTotalAmps();

    double powerLimit = 1;

    if (totalCurrent > ALLOWED_CURRENT){
      double abovePercentage = (totalCurrent - ALLOWED_CURRENT) / ALLOWED_CURRENT;
      powerLimit = 1 - abovePercentage;
    }

    for (AdvancedSubsystem a : subsystemList.values()) {
      a.setPowerLimit(powerLimit);
    }
  }

  public void addSubsystem(AdvancedSubsystem subsystem) {
    subsystemList.put(subsystem.getName(), subsystem);
  }

  public void removeSubsystem(AdvancedSubsystem subsystem) {
    subsystemList.remove(subsystem.getName());
  }
}
