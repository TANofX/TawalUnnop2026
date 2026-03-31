package frc.robot.commands;

import java.util.List;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.RobotContainer;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.VisionCalibrationEngine;

import com.ctre.phoenix6.swerve.SwerveRequest;

/**
 * Multi-Point Vision Calibration Command
 * 
 * Executes vision calibration at multiple field locations with autonomous routing between points.
 * 
 * IMPORTANT: Robot must be manually placed at the first calibration point before starting.
 * The command accepts the robot's current position as the known starting location.
 * 
 * Workflow:
 * 1. Operator places robot at first calibration point and starts this command
 * 2. Operator clicks "Start This Point" to reset odometry to current position and begin calibration
 * 3. Robot executes spiral calibration motion at that point (30 seconds)
 * 4. Robot autonomously navigates to next point using odometry + proportional drive
 * 5. Repeats steps 2-3 for all remaining points (operator confirmation at each point)
 * 6. Command completes, all data collected
 * 
 * Safety Features:
 * - Operator verification at each point ensures known starting pose for calibration
 * - Slow proportional drive (0.5 m/s max) during routing between points
 * - Timeout at each point (2 minutes) to prevent indefinite waiting
 * - SmartDashboard feedback showing current point, distance, elapsed time
 * 
 * SmartDashboard Integration:
 * - "Vision/Calibration/Execute (Multi-Point Grid)" - Main command button
 * - "Vision/Calibration/Start This Point" - Resets odometry and starts calibration at current point
 * - Real-time status: Point number, distance remaining, current state
 */
public class MultiPointVisionCalibrationCommand extends Command {
    
    private final CommandSwerveDrivetrain drivetrain;
    private final VisionCalibrationEngine calibrationEngine;
    private final List<Pose2d> calibrationPoints;
    private final SwerveRequest.RobotCentric driveRequest = new SwerveRequest.RobotCentric();
    
    private int currentPointIndex = 0;
    private VisionCalibrationCommand currentCalibrationCommand;
    private State sequenceState = State.NAVIGATING_TO_POINT;
    private double stateStartTime = 0;
    
    // Configuration
    private static final double MAX_NAVIGATION_VELOCITY = 0.5;     // m/s - conservative speed
    private static final double ARRIVAL_DISTANCE_TOLERANCE = 0.1;  // meters
    private static final double ARRIVAL_ROTATION_TOLERANCE = 3.0;  // degrees
    private static final double POINT_WAIT_TIMEOUT = 120.0;        // seconds
    
    enum State {
        NAVIGATING_TO_POINT,  // Autonomous routing to next calibration point
        AT_POINT_WAITING,     // Arrived at point, waiting for operator confirmation
        CALIBRATING,          // Running spiral calibration at this point
        COMPLETE              // All points processed
    }
    
    /**
     * Construct multi-point calibration command
     * 
     * @param drivetrain The swerve drivetrain subsystem
     * @param calibrationEngine The vision calibration engine subsystem
     * @param calibrationPoints List of Pose2d locations to calibrate at (including rotation)
     */
    public MultiPointVisionCalibrationCommand(
            CommandSwerveDrivetrain drivetrain,
            VisionCalibrationEngine calibrationEngine,
            List<Pose2d> calibrationPoints) {
        this.drivetrain = drivetrain;
        this.calibrationEngine = calibrationEngine;
        this.calibrationPoints = calibrationPoints;
        
        addRequirements(drivetrain, calibrationEngine);
    }
    
