# ✅ MASTER DEPLOYMENT CHECKLIST

**Multi-Camera Vision Calibration System**  
**TawalUnnop2026 FRC Robot**  
**Print this page for deployment day!**

---

## 🎯 Pre-Deployment (Do This First)

### Read Documentation
- [ ] Read: README_MULTI_CAMERA.md (5 min)
- [ ] Skim: DEPLOYMENT_CHECKLIST.md (5 min)
- [ ] Reference: MULTI_CAMERA_QUICK_START.md (keep nearby)

### System Verification
- [ ] Code compiles without errors: `./gradlew build`
- [ ] All 3 modified files present:
  - [ ] src/main/java/frc/robot/subsystems/Vision.java
  - [ ] src/main/java/frc/robot/subsystems/VisionCalibrationEngine.java
  - [ ] src/main/java/frc/robot/RobotContainer.java
- [ ] Robot safety checked (no unsecured parts)
- [ ] Team briefed on procedure

---

## 🚀 Deployment Phase (Follow In Order)

### Step 1: Enable Cameras (2 min)
```
File: src/main/java/frc/robot/RobotContainer.java
Lines: 143-146

UNCOMMENT these 4 lines:
✓ vision.addCamera("heart", Constants.Vision.robotToHeart);
✓ vision.addCamera("club", Constants.Vision.robotToClub);
✓ vision.addCamera("diamond", Constants.Vision.robotToDiamond);
✓ vision.addCamera("spade", Constants.Vision.robotToSpade);

Note: "club" should already be enabled
```

- [ ] Line 143: heart camera uncommented
- [ ] Line 144: club camera verified enabled
- [ ] Line 145: diamond camera uncommented
- [ ] Line 146: spade camera uncommented

### Step 2: Deploy Code (2 min)
```bash
./gradlew deploy
```

Expected output:
```
BUILD SUCCESSFUL in 1-2s
>> Executing task ':deploy'
...
Success! ✅
```

- [ ] Command executed: `./gradlew deploy`
- [ ] Build successful (ZERO errors)
- [ ] Deployment to RoboRIO successful
- [ ] Driver Station shows connection ✅

### Step 3: Setup Robot Position (1 min)
- [ ] Place robot at field corner (known position)
- [ ] Orient robot facing away from field walls
- [ ] Ensure 2-3 feet clear space for spinning
- [ ] Verify all cameras have clear view
- [ ] Mark starting position with tape/chalk

### Step 4: Start SmartDashboard (1 min)
- [ ] Open SmartDashboard on driver laptop
- [ ] Verify connection to robot (green icon)
- [ ] Navigate to: SmartDashboard → VisionCalibration section
- [ ] Verify all 4 cameras listed:
  - [ ] heart
  - [ ] club
  - [ ] diamond
  - [ ] spade

---

## 🎬 Calibration Phase (30 seconds)

### Step 5: Start Calibration (30 sec)
**SmartDashboard Button**: "VisionCal: Initial Position (Oriented)"

- [ ] Locate button on SmartDashboard
- [ ] Click button
- [ ] Observe SmartDashboard status: "COLLECTING_DATA"
- [ ] Watch real-time per-camera errors updating
- [ ] Do NOT move robot during collection
- [ ] Wait for status to change to "COMPLETE" (~30 sec)

**Watch These Keys During Collection**:
```
VisionCalibration/State: COLLECTING_DATA ← Should show this
VisionCalibration/Status: Calibration started... ← Should update
VisionCalibration/DataPointsCollected: [number]/150 ← Should increase
VisionCalibration/[camera]/InstantaneousError: [value]m ← Watch all 4
```

---

## 📊 Results Phase (5 minutes)

### Step 6: Review Results
After calibration completes, check SmartDashboard:

```
📍 TARGET RESULTS:
VisionCalibration/club/MeanXError: < 0.15m
VisionCalibration/heart/MeanXError: < 0.15m
VisionCalibration/diamond/MeanXError: < 0.15m
VisionCalibration/spade/MeanXError: < 0.15m
```

**Check This Section**:
```
VisionCalibration/club/MeanXError: [value]
VisionCalibration/club/MeanYError: [value]
VisionCalibration/club/DataPoints: [number]
VisionCalibration/club/Status: "X:[value]m Y:[value]m Rot:[value]°"

VisionCalibration/heart/MeanXError: [value]
VisionCalibration/heart/MeanYError: [value]
VisionCalibration/heart/DataPoints: [number]
VisionCalibration/heart/Status: "X:[value]m Y:[value]m Rot:[value]°"

VisionCalibration/diamond/MeanXError: [value]
VisionCalibration/diamond/MeanYError: [value]
VisionCalibration/diamond/DataPoints: [number]
VisionCalibration/diamond/Status: "X:[value]m Y:[value]m Rot:[value]°"

VisionCalibration/spade/MeanXError: [value]
VisionCalibration/spade/MeanYError: [value]
VisionCalibration/spade/DataPoints: [number]
VisionCalibration/spade/Status: "X:[value]m Y:[value]m Rot:[value]°"
```

