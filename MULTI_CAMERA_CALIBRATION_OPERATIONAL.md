# Multi-Camera Calibration - Operational Guide

**For**: Robot operators and field teams  
**Purpose**: Step-by-step calibration procedure with expected outputs and interpretation  
**Time Required**: 30-40 minutes for complete 4-camera calibration

---

## Pre-Calibration Checklist

- [ ] Robot deployed to RoboRIO
- [ ] All 4 cameras enabled in RobotContainer (lines 143-146):
  ```java
  vision.addCamera("heart", Constants.Vision.robotToHeart);
  vision.addCamera("club", Constants.Vision.robotToClub);
  vision.addCamera("diamond", Constants.Vision.robotToDiamond);
  vision.addCamera("spade", Constants.Vision.robotToSpade);
  ```
- [ ] AprilTag field layout matches code (Constants.apriltagLayout)
- [ ] Robot gyro calibrated
- [ ] SmartDashboard accessible
- [ ] Robot placed at known field position (within ±5 inches)
- [ ] No obstructions around robot (30-foot diameter clear space needed)
- [ ] Robot has power and can move in circles for 30+ seconds

---

## Step-by-Step Calibration Procedure

### Step 1: Verify Live Camera Data (5 minutes)

1. **Place robot at field corner** with known position
   - Example: Blue alliance corner at (1, 1) meters
   - Mark robot position clearly

2. **Open SmartDashboard** on driver station computer

3. **Check Vision/Cameras section**
   - Look for entries: `Vision/Cameras/club/`, `Vision/Cameras/heart/`, etc.
   - Each should show:
     - X_m, Y_m (position in meters)
     - Rotation_deg (rotation in degrees)
     - TagsVisible (number of AprilTags detected)
     - Summary (readable format)

4. **Verify cameras reporting live data (note: simultaneity not required)**
   - Ideally each enabled camera reports TagsVisible > 0 during this check, but it's not required that all cameras see tags at the same instant.
   - Cameras have different fields of view; occlusions and field layout mean some cameras will only see tags at different robot poses.
   - All positions should be reasonable (no NaN or inf values).
   - If a camera shows 0 tags right now this may be normal — what matters is whether the camera accumulates sufficient observations during the full collection window. If a camera reports 0 tags persistently (not just during this quick check), then troubleshoot hardware/network/PhotonVision.
   
   **Expected Output Example**:
   ```
   Vision/Cameras/club/X_m: 1.05
   Vision/Cameras/club/Y_m: 0.98
   Vision/Cameras/club/Rotation_deg: 0.0
   Vision/Cameras/club/TagsVisible: 4
   Vision/Cameras/club/Summary: "club: (1.05, 0.98) rot=0.0° tags=4 dist=2.1m"
   
   Vision/Cameras/heart/X_m: 0.95
   Vision/Cameras/heart/Y_m: 1.05
   Vision/Cameras/heart/Rotation_deg: -2.0
   Vision/Cameras/heart/TagsVisible: 3
   Vision/Cameras/heart/Summary: "heart: (0.95, 1.05) rot=-2.0° tags=3 dist=2.3m"
   
   [Same for diamond and spade]
   ```

5. **Troubleshooting if cameras not reporting**:
   - [ ] Check camera USB connections
   - [ ] Check PhotonVision web UI (robot.local:5800)
   - [ ] Verify AprilTag detection working in PhotonVision
   - [ ] Check network connectivity
   - [ ] Redeploy robot code if needed

---

### Step 2: Run Calibration Collection (1 minute setup + 30 seconds collection)

1. **Navigate to SmartDashboard Vision buttons**
   - Look for buttons in SmartDashboard
   - Find: `Vision/Calibration/` section (or similar)

2. **Click button to start calibration**
   - Button name: "VisionCal: Initial Position (Oriented)" or similar
   - Robot should begin a spiral motion pattern
   - **Duration**: 30 seconds
   - **Motion pattern**: Robot rotates in place while moving in gentle spiral
   - **Important**: Don't move robot manually during this time!

3. **Watch progress on SmartDashboard**
   - You should see SmartDashboard updating:
     - `VisionCalibration/State: COLLECTING_DATA`
     - `VisionCalibration/DataPointsCollected: [increasing count]/150`
     - Per-camera data:
       - `VisionCalibration/club/InstantaneousError: [value]`
       - `VisionCalibration/club/TagsVisible: [count]`

