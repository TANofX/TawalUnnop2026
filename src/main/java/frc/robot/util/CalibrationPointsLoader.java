package frc.robot.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * Loads and manages calibration points from external CSV file.
 * 
 * Allows operators to update calibration point positions without recompiling code.
 * This is especially useful for:
 * - Different field layouts (competition vs. practice)
 * - Adjusting calibration points based on AprilTag placement
 * - Testing different grid configurations
 * - Quickly switching between single-point and multi-point calibration
 * 
 * File Format: calibration_points.csv in deploy directory
 * - name: Unique point identifier (e.g., BLUE_CORNER_LOW, CENTER)
 * - x_meters: Field X coordinate
 * - y_meters: Field Y coordinate  
 * - rotation_degrees: Robot heading
 * - initial_position: true if this is the reset position, false otherwise
 * 
 * CRITICAL: Exactly one point must have initial_position=true
 */
public class CalibrationPointsLoader {
    
    private List<CalibrationPoint> allPoints = new ArrayList<>();
    private CalibrationPoint initialPosition;
    private Map<String, CalibrationPoint> pointsByName = new HashMap<>();
    
    private static final String CONFIG_FILE = "calibration_points.csv";
    private static final String DEFAULT_CONFIG_RESOURCE = "/deploy/calibration_points.csv";
    
    public CalibrationPointsLoader() {
        loadCalibrationPoints();
    }
    
    /**
     * Load calibration points from CSV file.
     * Tries two locations:
     * 1. File system (deploy directory) - allows runtime updates
     * 2. Resource (JAR) - fallback if file not found
     */
    private void loadCalibrationPoints() {
        try {
            // Try to load from file system first (allows runtime updates on RoboRIO)
            java.io.File deployDir = Filesystem.getDeployDirectory();
            java.io.File configFile = new java.io.File(deployDir, CONFIG_FILE);
            if (configFile.exists()) {
                loadFromFile(configFile.getAbsolutePath());
                SmartDashboard.putString("CalibrationPoints/LoadSource", "File system: " + configFile.getAbsolutePath());
                return;
            }
        } catch (Exception e) {
            SmartDashboard.putString("CalibrationPoints/Warning", 
                "Failed to load from file system: " + e.getMessage());
        }
        
        // Fallback: load from JAR resource
        try {
            loadFromResource(DEFAULT_CONFIG_RESOURCE);
            SmartDashboard.putString("CalibrationPoints/LoadSource", "JAR resource (default)");
            return;
        } catch (Exception e) {
            SmartDashboard.putString("CalibrationPoints/Error", 
                "Failed to load calibration points: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load calibration points from file system
     */
    private void loadFromFile(String filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath))) {
            parseCSV(reader);
        }
    }
    
