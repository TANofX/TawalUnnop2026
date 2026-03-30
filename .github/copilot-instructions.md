# Copilot Instructions for TawalUnnop2026 FRC Robot

## Project Overview
This is a **2026 FIRST Robotics Competition (FRC)** robot codebase using **WPILib's command-based programming model**. The robot is a swerve drive system with multi-subsystem gameplay mechanisms (shooter, intake, turret, indexer). The team targets competition on the 2026 game (April 2026).

**Key Technologies**: Java 17, WPILib 2026, CTRE Phoenix 6, REV Robotics (SparkFlex), PathPlanner for autonomous, AprilTag vision.

## Architecture & Data Flow

### Core Subsystems (in `src/main/java/frc/robot/subsystems/`)
- **CommandSwerveDrivetrain**: CTRE's swerve drive wrapper (MK4 Pro modules) - manages odometry, field-relative driving, PathPlanner integration
- **FireControl**: Shoots at alliance targets using shooter RPM interpolation from `distance_rpm.csv`; selects hub or feed targets based on alliance/field zone
- **Turret**: Rotates shooter using motor + hall-effect homing
- **Shooter**: Manages 4 shooter motor RPMs (top/bottom left/right)
- **Intake**: Dual motors for lift and fuel intake
- **Indexer**: Single motor to feed fuel to shooter
- **Vision**: Multi-camera AprilTag/target detection using PhotonLib; fuses into pose estimator
- **VisionCalibrationEngine**: (Calibration-only subsystem) Performs automated vision system calibration by collecting odometry vs. vision data, computing camera transform corrections, detecting time offsets, and estimating measurement std devs

### RobotContainer & Control Flow
**Location**: `src/main/java/frc/robot/RobotContainer.java`

Static subsystem instances initialized here; button bindings map Xbox controllers (driver/coDriver, deadband 0.1) to commands. Key patterns:
- **Field Coordinate System**: Swerve uses field-relative drive by default; X = forward (blue alliance), Y = left
- **Vision Fusion**: Vision measurements automatically added to `drivetrain.addVisionMeasurement()` with configurable std devs
- **Named Commands for Autos**: PathPlanner autos trigger named commands registered via `NamedCommands.registerCommand()`
- **Static Access Pattern**: All subsystems are public static for easy access (e.g., `RobotContainer.shooter`, `RobotContainer.turret`)

### Constants Management
**Location**: `src/main/java/frc/robot/Constants.java` (403 lines)

- **@CanId Annotation**: CAN IDs tracked to prevent duplicates in unit tests
- **CARNIVORE_BUS_NAME**: CAN bus "Sonic" for Phoenix 6 devices
- **Field Geometry**: AprilTag-based positions (HUB_RED, HUB_BLUE, feed points), alliance zones, bump zones
- **Transforms**: Robot-to-camera, robot-to-shooter (3D transforms for calibration)
- **PID/Motion Constants**: Swerve path-following (translation/rotation PID), teleop constraints, shooter/turret tuning
- **Subsystem IDs**: Motor CAN IDs, hall-effect pins, encoder IDs organized by subsystem

## Critical Developer Workflows

### Building & Deploying
```bash
# Build project
./gradlew build

# Deploy to RoboRIO
./gradlew deploy

# Simulate locally
./gradlew simulateJava
```

**Team Number**: Auto-loaded from `.wpilib/wpilib_preferences.json`; set via WPILib extension.

### Testing
**Test Framework**: JUnit 5 (Jupiter)
- **Location**: `src/test/java/frc/robot/subsystems/FireControlTest.java` (9 tests)
- **Example Pattern**: `@Test void testGetCurrentTarget_blue_hub() { ... }`
- Run tests: `./gradlew test`
- Tests validate FireControl target-finding logic; distance interpolation from CSV

### Debugging on RoboRIO
- **Data Logging**: `DataLogManager.start()` in `Robot` constructor auto-logs all telemetry
- **SmartDashboard**: Published via `SmartDashboard.putData()` or networktables (swerve module states, system checks)
- **Phoenix Tuning**: TalonFX/CANCoder configs via Phoenix Tuner X GUI; constants imported from configs

### Hardware Calibration
1. **Swerve Module Zeroing**: Run "Trim Modules" command before match (updates absolute encoder offsets)
2. **Turret Homing**: `CalibrateTurret` command triggers hall-effect sensor to find zero position
3. **Vision Camera Mount**: Transform3d constants in `Constants.Vision` define camera pose relative to robot center

## Project-Specific Patterns & Conventions

### Custom Library Classes (in `src/main/java/frc/lib/`)
- **AdvancedSubsystem**: Base class extending SubsystemBase with hardware health monitoring and `systemCheckCommand()` 
  - Tracks faults, registers hardware (motors, encoders, gyros) for automatic self-checks
  - Used by Swerve, Turret, Intake, Indexer
