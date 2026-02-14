// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.
package frc.robot;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.PowerDistribution;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.lib.input.controllers.XboxControllerWrapper;
import edu.wpi.first.wpilibj2.command.*;
import frc.robot.commands.SwerveDriveWithGamepad;
import frc.robot.subsystems.*;
import frc.robot.subsystems.FireControl;


public class RobotContainer {
  // Controllers
  public static final XboxControllerWrapper driver = new XboxControllerWrapper(0, 0.1);
  public static final XboxControllerWrapper coDriver = new XboxControllerWrapper(1, 0.1);

  // Subsystems
  public static final Swerve swerve = new Swerve();// new Swerve();
  public static final PowerDistribution powerDistribution = new PowerDistribution();
  public static final Vision vision = new Vision(
      (visionPose, timestamp, stdDevs) -> {
        swerve.getPoseEstimator().addVisionMeasurement(
            visionPose,
            timestamp,
            VecBuilder.fill(stdDevs.get(0, 0), stdDevs.get(1, 0), stdDevs.get(2, 0)));
      });

  public static final Intake intake = new Intake();
  public static final Indexer funnyName = new Indexer();

  // Vision clients
  // public static final JetsonClient jetson = new JetsonClient();

  public Command getAutonomousCommand() {
    return Commands.print("No autonomous command configured");
  }

  public RobotContainer() {
    configureButtonBindings();

    vision.addCamera("name", Constants.Vision.robotToCam1);
    
    SmartDashboard.putData(swerve.zeroModulesCommand());
    
    swerve.setDefaultCommand(new SwerveDriveWithGamepad(swerve));

    SmartDashboard.putData("Reset position", Commands.runOnce(() -> {
      swerve.resetOdometry(Pose2d.kZero);
    }, swerve));
  }

  private void configureButtonBindings() {
    coDriver.START();
    driver.A().whileTrue(intake.intakeFuel());
    driver.B().whileTrue(intake.extakeFuel());
    driver.X().onTrue(intake.putUpIntake());
    driver.Y().onTrue(intake.putDownIntake());
    driver.DUp().whileTrue(funnyName.shootFuel());
    driver.DDown().whileTrue(funnyName.clearFuel());
  /*   
        }, shooter))).andThen(new Shoot().andThen(Commands.waitSeconds(0.5).andThen(Commands.runOnce(() -> {
          shooter.stopMotors();

        }))))); */
  
  }
}