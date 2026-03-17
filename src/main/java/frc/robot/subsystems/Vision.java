package frc.robot.subsystems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.targeting.PhotonTrackedTarget;

import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.robot.Constants;

public final class Vision extends AdvancedSubsystem {
    private final ArrayList<VisionCamera> cameras = new ArrayList<>();
    private final VisionConsumer consumer;

    public Vision(VisionConsumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void periodic() {
        for (VisionCamera v : cameras) {
            v.estimatePose();
        }
    }

    public void addCamera(String name, Transform3d robotToCam) {
        cameras.add(new VisionCamera(name, robotToCam, consumer));
    }

    @Override
    public Command systemCheckCommand() {
        return Commands.none();
    }

    public static interface VisionConsumer {
        public void accept(Pose2d visionRobotPoseMeters, double timestampSeconds,
                Matrix<N3, N1> visionMeasurementStdDevs); //TODO latency between photonvision timestamp and Roborio clock
    }

    private class VisionCamera {
        private PhotonCamera cam;
        private PhotonPoseEstimator estimator;
        private VisionConsumer consumer;

        public VisionCamera(String name, Transform3d robotToCam, VisionConsumer consumer) {
            this.consumer = consumer;
            cam = new PhotonCamera(name);
            estimator = new PhotonPoseEstimator(
                    Constants.apriltagLayout,
                    PhotonPoseEstimator.PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR,
                    robotToCam);
        }

        public void estimatePose() {
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
                            ;

                            consumer.accept(estimate.estimatedPose.toPose2d(), estimate.timestampSeconds, estStdDevs);
                        });
            }
            // estimator.setMultiTagFallbackStrategy(PhotonPoseEstimator.PoseStrategy.LOWEST_AMBIGUITY);
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
                    estStdDevs = estStdDevs.times(1 + (avgDist * avgDist / 30));
                curStdDevs = estStdDevs;
            }
            return curStdDevs;
        }
    }
}