4. **Wait for collection to complete**
   - After ~30 seconds, motion should stop
   - You should see:
     - `VisionCalibration/State: COMPLETE` (or `PROCESSING`)
     - `VisionCalibration/Status: "Calibration complete!"`

5. **If collection fails**:
   - [ ] Robot didn't move: Check drivetrain power
   - [ ] Cameras lost tags: Check field lighting, camera lenses
   - [ ] Status shows error: Check RoboRIO logs
   - **Try again**: Just click the button to restart

---

### Step 3: Review Per-Camera Calibration Results (5 minutes)

1. **Check SmartDashboard for calibration results**
   - Look for section: `VisionCalibration/`
   - You should see results for each camera

2. **For EACH camera, record these values**:

   **Club Camera**:
   - `VisionCalibration/club/MeanXError: [value]` → Should be < 0.15m
   - `VisionCalibration/club/MeanYError: [value]` → Should be < 0.15m
   - `VisionCalibration/club/MeanRotError: [value]` → Should be < 3 degrees
   - `VisionCalibration/club/DataPoints: [count]` → Should be 100+
   - `VisionCalibration/club/Status: [string]` → Human-readable summary

   **Heart Camera**:
   - `VisionCalibration/heart/MeanXError: [value]`
   - `VisionCalibration/heart/MeanYError: [value]`
   - `VisionCalibration/heart/MeanRotError: [value]`
   - `VisionCalibration/heart/DataPoints: [count]`
   - `VisionCalibration/heart/Status: [string]`

   **Diamond Camera**:
   - [Same 5 values as above]

   **Spade Camera**:
   - [Same 5 values as above]

3. **Interpret results**:
   - **Green (Good)**: Error < 0.10m on X and Y, < 2° rotation
   - **Yellow (Acceptable)**: Error 0.10m - 0.15m on X/Y, 2-3° rotation
   - **Red (Problem)**: Error > 0.15m or > 3° rotation

   **Example Results**:
   ```
   VisionCalibration/club/MeanXError: 0.052m     ✅ Excellent
   VisionCalibration/club/MeanYError: 0.031m     ✅ Excellent
   VisionCalibration/club/MeanRotError: 1.2°     ✅ Excellent
   
   VisionCalibration/heart/MeanXError: 0.18m     ⚠️ Needs adjustment
   VisionCalibration/heart/MeanYError: 0.12m     ✅ OK
   VisionCalibration/heart/MeanRotError: 2.5°    ✅ OK
   
   VisionCalibration/diamond/MeanXError: 0.042m  ✅ Excellent
   VisionCalibration/diamond/MeanYError: 0.028m  ✅ Excellent
   VisionCalibration/diamond/MeanRotError: 0.8°  ✅ Excellent
   
   VisionCalibration/spade/MeanXError: 0.095m    ✅ Good
   VisionCalibration/spade/MeanYError: 0.078m    ✅ Good
   VisionCalibration/spade/MeanRotError: 1.8°    ✅ Good
   ```

---

### Step 4: Decide if Corrections Needed

**Decision Tree**:

```
All cameras < 0.15m error?
    ├─ YES → Skip to Step 5 (Verification)
    └─ NO → Check which cameras failed
           ├─ 1 camera bad → Correct that camera (Step 4a)
           ├─ 2+ cameras bad → Multiple issues, investigate
           └─ All cameras bad → Field layout or gyro issue?
```

---

### Step 4a: Correct Problem Cameras (If Needed) - 10 minutes

**Only do this if a camera error is > 0.15m**

1. **Identify problem camera and its error**
   - Example: `VisionCalibration/heart/MeanXError: 0.18m`
   - This means heart camera's X estimate was off by 0.18m too much

2. **Edit Constants.java camera transform**
   - File: `src/main/java/frc/robot/Constants.java`
   - Find section: Vision (around line 173)
   - Find: `robotToHeart` transform
   
   ```java
   public static final Transform3d robotToHeart = new Transform3d(
       new Translation3d(
           Units.inchesToMeters(-0.300 - 0.18),  // ← SUBTRACT the error!
           Units.inchesToMeters(-8.414 - 0.12),  // ← SUBTRACT the Y error!
           Units.inchesToMeters(20.743)),
       new Rotation3d(...)
   );
   ```

