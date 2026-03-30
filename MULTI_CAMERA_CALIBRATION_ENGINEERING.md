# Multi-Camera Calibration - Engineering Guide

**For**: Software engineers and programmers  
**Purpose**: How the calibration system works and where to make changes  
**Scope**: Architecture, code flow, extension points

---

## System Architecture Overview

### High-Level Data Flow

```
Match Operation:
  Robot → Vision Subsystem (4 cameras)
    ├─ Detects AprilTags
    ├─ Estimates pose per camera
    ├─ Publishes to SmartDashboard (live)
    └─ Sends to Drivetrain PoseEstimator
      └─ Fused pose estimate
      └─ Used by PathPlanner/Autonomous

Calibration Mode:
  Calibration Command
    └─ Runs 30-second spiral motion
    └─ Collects Vision.getCameraMeasurements() every cycle
    └─ Stores per-camera measurements
    └─ After collection:
      ├─ Calculates per-camera error statistics
      ├─ Publishes results to SmartDashboard
      ├─ Suggests camera corrections
      └─ Done!
```

### Core Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| Vision | subsystems/Vision.java | Manages 4 cameras, collects measurements, publishes to SmartDashboard |
| VisionCalibrationEngine | subsystems/VisionCalibrationEngine.java | Orchestrates calibration process, analyzes errors, suggests corrections |
| RobotContainer | RobotContainer.java | Initializes cameras, sets up SmartDashboard UI |
| CameraMeasurement | subsystems/Vision.java (inner class) | Data structure for one camera's measurement |

---

## Vision Subsystem Deep Dive

### File: `src/main/java/frc/robot/subsystems/Vision.java`

#### Key Components

##### 1. CameraMeasurement Class (Inner)
```java
public static class CameraMeasurement {
    public final String cameraName;              // "club", "heart", etc.
    public final Pose2d estimatedPose;           // Position and rotation
    public final double timestampSeconds;        // When measured
    public final Matrix<N3, N1> stdDevs;         // Measurement uncertainty
    public final int numTagsVisible;             // # of tags in view
    public final double avgDistanceToTags;       // Average distance to tags
}
```

**Used for**: Tracking which camera produced which measurement during calibration

**To modify**: Add more fields if you need additional per-camera data (e.g., tag IDs, individual tag distances, camera temperature)

##### 2. VisionCamera Class (Inner)
```java
private class VisionCamera {
    private PhotonCamera cam;              // PhotonVision camera
    private PhotonPoseEstimator estimator; // Pose estimation
    private String cameraName;             // Camera identifier
    private BiConsumer<...> measurementRecorder;  // Callback to record measurements
    
    public void estimatePose(Translation2d robotPosition) {
        // Estimates pose using AprilTags
        // Creates CameraMeasurement
        // Calls measurementRecorder callback
    }
}
```

**Used for**: Internal camera management, pose estimation per camera

**To modify**: If changing AprilTag detection strategy or pose estimation algorithm

##### 3. Vision Class (Public)
```java
public final class Vision extends AdvancedSubsystem {
    private final ArrayList<VisionCamera> cameras;
    private final Map<String, CameraMeasurement> lastMeasurements;
    
    public void addCamera(String name, Transform3d robotToCam) {
        // Adds a new camera to system
        // Called from RobotContainer during initialization
    }
    
    public List<String> getCameraNames() {
        // Returns ["club", "heart", "diamond", "spade"]
        // Used by calibration and UI code
    }
    
    public List<CameraMeasurement> getCameraMeasurements() {
        // Returns current measurements from all cameras
        // Called every cycle by VisionCalibrationEngine during calibration
    }
    
    public void publishCameraMeasurementsToSmartDashboard() {
        // Publishes live camera data to SmartDashboard
        // Called every cycle from periodic()
        // Creates Vision/Cameras/[name]/* paths
    }
    
    @Override
    public void periodic() {
        // Called every 20ms by WPILib scheduler
        // 1. Updates pose for each camera
        // 2. Publishes to SmartDashboard
    }
}
```