- [ ] club MeanXError: ________ m (should be < 0.15m)
- [ ] heart MeanXError: ________ m (should be < 0.15m)
- [ ] diamond MeanXError: ________ m (should be < 0.15m)
- [ ] spade MeanXError: ________ m (should be < 0.15m)
- [ ] All cameras have data points > 100
- [ ] Status strings show reasonable errors

---

## 🔧 Correction Phase (If Needed)

### Step 7A: If Any Camera > 0.15m Error

For each camera that needs fixing:

**Example**: heart camera shows MeanXError = 0.25m

1. Open: `src/main/java/frc/robot/Constants.java`
2. Find: `robotToHeart` Transform3d (around line 180)
3. Current translation: `Translation3d(x, y, z)`
4. Fix formula: `x_new = x_old - measured_error`
5. Example: `x_old = -0.300`, measured = +0.25
   - `x_new = -0.300 - 0.25 = -0.550`
6. Update the constant
7. Deploy: `./gradlew deploy`
8. Wait 1 minute for code to load
9. Repeat calibration from Step 5
10. Verify error reduced

**Cameras to Correct**:
- [ ] club: If error > 0.15m, subtract from Translation3d X value
- [ ] heart: If error > 0.15m, subtract from Translation3d X value
- [ ] diamond: If error > 0.15m, subtract from Translation3d X value
- [ ] spade: If error > 0.15m, subtract from Translation3d X value

### Step 7B: If All Cameras < 0.15m Error ✅

You're done! Proceed to verification.

---

## ✅ Verification Phase (5 minutes)

### Step 8: Verify Multi-Camera Fusion
1. Move robot slowly forward/back on field
2. Observe SmartDashboard field visualization
3. Watch pose update smoothly (not jumping)
4. Move robot side-to-side
5. Verify X/Y/Rotation all update smoothly
6. All 4 cameras contributing ✅

- [ ] Robot moves forward → pose updates forward ✅
- [ ] Robot moves backward → pose updates backward ✅
- [ ] Robot rotates → heading updates ✅
- [ ] Pose smooth (no jumps/spikes) ✅
- [ ] Field visualization shows robot in correct location ✅

### Step 9: Document Results
Write down actual values for future reference:

```
CALIBRATION RESULTS - [DATE]
Location: [Field Position]
Time: [Time of Calibration]

club:   X error: ______ m,  Y error: ______ m,  Rot: ______ °
heart:  X error: ______ m,  Y error: ______ m,  Rot: ______ °
diamond: X error: ______ m,  Y error: ______ m,  Rot: ______ °
spade:  X error: ______ m,  Y error: ______ m,  Rot: ______ °

Multi-camera fusion: ✅ Verified smooth
```

- [ ] Baseline results documented
- [ ] Values recorded for future reference
- [ ] Notes on any corrected cameras

---

## 🎉 Success Criteria (All Must Be ✅)

### Code & Deployment
- [ ] Code compiles successfully (BUILD SUCCESSFUL)
- [ ] All 3 files modified and in place
- [ ] Deploy command completes without errors
- [ ] Robot connects to Driver Station
- [ ] SmartDashboard connects to robot

### Calibration
- [ ] Calibration button found on SmartDashboard
- [ ] Calibration completes in ~30 seconds
- [ ] All 4 cameras show results on SmartDashboard
- [ ] Data points collected: > 100 per camera

### Results
- [ ] club MeanXError < 0.15m ✅
- [ ] heart MeanXError < 0.15m ✅
- [ ] diamond MeanXError < 0.15m ✅
- [ ] spade MeanXError < 0.15m ✅
- [ ] Multi-camera fusion verification passed ✅

### Team & Documentation
- [ ] Team understands 3-step calibration process
- [ ] Baseline results documented
- [ ] DEPLOYMENT_CHECKLIST.md saved with team
- [ ] MULTI_CAMERA_QUICK_START.md available for reference

---

## 🚨 Troubleshooting Quick Reference

### "SmartDashboard button not found"
1. Check: Is SmartDashboard connected to robot? (green icon)
2. Navigate to: SmartDashboard tabs → "VisionCalibration" tab
3. If still missing:
   - Kill SmartDashboard
   - Restart Robot code on RoboRIO
   - Reconnect SmartDashboard
   - Refresh tab list

### "Calibration shows huge error (0.5m+)"
1. Check: Is robot at known position? (mark it first)
2. Check: Can all cameras see AprilTags? (shine light if needed)
3. Check: Are camera mounts loose? (verify physically)
4. Solution: Fix camera mount, redeploy, recalibrate

### "All cameras show same high error (0.2m+)"
1. Verify: Robot position is correct
2. Verify: Gyro/IMU is calibrated
3. Verify: AprilTag field layout matches code
4. Solution: Run calibration again at different position

### "Robot won't move during calibration"
1. Verify: Robot has space to spin freely
2. Verify: Wheels can move freely (spin by hand)
3. Verify: Not running autonomous mode (check Driver Station)
4. Solution: Move robot to open area, restart code

