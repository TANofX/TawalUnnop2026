# Vision Calibration System: User's Guide

## What This System Does

The vision calibration system automatically improves your robot's AprilTag tracking accuracy by measuring camera mount errors and adjusting the Kalman filter to trust vision appropriately.

**Expected outcome**: 40-50% improvement in vision pose estimation accuracy (from ~15-20% error to ~5-10%).

---

## Quick Start (5 Minutes)

### Prerequisites
- Robot deployed with calibration buttons available on SmartDashboard
- Clear sight lines to multiple AprilTags from calibration location
- Adequate field lighting
- Marked calibration position on field (tape mark or known coordinates)

### Two-Phase Process

**Phase 1: Estimate Camera Mount Errors** (~5 minutes)
1. Position robot at marked field location (±0.1m translation, ±3° rotation accuracy)
2. Click button: **"Vision/Cal/Phase1_EstimateTransforms"**
3. Robot executes 30-second spiral motion (stays in place, rotates and moves locally)
4. SmartDashboard displays transform corrections for each camera
5. Operator manually updates Constants.java with corrections and redeploys

**Phase 2: Estimate Vision Measurement Accuracy** (~5 minutes)
1. Position robot at same or different marked field location
2. Click button: **"Vision/Cal/Phase2_EstimateStdDevs"**
3. Robot executes 30-second spiral motion
4. SmartDashboard displays recommended std dev values
5. Operator updates Constants.java and redeploys

---

## Pre-Calibration: Validate Field Setup (Optional, ~5 minutes)

Before running the full calibration, you can validate that your robot can navigate to all calibration points defined in `calibration_points.csv`.

### Why Validate First?
- Confirms robot navigation works correctly
- Identifies field positioning or lighting issues early
- Saves time if there are setup problems
- Quick verification without full calibration

### How to Run Validation

1. Click button: **"Vision/Calibration/Validate Points"**
2. **Robot will automatically**:
   - Record starting position (where you currently are)
   - Navigate to the first calibration point defined in your CSV file
3. **On SmartDashboard**, monitor:
   - `CalibrationValidation/CurrentPointName` — Which point the robot is going to
   - `CalibrationValidation/DistanceError` — How far away (should decrease to < 0.15m)
   - `CalibrationValidation/RotationError` — Rotation error (should decrease to < 5°)
   - `CalibrationValidation/Status` — Current status message
4. When robot reaches the point, you'll see: **"At point [name] - Click NextPoint to continue"**
5. Click the **"CalibrationValidation/NextPoint"** button on SmartDashboard to advance to next point
6. Repeat for all points in your CSV file
7. After the last point, robot will **automatically return to the starting position**
8. When validation completes, you'll see: **"COMPLETE - Robot returned to starting position and is ready for calibration"**

**Key Point**: The validation command automatically returns the robot to its starting position after visiting all points. This ensures the robot is properly positioned for calibration without requiring manual repositioning.

### Troubleshooting Validation

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| Robot doesn't move | Odometry not working | Manually drive robot to verify odometry, then try validation again |
| Robot moves in wrong direction | CSV coordinates incorrect | Verify calibration_points.csv has correct field coordinates |
| Robot times out (>30 sec per point) | Point too far away | Check CSV - max distance between points should be ~3m |
| Robot oscillates and can't reach point | Proportional gain too high | Contact software team for tuning |

### SmartDashboard Validation Tab Layout

During validation, watch for these values:

```
CalibrationValidation/
├─ Status                          → Current status message
├─ StartingX / StartingY            → Robot's recorded starting position (meters)
├─ StartingRotation                 → Robot's starting heading (degrees)
├─ TotalPoints                      → How many points in CSV
├─ CurrentPointIndex                → Which point (1-indexed)
├─ CurrentPointName                 → Name of target point
├─ TargetX / TargetY                → Target coordinates (meters)
├─ TargetRotation                   → Target heading (degrees)
├─ CurrentX / CurrentY              → Robot's current position
├─ CurrentRotation                  → Robot's current heading
├─ DistanceError                    → Distance to target (meters)
├─ RotationError                    → Rotation to target (degrees)
└─ NextPoint                        → Button to click to advance (set to true)
```

**What's good**:
- ✅ DistanceError decreases from initial value down to < 0.15m
- ✅ RotationError approaches target within 5°
- ✅ Status shows "At point [name] - Click NextPoint to continue"

**What's bad**:
- ❌ DistanceError stays constant or increases
- ❌ Status shows "TIMEOUT - Could not reach..."
- ❌ Robot spins in place without moving forward