    @Override
    public void initialize() {
        currentPointIndex = 0;
        
        // CRITICAL: Disable vision updates during calibration
        // We want pure odometry-based navigation for accurate testing
        RobotContainer.disableVisionUpdates = true;
        SmartDashboard.putBoolean("VisionCal/VisionUpdatesDisabled", true);
        
        // CRITICAL: Reset odometry to first calibration point (known starting location)
        // This ensures clean odometry for navigation between points
        Pose2d firstPoint = calibrationPoints.get(0);
        drivetrain.resetPose(firstPoint);
        SmartDashboard.putString("VisionCal/OdometryReset", 
            String.format("Odometry reset to Point 1: (%.2f, %.2f)", firstPoint.getX(), firstPoint.getY()));
        
        // START AT FIRST POINT - Robot is already manually placed there
        // Skip navigation directly to waiting state
        sequenceState = State.AT_POINT_WAITING;
        stateStartTime = Timer.getFPGATimestamp();
        currentCalibrationCommand = null;
        
        SmartDashboard.putString("VisionCal/SequenceStatus", 
            "Multi-point calibration ready at Point 1 - Click 'Start This Point' to begin");
        SmartDashboard.putNumber("VisionCal/CurrentPointIndex", currentPointIndex);
        SmartDashboard.putNumber("VisionCal/TotalPoints", calibrationPoints.size());
    }
    
    @Override
    public void execute() {
        // Publish current state
        SmartDashboard.putString("VisionCal/SequenceState", sequenceState.toString());
        SmartDashboard.putNumber("VisionCal/ElapsedStateTime", 
            Timer.getFPGATimestamp() - stateStartTime);
        
        switch (sequenceState) {
            case NAVIGATING_TO_POINT:
                executeNavigation();
                break;
            case AT_POINT_WAITING:
                executeWaiting();
                break;
            case CALIBRATING:
                executeCalibration();
                break;
            case COMPLETE:
                // Nothing to do - waiting for isFinished()
                break;
        }
    }
    
    /**
     * Navigate robot to the next calibration point using proportional drive
     */
    private void executeNavigation() {
        if (currentPointIndex >= calibrationPoints.size()) {
            sequenceState = State.COMPLETE;
            drivetrain.setControl(new SwerveRequest.Idle());
            SmartDashboard.putString("VisionCal/SequenceStatus", "All points complete!");
            return;
        }
        
        Pose2d targetPose = calibrationPoints.get(currentPointIndex);
        Pose2d currentPose = drivetrain.getState().Pose;
        
        // Calculate distance and rotation error to target
        double distanceToTarget = currentPose.getTranslation()
            .getDistance(targetPose.getTranslation());
        double rotationError = Math.abs(
            targetPose.getRotation().minus(currentPose.getRotation()).getDegrees()
        );
        
        SmartDashboard.putNumber("VisionCal/DistanceToPoint", distanceToTarget);
        SmartDashboard.putNumber("VisionCal/RotationErrorToPoint", rotationError);
        SmartDashboard.putString("VisionCal/SequenceStatus", 
            String.format("Navigating to Point %d - Distance: %.2fm", 
                currentPointIndex + 1, distanceToTarget));
        
        // Check if we've arrived at the point
        if (distanceToTarget < ARRIVAL_DISTANCE_TOLERANCE && 
            rotationError < ARRIVAL_ROTATION_TOLERANCE) {
            
            sequenceState = State.AT_POINT_WAITING;
            stateStartTime = Timer.getFPGATimestamp();
            drivetrain.setControl(new SwerveRequest.Idle());
            SmartDashboard.putString("VisionCal/SequenceStatus", 
                "Arrived at Point " + (currentPointIndex + 1) + " - Click 'Start This Point' to calibrate");
            return;
        }
        
        // Calculate proportional drive commands
        double distanceScale = Math.min(1.0, distanceToTarget / 1.0);  // Scale velocity by distance
        double velocityScale = distanceScale * MAX_NAVIGATION_VELOCITY;
        
        // Calculate direction to target (field-relative)
        double dx = targetPose.getX() - currentPose.getX();
        double dy = targetPose.getY() - currentPose.getY();
        double distance = Math.sqrt(dx*dx + dy*dy) + 0.01;  // Add small epsilon to avoid division by zero
        
        double targetVelX = (dx / distance) * velocityScale;
        double targetVelY = (dy / distance) * velocityScale;
        
        // Rotation control: slowly rotate toward target orientation
        double omegaTarget = rotationError > ARRIVAL_ROTATION_TOLERANCE ? 0.3 : 0.0;  // rad/s
        
        // Command swerve drive
        drivetrain.setControl(driveRequest
            .withVelocityX(targetVelX)
            .withVelocityY(targetVelY)
            .withRotationalRate(omegaTarget));
    }
    
