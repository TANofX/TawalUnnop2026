# 90-Minute PhotonVision Multi-Camera Calibration Plan
**Goal:** Quickly stabilize robot localization using your existing cameras without implementing a full calibration pipeline.

This process prioritizes **usable results in one session**, not perfect calibration.

**Desired outcome:**
- Identify the **1–2 best cameras**
- Apply a **quick yaw correction** to those cameras
- Configure **conservative vision fusion**
- Disable cameras that introduce instability

---

# Preparation (Before the 90-minute session)

Bring:

- Tape measure
- Blue tape or gaffer tape
- Laptop with Shuffleboard / AdvantageScope
- Ability to adjust camera yaw offsets in code quickly
- Ability to enable/disable individual cameras easily

Enable telemetry for:

```
vision pose
odometry pose
vision-odometry difference
tag count
average tag distance
```

For the first half of this process:

**Disable vision fusion** and simply compare raw vision vs odometry.

---

# Phase 1 — Mark Test Locations (10 minutes)

Mark **four robot placement locations** on the floor with tape.

Precision is not critical.

Acceptable accuracy:

```
±2–3 inches
±2°
```

Example layout relative to central field tags:

```
Position A — near (~2 m)
Position B — mid (~4 m)
Position C — far (~6 m)
Position D — mid distance but off to the side
```

For each location also mark a **heading alignment line**.

This makes repeat placement much faster.

---

# Phase 2 — Rank the Cameras (20 minutes)

Test **each camera individually**.

Disable the other three cameras.

At each camera:

1. Place robot at **Position A**
2. Record the vision pose
3. Compare with odometry
4. Move to **Position B**
5. Repeat
6. Move to **Position C**
7. Repeat

You are looking for **behavior patterns**, not perfect accuracy.

Example notes:

```
front-left
A: good
B: +8cm east
C: +20cm east
```

or

```
rear-right
A: good
B: good
C: jumping
```

---

## Camera Ranking

Classify each camera.

### Tier A — Good

```
consistent bias
smooth error growth with distance
no jumping
```

### Tier B — Usable

```
moderate noise
occasional bad estimate
```

### Tier C — Disable

```
pose jumping
wild disagreement with odometry
frequent single-tag misestimates
```

Example result:

```
front-left   A
front-right  B
rear-left    C
rear-right   A
```

---

# Phase 3 — Choose Competition Cameras (5 minutes)

Select:

```
best front camera
best rear camera
```

Disable the others for pose fusion.

Two cameras are usually enough for **excellent localization**.

---

# Phase 4 — Quick Yaw Tuning (30 minutes)

Focus on the **best camera first**.

Use **Position B (mid distance)** for tuning.

Test yaw offsets:

```
-2°
-1°
-0.5°
0°
+0.5°
+1°
+2°
```

At each setting:

1. Place robot on mark
2. Compare vision pose vs odometry
3. Record the error direction

Example:

```
yaw 0°      → +15 cm east
yaw -1°     → +6 cm east
yaw -1.5°   → +2 cm east
yaw -2°     → -3 cm west
```

Select the yaw with **minimum error**.

Then verify at:

```
Position A
Position C
```

If errors improve across distances, keep the adjustment.

---

# Phase 5 — Validate the Two-Camera System (15 minutes)

Enable both selected cameras.

Observe behavior when the robot is stationary.

Check for:

```
pose jumping
oscillation
large disagreement
```

If jumping occurs:

- increase vision standard deviation
- reject outlier measurements

---

# Phase 6 — Harden Vision Filtering (10 minutes)

Implement quick filtering rules.

## Reject weak measurements

Ignore vision updates if:

```
tagCount < 2
distance > 6 m
ambiguity > 0.2
```

## Reject large pose jumps

If

```
visionPose − odometryPose > 0.75 m
```

ignore the update.

## Prefer the closest camera

If multiple cameras produce measurements simultaneously:

```
choose measurement with smallest tag distance
```

---

# Phase 7 — Stationary Stability Test (5 minutes)

Place the robot still for ~30 seconds.

Observe estimated pose.

Healthy behavior:

```
< 2–3 cm jitter
no drift
```

---

# Expected Results

Typical improvements from this procedure:

| Before | After |
|------|------|
| ±20 cm jumps | ±5 cm stable |
| 4 cameras fighting | 2 cameras cooperating |
| pose oscillation | smooth corrections |

---

# Biggest Time-Wasters to Avoid

Do **not** spend session time on:

```
perfect camera transform measurements
large calibration datasets
offline solver implementation
full 6-DOF tuning
simultaneous 4-camera calibration
```

Those are valuable **later**, but not necessary for a working robot.

---

# Important Note About Your Robot

Your cameras are mounted **near the corners of a rectangle**.

This geometry means:

```
small yaw errors → large position errors
```

So **yaw adjustment is the most important quick fix**.

Pitch and roll are usually secondary.

---

# Optional Follow-Up (After Event)

When more time is available you can:

- collect a full calibration dataset
- run the least-squares solver
- use diagnostic plots
- calibrate all cameras precisely

But the process above should produce a **stable competition-ready system within one session**.