**To modify**:
- **Add new camera**: Just call `vision.addCamera("name", transform)` from RobotContainer
- **Change publishing**: Modify `publishCameraMeasurementsToSmartDashboard()`
- **Change pose estimation**: Modify `VisionCamera.estimatePose()`

---

## VisionCalibrationEngine Subsystem Deep Dive

### File: `src/main/java/frc/robot/subsystems/VisionCalibrationEngine.java`

#### Key Methods

##### 1. startCalibration()
```java
public void startCalibration(Pose2d knownRobotPose) {
    // Called when calibration starts
    // Sets initial position
    // Clears previous data
    // Begins 30-second collection window
}
```

**To modify**: If you want different initial setup or configuration

##### 2. collectCalibrationData()
```java
public void collectCalibrationData() {
    // Called every cycle during collection (20ms intervals)
    // Gets current robot pose from drivetrain
    // Gets measurements from vision.getCameraMeasurements()
    // Stores separately: dataPointsByCamera.get(cameraName).add(dataPoint)
    // Publishes real-time SmartDashboard: VisionCalibration/[camera]/InstantaneousError
}
```

**Key insight**: Each camera's measurements stored independently in Map<String, List<CalibrationDataPoint>>

**To modify**: If you want to collect different data or modify collection strategy

##### 3. analyzePerCameraErrors()
```java
public void analyzePerCameraErrors() {
    // Called after collection completes
    // For each camera in dataPointsByCamera:
    //   1. Calculate mean X, Y, rotation error
    //   2. Calculate max X, Y error
    //   3. Publish to SmartDashboard
    //   4. Create status string
}
```

**Output**: SmartDashboard keys like:
- `VisionCalibration/club/MeanXError`
- `VisionCalibration/club/MeanYError`
- `VisionCalibration/club/MeanRotError`

**To modify**: If you want different error metrics or analysis

##### 4. processCalibrationData()
```java
public void processCalibrationData() {
    // Called when calibration command ends
    // 1. Analyzes all collected data
    // 2. Publishes results
    // 3. Cleans up
}
```

---

## SmartDashboard Publishing Architecture

### Active Publishing Pattern

```java
// In Vision.periodic() - called every 20ms
@Override
public void periodic() {
    for (VisionCamera v : cameras) {
        v.estimatePose(robotPose.get().getTranslation());
    }
    // Automatic publishing every cycle
    publishCameraMeasurementsToSmartDashboard();
}
```

**Key**: No manual button clicks needed - data automatically visible

**SmartDashboard Paths**:
```
Vision/
  NumCameras: [int]
  CameraNames: [String[]]
  Cameras/
    [cameraName]/
      X_m: [double]
      Y_m: [double]
      Rotation_deg: [double]
      TagsVisible: [double]
      AvgTagDistance_m: [double]
      Timestamp_s: [double]
      Summary: [String]
```

**To extend**: Add more fields in `publishCameraMeasurementsToSmartDashboard()` method

---

## Dynamic Camera Configuration Pattern

### Design: Single Source of Truth

```
RobotContainer initialization:
    vision.addCamera("club", Constants.Vision.robotToClub);
    vision.addCamera("heart", Constants.Vision.robotToHeart);
    vision.addCamera("diamond", Constants.Vision.robotToDiamond);
    vision.addCamera("spade", Constants.Vision.robotToSpade);
    
Vision subsystem stores cameras internally:
    private ArrayList<VisionCamera> cameras;
    
Any code needing camera list calls:
    vision.getCameraNames()  // Returns ["club", "heart", "diamond", "spade"]
    
No hardcoded lists elsewhere!
```

**Benefits**:
- Add camera: One change in RobotContainer
- Remove camera: One change in RobotContainer
- Everything else auto-updates
- No name mismatch possible

**To modify**: All camera references use `vision.getCameraNames()` instead of hardcoded arrays

---

## Extension Points: Where to Make Changes

### 1. Add a New Metric to Calibration Results

**Scenario**: You want to also track standard deviation per camera