3. **Apply the correction**:
   - **Formula**: NewValue = OldValue - MeasuredError
   - **Example**:
     - Old X: -0.300 inches
     - Measured X error: +0.18m (+7.1 inches)
     - New X: -0.300 - 7.1 = -7.4 inches
   - **Convert back**: New X in inches = -7.4 * 39.37 = -0.188m

4. **Redeploy code**:
   ```bash
   ./gradlew deploy
   ```
   - Wait for deployment to complete

5. **Recalibrate** to verify correction worked
   - Go back to Step 2
   - Run calibration again
   - Check if that camera's error improved
   - If still > 0.15m, apply additional correction
   - Repeat until < 0.15m

---

### Step 4b: Calibrate Kalman Filter Standard Deviations (5 minutes)

**The calibration engine automatically calculates recommended measurement standard deviations for the Kalman filter!** These tell the pose estimator how much to trust the vision measurements.

1. **Find the estimated standard deviations on SmartDashboard**:
   - Look for: `VisionCal/SingleTagStdDevs` - for single AprilTag measurements
   - Look for: `VisionCal/MultiTagStdDevs` - for multi-tag measurements (more reliable)
   
   **Example output**:
   ```
   VisionCal/SingleTagStdDevs: [0.235, 0.195, 0.152]
     → X: 0.235m, Y: 0.195m, Rotation: 0.152 radians
   
   VisionCal/MultiTagStdDevs: [0.052, 0.031, 0.142]
     → X: 0.052m, Y: 0.031m, Rotation: 0.142 radians
   ```

2. **Understanding the values**:
   - **Lower numbers** = more confident (tighter constraints) → Kalman filter trusts vision more
   - **Higher numbers** = less confident (looser constraints) → Kalman filter relies more on odometry
   - **X and Y** in meters - typically 0.05-0.3m for vision measurements
   - **Rotation** in radians - typically 0.05-0.3 rad (~3-17 degrees) for vision measurements

3. **Apply these to Constants.java** (Optional but recommended for optimal performance):
   - File: `src/main/java/frc/robot/Constants.java`
   - Find section: `Vision.singleTagStdDevs` and `Vision.multiTagStdDevs`
   
   **Before** (default tuning):
   ```java
   public static final Matrix<N3, N1> singleTagStdDevs = 
       VecBuilder.fill(0.5, 0.5, 999999.0);
   public static final Matrix<N3, N1> multiTagStdDevs = 
       VecBuilder.fill(0.00073, 0.00183, Units.degreesToRadians(0.142));
   ```
   
   **After** (using calibration results from example above):
   ```java
   public static final Matrix<N3, N1> singleTagStdDevs = 
       VecBuilder.fill(0.235, 0.195, Units.radiansToGradians(0.152));
   public static final Matrix<N3, N1> multiTagStdDevs = 
       VecBuilder.fill(0.052, 0.031, Units.radiansToGradians(0.142));
   ```

4. **Redeploy after updating**:
   ```bash
   ./gradlew deploy
   ```

5. **Why this matters**:
   - Correct standard deviations prevent **vision fighting** (oscillating pose estimates)
   - Multi-tag measurements are much more accurate → use tighter std devs
   - Single-tag measurements are less reliable → use looser std devs
   - If not tuned, the Kalman filter may over-trust or under-trust your vision system

**Note**: Standard deviations are calculated from the error distribution during calibration. If you correct camera transforms (Step 4a), errors should decrease → re-run calibration to get updated std dev recommendations.

---

### Step 5: Final Verification - Multi-Camera Fusion (10 minutes)

1. **With all 4 cameras enabled and calibrated**, drive robot around field:
   - Drive in straight line
   - Make 90-degree turns
   - Do rotational spins
   - Observe pose in SmartDashboard

2. **Check for smooth motion**:
   - Pose should NOT jump or flicker
   - When multiple cameras see tags, pose should be stable
   - If pose jumps: Vision measurements conflict (debug needed)

3. **Verify in autonomous**:
   - Run autonomous routine
   - Check that robot follows path accurately
   - If path following is poor: Vision integration may need tuning
   - If path following is good: ✅ System working!

