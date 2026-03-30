package frc.robot.subsystems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.targeting.PhotonTrackedTarget;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.robot.Constants;

public final class Vision extends AdvancedSubsystem {
    private final ArrayList<VisionCamera> cameras = new ArrayList<>();
    private final VisionConsumer consumer;
    private final Supplier<Pose2d> robotPose;
    private final Map<String, CameraMeasurement> lastMeasurements = new HashMap<>();

    public Vision(VisionConsumer consumer, Supplier<Pose2d> robotPose) {
        this.consumer = consumer;
        this.robotPose = robotPose;
    }

    /**
     * Publish all current camera measurements to SmartDashboard with rich formatting.
     * This creates a visually accessible display that operators can monitor directly.
     * Called by RobotContainer to ensure data visibility without additional UI configuration.
     */
    public void publishCameraMeasurementsToSmartDashboard() {
        // Publish each camera's current measurement
        for (String cameraName : getCameraNames()) {
            Optional<CameraMeasurement> measurement = getCameraMeasurement(cameraName);
            if (measurement.isPresent()) {
                CameraMeasurement m = measurement.get();
                
                // Publish individual fields for easy monitoring
                String prefix = "Vision/Cameras/" + cameraName;
                SmartDashboard.putNumber(prefix + "/X_m", m.estimatedPose.getX());
                SmartDashboard.putNumber(prefix + "/Y_m", m.estimatedPose.getY());
                SmartDashboard.putNumber(prefix + "/Rotation_deg", m.estimatedPose.getRotation().getDegrees());
                SmartDashboard.putNumber(prefix + "/TagsVisible", m.numTagsVisible);
                SmartDashboard.putNumber(prefix + "/AvgTagDistance_m", m.avgDistanceToTags);
                SmartDashboard.putNumber(prefix + "/Timestamp_s", m.timestampSeconds);
                SmartDashboard.putString(prefix + "/Summary", m.toString());
            } else {
                // Show when camera has no data
                String prefix = "Vision/Cameras/" + cameraName;
                SmartDashboard.putString(prefix + "/Summary", cameraName + ": [No data]");
            }
        }
        
        // Also publish count and names for reference
        SmartDashboard.putNumber("Vision/NumCameras", cameras.size());
        SmartDashboard.putStringArray("Vision/CameraNames", getCameraNames().toArray(new String[0]));
    }

    @Override
    public void periodic() {
        for (VisionCamera v : cameras) {
            v.estimatePose(robotPose.get().getTranslation());
        }
        // Publish measurements every cycle so operators can monitor data flow
        publishCameraMeasurementsToSmartDashboard();
    }

    public void addCamera(String name, Transform3d robotToCam) {
        cameras.add(new VisionCamera(name, robotToCam, consumer, this::recordMeasurement));
    }

    /**
     * Get all available camera measurements from the most recent frame.
     * Enables per-camera analysis (e.g., for calibration).
     * 
     * @return List of measurements, one per camera that has data this frame
     */
    public List<CameraMeasurement> getCameraMeasurements() {
        return new ArrayList<>(lastMeasurements.values());
    }

    /**
     * Get measurement from a specific camera by name.
     * 
     * @param cameraName Name of camera (e.g., "club", "heart")
     * @return Last measurement from that camera, or empty Optional
     */
    public Optional<CameraMeasurement> getCameraMeasurement(String cameraName) {
        return Optional.ofNullable(lastMeasurements.get(cameraName));
    }

    /**
     * Get list of all camera names that have been added to the vision system.
     * Useful for dynamic configuration and UI generation.
     * 
     * @return List of camera names (e.g., ["club", "heart", "diamond", "spade"])
     */
    public List<String> getCameraNames() {
        List<String> names = new ArrayList<>();
        for (VisionCamera cam : cameras) {
            names.add(cam.cameraName);
        }
        return names;
    }

    private void recordMeasurement(String cameraName, CameraMeasurement measurement) {
        lastMeasurements.put(cameraName, measurement);
    }

    @Override
    public Command systemCheckCommand() {
        return Commands.none();
    }


    public static interface VisionConsumer {
        public void accept(Pose2d visionRobotPoseMeters, double timestampSeconds,
                Matrix<N3, N1> visionMeasurementStdDevs); // TODO latency between photonvision timestamp and Roborio
                                                          // clock
    }

    /**
     * Per-camera measurement snapshot.
     * Enables calibration engine to track which camera produced which measurement.
     */
    public static class CameraMeasurement {
        public final String cameraName;
        public final Pose2d estimatedPose;
        public final double timestampSeconds;
        public final Matrix<N3, N1> stdDevs;
        public final int numTagsVisible;
        public final double avgDistanceToTags;

        public CameraMeasurement(String cameraName, Pose2d estimatedPose, 
                double timestampSeconds, Matrix<N3, N1> stdDevs,
                int numTagsVisible, double avgDistanceToTags) {
            this.cameraName = cameraName;
            this.estimatedPose = estimatedPose;
            this.timestampSeconds = timestampSeconds;
            this.stdDevs = stdDevs;
            this.numTagsVisible = numTagsVisible;
            this.avgDistanceToTags = avgDistanceToTags;
        }