**Changes**:

a) **In VisionCalibrationEngine.java**, in `analyzePerCameraErrors()`:
```java
// Add after calculating mean/max errors:
double stdDev = calculateStandardDeviation(errors);
SmartDashboard.putNumber(
    "VisionCalibration/" + cameraName + "/StdDev",
    stdDev
);
```

b) **Operators will see** new key on SmartDashboard: `VisionCalibration/club/StdDev`

### 2. Change How Cameras Are Detected/Configured

**Scenario**: You want cameras loaded from an external config file

**Changes**:

a) **In RobotContainer.java**, replace:
```java
// Old:
vision.addCamera("club", Constants.Vision.robotToClub);
vision.addCamera("heart", Constants.Vision.robotToHeart);
// etc...

// New:
List<CameraConfig> configs = loadCamerasFromFile("camera_config.json");
for (CameraConfig cfg : configs) {
    vision.addCamera(cfg.name, cfg.transform);
}
```

b) **Rest of system automatically works** with new cameras because it uses `vision.getCameraNames()`

### 3. Add Real-Time Diagnostics During Calibration

**Scenario**: You want to see if pose is stable (not jumping around)

**Changes**:

a) **In VisionCalibrationEngine.java**, in `collectCalibrationData()`:
```java
// Track if measurements are stable
double poseStability = calculatePoseStability(currentMeasurement, previousMeasurement);
SmartDashboard.putNumber(
    "VisionCalibration/" + cameraName + "/PoseStability",
    poseStability
);
```

b) **Operators see** stability metric in real-time during calibration

### 4. Change Camera Transform (Calibration Correction)

**Scenario**: Calibration shows club camera is 0.05m off in X direction

**Changes**:

a) **In Constants.java**, find `robotToClub` transform:
```java
// Old:
public static final Transform3d robotToClub = new Transform3d(
    new Translation3d(
        Units.inchesToMeters(-0.300),  // X offset
        Units.inchesToMeters(-8.414),  // Y offset
        Units.inchesToMeters(20.743)), // Z offset
    new Rotation3d(...)
);

// New (subtract 0.05m from X):
public static final Transform3d robotToClub = new Transform3d(
    new Translation3d(
        Units.inchesToMeters(-0.300 - 1.97),  // Subtract 0.05m (1.97 inches)
        Units.inchesToMeters(-8.414),
        Units.inchesToMeters(20.743)),
    new Rotation3d(...)
);
```

b) **Redeploy** and recalibrate to verify correction

### 5. Change Pose Estimation Strategy

**Scenario**: You want to use different AprilTag detection strategy

**Changes**:

a) **In Vision.java**, in `VisionCamera` constructor:
```java
// Old:
estimator = new PhotonPoseEstimator(
    Constants.apriltagLayout,
    PhotonPoseEstimator.PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR,
    robotToCam
);

// New (e.g., use different strategy):
estimator = new PhotonPoseEstimator(
    Constants.apriltagLayout,
    PhotonPoseEstimator.PoseStrategy.AVERAGE_BEST_TARGETS,  // Different!
    robotToCam
);
```

b) **Calibration system automatically uses** new strategy on next run

### 6. Add Per-Camera Diagnostics/Health Checks

**Scenario**: You want to flag cameras that consistently fail

**Changes**:

a) **In Vision.java**, in `publishCameraMeasurementsToSmartDashboard()`:
```java
// Add after publishing measurements:
for (String cameraName : getCameraNames()) {
    Optional<CameraMeasurement> m = getCameraMeasurement(cameraName);
    
    // Check camera health
    if (m.isEmpty() || m.get().numTagsVisible == 0) {
        SmartDashboard.putString(
            "Vision/" + cameraName + "/Health",
            "WARNING: No tag visibility"
        );
    } else {
        SmartDashboard.putString(
            "Vision/" + cameraName + "/Health",
            "OK"
        );
    }
}
```

b) **Operators see** camera health status in real-time

---

## Code Navigation