    /**
     * Load calibration points from JAR resource
     */
    private void loadFromResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            parseCSV(reader);
        }
    }
    
    /**
     * Parse CSV format
     * Skip comments (lines starting with #) and header line
     */
    private void parseCSV(BufferedReader reader) throws IOException {
        String line;
        int lineNumber = 0;
        boolean headerSkipped = false;
        
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            
            // Skip comments and empty lines
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            
            // Skip header line (first non-comment line)
            if (!headerSkipped) {
                if (line.toLowerCase().contains("name") && line.contains("x_meters")) {
                    headerSkipped = true;
                    continue;
                }
            }
            
            try {
                CalibrationPoint point = parseCSVLine(line);
                allPoints.add(point);
                pointsByName.put(point.name, point);
                
                if (point.isInitialPosition) {
                    if (initialPosition != null) {
                        throw new IllegalArgumentException(
                            "Multiple initial positions found: " + initialPosition.name + 
                            " and " + point.name + ". Only one initial_position can be true.");
                    }
                    initialPosition = point;
                }
            } catch (Exception e) {
                SmartDashboard.putString("CalibrationPoints/ParseError", 
                    "Line " + lineNumber + ": " + e.getMessage());
                throw new IOException("Parse error at line " + lineNumber + ": " + e.getMessage(), e);
            }
        }
        
        // Validate
        if (initialPosition == null) {
            throw new IOException("No initial position found. Exactly one point must have initial_position=true");
        }
        
        if (allPoints.isEmpty()) {
            throw new IOException("No calibration points loaded from CSV");
        }
        
        SmartDashboard.putNumber("CalibrationPoints/LoadedCount", allPoints.size());
        SmartDashboard.putString("CalibrationPoints/InitialPosition", initialPosition.name);
    }
    
    /**
     * Parse single CSV line
     * Format: name,x_meters,y_meters,rotation_degrees,initial_position
     */
    private CalibrationPoint parseCSVLine(String line) throws NumberFormatException {
        String[] parts = line.split(",");
        
        if (parts.length != 5) {
            throw new IllegalArgumentException(
                "Expected 5 columns, got " + parts.length + ": " + line);
        }
        
        String name = parts[0].trim();
        double x = Double.parseDouble(parts[1].trim());
        double y = Double.parseDouble(parts[2].trim());
        double rotation = Double.parseDouble(parts[3].trim());
        boolean isInitial = Boolean.parseBoolean(parts[4].trim());
        
        return new CalibrationPoint(name, x, y, rotation, isInitial);
    }
    
    /**
     * Get the initial position (where robot should reset odometry)
     */
    public Pose2d getInitialPosition() {
        if (initialPosition == null) {
            throw new RuntimeException("No initial position loaded. Check calibration_points.csv");
        }
        return initialPosition.toPose2d();
    }
    
    /**
     * Get the initial position as CalibrationPoint (includes name for logging)
     */
    public CalibrationPoint getInitialPositionPoint() {
        if (initialPosition == null) {
            throw new RuntimeException("No initial position loaded. Check calibration_points.csv");
        }
        return initialPosition;
    }
    
    /**
     * Get all calibration points (for multi-point calibration)
     * Returned list maintains order from CSV file
     */
    public List<Pose2d> getAllCalibrationPoints() {
        List<Pose2d> poses = new ArrayList<>();
        for (CalibrationPoint point : allPoints) {
            poses.add(point.toPose2d());
        }
        return poses;
    }
    
    /**
     * Get all calibration points with names (for debugging/logging)
     */
    public List<CalibrationPoint> getAllCalibrationPointsWithNames() {
        return new ArrayList<>(allPoints);
    }
    
    /**
     * Get a specific calibration point by name
     */
    public Optional<Pose2d> getPointByName(String name) {
        CalibrationPoint point = pointsByName.get(name);
        return point != null ? Optional.of(point.toPose2d()) : Optional.empty();
    }
    
    /**
     * Get number of calibration points loaded
     */
    public int getPointCount() {
        return allPoints.size();
    }
    
    /**
     * Publish information to SmartDashboard for operator visibility
     */
    public void publishToSmartDashboard() {
        SmartDashboard.putNumber("CalibrationPoints/Total", allPoints.size());
        SmartDashboard.putString("CalibrationPoints/InitialPos", 
            initialPosition != null ? initialPosition.name : "NOT SET");
        
        for (int i = 0; i < allPoints.size(); i++) {
            CalibrationPoint point = allPoints.get(i);
            String key = "CalibrationPoints/Point" + (i + 1);
            SmartDashboard.putString(key + "/Name", point.name);
            SmartDashboard.putNumber(key + "/X", point.x);
            SmartDashboard.putNumber(key + "/Y", point.y);
            SmartDashboard.putNumber(key + "/Rotation", point.rotation);
        }
    }
    
    /**
     * Inner class representing a single calibration point
     */
    public static class CalibrationPoint {
        public final String name;
        public final double x;
        public final double y;
        public final double rotation;  // In degrees
        public final boolean isInitialPosition;
        
        public CalibrationPoint(String name, double x, double y, double rotation, boolean isInitialPosition) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.isInitialPosition = isInitialPosition;
        }
        
        /**
         * Convert to WPILib Pose2d
         */
        public Pose2d toPose2d() {
            return new Pose2d(x, y, Rotation2d.fromDegrees(rotation));
        }
        
        @Override
        public String toString() {
            return String.format("%s: (%.2f, %.2f) @ %.0f°%s",
                name, x, y, rotation,
                isInitialPosition ? " [INITIAL]" : "");
        }
    }
}