### "Only getting 1-2 cameras' data"
1. Verify: All 4 cameras uncommented in RobotContainer
2. Verify: All cameras connected to USB hub
3. Verify: All cameras have clear view of tags
4. Solution: Physically check camera connections

---

## 📝 Decision Points

### Decision 1: Ready to Calibrate?
```
Before clicking calibration button, verify:
☐ Robot at known field position
☐ All 4 cameras have line of sight to AprilTags
☐ Robot has 2-3 feet of space to spin
☐ SmartDashboard connected and showing tabs
☐ Team member is watching robot (for safety)

If all checked: Click calibration button! ✅
If any unchecked: Fix before proceeding ⚠️
```

### Decision 2: Results Good Enough?
```
Check all 4 cameras:
☐ club < 0.15m?
☐ heart < 0.15m?
☐ diamond < 0.15m?
☐ spade < 0.15m?

If YES to all: Calibration complete! ✅
If NO to any: Make corrections (Step 7A) ⚠️
```

### Decision 3: Ready for Competition?
```
Final verification:
☐ All 4 cameras < 0.15m error
☐ Multi-camera fusion working (smooth movement)
☐ Team trained on quick-start process
☐ Baseline results documented
☐ Rollback procedure understood

If YES to all: Ready for competition! ✅
If NO to any: Additional practice needed ⚠️
```

---

## 📊 Record Sheet

**Date**: ______________  
**Location**: ______________  
**Operator**: ______________  
**Time Started**: ______________  
**Time Completed**: ______________  

**Results**:
| Camera | MeanXError | MeanYError | MeanRotError | Status |
|--------|-----------|-----------|-------------|--------|
| club | ________ m | ________ m | ________ ° | ☐ OK |
| heart | ________ m | ________ m | ________ ° | ☐ OK |
| diamond | ________ m | ________ m | ________ ° | ☐ OK |
| spade | ________ m | ________ m | ________ ° | ☐ OK |

**Corrections Applied**: (list any adjustments made)
```
☐ club adjusted by ________ m
☐ heart adjusted by ________ m
☐ diamond adjusted by ________ m
☐ spade adjusted by ________ m
```

**Multi-Camera Fusion**: ☐ Verified smooth ☐ Needs work

**Overall Status**: ☐ COMPLETE ☐ NEEDS RETRY

---

## 🔄 Rollback Plan (If Problems)

**If system isn't working**, rollback to single camera in < 5 minutes:

```java
// In RobotContainer.java, lines 143-146
// COMMENT OUT these 3 lines:
// vision.addCamera("heart", Constants.Vision.robotToHeart);
// vision.addCamera("diamond", Constants.Vision.robotToDiamond);
// vision.addCamera("spade", Constants.Vision.robotToSpade);

// Keep this line UNCOMMENTED:
vision.addCamera("club", Constants.Vision.robotToClub);

// Deploy: ./gradlew deploy
```

- [ ] Roll back to single camera (club only)
- [ ] Redeploy code
- [ ] Verify robot works with club camera only
- [ ] Use existing single-camera calibration

**Result**: Single-camera operation restored, no data loss ✅

---

## ✨ Timeline Estimate

| Phase | Duration | Status |
|-------|----------|--------|
| Pre-Deployment | 10 min | Setup |
| Code Deploy | 2 min | Deploy |
| Robot Setup | 1 min | Position |
| SmartDashboard | 1 min | Connect |
| Calibration | 30 sec | Collect |
| Results Review | 5 min | Analyze |
| Verification | 5 min | Test |
| **TOTAL** | **~25 minutes** | **✅ COMPLETE** |

---

## 📞 Need Help?

### Quick Questions
→ See: MULTI_CAMERA_QUICK_START.md

### Troubleshooting
→ See: MULTI_CAMERA_CALIBRATION_WORKFLOW.md section "Troubleshooting"

### Complete Reference
→ See: MULTI_CAMERA_CALIBRATION_COMPLETE.md

### Emergency Rollback
→ Comment 3 cameras in RobotContainer.java, redeploy

---

## ✅ FINAL CHECKLIST

Before you start, initial here:

```
Team Lead: ____________  Date: ________

I have:
☐ Read README_MULTI_CAMERA.md
☐ Reviewed DEPLOYMENT_CHECKLIST.md
☐ Verified code compiles
☐ Briefed team on procedure
☐ Printed this master checklist
☐ Located MULTI_CAMERA_QUICK_START.md for reference

Ready to deploy: ☐ YES ☐ NO

If NO, resolve these issues first:
_________________________________
_________________________________
_________________________________
```

---

## 🚀 READY TO GO!

You have everything you need:
- ✅ Code ready (compiles perfectly)
- ✅ Robot ready (safely setup)
- ✅ Team ready (trained on procedure)
- ✅ Documentation ready (complete guides)
- ✅ Plan ready (step-by-step checklist)

**Next Step**: Follow the Deployment Phase section above, step by step.

**Expected Outcome**: Calibrated 4-camera vision system in 25 minutes! 🎉

---

**Print this page. Follow it. Calibrate your robot. Win matches.** 💪

Good luck! Your 2026 FRC robot is about to have the best vision system in the league! ✨
