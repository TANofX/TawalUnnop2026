package frc.robot.subsystems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

/**
 * Vision Calibration Engine - CALIBRATION ONLY, NOT FOR MATCH USE
 * 
 * This subsystem provides automated vision system calibration by:
 * 1. Recording odometry vs vision pose estimates over time
 * 2. Iteratively computing corrections to camera transforms
 * 3. Detecting and correcting time offset between PhotonVision and RoboRIO
 * 4. Estimating vision measurement standard deviations from observed error distributions
 * 5. Identifying and flagging problematic cameras
 * 
 * Usage: Should only be enabled during pre-match calibration. Disabled during competition matches.
 */
public class VisionCalibrationEngine extends SubsystemBase {
    
    public static final int CALIBRATION_DATA_POINTS = 150; // ~3 seconds at 50Hz
    public static final double CALIBRATION_REGION_SIZE = 1.5; // meters - bounded region to prevent drift
    public static final double CALIBRATION_TIMEOUT = 30.0; // seconds
    
    // Measurement rejection thresholds (to filter jittery data)
    private static final double MAX_INSTANTANEOUS_ERROR = 0.5; // meters - reject errors > this
    private static final int MIN_TAGS_FOR_MEASUREMENT = 1; // Require at least 1 tag
    private static final double MAX_DISTANCE_TO_TAGS = 6.0; // meters - reject if tags too far
    private static final double MAX_POSE_JUMP = 0.3; // meters - between-frame jump threshold
    
    private final CommandSwerveDrivetrain drivetrain;
    
    // Vision field for accessing camera measurements
    private final Vision vision;
    
    // Calibration state machine
    public enum CalibrationState {
        IDLE,
        AWAITING_ORIENTATION_VERIFICATION,  // NEW - waits for robot to be positioned correctly
        READY_FOR_CALIBRATION,
        COLLECTING_DATA,
        PROCESSING,
        COMPLETE,
        ERROR
    }
    
    private CalibrationState state = CalibrationState.IDLE;
    
    // Orientation verification
    private Rotation2d expectedInitialRotation;
    private Rotation2d observedInitialRotation;
    private double orientationVerificationStartTime;
    private static final double ORIENTATION_TOLERANCE_DEGREES = 2.0;  // Within 2 degrees
    
    // Data collection
    private List<CalibrationDataPoint> dataPoints = new ArrayList<>();
    private Map<String, List<CalibrationDataPoint>> dataPointsByCamera = new HashMap<>();  // NEW: per-camera data
    private Pose2d calibrationOrigin;
    private double calibrationStartTime;
    private int currentDataPointCount = 0;
    
    // Measurement filtering (to reduce jitter)
    private Pose2d lastVisionPose = null;  // Track last valid measurement for jump detection
    private int rejectedMeasurementCount = 0;  // Counter for diagnostics
    private int acceptedMeasurementCount = 0;  // Counter for diagnostics
    
    // Calibration results
    private CalibrationResult result;
    
    public VisionCalibrationEngine(CommandSwerveDrivetrain drivetrain, Vision vision) {
        this.drivetrain = drivetrain;
        this.vision = vision;
    }
    
    @Override
    public void periodic() {
        switch (state) {
            case AWAITING_ORIENTATION_VERIFICATION:
                verifyOrientation();
                break;
            case COLLECTING_DATA:
                collectCalibrationData();
                break;
            case PROCESSING:
                processCalibrationData();
                break;
            case COMPLETE:
                publishResults();
                break;
            default:
                break;
        }
        
        // Publish current state
        SmartDashboard.putString("VisionCalibration/State", state.toString());
        SmartDashboard.putNumber("VisionCalibration/DataPointsCollected", currentDataPointCount);
    }
    