- **Mk4SwerveModulePro**: Wraps 4 swerve modules (FL, FR, BL, BR) with module-specific drive/steer velocities
- **XboxControllerWrapper**: Adds deadband filtering and button helper methods
- **RobotPoseLookup**: Caches historical robot poses for replay/debugging

### Command Pattern
- Uses **WPILib Command factories** (e.g., `Commands.sequence()`, `Commands.startEnd()`) over traditional Command class inheritance
- Example: `ShootWithIndexer` is a default command that continuously runs when no override exists
- Subsystem requirements enforced via `addRequirements(subsystem)` to prevent concurrent motor conflicts

### SmartDashboard Integration
- **Auto Chooser**: PathPlanner autos auto-selected via `AutoBuilder.buildAutoChooser()`
- **Field Visualization**: `Field2d` shows robot pose and planned paths in real-time
- **System Checks**: One-button diagnostics for each subsystem health via `SmartDashboard.putData()`

## Integration Points & External Dependencies

### Vendor Libraries (in `vendordeps/`)
- **Phoenix 6**: TalonFX drive motors, CANCoder absolute encoders, Pigeon2 IMU on CARNIVORE bus
- **REV Robotics**: SparkFlex for shooter motors (sparkflex behavior configured in code)
- **PathPlanner**: Autonomous path definitions in JSON; integrates with WPILib's `AutoBuilder` for smooth trajectory following
- **PhotonLib**: Multi-camera AprilTag detection; fused into pose estimator with tunable std devs

### Deploy Files (in `src/main/deploy/`)
- **distance_rpm.csv**: Interpolation table mapping shooter distance (meters) to required RPM; loaded by `FireControl.readCsv()`
- **pathplanner/**: Auto path JSON definitions and field settings

## Vision Calibration System (Pre-Match Diagnostics)

**Location**: `subsystems/VisionCalibrationEngine.java`, `commands/VisionCalibrationCommand.java`, `docs/VISION_CALIBRATION_SYSTEM.md`

This optional calibration-only subsystem addresses vision "fighting" by:
1. Collecting odometry vs. vision pose pairs during bounded robot motion
2. Computing recommended Transform3d corrections for each camera
3. Detecting time offsets between PhotonVision and RoboRIO
4. Estimating optimal Kalman filter measurement std devs from observed error distributions
5. Identifying problematic cameras or field zones

**Usage** (calibration branch only):
```java
// In RobotContainer during calibration:
SmartDashboard.putData("Vision Cal", 
    new VisionCalibrationCommand(drivetrain, calibrationEngine, knownPose));
```

**Typical workflow**:
1. Place robot at marked position on field (±5 inches accuracy acceptable)
2. Press "Vision Cal" button
3. Robot executes 30-second spiral motion (~150 data points)
4. Review SmartDashboard: MeanXError, MeanYError, RecommendedStdDevs
5. Apply transform corrections to Constants.Vision

**Do NOT enable during match**. This is exclusively for pre-match diagnostics.

## Common Gotchas & Best Practices

1. **Alliance Detection**: Always check `DriverStation.getAlliance()` before fire control logic; defaults to Blue if not set
2. **Pose Reversals for Red**: `goToPoseCommand()` auto-mirrors paths for red alliance via PathPlanner configuration
3. **Vision Std Dev Tuning**: After calibration, use engine results; if not calibrated, `visionStdDevs` uses high Z value (999999) to ignore AprilTag yaw
4. **CAN Bus Saturation**: Limit StatusSignal refresh rates on CARNIVORE bus; high-frequency modules may cause timeouts
5. **Motor Safety**: Always call `.stop()` methods in command `end()` blocks to prevent accidental runaway
6. **Test Isolation**: `FireControlTest` uses supplier lambdas for dependency injection; mock pose/alliance for controlled tests
7. **Vision Calibration**: Run calibration at multiple field positions to account for zone-specific camera errors

## File Navigation Reference
- **Core Robot Logic**: `Robot.java`, `RobotContainer.java`
- **Subsystems**: `subsystems/` directory (all extend `AdvancedSubsystem` or `SubsystemBase`)
- **Commands**: `commands/` directory; most use command factory pattern
- **Configuration**: `Constants.java`, `TunerConstants.java` (auto-generated by Phoenix tuner)
- **Utilities**: `util/RobotPoseLookup.java`, `Telemetry.java`
- **Tests**: `src/test/java/frc/robot/subsystems/FireControlTest.java`
- **Calibration**: `docs/VISION_CALIBRATION_SYSTEM.md` (comprehensive guide)

## Questions Before Modifying?
Before making changes, clarify:
- Is this a subsystem change (requires updating `AdvancedSubsystem` health checks)?
- Does it affect autonomous (requires PathPlanner path updates)?
- Does it require new CAN IDs (check `Constants.java` @CanId annotations for conflicts)?
- Is telemetry needed (add SmartDashboard entries in `RobotContainer` or subsystem `periodic()`)?
- Does this touch vision? (Check calibration engine and std dev tuning in Constants.Vision)
