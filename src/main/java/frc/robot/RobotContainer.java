// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.
package frc.robot;

import static edu.wpi.first.units.Units.MetersPerSecond;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.RotationsPerSecond;

import java.util.function.Supplier;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.SwerveRequest;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
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
import frc.robot.commands.VisionCalibrationCommand;
import frc.robot.commands.VisionTransformEstimationCommand;
import frc.robot.commands.CalibrationPointsValidationCommand;
import frc.robot.commands.MultiPointVisionCalibrationCommand;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.FireControl;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Turret;
import frc.robot.subsystems.Vision;
import frc.robot.subsystems.VisionCalibrationEngine;
import frc.robot.util.CalibrationPointsLoader;
import frc.robot.util.RobotPoseLookup;

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
  public static final Supplier<Pose2d> robotPose = () -> drivetrain.getState().Pose;
  
  // Flag to disable vision updates during calibration
  // When true, vision measurements are NOT applied to the drivetrain
  // This ensures clean odometry-only testing during vision calibration
  public static boolean disableVisionUpdates = false;
  
  public static final Vision vision = new Vision(
      (visionPose, timestamp, stdDevs) -> {
        // Skip vision updates if calibration is in progress
        if (!disableVisionUpdates) {
          drivetrain.addVisionMeasurement(
              visionPose,
              timestamp,
              VecBuilder.fill(stdDevs.get(0, 0), stdDevs.get(1, 0), stdDevs.get(2, 0)));
        }
      }, robotPose);

  // Vision calibration engine - provides automated calibration and Kalman filter tuning
  public static final VisionCalibrationEngine visionCalibrationEngine = 
      new VisionCalibrationEngine(drivetrain, vision);

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
    autoChooser = AutoBuilder.buildAutoChooser();
    SmartDashboard.putData("Auto Mode", autoChooser);
    
  // Register Named PathPlanner Commands
  NamedCommands.registerCommand("Shoot", CreateFixedShooterCommand(fireControl.getCurrentTarget(), fireControl.getShooterRpm()));
  NamedCommands.registerCommand("Collect Fuel", Commands.sequence(intake.putDownIntake(), intake.intakeFuel()));


    configureButtonBindings();

    SmartDashboard.putData("Reset Position", Commands.runOnce(() -> {
      drivetrain.resetPose(Pose2d.kZero);
    }, drivetrain));

    vision.addCamera("heart", Constants.Vision.robotToHeart);
    vision.addCamera("club", Constants.Vision.robotToClub);
    vision.addCamera("diamond", Constants.Vision.robotToDiamond);
    vision.addCamera("spade", Constants.Vision.robotToSpade);

    // Configure multi-camera calibration and testing (NEW)
    configureMultiCameraCalibration();

    // Warmup PathPlanner to avoid Java pauses
    CommandScheduler.getInstance().schedule(FollowPathCommand.warmupCommand());
  }

  private void configureButtonBindings() {
    coDriver.START();
    SmartDashboard.putData(new ZeroTurret(turret));
    SmartDashboard.putData(new CalibrateTurret(turret));
    // SmartDashboard.putData("Autos", autoChooser());

    indexer.setDefaultCommand(new ShootWithIndexer(shooter, indexer, turret));
    // turret.setDefaultCommand(
    //     Commands.sequence(new CalibrateTurret(turret), new DefaultTurretCommand(turret, fireControl)));
    
    // Note that X is defined as forward according to WPILib convention,
    // and Y is defined as to the left according to WPILib convention.
    drivetrain.setDefaultCommand(
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

  /**
   * Configure multi-camera calibration and testing buttons for vision system tuning.
   * These buttons dynamically work with whatever cameras are configured in the Vision subsystem,
   * eliminating the need to hardcode camera names in multiple places.
   */
  private void configureMultiCameraCalibration() {
    // Load calibration points from calibration_points.csv
    CalibrationPointsLoader calibrationPoints = new CalibrationPointsLoader();
    
    // Get the initial position (where robot should be placed)
    // This is the marked position from calibration_points.csv with initial_position=true
    Pose2d initialCalibrationPose;
    try {
      initialCalibrationPose = calibrationPoints.getInitialPosition();
    } catch (Exception e) {
      // Fallback to blue corner if calibration points fail to load
      System.err.println("Failed to load calibration points: " + e.getMessage());
      initialCalibrationPose = new Pose2d(1.5, 1.5, Rotation2d.kZero);
    }
    
    // ===== PHASE 1: TRANSFORM ESTIMATION =====
    // Vision transform estimation command - estimates camera mount errors
    // Operator places robot at marked position, command collects data and analyzes
    // Produces SmartDashboard values for camera transform corrections
    SmartDashboard.putData("Vision/Cal/Phase1_EstimateTransforms",
        new VisionTransformEstimationCommand(
            drivetrain, 
            visionCalibrationEngine,
            initialCalibrationPose
        ));
    
    // ===== PHASE 2: STANDARD DEVIATION ESTIMATION =====
    // (Using renamed "Single Point" command - now Phase 2 of two-phase approach)
    // After operator manually updates Constants.Vision with transform corrections
    // and recompiles, this command estimates measurement std devs
    SmartDashboard.putData("Vision/Cal/Phase2_EstimateStdDevs",
        new VisionCalibrationCommand(
            drivetrain, 
            visionCalibrationEngine,
            initialCalibrationPose
        ));

    // ===== MULTI-POINT CALIBRATION =====
    // Load all calibration points (including the initial position)
    java.util.List<Pose2d> allCalibrationPoints;
    try {
      allCalibrationPoints = calibrationPoints.getAllCalibrationPoints();
    } catch (Exception e) {
      // Fallback to single point if loading fails
      System.err.println("Failed to load all calibration points: " + e.getMessage());
      allCalibrationPoints = java.util.List.of(initialCalibrationPose);
    }
    
    // Multi-point calibration command - navigates between calibration points automatically
    // Operator confirms arrival at each point; robot calibrates then moves to next
    MultiPointVisionCalibrationCommand multiPointCalibrationCommand = 
        new MultiPointVisionCalibrationCommand(
            drivetrain,
            visionCalibrationEngine,
            allCalibrationPoints
        );
    
    SmartDashboard.putData("Vision/Calibration/Execute (Multi-Point Grid)",
        multiPointCalibrationCommand);
    
    // Add button for operator confirmation during multi-point calibration
    // Call this to reset odometry to current position and start calibration spiral
    // IMPORTANT: Operator must verify robot is at correct marked position before clicking
    // (typically within ±0.1m and ±3 degrees). Odometry will be reset to this known position.
    // This is the critical step that ensures the calibration has an accurate reference frame.
    SmartDashboard.putData("Vision/Calibration/Start This Point",
        Commands.runOnce(() -> {
          multiPointCalibrationCommand.startCalibrationAtCurrentPoint();
        }));

    // Calibration points validation command
    // Drives robot through each calibration point in calibration_points.csv
    // Allows operator to verify field setup and robot navigation before running full calibration
    SmartDashboard.putData("Vision/Calibration/Validate Points",
        new CalibrationPointsValidationCommand(drivetrain));
    
    // SmartDashboard button to advance to next point during validation
    SmartDashboard.putBoolean("CalibrationValidation/NextPoint", false);

    // Display SmartDashboard info on all configured cameras (dynamically)
    SmartDashboard.putData("Vision/Show All Cameras",
        Commands.runOnce(() -> {
          for (String cameraName : vision.getCameraNames()) {
            var measurement = vision.getCameraMeasurement(cameraName);
            if (measurement.isPresent()) {
              var m = measurement.get();
              SmartDashboard.putString("Vision/" + cameraName + "/DetailedInfo", m.toString());
            } else {
              SmartDashboard.putString("Vision/" + cameraName + "/DetailedInfo", 
                  cameraName + ": [No measurement this frame]");
            }
          }
          SmartDashboard.putString("Vision/CamerasRefreshed", 
              "✓ All " + vision.getCameraNames().size() + " cameras displayed");
        }));

    // Display current camera configuration
    SmartDashboard.putData("Vision/Config/Refresh",
        Commands.runOnce(() -> {
          var cameraNames = vision.getCameraNames();
          SmartDashboard.putNumber("Vision/NumEnabledCameras", cameraNames.size());
          SmartDashboard.putStringArray("Vision/EnabledCameras", cameraNames.toArray(new String[0]));
          SmartDashboard.putString("Vision/ConfigStatus", 
              "Enabled cameras: " + String.join(", ", cameraNames));
        }));

    // Per-camera calibration status board (dynamically populated)
    SmartDashboard.putData("Vision/Calibration/Show Results",
        Commands.runOnce(() -> {
          SmartDashboard.putString("Vision/CalibrationStatus/Title", 
              "=== Per-Camera Calibration Results ===");
          
          // Dynamically publish results for all configured cameras
          for (String cameraName : vision.getCameraNames()) {
            String path = "Vision/CalibrationStatus/" + cameraName;
            SmartDashboard.putString(path, 
                "Camera '" + cameraName + "': Check VisionCalibration/" + cameraName + "/* keys");
          }
          
          SmartDashboard.putString("Vision/CalibrationStatus/Instructions", 
              "Review per-camera calibration data: Vision/Cameras/* paths show live data\n"
              + "Calibration results: VisionCalibration/* paths show calibration session results");
        }));

    // Guidance on multi-camera workflow
    SmartDashboard.putString("Vision/WorkflowGuide", 
        "Multi-Camera Workflow:\n"
        + "1. Check Vision/Cameras/* for live camera data\n"
        + "2. Run calibration command\n"
        + "3. Review per-camera errors: VisionCalibration/[camera]/MeanXError, etc\n"
        + "4. If any camera error > 0.15m:\n"
        + "   - Edit Constants.Vision.robotTo[Camera]\n"
        + "   - Redeploy\n"
        + "   - Recalibrate\n"
        + "5. All cameras < 0.15m? Done!");
    
    // Guidance for multi-point calibration workflow
    SmartDashboard.putString("Vision/MultiPointWorkflow",
        "Multi-Point Calibration Workflow:\n"
        + "1. Place robot at first marked position on field (within ±5 inches accuracy)\n"
        + "2. Click 'Vision/Calibration/Execute (Multi-Point Grid)' to start command\n"
        + "3. Command waits at current position - verify robot is correctly placed\n"
        + "4. When ready, click 'Vision/Calibration/Start This Point'\n"
        + "   - This resets odometry to current position (critical!)\n"
        + "   - Robot executes 30-second spiral calibration\n"
        + "5. After spiral completes, robot autonomously drives to next point\n"
        + "6. Move robot to next marked position (place it manually)\n"
        + "7. Click 'Vision/Calibration/Start This Point' again\n"
        + "   - Odometry resets to current position\n"
        + "   - Robot calibrates at this point\n"
        + "8. Repeat steps 5-7 for all remaining calibration points\n"
        + "NOTE: Manual placement + operator verification ensures accurate calibration!");
  }

  public Command getAutonomousCommand() {
    return autoChooser.getSelected();
  }
}