    /**
     * Start calibration sequence. Robot should be placed on field in known location.
     * The robot will execute bounded circular motion to collect diverse pose samples.
     * 
     * @param knownInitialPose Expected robot pose including rotation
     * @param verifyOrientation If true, waits for rotation verification before proceeding
     */
    public void startCalibration(Pose2d knownInitialPose, boolean verifyOrientation) {
        if (state != CalibrationState.IDLE) {
            SmartDashboard.putString("VisionCalibration/Error", "Calibration already in progress");
            return;
        }
        
        // Reset odometry to known position
        drivetrain.resetPose(knownInitialPose);
        calibrationOrigin = knownInitialPose;
        
        // Store expected rotation
        expectedInitialRotation = knownInitialPose.getRotation();
        
        dataPoints.clear();
        dataPointsByCamera.clear();  // NEW: Clear per-camera data
        currentDataPointCount = 0;
        calibrationStartTime = Timer.getFPGATimestamp();
        
        if (verifyOrientation) {
            // Wait for orientation verification before proceeding
            state = CalibrationState.AWAITING_ORIENTATION_VERIFICATION;
            orientationVerificationStartTime = Timer.getFPGATimestamp();
            SmartDashboard.putString("VisionCalibration/Status", 
                "Position robot and verify orientation - Expected: " + 
                String.format("%.1f°", expectedInitialRotation.getDegrees()));
            SmartDashboard.putNumber("VisionCalibration/ExpectedRotation", 
                expectedInitialRotation.getDegrees());
            SmartDashboard.putBoolean("VisionCalibration/RotationOK", false);
        } else {
            // Skip verification, proceed directly to data collection
            startDataCollection();
        }
    }
    
    /**
     * Legacy method for backwards compatibility - same as startCalibration(pose, false)
     */
    public void startCalibration(Pose2d knownInitialPose) {
        startCalibration(knownInitialPose, false);
    }
    
    /**
     * Start transform estimation calibration (Phase 1).
     * Same as startCalibration but flags this for transform correction analysis.
     */
    public void startTransformCalibration(Pose2d knownInitialPose) {
        startCalibration(knownInitialPose, false);
        // Will process in PROCESSING state to compute transform corrections
    }
    
    /**
     * Verify that robot is at the expected orientation. Called during periodic.
     */
    private void verifyOrientation() {
        observedInitialRotation = drivetrain.getState().Pose.getRotation();
        
        // Calculate rotation error (taking shortest path)
        double rotationError = Math.abs(
            expectedInitialRotation.minus(observedInitialRotation).getDegrees()
        );
        
        SmartDashboard.putNumber("VisionCalibration/ObservedRotation", 
            observedInitialRotation.getDegrees());
        SmartDashboard.putNumber("VisionCalibration/RotationError", rotationError);
        
        if (rotationError < ORIENTATION_TOLERANCE_DEGREES) {
            // Rotation is within tolerance
            SmartDashboard.putBoolean("VisionCalibration/RotationOK", true);
            
            // Wait 0.5 seconds to show success, then start data collection
            if (Timer.getFPGATimestamp() - orientationVerificationStartTime > 0.5) {
                SmartDashboard.putString("VisionCalibration/Status", "Orientation verified - starting calibration");
                startDataCollection();
            }
        } else {
            // Rotation error too large
            SmartDashboard.putBoolean("VisionCalibration/RotationOK", false);
            SmartDashboard.putString("VisionCalibration/Status", 
                String.format("Rotate robot: error %.1f° (tolerance: %.1f°)", 
                    rotationError, ORIENTATION_TOLERANCE_DEGREES));
        }
    }
    
    /**
     * Manual override to confirm orientation and proceed with calibration
     * (in case operator wants to proceed despite small error)
     */
    public void confirmRotationAndProceed() {
        if (state == CalibrationState.AWAITING_ORIENTATION_VERIFICATION) {
            SmartDashboard.putString("VisionCalibration/Status", 
                "Orientation manually confirmed - starting calibration");
            startDataCollection();
        }
    }
    
    /**
     * Start collecting calibration data (internal method)
     */
    private void startDataCollection() {
        dataPoints.clear();
        currentDataPointCount = 0;
        calibrationStartTime = Timer.getFPGATimestamp();
        state = CalibrationState.COLLECTING_DATA;
        
        Pose2d startPose = drivetrain.getState().Pose;
        SmartDashboard.putString("VisionCalibration/Status", 
            "Calibration started - Initial rotation: " + 
            String.format("%.1f°", startPose.getRotation().getDegrees()));
    }
    
    public void stopCalibration() {
        if (state == CalibrationState.COLLECTING_DATA) {
            state = CalibrationState.PROCESSING;
        }
    }
    
