// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.
package frc.robot;

import static edu.wpi.first.units.Units.MetersPerSecond;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.RotationsPerSecond;

import java.security.CodeSigner;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import com.ctre.phoenix6.swerve.SwerveRequest;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import com.pathplanner.lib.commands.FollowPathCommand;

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
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.RobotModeTriggers;
import frc.lib.input.controllers.XboxControllerWrapper;
import frc.lib.swerve.TunerConstants;
import frc.lib.util.DriveModes;
import frc.lib.util.DriveModes.Modes;
import frc.lib.util.RobotLogger;
import frc.robot.commands.FixedShooter;
import frc.robot.commands.ShootWithIndexer;
import frc.robot.commands.ShooterCommand;
import frc.robot.commands.ShooterSpeedAdjustment;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.FireControl;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.PowerManagement;
import frc.robot.subsystems.Shooter;
// import frc.robot.subsystems.Turret;
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

  // Controllers
  public static final XboxControllerWrapper driver = new XboxControllerWrapper(0, 0.1);
  public static final XboxControllerWrapper coDriver = new XboxControllerWrapper(1, 0.1);
  public static final XboxControllerWrapper logController = new XboxControllerWrapper(2,0.1);

  private double MaxSpeed = 0.75 * TunerConstants.kSpeedAt12Volts.in(MetersPerSecond); // kSpeedAt12Volts desired top speed
  private double MaxAngularRate = RotationsPerSecond.of(0.75).in(RadiansPerSecond); // 3/4 of a rotation per second max angular velocity

  private final Telemetry logger = new Telemetry(MaxSpeed);

  private final CommandXboxController joystick = new CommandXboxController(0);
  public static final CommandSwerveDrivetrain drivetrain = TunerConstants.createDrivetrain();
  public static final PowerDistribution powerDistribution = new PowerDistribution();
  public static final Supplier<Pose2d> robotPose = () -> drivetrain.getState().Pose;
  public static final Vision vision = new Vision(
      (visionPose, timestamp, stdDevs) -> {
        // drivetrain.addVisionMeasurement(
        //     visionPose,
        //     timestamp,
        //     VecBuilder.fill(stdDevs.get(0, 0), stdDevs.get(1, 0), stdDevs.get(2, 0)));
      }, robotPose);

  public static final Intake intake = new Intake(Constants.Intake.INTAKE_LIFT_MOTOR_ID,
      Constants.Intake.LEFT_INTAKE_MOTOR_ID, Constants.Intake.RIGHT_INTAKE_MOTOR_ID);

  public static final Indexer indexer = new Indexer(Constants.Indexer.INDEXER_MOTOR_ID, Constants.Indexer.AGITATOR_MOTOR_ID);

  // public static final Turret turret = new Turret("Turret", Constants.Turret.TURRET_MOTOR_ID,
  //     Constants.Turret.Turret_HALL_EFFECT_ID, null); // TODO obtain transform from robot to turret

  public static final Shooter shooter = new Shooter(
      Constants.Shooter.TOP_LEFT_SHOOTER_ID,
      Constants.Shooter.BOTTOM_LEFT_SHOOTER_ID,
      Constants.Shooter.TOP_RIGHT_SHOOTER_ID,
      Constants.Shooter.BOTTOM_RIGHT_SHOOTER_ID,
      Constants.Shooter.TRANSFER_SHOOTER_ID);

  public static final FireControl fireControl = new FireControl(
      () -> {
        return drivetrain.getState().Pose.transformBy(new Transform2d(
            Constants.Turret.ROBOT_TO_SHOOTER.getTranslation().toTranslation2d(),
            Constants.Turret.ROBOT_TO_SHOOTER.getRotation().toRotation2d()));
      },
      () -> DriverStation.getAlliance().orElse(Alliance.Blue),
      () -> new ChassisSpeeds());
      
  private final DriveModes driveMode = new DriveModes(joystick, drivetrain, fireControl, MaxSpeed, MaxAngularRate);
  public static final PowerManagement powerManagement = new PowerManagement(false);
  private final SendableChooser<Command> autoChooser;
  public static final RobotLogger robotLogger = new RobotLogger(shooter, fireControl, drivetrain);
  // private SendableChooser<Command> autoChooser() {
  // chooser = new SendableChooser<>();
  // chooser.addOption("rightTrench", rightTrenchAutoCommand());
  // chooser.addOption("leftTrench", leftTrenchAutoCommand());
  // chooser.addOption("rightBump", rightBumpAutoCommand());
  // chooser.addOption("leftBump", leftBumpAutoCommand());
  // return chooser;
  // }

  public RobotContainer() {
    configureButtonBindings();
    // Register Named PathPlanner Commands
    NamedCommands.registerCommand("Shoot", new ShooterCommand(shooter, 3300.0));
    NamedCommands.registerCommand("Collect Fuel", Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));
    NamedCommands.registerCommand("Intake Push", intakePushFuel());
    NamedCommands.registerCommand("Extake", Commands.startEnd(() -> intake.intakeBackward(), () -> intake.stopIntake(), intake));
    //NamedCommands.registerCommand("Jostle", manualIntakeDownCommand());
    NamedCommands.registerCommand("Auto Jostle", autoJostleCommand()); //maybe use for regular shooting

    autoChooser = AutoBuilder.buildAutoChooser();
    SmartDashboard.putData("Auto Mode", autoChooser);
    
    powerManagement.addSubsystem(shooter);
    powerManagement.addSubsystem(indexer);
    powerManagement.addSubsystem(intake);
    // powerManagement.addSubsystem(turret);

    SmartDashboard.putData("Reset Position", Commands.runOnce(() -> {
      drivetrain.resetPose(Pose2d.kZero);
    }, drivetrain));

    vision.addCamera("heart", Constants.Vision.robotToHeart);
    vision.addCamera("club", Constants.Vision.robotToClub);
    vision.addCamera("diamond", Constants.Vision.robotToDiamond);
    // vision.addCamera("spade", Constants.Vision.robotToSpade);

    CommandScheduler.getInstance().schedule(FollowPathCommand.warmupCommand());
  }

  private void configureButtonBindings() {
    ShooterSpeedAdjustment shooterAdjust = new ShooterSpeedAdjustment(shooter, logController);
    coDriver.START();
    indexer.setDefaultCommand(new ShootWithIndexer(shooter, indexer));
    // turret.setDefaultCommand(
    //     Commands.sequence(new CalibrateTurret(turret), new DefaultTurretCommand(turret,fireControl)));
    driveMode.setMode(Modes.NORMAL_JOYSTICK);
    drivetrain.setDefaultCommand(
        drivetrain.applyRequest(() -> driveMode.getDriveRequest()
        ));
    
    // SmartDashboard.putData(new ZeroTurret(turret));
    // SmartDashboard.putData(new CalibrateTurret(turret));

    // Idle while the robot is disabled. This ensures the configured
    // neutral mode is applied to the drive motors while disabled.
    final var idle = new SwerveRequest.Idle();
    RobotModeTriggers.disabled().whileTrue(
        drivetrain.applyRequest(() -> idle).ignoringDisable(true));

    driver.LT().whileTrue(Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));
    // driver.RT().whileTrue(new ShooterCommand(shooter, 2950.0));

    driver.RT().onTrue(Commands.sequence(Commands.runOnce(() -> driveMode.setMode(Modes.TESTING)), shooterAdjust));
    driver.RT().onFalse(Commands.sequence(Commands.runOnce(() -> shooterAdjust.cancelShooterAdjust()), Commands.runOnce(() -> driveMode.setMode(Modes.NORMAL_JOYSTICK))));
    driver.LB().whileTrue(intakePushFuel());
    // driver.Y().whileTrue(intake.putUpIntake());
    // driver.A().whileTrue(indexer.shootFuel());
    
    coDriver.B().whileTrue(intakePushFuel());
    coDriver.A().whileTrue(manualIntakeDownCommand());
    coDriver.X().onTrue(Commands.runOnce(() -> {robotLogger.logSnapshot();}));
    coDriver.DUp().onTrue(
    Commands.runOnce(() -> shooterAdjust.adjustRPM(shooterAdjust.getIncrement()))
    );
    coDriver.DDown().onTrue(
        Commands.runOnce(() -> shooterAdjust.adjustRPM(-shooterAdjust.getIncrement()))
    );

    coDriver.LT().whileTrue(Commands.startEnd(() -> indexer.indexerBackward(), () -> indexer.stopIndexer(), indexer));
    coDriver.RT().whileTrue(Commands.startEnd(() -> intake.intakeBackward(), () -> intake.stopIntake(), intake));
    
    // coDriver.RT().whileTrue(Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));
    // Set positions to shoot from if autos fail
    // coDriver.A().whileTrue(CreateFixedShooterCommand(() -> rightClimbAngle, () -> rightClimbRPM));
    // coDriver.B().whileTrue(CreateFixedShooterCommand(() -> rightTrenchAngle, () -> rightTrenchRPM));
    // coDriver.X().whileTrue(CreateFixedShooterCommand(() -> leftTrenchAngle, () -> leftTrenchRPM));
    // coDriver.Y().whileTrue(CreateFixedShooterCommand(() -> leftClimbAngle,() -> leftClimbRPM));
    
    // logController.DUp().onTrue(
    // Commands.runOnce(() -> shooterAdjust.adjustRPM(shooterAdjust.getIncrement()))
    // );

    // logController.DDown().onTrue(
    //     Commands.runOnce(() -> shooterAdjust.adjustRPM(-shooterAdjust.getIncrement()))
    // );

    // logController.A().onTrue(Commands.runOnce(() -> {robotLogger.logSnapshot();}));
    // logController.B().onTrue(Commands.sequence(Commands.runOnce(() -> driveMode.setMode(Modes.TESTING)), shooterAdjust));
    // logController.Y().onTrue(Commands.sequence(Commands.runOnce(() -> shooterAdjust.cancelShooterAdjust()), Commands.runOnce(() -> driveMode.setMode(Modes.NORMAL_JOYSTICK))));
    // logController.DLeft().onTrue(Commands.runOnce(() -> driveMode.setTestingTargetOffset(-10.0), vision));
    // logController.DRight().onTrue(Commands.runOnce(() -> driveMode.setTestingTargetOffset(10.0), vision));
    
    drivetrain.registerTelemetry(logger::telemeterize);
  }

  private Command CreateFixedShooterCommand(java.util.function.Supplier<Rotation2d> turretAngle, DoubleSupplier targetRPM) {
    return Commands.sequence(
        new FixedShooter(shooter, targetRPM, turretAngle).finallyDo(() -> shooter.stopMotors()));
  }

