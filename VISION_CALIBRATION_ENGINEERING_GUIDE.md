# Vision Calibration System: Engineering Guide

## Architecture Overview

### Core Components

**VisionCalibrationEngine** (`subsystems/VisionCalibrationEngine.java`)
- Main calibration logic
- Data collection and filtering
- Transform estimation via least-squares fitting
- Std dev computation from residual errors

**VisionTransformEstimationCommand** (`commands/VisionTransformEstimationCommand.java`)
- Phase 1 command (transform estimation)
- Executes expanding spiral motion
- Manages calibration state machine

**VisionCalibrationCommand** (`commands/VisionCalibrationCommand.java`)
- Phase 2 command (std dev estimation)
- Executes spiral with corrected transforms from Constants

**CalibrationPointsValidationCommand** (`commands/CalibrationPointsValidationCommand.java`)
- Pre-calibration diagnostic command
- Loads calibration points from `calibration_points.csv`
- Drives robot through each point using proportional control
- Allows operator to verify field setup before running full calibration
- No data collection or calibration occurs (navigation only)

**Constants.java** (`Constants.java` lines 173-210)
- Robot-to-camera transforms (robotToHeart, robotToDiamond, robotToClub, robotToSpade)
- Vision measurement std devs (singleTagStdDevs, multiTagStdDevs)
- Calibration parameters and thresholds

**Vision.java** (`subsystems/Vision.java`)
- PhotonVision integration
- Loads camera transforms from Constants at compile time
- Automatic Kalman filter std dev configuration

---

## Phase 1: Transform Estimation Algorithm

### Goal
Estimate systematic camera mount errors by comparing odometry vs vision measurements during constrained motion.

### Algorithm

```
Input: 
  - ~150 odometry-vision pose pairs (from spiral motion)
  - Known starting position (where odometry was reset)

For each camera:
  1. Collect all measurements where that camera saw AprilTags
  2. Filter outliers (measurement jump > 0.3m, distance jump > 0.3m)
  3. For each measurement:
       error = odometry_pose - vision_pose
       Store error_x, error_y, error_yaw
  4. Compute mean error (least-squares fitting):
       mean_error_x = average(error_x values)
       mean_error_y = average(error_y values)
       mean_error_yaw = average(error_yaw values)
  5. Transform correction = mean_error
       (The systematic offset IS the mount error)
  6. Compute residual error (fit quality):
       For each data point:
         residual = error - mean_error
       residual_error = sqrt(mean(residual²))
  7. Publish to SmartDashboard:
       - TransformCorrectionX/Y/Z (meters)
       - Roll/Pitch/Yaw (degrees)
       - ResidualError (meters)
       - NumDataPoints, Status

Output:
  - Per-camera transform corrections to SmartDashboard
  - ResidualError as quality metric
```

### Key Classes

**CalibrationDataPoint** (lines 754-760)
```java
class CalibrationDataPoint {
    Pose2d odometryPose;
    Pose2d visionPose;
    String cameraName;
    double timestamp;
}
```

**solveTransformCorrection()** (lines 532-567)
- Implements least-squares fitting
- Returns Transform3d representing correction

**publishTransformCorrectionToSmartDashboard()** (lines 570-610)
- Formats results for operator review
- Publishes per-camera data

**computeResidualError()** (lines 613-632)
- Measures fit quality
- Diagnostic metric

### Calibration State Machine

```
IDLE 
  → (Phase 1 button clicked)
COLLECTING_DATA 
  → (spiral executes for CALIBRATION_TIMEOUT seconds)
PROCESSING 
  → (transform corrections computed)
COMPLETE
  → (results published to SmartDashboard)
```

---

## Phase 2: Standard Deviation Estimation Algorithm

### Goal
Estimate vision measurement std devs from residual errors **after** correcting for camera mount errors.

### Algorithm

```
Input:
  - ~150 odometry-vision pose pairs (from Phase 2 spiral)
  - Corrected camera transforms from Constants.java

For each camera:
  1. Collect measurements where that camera saw AprilTags
  2. Apply CORRECTED transforms (from Phase 1 corrections now in Constants)
  3. For each measurement:
       corrected_error = odometry_pose - vision_pose_with_corrected_transform
       Store error_x, error_y, error_yaw
  4. Compute std devs from residual errors:
       std_dev_x = sqrt(mean(error_x²))
       std_dev_y = sqrt(mean(error_y²))
       std_dev_theta = sqrt(mean(error_theta²))

Output:
  - Recommended singleTagStdDevs
  - Recommended multiTagStdDevs
  - Published to SmartDashboard
```