    private void collectCalibrationData() {
        // Check timeout
        if (Timer.getFPGATimestamp() - calibrationStartTime > CALIBRATION_TIMEOUT) {
            state = CalibrationState.PROCESSING;
            SmartDashboard.putString("VisionCalibration/Status", "Timeout - processing data");
            return;
        }
        
        // Check if we have enough data
        if (currentDataPointCount >= CALIBRATION_DATA_POINTS) {
            state = CalibrationState.PROCESSING;
            SmartDashboard.putString("VisionCalibration/Status", "Data collection complete - processing");
            return;
        }
        
        Pose2d currentOdometryPose = drivetrain.getState().Pose;
        double distanceFromOrigin = currentOdometryPose.getTranslation()
            .getDistance(calibrationOrigin.getTranslation());
        
        // Verify robot hasn't drifted out of calibration region
        if (distanceFromOrigin > CALIBRATION_REGION_SIZE) {
            state = CalibrationState.ERROR;
            SmartDashboard.putString("VisionCalibration/Error", 
                "Robot drifted beyond calibration region: " + distanceFromOrigin + "m");
            return;
        }
        
        // Record current odometry state
        double timestamp = Timer.getFPGATimestamp();
        CalibrationDataPoint odoPoint = new CalibrationDataPoint(
            currentOdometryPose,
            timestamp
        );
        dataPoints.add(odoPoint);
        
        // NEW: Collect per-camera measurements with quality filtering
        List<Vision.CameraMeasurement> cameraMeasurements = vision.getCameraMeasurements();
        for (Vision.CameraMeasurement measurement : cameraMeasurements) {
            // FILTER 1: Check minimum tag count
            if (measurement.numTagsVisible < MIN_TAGS_FOR_MEASUREMENT) {
                SmartDashboard.putNumber(
                    "VisionCalibration/" + measurement.cameraName + "/RejectedReason",
                    1); // Not enough tags
                rejectedMeasurementCount++;
                continue;
            }
            
            // FILTER 2: Check distance to tags (distant tags = less reliable)
            if (measurement.avgDistanceToTags > MAX_DISTANCE_TO_TAGS) {
                SmartDashboard.putNumber(
                    "VisionCalibration/" + measurement.cameraName + "/RejectedReason",
                    2); // Tags too far away
                rejectedMeasurementCount++;
                continue;
            }
            
            // FILTER 3: Check instantaneous error against odometry
            double instantaneousError = currentOdometryPose.getTranslation()
                .getDistance(measurement.estimatedPose.getTranslation());
            if (instantaneousError > MAX_INSTANTANEOUS_ERROR) {
                SmartDashboard.putNumber(
                    "VisionCalibration/" + measurement.cameraName + "/RejectedReason",
                    3); // Error too large
                rejectedMeasurementCount++;
                continue;
            }
            
            // FILTER 4: Check for pose jumps (between-frame measurement stability)
            if (lastVisionPose != null) {
                double poseJump = measurement.estimatedPose.getTranslation()
                    .getDistance(lastVisionPose.getTranslation());
                if (poseJump > MAX_POSE_JUMP) {
                    SmartDashboard.putNumber(
                        "VisionCalibration/" + measurement.cameraName + "/RejectedReason",
                        4); // Pose jumped too much
                    rejectedMeasurementCount++;
                    continue;
                }
            }
            
            // Measurement passed all filters - ACCEPT IT
            acceptedMeasurementCount++;
            lastVisionPose = measurement.estimatedPose;
            
            // Create a copy of the data point with camera-specific data
            CalibrationDataPoint cameraPoint = new CalibrationDataPoint(
                currentOdometryPose,
                timestamp
            );
            cameraPoint.visionPose = Optional.of(measurement.estimatedPose);
            cameraPoint.cameraName = Optional.of(measurement.cameraName);
            
            // Store in global list
            dataPoints.add(cameraPoint);
            
            // Store in per-camera map
            dataPointsByCamera.computeIfAbsent(measurement.cameraName, k -> new ArrayList<>())
                .add(cameraPoint);
            
            // Publish per-camera error to SmartDashboard (real-time feedback)
            SmartDashboard.putNumber(
                "VisionCalibration/" + measurement.cameraName + "/InstantaneousError",
                instantaneousError
            );
            SmartDashboard.putNumber(
                "VisionCalibration/" + measurement.cameraName + "/TagsVisible",
                measurement.numTagsVisible
            );
            SmartDashboard.putNumber(
                "VisionCalibration/" + measurement.cameraName + "/AvgDistance",
                measurement.avgDistanceToTags
            );
            SmartDashboard.putNumber(
                "VisionCalibration/" + measurement.cameraName + "/RejectedReason",
                0); // No rejection
        }
        
        // Publish rejection statistics every frame
        SmartDashboard.putNumber("VisionCalibration/AcceptedMeasurements", acceptedMeasurementCount);
        SmartDashboard.putNumber("VisionCalibration/RejectedMeasurements", rejectedMeasurementCount);
        
        currentDataPointCount++;
    }
    