//   private Command shooterCommand() {
//     return Commands.run(
//         () -> {
//           double rpm = 2750; // test value
//           shooter.setShooterRPM(rpm);
//         }, shooter).finallyDo(() -> shooter.stopMotors());
// }

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
      intake.intakeToJostle();
    }).finallyDo(() -> {
      intake.stopLift();
      intake.stopIntake();
    });
  }

  public Command autoJostleCommand() {
    return Commands.sequence(intakePushFuel().withTimeout(0.2),
     manualIntakeDownCommand().withTimeout(0.5), 
     intakePushFuel().withTimeout(0.2),
     manualIntakeDownCommand().withTimeout(0.5),
     intakePushFuel().withTimeout(0.5),
     manualIntakeDownCommand().withTimeout(0.75),
     intakePushFuel().withTimeout(0.75),
     manualIntakeDownCommand().withTimeout(1.0),
     intakePushFuel().withTimeout(2.0));
  }


  // public Command leftBumpAutoCommand() {
  //   double rpm = 2300;
  //   Rotation2d angle = Rotation2d.fromDegrees(180);

  //   return Commands.sequence(new BumpPosition("left", drivetrain),
  //       new CalibrateTurret(turret),
  //       new FixedShooter(shooter, turret, () -> rpm, () -> angle).finallyDo(() -> shooter.stopShooterMotors()));
  // }

  // public Command rightBumpAutoCommand() {
  //   double rpm = 2300;
  //   Rotation2d angle = Rotation2d.fromDegrees(0);

  //   return Commands.sequence(new BumpPosition("right", drivetrain),
  //       new CalibrateTurret(turret),
  //       new FixedShooter(shooter, turret, () -> rpm, () -> angle).finallyDo(() -> shooter.stopShooterMotors()));
  // }

  // public Command rightTrenchAutoCommand() {
  //   double rpm = rightTrenchRPM;
  //   Rotation2d angle = rightTrenchAngle;

  //   return Commands.sequence(new TrenchPosition("right", drivetrain), new CalibrateTurret(turret),
  //       new FixedShooter(shooter, turret, () -> rpm, () -> angle).finallyDo(() -> shooter.stopShooterMotors()));
  // }

  // public Command leftTrenchAutoCommand() {
  //   double rpm = leftTrenchRPM;
  //   Rotation2d angle = leftTrenchAngle;

  //   return Commands.sequence(new TrenchPosition("left", drivetrain), new CalibrateTurret(turret),
  //       new FixedShooter(shooter, turret, () -> rpm, () -> angle).finallyDo(() -> shooter.stopShooterMotors()));
  // }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}