package frc.robot.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.ctre.phoenix6.swerve.SwerveRequest;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.CommandSwerveDrivetrain;

/**
 * Calibration Points Validation Command
 * 
 * Drives the robot through each calibration point defined in calibration_points.csv
 * without executing the full calibration spiral. This allows operators to verify that
 * the robot can successfully navigate to expected field positions before committing to
 * a full calibration run.
 * 
 * Usage:
 *   new CalibrationPointsValidationCommand(drivetrain)
 * 
 * Workflow:
 * 1. Click button to start validation
 * 2. For each calibration point:
 *    a. Robot navigates to the point
 *    b. Operator visually verifies robot is at correct location
 *    c. Operator clicks next button to advance to next point
 * 3. After all points visited, command completes
 */
public class CalibrationPointsValidationCommand extends Command {
    
    private final CommandSwerveDrivetrain drivetrain;
    private final SwerveRequest.RobotCentric driveRequest = new SwerveRequest.RobotCentric();
    
    private List<CalibrationPoint> calibrationPoints = new ArrayList<>();
    private int currentPointIndex = 0;
    private CalibrationPoint currentPoint = null;
    private Pose2d targetPose = null;
    private Pose2d startingPose = null;
    
    private Timer navigationTimer = new Timer();
    private boolean navigationComplete = false;
    private boolean operatorAdvancedToNext = false;
    private boolean returningToStart = false;
    
    private static final double MAX_DRIVE_VELOCITY = 0.5;      // m/s
    private static final double MAX_ANGULAR_VELOCITY = 1.0;    // rad/s
    private static final double POSITION_TOLERANCE = 0.15;     // meters
    private static final double ROTATION_TOLERANCE = 5.0;      // degrees
    private static final double MAX_NAVIGATION_TIME = 30.0;    // seconds per point
    
    private static final String CSV_PATH = "calibration_points.csv";
    
    /**
     * Calibration point data structure
     */
    private static class CalibrationPoint {
        String name;
        double x;
        double y;
        double rotationDegrees;
        
        CalibrationPoint(String name, double x, double y, double rotationDegrees, boolean isInitialPosition) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.rotationDegrees = rotationDegrees;
            // isInitialPosition not used in validation, but kept in CSV for consistency
        }
        
