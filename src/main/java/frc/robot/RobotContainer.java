// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.
package frc.robot;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.PowerDistribution;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.input.controllers.XboxControllerWrapper;
import frc.robot.commands.CalibrateTurret;
import frc.robot.commands.DefaultTurretCommand;
import frc.robot.commands.FixedShooter;
import frc.robot.commands.LadderPosition;
import frc.robot.commands.ResetOdometry;
import frc.robot.commands.ShootWithIndexer;
import frc.robot.commands.SwerveDriveWithGamepad;
import frc.robot.commands.ZeroTurret;
import frc.robot.subsystems.FireControl;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Swerve;
import frc.robot.subsystems.Turret;
import frc.robot.subsystems.Vision;

public class RobotContainer {
  // Define set points for shooting if autos fail
  public static final double rightClimbRPM = Constants.SetPoints.climbRightTargetRPM;
  public static final double leftClimbRPM = Constants.SetPoints.climbLeftTargetRPM;
  public static final double rightTrenchRPM = Constants.SetPoints.trenchRightTargetRPM;
  public static final double leftTrenchRPM = Constants.SetPoints.trenchLeftTargetRPM;
  public static final Rotation2d rightClimbAngle = Constants.SetPoints.climbRightTurretAngle;
  public static final Rotation2d leftClimbAngle = Constants.SetPoints.climbLeftTurretAngle;
  public static final Rotation2d rightTrenchAngle = Constants.SetPoints.trenchRightTurretAngle;
  public static final Rotation2d leftTrenchAngle = Constants.SetPoints.trenchLeftTurretAngle;
  public static SendableChooser<Command> chooser;
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

  public static final Intake intake = new Intake(Constants.Intake.INTAKE_LIFT_MOTOR_ID,
      Constants.Intake.INTAKE_MOTOR_ID);
  public static final Indexer indexer = new Indexer(Constants.Indexer.INDEXER_MOTOR_ID);
  public static final Turret turret = new Turret("Turret", Constants.Turret.TURRET_MOTOR_ID,
      Constants.Turret.Turret_HALL_EFFECT_ID, null); // TODO obtain transform from robot to turret
  public static final Shooter shooter = new Shooter(
      Constants.Shooter.TOP_LEFT_SHOOTER_ID,
      Constants.Shooter.BOTTOM_LEFT_SHOOTER_ID,
      Constants.Shooter.TOP_RIGHT_SHOOTER_ID,
      Constants.Shooter.BOTTOM_RIGHT_SHOOTER_ID);
  public static final FireControl fireControl = new FireControl(
      () -> {
        return swerve.getPose().transformBy(new Transform2d(
            Constants.Turret.ROBOT_TO_SHOOTER.getTranslation().toTranslation2d(),
            Constants.Turret.ROBOT_TO_SHOOTER.getRotation().toRotation2d()));
      },
      () -> DriverStation.getAlliance().orElse(Alliance.Blue),
      () -> new ChassisSpeeds());
  // Vision clients
  // public static final JetsonClient jetson = new JetsonClient();

  private SendableChooser<Command> autoChooser() {
    chooser = new SendableChooser<>();
    chooser.addOption("rightTrench", rightTrenchAutoCommand());
    chooser.addOption("leftTrench", leftTrenchAutoCommand());
    chooser.addOption("rightBump", rightBumpAutoCommand());
    chooser.addOption("leftBump", leftBumpAutoCommand());
    return chooser;
  }

  public Command getAutonomousCommand() {
    return chooser.getSelected();
  }

