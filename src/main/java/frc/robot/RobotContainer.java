// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.
package frc.robot;

import static edu.wpi.first.units.Units.MetersPerSecond;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.RotationsPerSecond;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.SwerveRequest;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.commands.FollowPathCommand;

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
import edu.wpi.first.wpilibj2.command.CommandScheduler;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.RobotModeTriggers;
import frc.lib.input.controllers.XboxControllerWrapper;
import frc.lib.swerve.TunerConstants;
import frc.robot.commands.CalibrateTurret;
import frc.robot.commands.DefaultTurretCommand;
import frc.robot.commands.FixedShooter;
import frc.robot.commands.BumpPosition;
import frc.robot.commands.TrenchPosition;
import frc.robot.commands.ShootWithIndexer;
import frc.robot.commands.ZeroTurret;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.FireControl;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Shooter;
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
  // Controllers
  public static final XboxControllerWrapper driver = new XboxControllerWrapper(0, 0.1);
  public static final XboxControllerWrapper coDriver = new XboxControllerWrapper(1, 0.1);

  private double MaxSpeed = 1.0 * TunerConstants.kSpeedAt12Volts.in(MetersPerSecond); // kSpeedAt12Volts desired top
                                                                                      // speed
  private double MaxAngularRate = RotationsPerSecond.of(0.75).in(RadiansPerSecond); // 3/4 of a rotation per second max
                                                                                    // angular velocity

  /* Setting up bindings for necessary control of the swerve drive platform */
  private final SwerveRequest.FieldCentric drive = new SwerveRequest.FieldCentric()
      .withDeadband(MaxSpeed * 0.1).withRotationalDeadband(MaxAngularRate * 0.1) // Add a 10% deadband
      .withDriveRequestType(DriveRequestType.OpenLoopVoltage); // Use open-loop control for drive motors

  private final Telemetry logger = new Telemetry(MaxSpeed);

  private final CommandXboxController joystick = new CommandXboxController(0);

  public static final CommandSwerveDrivetrain drivetrain = TunerConstants.createDrivetrain();
  public static final PowerDistribution powerDistribution = new PowerDistribution();
  public static final Vision vision = new Vision(
      (visionPose, timestamp, stdDevs) -> {
        drivetrain.addVisionMeasurement(
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
        return drivetrain.getState().Pose.transformBy(new Transform2d(
            Constants.Turret.ROBOT_TO_SHOOTER.getTranslation().toTranslation2d(),
            Constants.Turret.ROBOT_TO_SHOOTER.getRotation().toRotation2d()));
      },
      () -> DriverStation.getAlliance().orElse(Alliance.Blue),
      () -> new ChassisSpeeds());

  private final SendableChooser<Command> autoChooser;

  //Register Named PathPlanner Commands
  // NamedCommands.registerCommand("Shoot", );
  // NamedCommands.registerCommand("Collect Fuel");

  // Vision clients
  // public static final JetsonClient jetson = new JetsonClient();

  // private SendableChooser<Command> autoChooser() {
  // chooser = new SendableChooser<>();
  // chooser.addOption("rightTrench", rightTrenchAutoCommand());
  // chooser.addOption("leftTrench", leftTrenchAutoCommand());
  // chooser.addOption("rightBump", rightBumpAutoCommand());
  // chooser.addOption("leftBump", leftBumpAutoCommand());
  // return chooser;
  // }

  public RobotContainer() {
    autoChooser = AutoBuilder.buildAutoChooser("test");
    SmartDashboard.putData("Auto Mode", autoChooser);

    configureButtonBindings();

    SmartDashboard.putData("Reset Position", Commands.runOnce(() -> {
      drivetrain.resetPose(Pose2d.kZero);
    }, drivetrain));

    vision.addCamera("heart", Constants.Vision.robotToHeart);
    // vision.addCamera("club", Constants.Vision.robotToClub);
    // vision.addCamera("diamond", Constants.Vision.robotToDiamond);
    vision.addCamera("Arducam_OV9281_USB_Camera",
        Constants.Vision.robotToArudcam);

    // Warmup PathPlanner to avoid Java pauses
    CommandScheduler.getInstance().schedule(FollowPathCommand.warmupCommand());
  }

  private void configureButtonBindings() {
    coDriver.START();
    SmartDashboard.putData(new ZeroTurret(turret));
    SmartDashboard.putData(new CalibrateTurret(turret));
    // SmartDashboard.putData("Autos", autoChooser());

    indexer.setDefaultCommand(new ShootWithIndexer(shooter, indexer, turret));
    turret.setDefaultCommand(
        Commands.sequence(new CalibrateTurret(turret), new DefaultTurretCommand(turret, fireControl)));
    // Note that X is defined as forward according to WPILib convention,
    // and Y is defined as to the left according to WPILib convention.
    drivetrain.setDefaultCommand(
        // Drivetrain will execute this command periodically
        drivetrain.applyRequest(() -> drive.withVelocityX(-joystick.getLeftY() * MaxSpeed) // Drive forward with negative Y (forward)
            .withVelocityY(-joystick.getLeftX() * MaxSpeed) // Drive left with negative X (left)
            .withRotationalRate(-joystick.getRightX() * MaxAngularRate) // Drive counterclockwise with negative X (left)
        ));
    // Idle while the robot is disabled. This ensures the configured
    // neutral mode is applied to the drive motors while disabled.
    final var idle = new SwerveRequest.Idle();
    RobotModeTriggers.disabled().whileTrue(
        drivetrain.applyRequest(() -> idle).ignoringDisable(true));

    driver.LT().whileTrue(Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));
    driver.DUp().whileTrue(CreateFixedShooterCommand(Rotation2d.fromDegrees(0), 900));
    // driver.LB().whileTrue(intake.extakeFuel());
    driver.RT().whileTrue(shootTestFuelCommand());
    driver.Y().onTrue(intake.putUpIntake());

    coDriver.DUp().whileTrue(intakePushFuel());
    coDriver.DDown().whileTrue(manualIntakeDownCommand());
    coDriver.LT().whileTrue(Commands.startEnd(() -> indexer.indexerBackward(), () -> indexer.stopIndexer(), indexer));
    coDriver.RB().whileTrue(Commands.startEnd(() -> intake.intakeBackward(), () -> intake.stopIntake(), intake));
    coDriver.RT().whileTrue(Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));

    // Set positions to shoot from if autos fail
    coDriver.A().whileTrue(CreateFixedShooterCommand(rightClimbAngle, rightClimbRPM));
    coDriver.B().whileTrue(CreateFixedShooterCommand(rightTrenchAngle, rightTrenchRPM));
    coDriver.X().whileTrue(CreateFixedShooterCommand(leftTrenchAngle, leftTrenchRPM));
    coDriver.Y().whileTrue(CreateFixedShooterCommand(leftClimbAngle, leftClimbRPM));

    drivetrain.registerTelemetry(logger::telemeterize);
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

    return Commands.sequence(new BumpPosition("left", drivetrain),
        new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command rightBumpAutoCommand() {
    double rpm = 2300;
    Rotation2d angle = Rotation2d.fromDegrees(0);

    return Commands.sequence(new BumpPosition("right", drivetrain),
        new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command rightTrenchAutoCommand() {
    double rpm = rightTrenchRPM;
    Rotation2d angle = rightTrenchAngle;

    return Commands.sequence(new TrenchPosition("right", drivetrain), new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command leftTrenchAutoCommand() {
    double rpm = leftTrenchRPM;
    Rotation2d angle = leftTrenchAngle;

    return Commands.sequence(new TrenchPosition("left", drivetrain), new CalibrateTurret(turret),
        new FixedShooter(shooter, turret, rpm, angle).finallyDo(() -> shooter.stopShooterMotors()));
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}