| Task | File | Location |
|------|------|----------|
| Add/remove cameras | RobotContainer.java | Lines 143-146 |
| Change what data published | Vision.java | `publishCameraMeasurementsToSmartDashboard()` |
| Change camera transforms | Constants.java | Vision section (~line 173) |
| Change calibration metrics | VisionCalibrationEngine.java | `analyzePerCameraErrors()` |
| Change pose estimation | Vision.java | `VisionCamera` class |
| Change calibration motion | VisionCalibrationCommand.java | (if it exists) |
| Add camera buttons | RobotContainer.java | `configureMultiCameraCalibration()` |

---

## Data Flow During Calibration

```
1. Calibration starts:
   - VisionCalibrationEngine.startCalibration(knownPose)
   - Clear dataPointsByCamera map
   - Begin 30-second collection

2. Each cycle (20ms):
   - VisionCalibrationEngine.collectCalibrationData()
   - Get odometry position: drivetrain.getState().Pose
   - Get vision measurements: vision.getCameraMeasurements()
   - For each camera measurement:
     - Create CalibrationDataPoint(odometry, vision)
     - Store in dataPointsByCamera[cameraName]
     - Publish instantaneous error to SmartDashboard
   
3. After 30 seconds:
   - VisionCalibrationEngine.processCalibrationData()
   - VisionCalibrationEngine.analyzePerCameraErrors()
   - For each camera:
     - Calculate mean/max errors
     - Publish results to SmartDashboard
     - Done!

4. Operator reads results:
   - Opens SmartDashboard
   - Sees VisionCalibration/[camera]/* keys
   - Compares with targets (< 0.15m)
   - Decides if correction needed

---

## Assumptions about simultaneity and data sufficiency

The calibration engine is designed to collect and analyze per-camera, time-stamped measurements over the entire motion window. It does not require multiple cameras to see the same AprilTag at the same instant. Key behaviors and recommended thresholds:

- Per-camera independence: measurements are stored per camera (Map<String, List<CalibrationDataPoint>>). Each camera is analyzed independently.
- Staggered observations: cameras may observe tags at different times and from different poses; the engine aggregates those to compute mean/max errors for each camera.
- Minimum data requirements (recommended defaults):
    - Per-camera minimum observations to consider corrections: 30 (configurable)
    - Recommended per-camera target for robust stats: 100+ observations
    - For multi-camera cross-validation, aim for at least 2 cameras with >= 30 points each
- Missing or sparse data handling:
    - If a camera has zero or too-few observations, the engine publishes a status for that camera (e.g., NO_DATA or INSUFFICIENT_DATA) and will skip automatic transform correction for it.
    - The engine will still analyze and publish results for cameras that do have sufficient data.
- Robust statistics: use trimmed means or medians to reduce sensitivity to outliers when computing per-camera mean errors.

Operational implications:

- Operators do NOT need to make every camera see tags simultaneously — instead, ensure each camera collects enough observations during the spiral motion.
- If a camera repeatedly has zero data, treat it as a hardware/configuration problem (lens, orientation, PhotonVision pipeline, network).

SmartDashboard keys to watch (already published or recommended additions):
- `VisionCalibration/NumCamerasWithData` – count of cameras that met the minimum data point threshold
- `VisionCalibration/[name]/Status` – `OK` | `NO_DATA` | `INSUFFICIENT_DATA` | `HIGH_ERROR`

```

---

## Key Design Decisions

### 1. Per-Camera Storage
**Decision**: Store measurements separately per camera (`Map<String, List<CalibrationDataPoint>>`)  
**Why**: Enables independent analysis and correction  
**Alternative**: Single list of all measurements (but harder to debug individual cameras)

### 2. Active Publishing
**Decision**: Publish every cycle automatically from `Vision.periodic()`  
**Why**: Operators see data without manual setup  
**Alternative**: Publish only on button click (but data might be stale)

### 3. Dynamic Camera Discovery
**Decision**: `vision.getCameraNames()` is source of truth  
**Why**: Adds camera once, everywhere updates automatically  
**Alternative**: Hardcoded camera lists (error-prone, not scalable)