### Why Phase 2 is Separate

**Before Phase 1 corrections are applied**:
- Camera mount errors + measurement noise = large total error
- Std devs computed from this mix are inflated
- Kalman filter doesn't trust vision (std devs too large)

**After Phase 1 corrections are applied**:
- Camera mount errors removed (transform corrected)
- Std devs computed from measurement noise only
- Much smaller, more accurate values
- Kalman filter trusts vision appropriately

This two-phase approach ensures std devs reflect **actual measurement uncertainty**, not mount errors.

---

## Calibration Points Validation

### Purpose

`CalibrationPointsValidationCommand` is a pre-calibration diagnostic tool that:
- Loads calibration points from `src/main/deploy/calibration_points.csv`
- Navigates robot through each point sequentially
- Allows operators to verify field setup before running full calibration
- Performs NO data collection or calibration (navigation only)

### Algorithm

```
1. Load calibration points from CSV file
2. For each calibration point:
   a. Publish target coordinates to SmartDashboard
   b. Use proportional control to navigate toward target:
      - Drive velocity proportional to distance error
      - Rotation velocity proportional to angle error
   c. When within tolerance (< 0.15m, < 5°), stop and wait
   d. Operator clicks SmartDashboard button to advance to next point
   e. Repeat
3. After all points visited, command completes
```

### Navigation Control

**Proportional drive logic:**
```java
double driveVelocity = Math.min(MAX_DRIVE_VELOCITY, distanceError * 0.5);
double angularVelocity = Math.max(-MAX_ANGULAR_VELOCITY,
    Math.min(MAX_ANGULAR_VELOCITY, rotationError * 0.02));

// Convert field-relative velocity to robot-relative
double angle = Math.atan2(errorY, errorX);
double robotRelativeAngle = angle - currentRotation.getRadians();

double robotDriveVelocity = driveVelocity * Math.cos(robotRelativeAngle);
double robotStrafeVelocity = driveVelocity * Math.sin(robotRelativeAngle);
```

**Tolerances:**
- Position: < 0.15m
- Rotation: < 5.0°
- Timeout: 30 seconds per point

### CSV File Format

**File**: `src/main/deploy/calibration_points.csv`

```csv
name,x_meters,y_meters,rotation_degrees,initial_position
BLUE_CORNER_LOW,1.5,1.5,0,true
BLUE_SIDE_MID_LOW,4.1,1.5,0,false
RED_CORNER_LOW,6.7,1.5,0,false
[...more points...]
```

**Columns**:
- `name`: Unique point identifier (e.g., "BLUE_CORNER_LOW")
- `x_meters`: Field X coordinate (0-8.2 typical)
- `y_meters`: Field Y coordinate (0-4.1 typical)
- `rotation_degrees`: Robot heading (0=facing red, 180=facing blue)
- `initial_position`: Unused in validation (included for consistency with calibration)

### SmartDashboard Output

During validation, these values are published:

```
CalibrationValidation/
├─ Status                    → "Navigating to point X/Y: [name]" or "At point [name] - Click NextPoint"
├─ TotalPoints               → Number of points in CSV
├─ CurrentPointIndex         → Current point (1-indexed)
├─ CurrentPointName          → Name of target point
├─ TargetX/Y                 → Target coordinates (meters)
├─ TargetRotation            → Target heading (degrees)
├─ CurrentX/Y                → Robot's current position
├─ CurrentRotation           → Robot's current heading
├─ DistanceError             → Distance to target (meters)
├─ RotationError             → Angle to target (degrees)
└─ NextPoint                 → Set to true to advance to next point
```

### Use Cases

**Before Phase 1 Calibration**:
- Verify field coordinates in CSV are correct
- Confirm robot can navigate to expected positions
- Check for odometry drift
- Identify field lighting or AprilTag visibility issues

**During Setup**:
- Validate robot motion without full calibration commitment
- Test navigation to multiple field locations
- Verify proportional drive parameters work well

---

## Data Collection: Spiral Motion

### Spiral Path

The robot executes a controlled expanding spiral:

```java
double radiusMultiplier = Math.min(1.0, elapsedTime / 5.0);  // Expand over 5 sec
double currentRadius = 0.2 + (1.0 - 0.2) * radiusMultiplier; // 0.2m to 1.0m
double angle = elapsedTime * 0.5; // 0.5 rad/sec rotation

// Drive command:
driveRequest.withVelocityX(drive_velocity)
            .withVelocityY(strafe_velocity)
            .withRotationalRate(angular_velocity)
```

### Why Spiral?

- **Compact**: Stays within ~1m of starting position (safe indoors)
- **Comprehensive**: Rotates through 360°+ (sees all camera angles)
- **Smooth**: Continuous motion (no jerky acceleration)
- **Robust**: Multiple measurements from varied poses (reduces outliers)
- **Quick**: 30 seconds (~150 data points at 50Hz)

### Measurement Filtering

During spiral, measurements are filtered to reject bad data:

```
Acceptance criteria:
  ✓ At least 1 AprilTag detected
  ✓ Tags within 6 meters
  ✓ No pose jump > 0.3m (frame-to-frame)
  ✓ Error magnitude < 0.5m

Rejection: If any criterion fails, measurement is skipped
  (logged as rejectedMeasurementCount)
```

---

## Constants Configuration

### Camera Transforms (lines 173-207)

```java
// Example: Heart camera
// Default (uncalibrated):
robotToHeart = new Transform3d(
    new Translation3d(0.15, 0.08, 0.22),    // meters from robot center
    new Rotation3d(0, Math.toRadians(30), 0) // pitch angle
);

// After Phase 1 calibration (add corrections):
robotToHeart = new Transform3d(
    new Translation3d(0.15 + 0.05, 0.08 - 0.03, 0.22),  // Add X, Y corrections
    new Rotation3d(0, Math.toRadians(30 + 1.2), 0)      // Add Yaw to pitch
);
```