---

## Calibration Workflow

### Phase 1: Transform Estimation (15 minutes total)

#### Step 1: Position Robot
- Place robot at a **marked field location** (tape on floor, known coordinate, field corner, etc.)
- **Accuracy requirement**: ±0.1m (about 4 inches) translation, ±3 degrees rotation
- Use measuring tape or alignment marks to verify placement
- **Why this matters**: This becomes the ground truth for calibration. Odometry is reset to this position.

#### Step 2: Run Phase 1
1. Open SmartDashboard
2. Click button: **"Vision/Cal/Phase1_EstimateTransforms"**
3. **Robot will automatically**:
   - Reset odometry to your marked position
   - Display status: "Executing spiral motion..."
   - Move in an expanding spiral (~30 seconds)
   - Stay within ~1 meter of starting position
4. **Wait for completion** (progress bar or status change on SmartDashboard)

#### Step 3: Review SmartDashboard Results

Look for the following on SmartDashboard under `Vision/Cal/`:

```
For each camera (heart, diamond, club, spade):
├─ {camera}/TransformCorrectionX      → 0.XX meters
├─ {camera}/TransformCorrectionY      → 0.XX meters  
├─ {camera}/TransformCorrectionZ      → 0.XX meters
├─ {camera}/Roll                       → X.X degrees
├─ {camera}/Pitch                      → X.X degrees
├─ {camera}/Yaw                        → X.X degrees
├─ {camera}/ResidualError              → 0.XX meters
├─ {camera}/NumDataPoints              → 150
└─ {camera}/Status                     → "COMPLETE" or "ERROR"
```

**What to look for**:
- ✅ **Good results**: TransformCorrection values in ±0.2m range (typical), ResidualError < 0.15m
- ⚠️ **Questionable results**: ResidualError > 0.20m (might indicate placement was inaccurate)
- ❌ **Bad results**: All-zeros corrections or very large errors (indicates problem with vision system)

#### Step 4: Decide Which Corrections to Apply

**Decision framework**:

| Condition | Decision | Action |
|-----------|----------|--------|
| ResidualError < 0.10m | ✅ Apply | Use correction values |
| ResidualError 0.10-0.15m | ✅ Apply | Use correction values |
| ResidualError 0.15-0.25m | ⚠️ Apply carefully | Can apply, monitor results |
| ResidualError > 0.25m | ❌ Don't apply | Indicates calibration failure, re-run from better position |
| Camera shows all zeros | ❌ Don't apply | Camera didn't collect enough data |
| One camera much different | ✅ Apply selectively | Apply others, skip the outlier |

**General rule**: Apply corrections from cameras with ResidualError < 0.20m. Skip cameras that failed.

#### Step 5: Update Constants.java

**File**: `src/main/java/frc/robot/Constants.java`  
**Lines**: 173-207 (camera transforms section)

**Example**: Camera "heart" shows corrections: X=+0.05m, Y=-0.03m, Yaw=+1.2°

Before:
```java
robotToHeart = new Transform3d(
    new Translation3d(0.15, 0.08, 0.22),
    new Rotation3d(0, Math.toRadians(30), 0)
);
```

After (add corrections):
```java
robotToHeart = new Transform3d(
    new Translation3d(0.15 + 0.05, 0.08 - 0.03, 0.22),  // Add X and Y corrections
    new Rotation3d(0, Math.toRadians(30 + 1.2), 0)      // Add Yaw correction
);
```

**For each camera**: heart, diamond, club, spade
- Look up camera in Constants.java (search for `robotTo{Camera}`)
- Add TransformCorrectionX, Y, Z to Translation3d
- Add Yaw to rotation (convert degrees to radians with `Math.toRadians()`)

**Save and rebuild**:
```bash
./gradlew build
./gradlew deploy
```

**Verify**: No compile errors. SmartDashboard should show new transform values when you check.

#### Step 6: (OPTIONAL) Verify Improvements

After deploying Phase 1 corrections:

1. Position robot at same location
2. Click button: **"Vision/Cal/Verify_PlacementAfterPhase1"** (if available)
3. SmartDashboard shows:
   - ✅ "PASS - Phase 1 corrections successful! 2.5x improvement"
   - ❌ "WARNING - Phase 1 didn't help. Investigate..."

If improvements aren't showing, Phase 1 might have had issues. You can re-run from a different field location.

---

### Phase 2: Standard Deviation Estimation (15 minutes total)

