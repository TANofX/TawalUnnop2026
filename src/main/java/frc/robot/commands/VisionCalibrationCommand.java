package frc.robot.commands;

import com.ctre.phoenix6.swerve.SwerveRequest;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.VisionCalibrationEngine;
import frc.robot.subsystems.VisionCalibrationEngine.CalibrationState;

/**
 * Vision Calibration Command - CALIBRATION ONLY
 * 
 * This command executes a bounded circular motion pattern to collect vision calibration data.
 * The robot will move in a spiral pattern around the initial position, allowing vision system
 * to observe the same landmarks from multiple angles and distances.
 * 
 * Typical usage in RobotContainer during calibration:
 * SmartDashboard.putData("Calibrate Vision", new VisionCalibrationCommand(
 *   drivetrain, calibrationEngine, knownInitialPose));
 */
public class VisionCalibrationCommand extends Command {
    private final CommandSwerveDrivetrain drivetrain;
    private final VisionCalibrationEngine calibrationEngine;
    private final Pose2d knownInitialPose;
    
    private double startTime;
    private double maxCommandDuration = 30.0; // seconds
    
    // Motion parameters
    private static final double ROTATION_SPEED = 0.5; // rad/s
    private static final double SPIRAL_RADIUS_MIN = 0.2; // meters
    private static final double SPIRAL_RADIUS_MAX = 1.0; // meters
    
    private final SwerveRequest.RobotCentric driveRequest = new SwerveRequest.RobotCentric();
    
    public VisionCalibrationCommand(CommandSwerveDrivetrain drivetrain, 
                                     VisionCalibrationEngine calibrationEngine,
                                     Pose2d knownInitialPose) {
        this.drivetrain = drivetrain;
        this.calibrationEngine = calibrationEngine;
        this.knownInitialPose = knownInitialPose;
        
        addRequirements(drivetrain);
    }
    
    @Override
    public void initialize() {
        System.out.println("Starting vision calibration from pose: " + knownInitialPose);
        startTime = Timer.getFPGATimestamp();
        calibrationEngine.startCalibration(knownInitialPose);
    }
    
    @Override
    public void execute() {
        double elapsedTime = Timer.getFPGATimestamp() - startTime;
        
        // Execute spiral motion pattern: expand radius over time while rotating
        double spiralPhase = elapsedTime / maxCommandDuration; // 0 to 1
        double currentRadius = SPIRAL_RADIUS_MIN + 
            (SPIRAL_RADIUS_MAX - SPIRAL_RADIUS_MIN) * spiralPhase;
        
        double rotationAngle = ROTATION_SPEED * elapsedTime;
        
        // Calculate target position using spiral: circular motion with expanding radius
        double targetX = knownInitialPose.getX() + currentRadius * Math.cos(rotationAngle);
        double targetY = knownInitialPose.getY() + currentRadius * Math.sin(rotationAngle);
        Pose2d targetPose = new Pose2d(targetX, targetY, knownInitialPose.getRotation());
        
        // Simple proportional drive toward target
        Pose2d currentPose = drivetrain.getState().Pose;
        double dx = targetPose.getX() - currentPose.getX();
        double dy = targetPose.getY() - currentPose.getY();
        double distance = Math.hypot(dx, dy);
        
        // Scale velocity based on distance from target
        double maxVelocity = 0.5; // m/s
        double velocityScale = Math.min(1.0, distance / 0.5); // scale down near target
        
        double targetVx = (dx / (distance + 0.01)) * maxVelocity * velocityScale;
        double targetVy = (dy / (distance + 0.01)) * maxVelocity * velocityScale;
        
        // Rotate while moving
        double omegaTarget = ROTATION_SPEED;
        
        drivetrain.setControl(driveRequest
            .withVelocityX(targetVx)
            .withVelocityY(targetVy)
            .withRotationalRate(omegaTarget));
    }
    
    @Override
    public void end(boolean interrupted) {
        drivetrain.setControl(new SwerveRequest.Idle());
        
        if (!interrupted && calibrationEngine.getCalibrationState() == CalibrationState.COLLECTING_DATA) {
            calibrationEngine.stopCalibration();
        }
    }
    
    @Override
    public boolean isFinished() {
        double elapsedTime = Timer.getFPGATimestamp() - startTime;
        return elapsedTime > maxCommandDuration || 
               calibrationEngine.getCalibrationState() == CalibrationState.COMPLETE;
    }
}