        @Override
        public String toString() {
            return String.format("%s: (%.2f, %.2f) rot=%.1f° tags=%d dist=%.2fm",
                cameraName, estimatedPose.getX(), estimatedPose.getY(),
                estimatedPose.getRotation().getDegrees(), numTagsVisible, avgDistanceToTags);
        }
    }

    private class VisionCamera {
        private PhotonCamera cam;
        private PhotonPoseEstimator estimator;
        private VisionConsumer consumer;
        private String cameraName;
        private BiConsumer<String, CameraMeasurement> measurementRecorder;

        public VisionCamera(String name, Transform3d robotToCam, VisionConsumer consumer, 
                BiConsumer<String, CameraMeasurement> measurementRecorder) {
            this.cameraName = name;
            this.consumer = consumer;
            this.measurementRecorder = measurementRecorder;
            cam = new PhotonCamera(name);
            estimator = new PhotonPoseEstimator(
                    Constants.apriltagLayout,
                    PhotonPoseEstimator.PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR,
                    robotToCam);

        }

        public void estimatePose(Translation2d robotPosition) {
            Optional<EstimatedRobotPose> visionEst = Optional.empty();
            PhotonCamera camera = cam;
            PhotonPoseEstimator estimator = this.estimator;

            for (var result : camera.getAllUnreadResults()) {
                visionEst = estimator.estimateCoprocMultiTagPose(result);

                if (visionEst.isEmpty()) {
                    visionEst = estimator.estimateLowestAmbiguityPose(result);
                }

                visionEst.ifPresent(
                        estimate -> {
                            // Change our trust in the measurement based on the tags we can see
                            var estStdDevs = updateStdDevs(estimator, estimate, result.getTargets());
                            Pose2d estimatedPose = estimate.estimatedPose.toPose2d();
                            
                            // Calculate tag statistics for CameraMeasurement
                            int numTags = result.getTargets().size();
                            double avgDist = calculateAverageTagDistance(estimator, estimate, result.getTargets());
                            
                            // Record per-camera measurement
                            CameraMeasurement measurement = new CameraMeasurement(
                                cameraName, estimatedPose, estimate.timestampSeconds, estStdDevs,
                                numTags, avgDist
                            );
                            measurementRecorder.accept(cameraName, measurement);
                            
                            SmartDashboard.putNumberArray("Vision/" + cam.getName() + "/estimatedPose",
                                    new double[] { estimatedPose.getX(), estimatedPose.getY(),
                                            estimatedPose.getRotation().getDegrees() });
                            if (estimatedPose.getTranslation().getDistance(robotPosition) < 1.0) {
                                consumer.accept(estimatedPose, estimate.timestampSeconds, estStdDevs);
                            }
                        });
            }
            // estimator.setMultiTagFallbackStrategy(PhotonPoseEstimator.PoseStrategy.LOWEST_AMBIGUITY);
        }

        private double calculateAverageTagDistance(PhotonPoseEstimator poseEstimator, 
                EstimatedRobotPose estimatedPose, List<PhotonTrackedTarget> targets) {
            if (targets.isEmpty()) return 0.0;
            
            double totalDist = 0;
            int count = 0;
            for (var tgt : targets) {
                var tagPose = poseEstimator.getFieldTags().getTagPose(tgt.getFiducialId());
                if (tagPose.isEmpty()) continue;
                
                totalDist += tagPose.get().toPose2d().getTranslation()
                    .getDistance(estimatedPose.estimatedPose.toPose2d().getTranslation());
                count++;
            }
            return count > 0 ? totalDist / count : 0.0;
        }

        private Matrix<N3, N1> updateStdDevs(PhotonPoseEstimator poseEstimator, EstimatedRobotPose estimatedPose,
                List<PhotonTrackedTarget> targets) {
            Matrix<N3, N1> curStdDevs = Constants.Vision.singleTagStdDevs;

            // Pose present. Start running Heuristic
            var estStdDevs = Constants.Vision.singleTagStdDevs;
            int numTags = 0;
            double avgDist = 0;

            // Precalculation - see how many tags we found, and calculate an
            // average-distance metric
            for (var tgt : targets) {
                var tagPose = poseEstimator.getFieldTags().getTagPose(tgt.getFiducialId());
                if (tagPose.isEmpty())
                    continue;
                numTags++;
                avgDist += tagPose
                        .get()
                        .toPose2d()
                        .getTranslation()
                        .getDistance(estimatedPose.estimatedPose.toPose2d().getTranslation());
            }

            if (numTags == 0) {
                // No tags visible. Default to single-tag std devs
                curStdDevs = Constants.Vision.singleTagStdDevs;
            } else {
                // One or more tags visible, run the full heuristic.
                avgDist /= numTags;
                // Decrease std devs if multiple targets are visible
                if (numTags > 1)
                    estStdDevs = Constants.Vision.multiTagStdDevs;
                // Increase std devs based on (average) distance
                if (numTags == 1 && avgDist > 4)
                    estStdDevs = VecBuilder.fill(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
                else
                    estStdDevs = estStdDevs.times(1 + (avgDist * avgDist / 4));
                curStdDevs = estStdDevs;
            }
            return curStdDevs;
        }
    }
}