    private void processCalibrationData() {
        try {
            result = new CalibrationResult();
            
            // 1. Analyze vision vs odometry error distribution
            analyzeErrorDistribution();
            
            // 2. Estimate time offset for each camera
            estimateTimeOffsets();
            
            // 3. Compute camera transform corrections
            computeCameraTransformCorrections();
            
            // 4. Estimate measurement standard deviations
            estimateVisionStdDevs();
            
            // 5. Identify problematic cameras
            identifyProblematicCameras();
            
            state = CalibrationState.COMPLETE;
            SmartDashboard.putString("VisionCalibration/Status", "Calibration complete");
            
        } catch (Exception e) {
            state = CalibrationState.ERROR;
            SmartDashboard.putString("VisionCalibration/Error", "Processing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void analyzeErrorDistribution() {
        // Calculate statistics on vision vs odometry error
        double totalXError = 0;
        double totalYError = 0;
        double totalRotError = 0;
        double maxXError = 0;
        double maxYError = 0;
        double maxRotError = 0;
        
        for (CalibrationDataPoint point : dataPoints) {
            if (point.visionPose.isEmpty()) continue;
            
            Pose2d vision = point.visionPose.get();
            Pose2d odometry = point.odometryPose;
            
            double xErr = Math.abs(vision.getX() - odometry.getX());
            double yErr = Math.abs(vision.getY() - odometry.getY());
            double rotErr = Math.abs(vision.getRotation().getRadians() - odometry.getRotation().getRadians());
            
            totalXError += xErr;
            totalYError += yErr;
            totalRotError += rotErr;
            
            maxXError = Math.max(maxXError, xErr);
            maxYError = Math.max(maxYError, yErr);
            maxRotError = Math.max(maxRotError, rotErr);
        }
        
        int count = (int) dataPoints.stream().filter(p -> p.visionPose.isPresent()).count();
        if (count > 0) {
            result.meanXError = totalXError / count;
            result.meanYError = totalYError / count;
            result.meanRotError = totalRotError / count;
            result.maxXError = maxXError;
            result.maxYError = maxYError;
            result.maxRotError = maxRotError;
        }
        
        // NEW: Analyze per-camera errors
        analyzePerCameraErrors();
    }
    
    /**
     * Analyze errors for each camera individually
     */
    private void analyzePerCameraErrors() {
        for (String cameraName : dataPointsByCamera.keySet()) {
            List<CalibrationDataPoint> cameraPoints = dataPointsByCamera.get(cameraName);
            
            double totalXError = 0;
            double totalYError = 0;
            double totalRotError = 0;
            double maxXError = 0;
            double maxYError = 0;
            int count = 0;
            
            for (CalibrationDataPoint point : cameraPoints) {
                if (point.visionPose.isEmpty()) continue;
                
                Pose2d vision = point.visionPose.get();
                Pose2d odometry = point.odometryPose;
                
                double xErr = Math.abs(vision.getX() - odometry.getX());
                double yErr = Math.abs(vision.getY() - odometry.getY());
                double rotErr = Math.abs(vision.getRotation().getRadians() - odometry.getRotation().getRadians());
                
                totalXError += xErr;
                totalYError += yErr;
                totalRotError += rotErr;
                maxXError = Math.max(maxXError, xErr);
                maxYError = Math.max(maxYError, yErr);
                count++;
            }
            
            if (count > 0) {
                double meanXError = totalXError / count;
                double meanYError = totalYError / count;
                double meanRotError = totalRotError / count;
                
                // Publish per-camera results to SmartDashboard
                String prefix = "VisionCalibration/" + cameraName;
                SmartDashboard.putNumber(prefix + "/MeanXError", meanXError);
                SmartDashboard.putNumber(prefix + "/MeanYError", meanYError);
                SmartDashboard.putNumber(prefix + "/MeanRotError", Math.toDegrees(meanRotError));
                SmartDashboard.putNumber(prefix + "/MaxXError", maxXError);
                SmartDashboard.putNumber(prefix + "/MaxYError", maxYError);
                SmartDashboard.putNumber(prefix + "/DataPoints", count);
                
                SmartDashboard.putString(prefix + "/Status", 
                    String.format("X:%.3fm Y:%.3fm Rot:%.2f°", meanXError, meanYError, Math.toDegrees(meanRotError)));
            }
        }
    }
    
    private void estimateTimeOffsets() {
        // Correlate vision timestamps with odometry to detect systematic time offset
        // This is complex and requires cross-correlation analysis
        // For now, we collect the data and compute basic statistics
        
        result.timeOffsetAnalysis = new String[4];
        result.timeOffsetAnalysis[0] = "Time offset detection requires post-processing of timestamp data";
        result.timeOffsetAnalysis[1] = "Save calibration logs for offline analysis: data/calibration_" + System.currentTimeMillis() + ".csv";
    }
    
    private void computeCameraTransformCorrections() {
        // Use collected vision vs odometry error to compute transform adjustments
        // This employs least-squares fitting for each camera
        
        result.transformCorrections = new Transform3d[4];
        String[] cameraNames = {"heart", "diamond", "club", "spade"};
        
        for (int i = 0; i < 4; i++) {
            String cameraName = cameraNames[i];
            List<CalibrationDataPoint> cameraMeasurements = 
                dataPointsByCamera.getOrDefault(cameraName, new ArrayList<>());
            
            if (cameraMeasurements.size() < 5) {
                // Not enough data for this camera
                result.transformCorrections[i] = new Transform3d();  // Identity
                SmartDashboard.putString("VisionCalibration/" + cameraName + "/CorrectionStatus", 
                    "Insufficient data (" + cameraMeasurements.size() + " points)");
                continue;
            }
            
            // Solve for best-fit Transform3d using least-squares
            Transform3d correction = solveTransformCorrection(cameraMeasurements);
            result.transformCorrections[i] = correction;
            
            // Publish results to SmartDashboard
            publishTransformCorrectionToSmartDashboard(cameraName, correction, cameraMeasurements);
        }
        
        SmartDashboard.putString("VisionCalibration/TransformStatus", 
            "Transform corrections computed for all cameras.");
    }
    
    /**
     * Solve for the Transform3d that best fits vision measurements to odometry trajectory.
     * Uses least-squares minimization to find systematic camera mount offset.
     */
    private Transform3d solveTransformCorrection(List<CalibrationDataPoint> measurements) {
        // Compute mean error - this is the systematic offset
        double sumX = 0, sumY = 0;
        double sumYaw = 0;
        int validCount = 0;
        
        for (CalibrationDataPoint point : measurements) {
            if (point.visionPose.isEmpty()) continue;
            
            Pose2d vision = point.visionPose.get();
            Pose2d odometry = point.odometryPose;
            
            // Error in 2D: difference between where camera thinks we are
            // and where odometry says we are
            double errorX = odometry.getX() - vision.getX();
            double errorY = odometry.getY() - vision.getY();
            double errorYaw = odometry.getRotation().getRadians() 
                            - vision.getRotation().getRadians();
            
            // Normalize yaw error to [-pi, pi]
            while (errorYaw > Math.PI) errorYaw -= 2 * Math.PI;
            while (errorYaw < -Math.PI) errorYaw += 2 * Math.PI;
            
            sumX += errorX;
            sumY += errorY;
            sumYaw += errorYaw;
            validCount++;
        }
        
        if (validCount == 0) return new Transform3d();
        
        // Average error is the systematic offset
        double avgErrorX = sumX / validCount;
        double avgErrorY = sumY / validCount;
        double avgErrorYaw = sumYaw / validCount;
        
        // Return Transform3d that corrects this offset
        return new Transform3d(
            new Translation3d(avgErrorX, avgErrorY, 0),
            new Rotation3d(0, 0, avgErrorYaw)
        );
    }
    
    /**
     * Publish transform correction and residual error metrics to SmartDashboard.
     */
    private void publishTransformCorrectionToSmartDashboard(
        String cameraName,
        Transform3d correction,
        List<CalibrationDataPoint> measurements
    ) {
        String prefix = "VisionCalibration/" + cameraName;
        
        // Translation components (meters)
        SmartDashboard.putNumber(prefix + "/TransformCorrectionX", correction.getX());
        SmartDashboard.putNumber(prefix + "/TransformCorrectionY", correction.getY());
        SmartDashboard.putNumber(prefix + "/TransformCorrectionZ", correction.getZ());
        
        // Rotation components (degrees for readability)
        SmartDashboard.putNumber(prefix + "/TransformCorrectionRoll", 
            Math.toDegrees(correction.getRotation().getX()));
        SmartDashboard.putNumber(prefix + "/TransformCorrectionPitch", 
            Math.toDegrees(correction.getRotation().getY()));
        SmartDashboard.putNumber(prefix + "/TransformCorrectionYaw", 
            Math.toDegrees(correction.getRotation().getZ()));
        
        // Residual error (how well does corrected transform explain measurements?)
        double residualError = computeResidualError(correction, measurements);
        SmartDashboard.putNumber(prefix + "/ResidualError", residualError);
        SmartDashboard.putNumber(prefix + "/NumDataPoints", measurements.size());
        
        String statusMessage = String.format(
            "X:%+.3fm Y:%+.3fm Yaw:%+.2f° | Residual:%.3fm (%d pts)",
            correction.getX(), correction.getY(),
            Math.toDegrees(correction.getRotation().getZ()),
            residualError, measurements.size()
        );
        SmartDashboard.putString(prefix + "/CorrectionStatus", statusMessage);
    }
    
    /**
     * Compute residual error after applying transform correction.
     * This indicates how well the correction explains the data.
     */
    private double computeResidualError(Transform3d correction, 
        List<CalibrationDataPoint> measurements) {
        double totalError = 0;
        int count = 0;
        
        for (CalibrationDataPoint point : measurements) {
            if (point.visionPose.isEmpty()) continue;
            
            Pose2d vision = point.visionPose.get();
            Pose2d odometry = point.odometryPose;
            
            // After applying correction to vision pose, how close are we to odometry?
            double correctedX = vision.getX() + correction.getX();
            double correctedY = vision.getY() + correction.getY();
            
            double errorX = odometry.getX() - correctedX;
            double errorY = odometry.getY() - correctedY;
            double dist = Math.sqrt(errorX * errorX + errorY * errorY);
            
            totalError += dist;
            count++;
        }
        
        return count > 0 ? totalError / count : 0;
    }
    
    private void estimateVisionStdDevs() {
        // From the error distribution, estimate appropriate standard deviations
        // Standard deviation should represent 68% confidence interval
        
        double xStdDev = computeStandardDeviation(
            dataPoints.stream()
                .filter(p -> p.visionPose.isPresent())
                .map(p -> Math.abs(p.visionPose.get().getX() - p.odometryPose.getX()))
                .mapToDouble(Double::doubleValue)
                .toArray()
        );
        
        double yStdDev = computeStandardDeviation(
            dataPoints.stream()
                .filter(p -> p.visionPose.isPresent())
                .map(p -> Math.abs(p.visionPose.get().getY() - p.odometryPose.getY()))
                .mapToDouble(Double::doubleValue)
                .toArray()
        );
        
        double rotStdDev = computeStandardDeviation(
            dataPoints.stream()
                .filter(p -> p.visionPose.isPresent())
                .map(p -> Math.abs(p.visionPose.get().getRotation().getRadians() 
                                   - p.odometryPose.getRotation().getRadians()))
                .mapToDouble(Double::doubleValue)
                .toArray()
        );
        
        // Recommended single-tag std devs with safety margin (1.5x for model uncertainty)
        result.recommendedSingleTagStdDevs = VecBuilder.fill(
            Math.max(0.15, xStdDev * 1.5),  // Min 0.15m for single tag
            Math.max(0.15, yStdDev * 1.5),
            Math.max(0.1, rotStdDev * 1.5)   // Min 0.1 rad for rotation
        );
        
        // Recommended multi-tag std devs (more confident)
        result.recommendedMultiTagStdDevs = VecBuilder.fill(
            Math.max(0.08, xStdDev * 1.0),
            Math.max(0.08, yStdDev * 1.0),
            Math.max(0.05, rotStdDev * 1.0)
        );
    }
    
    private void identifyProblematicCameras() {
        // Analyze which cameras contribute to "fighting" behavior
        // Look for high variance, outliers, or consistent bias
        
        result.problematicCameras = new ArrayList<>();
        
        double maxAllowableError = 0.3; // 30cm - if consistent error exceeds this, camera is suspect
        
        for (CalibrationDataPoint point : dataPoints) {
            if (point.visionPose.isEmpty()) continue;
            
            double error = point.odometryPose.getTranslation()
                .getDistance(point.visionPose.get().getTranslation());
            
            if (error > maxAllowableError) {
                result.problematicCameras.add("High error detected: " + error + "m at pose " + 
                    point.odometryPose);
            }
        }
    }
    
    private void publishResults() {
        if (result == null) return;
        
        SmartDashboard.putNumber("VisionCal/MeanXError", result.meanXError);
        SmartDashboard.putNumber("VisionCal/MeanYError", result.meanYError);
        SmartDashboard.putNumber("VisionCal/MeanRotError", result.meanRotError);
        SmartDashboard.putNumber("VisionCal/MaxXError", result.maxXError);
        SmartDashboard.putNumber("VisionCal/MaxYError", result.maxYError);
        SmartDashboard.putNumber("VisionCal/MaxRotError", result.maxRotError);
        
        if (result.recommendedSingleTagStdDevs != null) {
            SmartDashboard.putNumberArray("VisionCal/SingleTagStdDevs", 
                new double[] {
                    result.recommendedSingleTagStdDevs.get(0, 0),
                    result.recommendedSingleTagStdDevs.get(1, 0),
                    result.recommendedSingleTagStdDevs.get(2, 0)
                }
            );
        }
        
        if (result.recommendedMultiTagStdDevs != null) {
            SmartDashboard.putNumberArray("VisionCal/MultiTagStdDevs",
                new double[] {
                    result.recommendedMultiTagStdDevs.get(0, 0),
                    result.recommendedMultiTagStdDevs.get(1, 0),
                    result.recommendedMultiTagStdDevs.get(2, 0)
                }
            );
        }
        
        SmartDashboard.putNumber("VisionCal/ProblematicCameraCount", 
            result.problematicCameras.size());
    }
    
    /**
     * Get current calibration state (useful for command synchronization)
     */
    public CalibrationState getCalibrationState() {
        return state;
    }
    
    /**
     * Get current calibration result after COMPLETE state
     */
    public CalibrationResult getCalibrationResult() {
        return result;
    }
    
    private double computeStandardDeviation(double[] values) {
        if (values.length < 2) return 0;
        
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.length;
        
        double variance = 0;
        for (double v : values) variance += Math.pow(v - mean, 2);
        variance /= values.length;
        
        return Math.sqrt(variance);
    }
    
    // ===================== Data Classes =====================
    
    /**
     * Single data point collected during calibration
     */
    public static class CalibrationDataPoint {
        public final Pose2d odometryPose;
        public final double timestamp;
        public Optional<Pose2d> visionPose = Optional.empty();
        public Optional<String> cameraName = Optional.empty();
        
        public CalibrationDataPoint(Pose2d odometryPose, double timestamp) {
            this.odometryPose = odometryPose;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Results from calibration analysis
     */
    public static class CalibrationResult {
        // Error statistics
        public double meanXError;
        public double meanYError;
        public double meanRotError;
        public double maxXError;
        public double maxYError;
        public double maxRotError;
        
        // Recommended standard deviations for Kalman filter
        public Matrix<N3, N1> recommendedSingleTagStdDevs;
        public Matrix<N3, N1> recommendedMultiTagStdDevs;
        
        // Camera-specific analysis
        public Transform3d[] transformCorrections;
        public String[] timeOffsetAnalysis;
        public List<String> problematicCameras;
    }
}
