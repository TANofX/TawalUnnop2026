// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Swerve;

/* You should consider using the more terse Command factories API instead https://docs.wpilib.org/en/stable/docs/software/commandbased/organizing-command-based.html#defining-commands */
public class ResetOdometry extends Command {
  private String side;
  private Swerve swerve;

  /** Creates a new ResetOdometry. */
  public ResetOdometry(String side, Swerve swerve) {
    this.side = side;
    this.swerve = swerve;
    // Use addRequirements() here to declare subsystem dependencies.
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    boolean isRedAlliance = DriverStation.getAlliance()
        .orElse(DriverStation.Alliance.Blue) == DriverStation.Alliance.Red;
    if (side == "right") {
      if (!isRedAlliance) {
        swerve.resetOdometry(
            new Pose2d(Units.inchesToMeters(156.06), Units.inchesToMeters(26 / 2), Rotation2d.fromDegrees(0)));
        // blue side right
      } else {
        swerve.resetOdometry(
            new Pose2d(Units.inchesToMeters(489.3), Units.inchesToMeters(317.69 - (26 / 2)),
                Rotation2d.fromDegrees(180)));
        // red side right
      }
    } else {
      if (!isRedAlliance) {
        swerve.resetOdometry(
            new Pose2d(Units.inchesToMeters(156.06), Units.inchesToMeters(317.69 - (26 / 2)),
                Rotation2d.fromDegrees(180)));
        // blue side left
      } else {
        swerve.resetOdometry(
            new Pose2d(Units.inchesToMeters(489.3), Units.inchesToMeters(26 / 2), Rotation2d.fromDegrees(0)));
        // red side left
      }
    }
  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {
  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return true;
  }
}