### 4. Separate Calibration Subsystem
**Decision**: VisionCalibrationEngine is separate from Vision  
**Why**: Vision runs during match, calibration only during setup  
**Alternative**: Combine both (but makes match code more complex)

---

## Testing Changes

### When you modify Vision.java:
```bash
./gradlew compileJava
```
Should compile with 0 errors

### When you modify VisionCalibrationEngine.java:
```bash
./gradlew test
./gradlew build
```
Should pass all tests

### When you modify RobotContainer.java:
```bash
./gradlew build
./gradlew deploy
```
Should deploy successfully to RoboRIO

---

## Common Mistakes to Avoid

1. **Hardcoding camera names**
   ```java
   // ❌ WRONG: Hardcoded list
   for (String cam : new String[]{"club", "heart", "diamond", "spade"}) {
       // ...
   }
   
   // ✅ RIGHT: Dynamic query
   for (String cam : vision.getCameraNames()) {
       // ...
   }
   ```

2. **Forgetting to update SmartDashboard**
   ```java
   // ❌ WRONG: Calculate error but don't publish
   double error = calculateError(expected, measured);
   
   // ✅ RIGHT: Calculate and publish
   double error = calculateError(expected, measured);
   SmartDashboard.putNumber("Vision/Error", error);
   ```

3. **Not matching odometry and vision timestamps**
   ```java
   // ❌ WRONG: Using current time
   CalibrationDataPoint point = new CalibrationDataPoint(
       odometry,  // Position at T+5ms
       vision,    // Position at T (5ms ago!)
       System.currentTimeMillis()  // Current time
   );
   
   // ✅ RIGHT: Use vision timestamp
   CalibrationDataPoint point = new CalibrationDataPoint(
       odometry,
       vision,
       vision.timestampSeconds  // Use vision's timestamp
   );
   ```

4. **Forgetting to enable cameras in RobotContainer**
   ```java
   // ❌ WRONG: Cameras added but not enabled
   vision.addCamera("club", Constants.Vision.robotToClub);
   // heart, diamond, spade commented out
   
   // ✅ RIGHT: Uncomment to use
   vision.addCamera("club", Constants.Vision.robotToClub);
   vision.addCamera("heart", Constants.Vision.robotToHeart);  // Uncommented!
   ```

---

## Performance Considerations

- **Per-cycle publishing cost**: ~10ms (fast, acceptable)
- **Calibration collection**: 30 seconds, ~150 data points (reasonable)
- **Error analysis**: ~5ms (acceptable, done after collection)
- **Memory**: ~600 data points × 8 bytes = ~5KB per camera (negligible)

No performance optimization needed unless you have 10+ cameras.

---

## Future Enhancement Ideas

1. **Save calibration results to file**: Store baseline for quick re-verification
2. **Time offset detection**: Measure latency between vision and odometry
3. **Per-camera confidence scoring**: Trust some cameras more than others
4. **Automatic correction**: Apply corrections automatically based on calibration results
5. **Multi-location baseline**: Store calibration results for different field zones
6. **Camera failure detection**: Automatically disable bad cameras
7. **Real-time Kalman tuning**: Adjust measurement std devs based on calibration

All possible with current architecture!

---

## References

- **PhotonVision**: https://docs.photonvision.org/
- **AprilTags**: https://april.eecs.umich.edu/software/apriltag/
- **WPILib Pose Estimation**: https://docs.wpilib.org/en/stable/docs/software/advanced-controls/state-space/state-space-intro.html
- **Constants.java**: See Vision section for camera transforms

---

## Questions to Answer Before Modifying

1. **Does this change match functionality?** (If yes, be careful!)
2. **Does this change require Constants updates?** (Recompile if yes)
3. **Does this change break per-camera independence?** (Don't do that!)
4. **Is this SmartDashboard change discoverable by operators?** (Test visibility)
5. **Does this calibration change work with any number of cameras?** (Must scale!)

If all answers are "yes" (where applicable), safe to proceed! ✅