    /**
     * Wait at current point for operator to confirm and start calibration
     */
    private void executeWaiting() {
        double elapsedWait = Timer.getFPGATimestamp() - stateStartTime;
        
        SmartDashboard.putString("VisionCal/SequenceStatus", 
            String.format("Point %d/%d ready - waiting for operator (%.1fs elapsed)", 
                currentPointIndex + 1, calibrationPoints.size(), elapsedWait));
        
        // Auto-advance if waiting too long
        if (elapsedWait > POINT_WAIT_TIMEOUT) {
            SmartDashboard.putString("VisionCal/Warning", 
                "Timeout waiting at Point " + (currentPointIndex + 1) + " - auto-advancing");
            proceedToNextPoint();
        }
    }
    
    /**
     * Execute calibration at current point
     */
    private void executeCalibration() {
        if (currentCalibrationCommand == null) {
            // Initialize calibration command for this point
            Pose2d currentPoint = calibrationPoints.get(currentPointIndex);
            currentCalibrationCommand = new VisionCalibrationCommand(
                drivetrain, calibrationEngine, currentPoint);
            currentCalibrationCommand.initialize();
            
            SmartDashboard.putString("VisionCal/SequenceStatus", 
                String.format("Calibrating at Point %d - Running 30-second spiral", 
                    currentPointIndex + 1));
        }
        
        // Execute the calibration command
        currentCalibrationCommand.execute();
        
        // Check if calibration completed
        if (currentCalibrationCommand.isFinished()) {
            SmartDashboard.putString("VisionCal/SequenceStatus", 
                String.format("Point %d calibration complete", currentPointIndex + 1));
            
            proceedToNextPoint();
        }
    }
    
    /**
     * Move to the next calibration point
     */
    private void proceedToNextPoint() {
        currentPointIndex++;
        currentCalibrationCommand = null;
        stateStartTime = Timer.getFPGATimestamp();
        
        if (currentPointIndex < calibrationPoints.size()) {
            sequenceState = State.NAVIGATING_TO_POINT;
            SmartDashboard.putNumber("VisionCal/CurrentPointIndex", currentPointIndex);
        } else {
            sequenceState = State.COMPLETE;
        }
    }
    
    /**
     * SmartDashboard button callback: Confirm arrival at current point and start calibration
     * Call this when operator confirms robot is at the desired location
     */
    public void startCalibrationAtCurrentPoint() {
        if (sequenceState == State.AT_POINT_WAITING) {
            Pose2d currentPoint = calibrationPoints.get(currentPointIndex);
            calibrationEngine.startCalibration(currentPoint, false);  // No orientation verification for multi-point
            sequenceState = State.CALIBRATING;
            stateStartTime = Timer.getFPGATimestamp();
        }
    }
    
    @Override
    public void end(boolean interrupted) {
        drivetrain.setControl(new SwerveRequest.Idle());
        
        // Re-enable vision updates now that calibration is complete
        RobotContainer.disableVisionUpdates = false;
        SmartDashboard.putBoolean("VisionCal/VisionUpdatesDisabled", false);
        
        if (interrupted) {
            SmartDashboard.putString("VisionCal/SequenceStatus", "Multi-point calibration interrupted!");
        } else {
            SmartDashboard.putString("VisionCal/SequenceStatus", 
                "Multi-point calibration complete! Review results on SmartDashboard.");
        }
    }
    
    @Override
    public boolean isFinished() {
        return sequenceState == State.COMPLETE;
    }
}