  public RobotContainer() {
    configureButtonBindings();

    vision.addCamera("heart", Constants.Vision.robotToHeart);
    vision.addCamera("club", Constants.Vision.robotToClub);
    vision.addCamera("diamond", Constants.Vision.robotToDiamond);
    vision.addCamera("Arducam_OV9281_USB_Camera",
    Constants.Vision.robotToArudcam);

    SmartDashboard.putData(swerve.zeroModulesCommand());
    swerve.setDefaultCommand(new SwerveDriveWithGamepad(swerve));

    indexer.setDefaultCommand(new ShootWithIndexer(shooter, indexer, turret));

    SmartDashboard.putData("Reset position", Commands.runOnce(() -> {
      swerve.resetOdometry(Pose2d.kZero);
    }, swerve));
    turret.setDefaultCommand(
        Commands.sequence(new CalibrateTurret(turret), new DefaultTurretCommand(turret, fireControl)));
  }

  private void configureButtonBindings() {
    coDriver.START();
    SmartDashboard.putData(new ZeroTurret(turret));
    SmartDashboard.putData(new CalibrateTurret(turret));
    SmartDashboard.putData("Autos", autoChooser());

    driver.LT().whileTrue(Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));
    driver.DUp().whileTrue(CreateFixedShooterCommand(Rotation2d.fromDegrees(0), 900));
    // driver.LB().whileTrue(intake.extakeFuel());
    driver.RT().whileTrue(shootTestFuelCommand());
    driver.Y().onTrue(intake.putUpIntake());
    coDriver.DUp().whileTrue(intakePushFuel());
    coDriver.DDown().whileTrue(manualIntakeDownCommand());

    // Clear intake/indexer
    coDriver.LT().whileTrue(Commands.startEnd(() -> indexer.indexerBackward(), () -> indexer.stopIndexer(), indexer));
    coDriver.RB().whileTrue(Commands.startEnd(() -> intake.intakeBackward(), () -> intake.stopIntake(), intake));
    coDriver.RT().whileTrue(Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));

    // Set positions to shoot from if autos fail
    coDriver.A().whileTrue(CreateFixedShooterCommand(rightClimbAngle, rightClimbRPM));
    coDriver.B().whileTrue(CreateFixedShooterCommand(rightTrenchAngle, rightTrenchRPM));
    coDriver.X().whileTrue(CreateFixedShooterCommand(leftTrenchAngle, leftTrenchRPM));
    coDriver.Y().whileTrue(CreateFixedShooterCommand(leftClimbAngle, leftClimbRPM));
  }

  private Command CreateFixedShooterCommand(Rotation2d angle, double rpm) {
    return Commands.sequence(new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command shootTestFuelCommand() {
    return Commands.run(
        () -> {
          double targetRPM = fireControl.getShooterRpm();
          shooter.setShooterRPM(targetRPM, targetRPM);
        }, shooter).finallyDo(() -> shooter.stopShooterMotors());
  }

  public Command intakePushFuel() {
    return intake.run(() -> {
      intake.raiseIntakeToJostle();
      intake.intakeToJostle();
    }).finallyDo((() -> {
      intake.stopIntake();
      intake.stopLift();
    }));
  }

  public Command manualIntakeDownCommand() {
    return intake.run(() -> {
      intake.lowerIntakeManually();
    }).finallyDo(() -> {
      intake.stopLift();
    });

  }

  public Command leftBumpAutoCommand() {
    double rpm = 2300;
    Rotation2d angle = Rotation2d.fromDegrees(180);

    return Commands.sequence(new LadderPosition("left", swerve), new ResetOdometry("left", swerve),
        new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command rightBumpAutoCommand() {
    double rpm = 2300;
    Rotation2d angle = Rotation2d.fromDegrees(0);

    return Commands.sequence(new LadderPosition("right", swerve), new ResetOdometry("right", swerve),
        new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command rightTrenchAutoCommand() {
    double rpm = rightTrenchRPM;
    Rotation2d angle = rightTrenchAngle;

    return Commands.sequence(new ResetOdometry("right", swerve), new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command leftTrenchAutoCommand() {
    double rpm = leftTrenchRPM;
    Rotation2d angle = leftTrenchAngle;

    return Commands.sequence(new ResetOdometry("left", swerve), new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

}