        Pose2d toPose2d() {
            return new Pose2d(x, y, Rotation2d.fromDegrees(rotationDegrees));
        }
    }
    
    public CalibrationPointsValidationCommand(CommandSwerveDrivetrain drivetrain) {
        this.drivetrain = drivetrain;
        addRequirements(drivetrain);
    }
    
    @Override
    public void initialize() {
        SmartDashboard.putString("CalibrationValidation/Status", "Loading calibration points...");
        
        // Store starting pose so we can return to it after validation
        startingPose = drivetrain.getState().Pose;
        SmartDashboard.putNumber("CalibrationValidation/StartingX", startingPose.getX());
        SmartDashboard.putNumber("CalibrationValidation/StartingY", startingPose.getY());
        SmartDashboard.putNumber("CalibrationValidation/StartingRotation", startingPose.getRotation().getDegrees());
        
        // Load calibration points from CSV
        loadCalibrationPoints();
        
        if (calibrationPoints.isEmpty()) {
            SmartDashboard.putString("CalibrationValidation/Status", "ERROR: No calibration points loaded!");
            return;
        }
        
        // Start at the first point
        currentPointIndex = 0;
        returningToStart = false;
        navigateToNextPoint();
        
        SmartDashboard.putNumber("CalibrationValidation/TotalPoints", calibrationPoints.size());
        SmartDashboard.putNumber("CalibrationValidation/CurrentPointIndex", currentPointIndex + 1);
        SmartDashboard.putString("CalibrationValidation/Status", "Navigating to first point...");
    }
    
    @Override
    public void execute() {
        if (calibrationPoints.isEmpty()) {
            return;
        }
        
        // If returning to start, navigate back
        if (returningToStart) {
            navigateTowardTarget();
            return;
        }
        
        // Check if operator clicked "next" button
        boolean nextButtonPressed = SmartDashboard.getBoolean("CalibrationValidation/NextPoint", false);
        if (nextButtonPressed) {
            SmartDashboard.putBoolean("CalibrationValidation/NextPoint", false); // Reset button
            operatorAdvancedToNext = true;
        }
        
        // If at a point and operator advanced, move to next point
        if (navigationComplete && operatorAdvancedToNext) {
            operatorAdvancedToNext = false;
            currentPointIndex++;
            
            if (currentPointIndex < calibrationPoints.size()) {
                navigateToNextPoint();
            } else {
                // All points visited - now return to starting position
                returningToStart = true;
                targetPose = startingPose;
                navigationComplete = false;
                navigationTimer.restart();
                SmartDashboard.putString("CalibrationValidation/Status", 
                    "Returning to starting position for calibration...");
                System.out.println("[CalibrationValidation] Returning to start: (" + 
                    startingPose.getX() + ", " + startingPose.getY() + ")");
                return;
            }
        }
        
        // Navigate toward current target
        if (targetPose != null && !navigationComplete) {
            navigateTowardTarget();
        }
    }
    
    @Override
    public boolean isFinished() {
        // Finished when we've returned to starting position after visiting all points
        return returningToStart && navigationComplete;
    }
    
    @Override
    public void end(boolean interrupted) {
        drivetrain.applyRequest(() -> driveRequest.withVelocityX(0).withVelocityY(0).withRotationalRate(0));
        if (interrupted) {
            SmartDashboard.putString("CalibrationValidation/Status", "CANCELLED by operator");
            System.out.println("[CalibrationValidation] Command cancelled by operator");
        } else {
            SmartDashboard.putString("CalibrationValidation/Status", 
                "COMPLETE - Robot returned to starting position and is ready for calibration");
            System.out.println("[CalibrationValidation] Validation complete - robot at starting position ready for calibration");
        }
    }
    
    /**
     * Load calibration points from CSV file
     */
    private void loadCalibrationPoints() {
        calibrationPoints.clear();
        
        try {
            InputStream inputStream = Filesystem.getDeployDirectory().toPath()
                .resolve(CSV_PATH).toUri().toURL().openStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                
                // Skip comments and empty lines
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                
                // Skip header
                if (line.startsWith("name,")) {
                    continue;
                }
                
                // Parse CSV line
                String[] parts = line.split(",");
                if (parts.length < 5) {
                    System.err.println("[CalibrationValidation] Skipping invalid line " + lineNum + ": " + line);
                    continue;
                }
                
                try {
                    String name = parts[0].trim();
                    double x = Double.parseDouble(parts[1].trim());
                    double y = Double.parseDouble(parts[2].trim());
                    double rotation = Double.parseDouble(parts[3].trim());
                    boolean isInitial = Boolean.parseBoolean(parts[4].trim());
                    
                    calibrationPoints.add(new CalibrationPoint(name, x, y, rotation, isInitial));
                } catch (NumberFormatException e) {
                    System.err.println("[CalibrationValidation] Failed to parse line " + lineNum + ": " + e.getMessage());
                }
            }
            
            reader.close();
            System.out.println("[CalibrationValidation] Loaded " + calibrationPoints.size() + " calibration points");
            
        } catch (IOException e) {
            System.err.println("[CalibrationValidation] Failed to load calibration points: " + e.getMessage());
        }
    }
    
    /**
     * Start navigation to the next calibration point
     */
    private void navigateToNextPoint() {
        if (currentPointIndex >= calibrationPoints.size()) {
            return;
        }
        
        currentPoint = calibrationPoints.get(currentPointIndex);
        targetPose = currentPoint.toPose2d();
        navigationComplete = false;
        navigationTimer.restart();
        
        SmartDashboard.putNumber("CalibrationValidation/CurrentPointIndex", currentPointIndex + 1);
        SmartDashboard.putString("CalibrationValidation/CurrentPointName", currentPoint.name);
        SmartDashboard.putNumber("CalibrationValidation/TargetX", targetPose.getX());
        SmartDashboard.putNumber("CalibrationValidation/TargetY", targetPose.getY());
        SmartDashboard.putNumber("CalibrationValidation/TargetRotation", targetPose.getRotation().getDegrees());
        SmartDashboard.putString("CalibrationValidation/Status", 
            "Navigating to point " + (currentPointIndex + 1) + "/" + calibrationPoints.size() + ": " + currentPoint.name);
        
        System.out.println("[CalibrationValidation] Navigating to point " + (currentPointIndex + 1) + 
                         ": " + currentPoint.name + " at (" + targetPose.getX() + ", " + targetPose.getY() + ")");
    }
    
    /**
     * Navigate toward the target pose using proportional control
     */
    private void navigateTowardTarget() {
        Pose2d currentPose = drivetrain.getState().Pose;
        
        // Calculate error to target
        double errorX = targetPose.getX() - currentPose.getX();
        double errorY = targetPose.getY() - currentPose.getY();
        double distanceError = Math.hypot(errorX, errorY);
        
        Rotation2d currentRotation = currentPose.getRotation();
        Rotation2d targetRotation = targetPose.getRotation();
        double rotationError = targetRotation.minus(currentRotation).getDegrees();
        
        // Normalize rotation error to [-180, 180]
        while (rotationError > 180) rotationError -= 360;
        while (rotationError < -180) rotationError += 360;
        
        // Update SmartDashboard with current error
        SmartDashboard.putNumber("CalibrationValidation/DistanceError", distanceError);
        SmartDashboard.putNumber("CalibrationValidation/RotationError", rotationError);
        SmartDashboard.putNumber("CalibrationValidation/CurrentX", currentPose.getX());
        SmartDashboard.putNumber("CalibrationValidation/CurrentY", currentPose.getY());
        SmartDashboard.putNumber("CalibrationValidation/CurrentRotation", currentRotation.getDegrees());
        
        // Check if we've reached the target
        if (distanceError < POSITION_TOLERANCE && Math.abs(rotationError) < ROTATION_TOLERANCE) {
            navigationComplete = true;
            drivetrain.applyRequest(() -> driveRequest.withVelocityX(0).withVelocityY(0).withRotationalRate(0));
            SmartDashboard.putString("CalibrationValidation/Status", 
                "At point " + currentPoint.name + " - Click NextPoint to continue");
            System.out.println("[CalibrationValidation] Reached point: " + currentPoint.name);
            return;
        }
        
        // Check for timeout
        if (navigationTimer.get() > MAX_NAVIGATION_TIME) {
            drivetrain.applyRequest(() -> driveRequest.withVelocityX(0).withVelocityY(0).withRotationalRate(0));
            SmartDashboard.putString("CalibrationValidation/Status", 
                "TIMEOUT - Could not reach " + currentPoint.name + " in " + MAX_NAVIGATION_TIME + " seconds");
            System.err.println("[CalibrationValidation] Timeout reaching point: " + currentPoint.name);
            navigationComplete = true;
            return;
        }
        
        // Proportional drive to target
        // Compute drive velocity (robot-relative)
        double driveVelocity = Math.min(MAX_DRIVE_VELOCITY, distanceError * 0.5);
        final double angularVelocity = Math.max(-MAX_ANGULAR_VELOCITY, 
            Math.min(MAX_ANGULAR_VELOCITY, rotationError * 0.02)); // Proportional rotation control
        
        // For field-relative motion, we need to rotate the velocity vector
        // to account for current robot orientation
        double angle = Math.atan2(errorY, errorX);
        double robotRelativeAngle = angle - currentRotation.getRadians();
        
        double robotDriveVelocity = driveVelocity * Math.cos(robotRelativeAngle);
        double robotStrafeVelocity = driveVelocity * Math.sin(robotRelativeAngle);
        
        drivetrain.applyRequest(() -> 
            driveRequest
                .withVelocityX(robotDriveVelocity)
                .withVelocityY(robotStrafeVelocity)
                .withRotationalRate(angularVelocity)
        );
    }
}