#### Step 1: Position Robot
- Place robot at same field location as Phase 1 (or different, if testing robustness)
- **Accuracy requirement**: Same as Phase 1 (±0.1m, ±3°)

#### Step 2: Run Phase 2
1. Click button: **"Vision/Cal/Phase2_EstimateStdDevs"**
2. **Robot will automatically** (same spiral as Phase 1, but with corrected transforms):
   - Execute spiral motion (~30 seconds)
   - Collect measurements with corrected camera transforms
   - Calculate std devs from residual measurement errors

#### Step 3: Review SmartDashboard Results

Look for:
```
Vision/Cal/SingleTagStdDevs    → [0.12, 0.14, 0.09]  (X, Y, Theta in rad)
Vision/Cal/MultiTagStdDevs     → [0.06, 0.07, 0.04]  (X, Y, Theta in rad)
```

**What to expect**:
- ✅ **Good**: Std devs **30-50% lower than Phase 1** results (because mount errors now removed)
- ✅ **Good**: Multi-tag std devs < Single-tag std devs (multiple tags more reliable)
- ✅ **Good**: Values in reasonable range (XY: 0.05-0.2m, Theta: 0.02-0.1 rad)

#### Step 4: Update Constants.java with Std Devs

**File**: `src/main/java/frc/robot/Constants.java`  
**Lines**: 209-210 (std dev constants section)

Before:
```java
public static final Matrix<N3, N1> singleTagStdDevs = VecBuilder.fill(0.15, 0.15, 0.15);
public static final Matrix<N3, N1> multiTagStdDevs = VecBuilder.fill(0.08, 0.08, 0.08);
```

After (update with Phase 2 results):
```java
public static final Matrix<N3, N1> singleTagStdDevs = VecBuilder.fill(0.12, 0.14, 0.09);
public static final Matrix<N3, N1> multiTagStdDevs = VecBuilder.fill(0.06, 0.07, 0.04);
```

**Note**: Order is [X_error, Y_error, Rotation_error_in_radians]

**Save and rebuild**:
```bash
./gradlew build
./gradlew deploy
```

---

## SmartDashboard Tab Layout

All calibration data appears under the tab: **"Vision/Cal/"**

### During Phase 1:
```
Vision/Cal/
├─ Phase                          → "Phase 1: Transform Estimation"
├─ Status                         → "Executing spiral motion..." or "Complete"
├─ heart/
│  ├─ TransformCorrectionX        → 0.05
│  ├─ TransformCorrectionY        → -0.03
│  ├─ Yaw                         → 1.2
│  ├─ ResidualError               → 0.08
│  ├─ NumDataPoints               → 147
│  └─ Status                      → "COMPLETE"
├─ diamond/
│  └─ [same structure]
├─ club/
│  └─ [same structure]
└─ spade/
   └─ [same structure]
```

### During Phase 2:
```
Vision/Cal/
├─ Phase                          → "Phase 2: Std Dev Estimation"
├─ Status                         → "Executing spiral motion..." or "Complete"
├─ SingleTagStdDevs               → [0.12, 0.14, 0.09]
├─ MultiTagStdDevs                → [0.06, 0.07, 0.04]
└─ [Per-camera measurements, same as Phase 1]
```

---

## Troubleshooting

### Phase 1: Large or strange transform corrections

**Symptom**: One or more cameras show very large corrections (>0.5m) or all zeros

**Possible causes**:
1. Robot placement was inaccurate (> ±0.1m off)
2. Camera can't see AprilTags from that location
3. Poor field lighting

**Solution**:
1. Verify robot is actually at the marked position using measuring tape
2. Check field lighting and AprilTag visibility from position
3. Try Phase 1 again from a different field location with better sight lines
4. If same camera consistently fails, check camera is mounted and functioning

### Phase 1: ResidualError very high (> 0.25m)

**Symptom**: Phase 1 completes but ResidualError is large for all cameras

**Possible causes**:
1. Robot positioning was very inaccurate
2. AprilTags not visible or too far away
3. Multiple cameras failing

**Solution**:
1. Check exact robot position with measuring tape
2. If off by > 0.2m, re-run from better position
3. Verify you can see at least 2-3 AprilTags from position
4. Move closer to AprilTag field (typical range: 3-6 meters)

### Phase 2: No improvement or got worse

**Symptom**: After applying Phase 1 corrections, Phase 2 shows same or worse std devs

**Possible causes**:
1. Phase 1 corrections not actually applied to Constants.java
2. Code not rebuilt or redeployed
3. Placed robot in very different location
4. Phase 1 corrections were actually wrong

