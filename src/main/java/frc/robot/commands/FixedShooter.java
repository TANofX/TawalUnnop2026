// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Turret;

public class FixedShooter extends Command {
  
  private Shooter theShooter;
  private Turret theTurret;
  private double theTargetRPM;
  private Rotation2d theTurretAngle;

  public FixedShooter(Shooter shooter, Turret turret, double targetRPM, Rotation2d turretAngle) {
    theTurret = turret;
    theShooter = shooter;
    theTargetRPM = targetRPM;
    theTurretAngle = turretAngle;
    addRequirements(theShooter, theTurret);
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    theShooter.setShooterRPM(theTargetRPM, theTargetRPM);
    theTurret.pointToTarget(theTurretAngle);
  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {}

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
    theShooter.stopShooterMotors();
  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return false;
  }
}
