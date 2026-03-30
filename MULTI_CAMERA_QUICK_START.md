# Multi-Camera Calibration - Quick Start (3 Steps)

**Goal**: Calibrate 4 cameras, identify problems, fix them surgically

---

## **STEP 1: Enable All Cameras** (1 minute)

Edit `src/main/java/frc/robot/RobotContainer.java`, lines 143-146:

**UNCOMMENT these lines**:
```java
vision.addCamera("heart", Constants.Vision.robotToHeart);      // ← UNCOMMENT
vision.addCamera("club", Constants.Vision.robotToClub);
vision.addCamera("diamond", Constants.Vision.robotToDiamond);  // ← UNCOMMENT
vision.addCamera("spade", Constants.Vision.robotToSpade);      // ← UNCOMMENT
```

Deploy:
```bash
./gradlew deploy
```

---

## **STEP 2: Run Calibration** (5 minutes)

1. Place robot at field corner (tape mark on field)
2. Open SmartDashboard
3. Click button: **"VisionCal: Initial Position (Oriented)"**
4. Wait 30 seconds (robot spins in circle collecting data)
5. Review SmartDashboard

---

## **STEP 3: Check Results** (2 minutes)

Look for these on SmartDashboard:

```
VisionCalibration/club/MeanXError: 0.05m      ✅ OK
VisionCalibration/heart/MeanXError: 0.25m     ❌ PROBLEM!
VisionCalibration/diamond/MeanXError: 0.08m   ✅ OK
VisionCalibration/spade/MeanXError: 0.12m     ✅ OK (close)
```

**If all < 0.15m**: Done! Cameras are calibrated.

**If any > 0.15m**: Fix it (see below)

---

## **FIX Problem Cameras** (10 minutes per camera)

Example: Heart camera shows **0.25m X error** + **0.15m Y error**

1. **Edit `Constants.java`, line ~173**:
```java
// BEFORE:
public static final Transform3d robotToHeart = new Transform3d(
    new Translation3d(Units.inchesToMeters(-0.300),      // X: -0.3 inches
                      Units.inchesToMeters(-8.414),      // Y: -8.414 inches
                      Units.inchesToMeters(20.743)),
    // ... rotation ...
);

// AFTER: Subtract the error
public static final Transform3d robotToHeart = new Transform3d(
    new Translation3d(Units.inchesToMeters(-0.300 - 0.25),   // Subtract 0.25m error!
                      Units.inchesToMeters(-8.414 - 0.15),   // Subtract 0.15m error!
                      Units.inchesToMeters(20.743)),
    // ... rotation ...
);
```

2. **Redeploy**:
```bash
./gradlew deploy
```

3. **Rerun calibration**:
   - Place robot at same location
   - Click button: **"VisionCal: Initial Position (Oriented)"**
   - Wait 30 seconds

4. **Check error dropped**:
   - Heart X error should now be ~0.05m ✅
   - If still > 0.15m, repeat with adjusted value

---

## **SmartDashboard Keys to Watch**

```
VisionCalibration/[camera]/MeanXError       ← Position error X (meters)
VisionCalibration/[camera]/MeanYError       ← Position error Y (meters)
VisionCalibration/[camera]/MeanRotError     ← Rotation error (degrees)
VisionCalibration/[camera]/DataPoints       ← Number of samples collected
VisionCalibration/[camera]/Status           ← Summary string
```

**Expected Good Values**:
- MeanXError < 0.10m
- MeanYError < 0.10m
- MeanRotError < 2.0°

**Acceptable Values**:
- MeanXError < 0.15m
- MeanYError < 0.15m
- MeanRotError < 3.0°

---

## **Troubleshooting**

| Problem | Cause | Fix |
|---------|-------|-----|
| One camera: huge error (0.5m+) | Camera mount wrong | Check CAD, adjust transform |
| All cameras: high error | Gyro drift | Verify IMU calibration |
| Error looks random | Not enough samples | Run calibration again |
| Camera shows zero error | Camera not enabled | Check RobotContainer line 143-146 |

---

## **Timeline**

- **5 min**: Enable all cameras + deploy
- **5 min**: Run calibration
- **2 min**: Check results
- **10 min**: Fix problem cameras (if any)
- **5 min**: Final verification run

**Total**: ~25-35 minutes for full 4-camera calibration

---

## **Competition Day**

Once calibrated:
1. All 4 camera lines stay **UNCOMMENTED**
2. System auto-uses all cameras for best accuracy
3. Vision works for:
   - Auto path following
   - Pose estimation
   - Multi-camera fusion

No more calibration needed unless you move cameras or change field layout.

---

**Questions?** See full guide: `docs/MULTI_CAMERA_CALIBRATION_WORKFLOW.md`