**Solution**:
1. Check Constants.java lines 173-207: Do you see your corrections there?
2. Run `./gradlew build`: Did it compile successfully?
3. Check SmartDashboard: Do Vision/Cameras/* show new transform values?
4. If moved to different location: ResidualError might look different, this is normal
5. Try Phase 1 and Phase 2 again from same location for direct comparison

### Phase 2: Std devs look unreasonably small

**Symptom**: Std devs are very small (< 0.03m), seems too good to be true

**This is usually OK**, but verify:
1. ResidualError from Phase 1 was low (< 0.10m)
2. You did get good improvement from Phase 1 corrections
3. Phase 2 ResidualError was also low

Small std devs are great! It means vision measurement is very accurate and Kalman filter can trust it heavily.

### Vision Calibration button doesn't appear on SmartDashboard

**Possible causes**:
1. Robot code not deployed
2. SmartDashboard not connected
3. Buttons disabled for match safety (normal during competition)

**Solution**:
1. Check robot is powered on and connected
2. Refresh SmartDashboard window (F5)
3. Look in "Sendable Choosers" or "Live Window" tabs if not on main dashboard
4. Contact software team if buttons still don't appear

---

## Best Practices

### Before Calibration
- [ ] Field has good lighting (no dark corners)
- [ ] AprilTags are visible and not damaged
- [ ] Robot cameras are clean (wipe with soft cloth)
- [ ] Robot odometry is working (drive in straight line, check it matches estimate)
- [ ] Battery is fully charged (low battery can affect vision processing)

### During Calibration
- [ ] Use same marked position for both Phase 1 and Phase 2 (for comparison)
- [ ] Wait for spiral to complete fully (~30 seconds) before moving robot
- [ ] Don't interrupt SmartDashboard during data collection
- [ ] Take notes on ResidualError values for each camera (helps debugging)

### After Calibration
- [ ] Review transformation corrections for reasonableness (±0.2m typical)
- [ ] Check std devs improved from Phase 1 to Phase 2 (30-50% better is normal)
- [ ] Test vision accuracy in actual match scenarios (verify improvements real)
- [ ] If not working well, document issues for software team

### Multi-Location Calibration
- [ ] Run Phase 1 and Phase 2 from different field locations if possible
- [ ] Compare results - should be consistent across locations
- [ ] If one location shows bad results, it might have poor lighting or bad AprilTag layout
- [ ] Use location with most reliable results for match preparation

---

## Safety Notes

⚠️ **During Calibration Motion**:
- Keep people 2+ meters away from robot
- Don't manually move robot during spiral (let motion complete)
- Don't grab robot if error occurs - wait for it to stop

⚠️ **Code Deployment**:
- Always verify code compiles (`./gradlew build` with no errors)
- Always verify code deploys successfully (`./gradlew deploy`)
- If deploy fails, robot keeps previous code (safe, just won't have new corrections)

⚠️ **Applying Corrections**:
- Only apply corrections from cameras with ResidualError < 0.20m
- If unsure about a correction, don't apply it (safe to skip)
- Worst case: Skip a camera, re-run Phase 1 from better position

---

## Expected Timeline

Typical calibration session:

```
2:00 PM  - Start
2:02 PM  - Position robot, click Phase 1 button
2:03 PM  - Phase 1 running (spiral motion)
2:04 PM  - Phase 1 complete, review SmartDashboard
2:09 PM  - Corrections applied to Constants.java, code redeployed
2:10 PM  - Position robot, click Phase 2 button
2:11 PM  - Phase 2 running (spiral motion)
2:12 PM  - Phase 2 complete, review SmartDashboard
2:17 PM  - Std devs applied to Constants.java, code redeployed
2:18 PM  - Done! Calibration complete, vision system improved
```

Total: ~18 minutes from start to finish

---

## When to Re-Calibrate

- **After moving cameras**: Re-run both phases to get new transforms
- **After changing field position**: Can re-run from new location (optional, for robustness)
- **If vision accuracy degrades mid-competition**: Can quickly re-run Phase 2 only (faster)
- **After replacing AprilTag field**: Recalibrate transforms (Phase 1)
- **Seasonal recalibration**: Once per season or after major setup changes

---

## Contact & Support

If calibration doesn't work or you have questions:
1. Check troubleshooting section above
2. Review Phase 1 ResidualError values - they're diagnostic
3. Try from different field location
4. Contact software team with:
   - Phase 1 and Phase 2 ResidualError values
   - SmartDashboard screenshots
   - Description of what went wrong