**Important**: Corrections are **additive** (add to existing values, don't replace)

### Standard Deviations (lines 209-210)

```java
// Format: VecBuilder.fill(std_dev_x, std_dev_y, std_dev_theta_in_radians)

// Before calibration (conservative defaults):
singleTagStdDevs = VecBuilder.fill(0.15, 0.15, 0.15);
multiTagStdDevs = VecBuilder.fill(0.08, 0.08, 0.08);

// After Phase 2 calibration (from SmartDashboard):
singleTagStdDevs = VecBuilder.fill(0.12, 0.14, 0.09);    // [X, Y, Theta]
multiTagStdDevs = VecBuilder.fill(0.06, 0.07, 0.04);     // [X, Y, Theta]
```

**Units**: X and Y in meters, Theta in radians (not degrees!)

### Calibration Thresholds (tunable)

```java
// Data collection
CALIBRATION_DATA_POINTS = 150;              // ~3 seconds at 50Hz
CALIBRATION_REGION_SIZE = 1.5;              // Stay within 1.5m of start
CALIBRATION_TIMEOUT = 30.0;                 // Total spiral duration

// Measurement filtering
MAX_INSTANTANEOUS_ERROR = 0.5;              // Reject > 0.5m error
MIN_TAGS_FOR_MEASUREMENT = 1;               // Require >= 1 tag
MAX_DISTANCE_TO_TAGS = 6.0;                 // Reject tags > 6m away
MAX_POSE_JUMP = 0.3;                        // Reject frame jumps > 0.3m
```

---

## SmartDashboard Integration

### Phase 1 Output Format

```
Vision/Cal/
├─ {camera}/TransformCorrectionX        [meters]
├─ {camera}/TransformCorrectionY        [meters]
├─ {camera}/TransformCorrectionZ        [meters]
├─ {camera}/Roll                        [degrees]
├─ {camera}/Pitch                       [degrees]
├─ {camera}/Yaw                         [degrees]
├─ {camera}/ResidualError               [meters] ← Quality metric
├─ {camera}/NumDataPoints               [count]
└─ {camera}/Status                      ["COMPLETE" | "ERROR"]
```

### Phase 2 Output Format

```
Vision/Cal/
├─ SingleTagStdDevs                     [array: X, Y, Theta]
├─ MultiTagStdDevs                      [array: X, Y, Theta]
└─ [Same per-camera output as Phase 1]
```

---

## Making Changes

### To Adjust Calibration Parameters

**File**: `Constants.java` lines 40-45

```java
// Example: Increase spiral radius
CALIBRATION_REGION_SIZE = 2.0;  // Changed from 1.5

// Example: Change spiral duration
CALIBRATION_TIMEOUT = 45.0;  // Changed from 30.0

// Example: More lenient filtering
MAX_INSTANTANEOUS_ERROR = 0.7;  // Changed from 0.5
```

After changes: `./gradlew build && ./gradlew deploy`

### To Change Spiral Motion Characteristics

**File**: `VisionTransformEstimationCommand.java` lines 30-33

```java
private static final double SPIRAL_TIMEOUT_SECONDS = 30.0;  // Duration
private static final double MAX_SPIRAL_RADIUS = 1.0;        // Max distance from start
private static final double ANGULAR_VELOCITY = 0.5;         // rad/sec
private static final double MAX_DRIVE_VELOCITY = 0.5;       // m/sec
```

### To Modify Transform Calculation Logic

**File**: `VisionCalibrationEngine.java` lines 532-567

The `solveTransformCorrection()` method implements the least-squares fitting. Key lines:

```java
// Current: Use mean error as correction
Transform3d correction = new Transform3d(
    new Translation3d(meanErrorX, meanErrorY, 0),
    new Rotation3d(0, 0, Math.toRadians(meanErrorYaw))
);

// Alternative approaches:
// - Weighted least squares (weight more recent measurements higher)
// - Robust fitting (downweight outliers)
// - Per-zone corrections (different transforms for different field areas)
```

### To Modify Std Dev Calculation Logic

**File**: `VisionCalibrationEngine.java` lines 700-750

The std dev computation currently uses:
```java
stdDev = sqrt(mean(error²))  // RMS error
```

Alternative approaches:
```java
// Percentile-based (more robust to outliers)
stdDev = percentile(error, 68)  // ~68% of measurements within 1 σ

// Robust MAD (median absolute deviation)
stdDev = 1.4826 * median(|error - median(error)|)

// Exponential weighting (recent measurements matter more)
stdDev = sqrt(sum(weight_t * error_t²) / sum(weight_t))
```

---

## Testing & Validation

### Unit Tests

**File**: `src/test/java/frc/robot/subsystems/FireControlTest.java`

Currently tests FireControl logic. To add calibration tests:

```java
@Test
void testTransformCorrectionComputation() {
    // Create sample measurements
    List<CalibrationDataPoint> dataPoints = new ArrayList<>();
    // Add points with known offset...
    
    // Run transformation solver
    Transform3d correction = engine.solveTransformCorrection(dataPoints);
    
    // Verify correction matches expected offset
    assertEquals(0.05, correction.getTranslation().getX(), 0.01);
}
```

### Manual Testing Checklist

- [ ] Build clean (`./gradlew build`)
- [ ] Deploy successfully (`./gradlew deploy`)
- [ ] Phase 1 button appears on SmartDashboard
- [ ] Phase 2 button appears on SmartDashboard
- [ ] Phase 1 executes spiral (observe robot motion)
- [ ] Phase 1 shows results on SmartDashboard (non-zero corrections)
- [ ] Update Constants.java with corrections
- [ ] Code recompiles without errors
- [ ] Phase 2 executes spiral
- [ ] Phase 2 shows std devs on SmartDashboard
- [ ] Std devs are smaller than Phase 1 errors
- [ ] Vision accuracy tests show improvement

### Field Validation

- Calibrate at 3+ different field locations
- Compare results - should be consistent
- Run Phase 1 multiple times - results should be repeatable
- Run Phase 2 after applying corrections - verify improvement
- Test in match scenarios (autonomous, teleop) - verify actual accuracy gain

---

## Troubleshooting Code Issues

### Phase 1 Fails (State stays in COLLECTING_DATA)

**Possible causes**:
- Spiral motion not executing (check drivetrain code)
- Timer not advancing (check Timer.get() working)
- Data collection loop failing silently

**Debug**:
1. Add logging to `VisionTransformEstimationCommand.execute()`: `System.out.println("Elapsed: " + timer.get())`
2. Check that drivetrain motion is working (manual test: click other buttons that move robot)
3. Verify no exceptions in driver station logs

### Phase 1 Completes but All-Zero Corrections

**Possible causes**:
- No camera measurements collected (vision not running)
- Measurements all filtered out (outlier thresholds too strict)
- Camera measurement parsing failing

**Debug**:
1. Check `VisionCalibrationEngine.collectCalibrationData()`: Is `acceptedMeasurementCount` > 0?
2. If `rejectedMeasurementCount` too high, increase thresholds in Constants.java
3. Verify Vision.java is publishing camera measurements
4. Check SmartDashboard Vision/Cameras/* for raw measurement output

### Phase 2 Std Devs Don't Improve

**Possible causes**:
- Constants.java not actually updated with Phase 1 corrections
- Code not redeployed
- Corrections applied wrong (added where should be replaced, or vice versa)
- Phase 1 gave bad corrections

**Debug**:
1. Open Constants.java lines 173-207, verify corrections are there
2. Check build output: `./gradlew build` shows changed files?
3. Check driver station: Are Vision/Cameras/* showing new transform values?
4. Compare Phase 1 ResidualError vs Phase 2 ResidualError:
   - Should improve if Phase 1 was correct
   - Should stay same or increase if Phase 1 was wrong

---

## Performance Characteristics

### Timing

- **Phase 1 execution**: ~30 seconds (spiral motion)
- **Phase 1 computation**: <1 second (least-squares fitting)
- **Phase 2 execution**: ~30 seconds (spiral motion)
- **Phase 2 computation**: <1 second (std dev calculation)
- **Data points collected**: ~150 per phase
- **SmartDashboard publish rate**: 10 Hz

### Network Usage

- SmartDashboard data: ~1KB per phase (transform corrections + std devs)
- No real-time streaming (data published after phase completes)
- Safe to run on RoboRIO without bandwidth issues

### Accuracy

- **Expected transform accuracy**: ±0.05-0.15m per camera
- **Expected std dev accuracy**: Within 0.01-0.02m of true value
- **Repeatability**: Should get same results ±0.02m between runs at same location

---

## Integration with Match Code

### During Match (Competition)

⚠️ **IMPORTANT**: Calibration should NOT run during matches.

**Safeguards**:
1. Calibration buttons only visible during pre-match mode
2. Commands are disabled during match (check `FMSInfo.isMatchStarted()`)
3. Vision uses corrected Constants compiled into code

### After Calibration (Pre-Match Deployment)

1. Run Phase 1 + Phase 2 during practice/testing
2. Apply corrections to Constants.java
3. Deploy code with corrections
4. Code is locked in for match (no runtime changes)
5. Vision subsystem automatically uses corrected Constants

### Quick Re-Calibration (Emergency)

If vision accuracy needs adjustment mid-competition:
1. Run Phase 2 only (fast, ~5 minutes)
2. Update std devs in Constants.java
3. Redeploy
4. Match resumes with better Kalman filter weighting

---

## Future Enhancements

### Potential Improvements

1. **Multi-location calibration** - Calibrate from 3+ field positions, average results
2. **Time offset detection** - Detect lag between PhotonVision and RoboRIO
3. **Per-zone corrections** - Different transforms for different field regions
4. **Camera health monitoring** - Flag cameras that are consistently wrong
5. **Automatic outlier rejection** - Use robust statistics (RANSAC, median absolute deviation)
6. **Adaptive std devs** - Adjust std devs based on number of AprilTags visible

### Integration Points

- **Vision subsystem** (`Vision.java`): Loads Constants at startup
- **Pose estimator** (WPILib `SwerveDrivePoseEstimator`): Uses std devs for Kalman filter
- **Drivetrain** (`CommandSwerveDrivetrain`): Provides odometry for comparison
- **SmartDashboard**: Displays results, no back-integration

---

## Code Organization

```
src/main/java/frc/robot/
├─ subsystems/
│  ├─ VisionCalibrationEngine.java       ← Core algorithm
│  └─ Vision.java                        ← Loads Constants, uses transforms
├─ commands/
│  ├─ VisionTransformEstimationCommand.java  ← Phase 1
│  └─ VisionCalibrationCommand.java         ← Phase 2
├─ Constants.java                        ← Configuration (lines 173-210)
└─ RobotContainer.java                   ← Button registration
```

Key file sizes:
- VisionCalibrationEngine: ~800 lines
- VisionTransformEstimationCommand: ~140 lines
- VisionCalibrationCommand: ~100 lines
- Calibration-related Constants: ~40 lines

Total: ~1,080 lines of calibration-specific code

---

## References

- **WPILib Vision**: https://docs.wpilib.org/en/stable/docs/software/vision-processing/
- **PhotonLib**: https://docs.photonvision.org/
- **AprilTag Detection**: https://april.eecs.umich.edu/
- **Kalman Filter Theory**: WPILib SwerveDrivePoseEstimator documentation
- **Least-Squares Fitting**: Standard numerical methods texts

