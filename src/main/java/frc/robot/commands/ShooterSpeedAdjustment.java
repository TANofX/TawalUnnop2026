// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Shooter;

public class ShooterSpeedAdjustment extends Command {
  private final Shooter shooter;
  private double targetRPM;
  private String baseKey;

  public ShooterSpeedAdjustment(Shooter shooter) {
    this.shooter = shooter;
    addRequirements(shooter);
  }

  @Override
  public void initialize() {
    baseKey = shooter.getName();

    targetRPM = 100;
    shooter.setShooterRPM(targetRPM, targetRPM);

    SmartDashboard.putNumber(baseKey + "/Target", targetRPM);
    SmartDashboard.putNumber(baseKey + "/RPM Increment", 100);
  }

  public double getIncrement() {
    return SmartDashboard.getNumber(baseKey + "/RPM Increment", 100);
  }

  public void adjustRPM(double delta) {
    targetRPM += delta;

    // Optional safety clamp 
    if (targetRPM < 0) targetRPM = 0;
    shooter.setShooterRPM(targetRPM, targetRPM);
    SmartDashboard.putNumber(baseKey + "/Target", targetRPM);
  }

  @Override
  public boolean isFinished() {
    return false;
  }
}