4. **Expected smooth performance**:
   - Pose updates smoothly every cycle
   - No large jumps between measurements
   - Pose stays within ~6 inches during normal play
   - Rotation stable to within ~2 degrees

---

## Expected Output Reference

### During Calibration Collection
```
VisionCalibration/State: COLLECTING_DATA
VisionCalibration/DataPointsCollected: 127/150
VisionCalibration/club/InstantaneousError: 0.045m
VisionCalibration/club/TagsVisible: 3
VisionCalibration/heart/InstantaneousError: 0.18m
VisionCalibration/heart/TagsVisible: 2
VisionCalibration/diamond/InstantaneousError: 0.038m
VisionCalibration/diamond/TagsVisible: 4
VisionCalibration/spade/InstantaneousError: 0.095m
VisionCalibration/spade/TagsVisible: 3
```

### After Calibration Complete
```
VisionCalibration/State: COMPLETE
VisionCalibration/Status: "Calibration analysis complete!"
VisionCalibration/club/MeanXError: 0.052m
VisionCalibration/club/MeanYError: 0.031m
VisionCalibration/club/MeanRotError: 1.2°
VisionCalibration/club/MaxXError: 0.18m
VisionCalibration/club/MaxYError: 0.12m
VisionCalibration/club/DataPoints: 147
VisionCalibration/club/Status: "X:0.052m Y:0.031m Rot:1.20°"

VisionCalibration/heart/MeanXError: 0.18m
VisionCalibration/heart/MeanYError: 0.12m
VisionCalibration/heart/MeanRotError: 2.5°
VisionCalibration/heart/MaxXError: 0.45m
VisionCalibration/heart/MaxYError: 0.31m
VisionCalibration/heart/DataPoints: 142
VisionCalibration/heart/Status: "X:0.180m Y:0.120m Rot:2.50°"

[And so on for diamond, spade...]

VisionCal/SingleTagStdDevs: [0.235, 0.195, 0.152]
  → X: 0.235m, Y: 0.195m, Rotation: 0.152 rad (8.7°)

VisionCal/MultiTagStdDevs: [0.052, 0.031, 0.075]
  → X: 0.052m, Y: 0.031m, Rotation: 0.075 rad (4.3°)
```

**⚠️ Important**: The `SingleTagStdDevs` and `MultiTagStdDevs` are the **estimated measurement standard deviations** for your Kalman filter! See Step 4b to apply these to Constants.java.

### Live Camera Data (Every Cycle)
```
Vision/NumCameras: 4
Vision/CameraNames: ["club", "heart", "diamond", "spade"]
Vision/Cameras/club/X_m: 3.21
Vision/Cameras/club/Y_m: 1.45
Vision/Cameras/club/Rotation_deg: 45.3
Vision/Cameras/club/TagsVisible: 3
Vision/Cameras/club/AvgTagDistance_m: 2.1
Vision/Cameras/club/Timestamp_s: 125.43
Vision/Cameras/club/Summary: "club: (3.21, 1.45) rot=45.3° tags=3 dist=2.10m"

[And so on for other cameras...]
```

---

## Troubleshooting Common Issues

### Problem: All cameras show 0 tags
**Causes**: Camera pointing wrong direction, poor lighting, lens dirty  
**Solution**:
1. Check camera physical orientation in Constants
2. Check PhotonVision web UI for tag detection
3. Clean camera lenses
4. Check field lighting
5. Verify AprilTag layout matches Constants.apriltagLayout

### Problem: One camera shows > 0.15m error consistently
**Causes**: Camera not properly mounted, wrong Transform3d, optical issue  
**Solution**:
1. Physically verify camera mount matches CAD
2. Re-measure camera position and update Transform3d
3. Try correcting the error (Step 4a) and recalibrating
4. Check for lens distortion or focus issues

### Problem: All cameras show high error (0.2m+)
**Causes**: Gyro not calibrated, robot position wrong, field layout wrong  
**Solution**:
1. Calibrate robot gyro
2. Verify robot placed at known position (±5 inches)
3. Verify AprilTag field layout in Constants matches actual field
4. Run calibration again at different field location

### Problem: Pose jumps around during autonomous
**Causes**: Conflicting camera measurements, timing issues  
**Solution**:
1. Check all cameras have error < 0.15m
2. Verify all cameras can see tags in autonomous area
3. Check Kalman filter tuning (Vision std devs)
4. May need to disable problematic camera for that area

