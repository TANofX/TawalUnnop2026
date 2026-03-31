package frc.robot.commands;

import com.ctre.phoenix6.swerve.SwerveRequest;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.RobotContainer;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.VisionCalibrationEngine;
import frc.robot.subsystems.VisionCalibrationEngine.CalibrationState;

/**
 * Vision Transform Estimation Command - PHASE 1 of Calibration
 * 
 * Executes a spiral motion to collect vision vs odometry data,
 * then estimates systematic camera mount errors using least-squares fitting.
 * 
 * Operator then manually updates Constants.Vision with recommended corrections.
 * 
 * Usage:
 *   new VisionTransformEstimationCommand(drivetrain, calibrationEngine, knownPose)
 */
public class VisionTransformEstimationCommand extends Command {
    private final CommandSwerveDrivetrain drivetrain;
    private final VisionCalibrationEngine calibrationEngine;
    private final Pose2d knownInitialPose;
    private final Timer timer = new Timer();
    
    private static final double SPIRAL_TIMEOUT_SECONDS = 30.0;
    private static final double MAX_SPIRAL_RADIUS = 1.0;  // meters
    private static final double ANGULAR_VELOCITY = 0.5;   // rad/sec
    private static final double MAX_DRIVE_VELOCITY = 0.5; // m/s
    
    private final SwerveRequest.RobotCentric driveRequest = new SwerveRequest.RobotCentric();
    
    public VisionTransformEstimationCommand(
        CommandSwerveDrivetrain drivetrain, 
        VisionCalibrationEngine calibrationEngine,
        Pose2d knownInitialPose
    ) {
        this.drivetrain = drivetrain;
        this.calibrationEngine = calibrationEngine;
        this.knownInitialPose = knownInitialPose;
        
        addRequirements(drivetrain, calibrationEngine);
    }
    
    @Override
    public void initialize() {
        // Disable vision updates during calibration (use odometry only)
        RobotContainer.disableVisionUpdates = true;
        SmartDashboard.putBoolean("VisionCal/VisionUpdatesDisabled", true);
        
        // Reset odometry to known starting position
        drivetrain.resetPose(knownInitialPose);
        
        // Start calibration data collection
        calibrationEngine.startTransformCalibration(knownInitialPose);
        
        // User feedback
        SmartDashboard.putString("VisionCal/Phase", "Phase 1: Transform Estimation");
        SmartDashboard.putString("VisionCal/Status", "Executing spiral motion...");
        
        timer.restart();
    }
    
    @Override
    public void execute() {
        double elapsedTime = timer.get();
        
        // Expanding spiral: radius grows from 0.2m to 1.0m over 5 seconds
        double radiusMultiplier = Math.min(1.0, elapsedTime / 5.0);
        double currentRadius = 0.2 + (MAX_SPIRAL_RADIUS - 0.2) * radiusMultiplier;
        
        // Continuous rotation: 0.5 rad/sec
        double angle = elapsedTime * ANGULAR_VELOCITY;
        
        // Target point on spiral
        double targetX = knownInitialPose.getX() + currentRadius * Math.cos(angle);
        double targetY = knownInitialPose.getY() + currentRadius * Math.sin(angle);
        Pose2d targetPose = new Pose2d(targetX, targetY, new Rotation2d(angle));
        
        // Simple proportional drive toward target
        Pose2d currentPose = drivetrain.getState().Pose;
        double dx = targetPose.getX() - currentPose.getX();
        double dy = targetPose.getY() - currentPose.getY();
        double distance = Math.hypot(dx, dy);
        
        // Scale velocity based on distance from target
        double velocityScale = Math.min(1.0, distance / 0.5); // scale down near target
        
        double targetVx = (dx / (distance + 0.01)) * MAX_DRIVE_VELOCITY * velocityScale;
        double targetVy = (dy / (distance + 0.01)) * MAX_DRIVE_VELOCITY * velocityScale;
        
        // Rotate while moving
        double omegaTarget = ANGULAR_VELOCITY;
        
        drivetrain.setControl(driveRequest
            .withVelocityX(targetVx)
            .withVelocityY(targetVy)
            .withRotationalRate(omegaTarget));
        
        // Real-time feedback
        SmartDashboard.putNumber("VisionCal/ElapsedTime", elapsedTime);
        SmartDashboard.putNumber("VisionCal/SpiralRadius", currentRadius);
    }
    
    @Override
    public void end(boolean interrupted) {
        // Stop robot
        drivetrain.setControl(new SwerveRequest.Idle());
        
        // Re-enable vision for normal operation
        RobotContainer.disableVisionUpdates = false;
        SmartDashboard.putBoolean("VisionCal/VisionUpdatesDisabled", false);
        
        // Stop calibration if still collecting
        if (calibrationEngine.getCalibrationState() == CalibrationState.COLLECTING_DATA) {
            calibrationEngine.stopCalibration();
        }
        
        // Results will be processed and published to SmartDashboard
        SmartDashboard.putString("VisionCal/Phase", "Phase 1 Complete");
        SmartDashboard.putString("VisionCal/Status", 
            "Review SmartDashboard for transform corrections. Update Constants.Vision and recompile.");
        
        if (interrupted) {
            SmartDashboard.putString("VisionCal/Error", "Calibration interrupted");
        }
    }
    
    @Override
    public boolean isFinished() {
        return timer.hasElapsed(SPIRAL_TIMEOUT_SECONDS);
    }
}