---

## Quick Reference: SmartDashboard Keys

**Live Camera Data** (updated every cycle):
- `Vision/NumCameras` - Total number of cameras
- `Vision/CameraNames` - Array of camera names
- `Vision/Cameras/[name]/X_m` - X position
- `Vision/Cameras/[name]/Y_m` - Y position
- `Vision/Cameras/[name]/Rotation_deg` - Rotation in degrees
- `Vision/Cameras/[name]/TagsVisible` - Number of visible tags
- `Vision/Cameras/[name]/AvgTagDistance_m` - Average distance to tags
- `Vision/Cameras/[name]/Timestamp_s` - Measurement timestamp
- `Vision/Cameras/[name]/Summary` - Readable summary

**Calibration Results** (after calibration completes):
- `VisionCalibration/State` - Current state (COLLECTING_DATA, COMPLETE)
- `VisionCalibration/Status` - Human-readable status
- `VisionCalibration/DataPointsCollected` - Points collected so far
- `VisionCalibration/[name]/MeanXError` - Average X error for camera
- `VisionCalibration/[name]/MeanYError` - Average Y error for camera
- `VisionCalibration/[name]/MeanRotError` - Average rotation error
- `VisionCalibration/[name]/MaxXError` - Maximum X error
- `VisionCalibration/[name]/MaxYError` - Maximum Y error
- `VisionCalibration/[name]/DataPoints` - Number of data points collected
- `VisionCalibration/[name]/Status` - Status string for this camera

**Kalman Filter Tuning** (after calibration completes - **important for odometry accuracy**):
- `VisionCal/SingleTagStdDevs` - [X, Y, Rotation] array - recommended std devs for single-tag measurements (more conservative)
- `VisionCal/MultiTagStdDevs` - [X, Y, Rotation] array - recommended std devs for multi-tag measurements (more accurate, tighter bounds)
- **Use these values in Constants.java Vision.singleTagStdDevs and Vision.multiTagStdDevs** (see Step 4b)

---

## Performance Targets

| Metric | Target | Good | Acceptable | Problem |
|--------|--------|------|------------|---------|
| X Error | < 0.05m | ✅ | ⚠️ 0.05-0.15m | ❌ > 0.15m |
| Y Error | < 0.05m | ✅ | ⚠️ 0.05-0.15m | ❌ > 0.15m |
| Rot Error | < 1.0° | ✅ | ⚠️ 1-3° | ❌ > 3° |
| Data Points | > 140 | ✅ | ⚠️ 100-140 | ❌ < 100 |
| Tags Visible | > 2 | ✅ | ⚠️ 1-2 | ❌ 0 |

---

## Calibration Success Checklist (practical)

- [ ] Each enabled camera reports live data (Vision/Cameras/[name]/Summary) or has been confirmed intentionally disabled.
- [ ] Each camera accumulates a minimum number of observations during collection (recommended: >= 30 points; target for good stats: 100+). See `VisionCalibration/[name]/DataPoints`.
- [ ] Calibration collection completes without errors
- [ ] For cameras with sufficient data, X and Y mean error < 0.15m (prefer < 0.10m)
- [ ] For cameras with sufficient data, rotation error < 3°
- [ ] Pose moves smoothly during autonomous testing
- [ ] No large jumps in pose estimate
- [ ] Autonomous follows paths accurately

Notes:
- It is not required that all enabled cameras simultaneously see tags — calibration aggregates per-camera measurements over the entire collection run. What matters is sufficient, well-distributed observations per camera.
- If a camera has zero or very few data points after a collection run, the engine will publish a NO_DATA/INSUFFICIENT_DATA status for that camera and skip transform corrections for it. Troubleshoot such cameras before applying automatic corrections.

**When the practical checklist above is satisfied for the cameras you intend to use**: ✅ Calibration successful! Ready for competition.

---

## Between-Match Recalibration

If positioning seems off during competition:
1. Go to quiet area of field
2. Place robot at known position
3. Run quick calibration (Step 2)
4. Check if camera errors increased
5. If one camera > 0.15m: Apply correction (Step 4a)
6. Redeploy and test before next match

This can all be done in 15-20 minutes between matches!
