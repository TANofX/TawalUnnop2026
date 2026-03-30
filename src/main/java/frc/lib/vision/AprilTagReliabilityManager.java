package frc.lib.vision;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * Manages AprilTag reliability assessment for vision calibration and localization.
 * 
 * Provides:
 * - Whitelist of known-good AprilTags (verified on your specific field)
 * - Blacklist of known-bad AprilTags (poor mounting, lighting issues, etc.)
 * - Runtime metrics tracking (average error, sample count)
 * - SmartDashboard integration for approval/rejection workflows
 * 
 * Usage:
 * 1. Define RELIABLE_TAGS set based on your field's AprilTag quality assessment
 * 2. Call recordMeasurement() whenever a camera produces a pose estimate
 * 3. Run isTagReliable(tagID) to filter pose estimates during calibration
 * 4. Check SmartDashboard for tag metrics and add/remove from reliability lists as needed
 */
public class AprilTagReliabilityManager {
    
    // ==================== Configuration ====================
    
    /**
     * Tags verified as reliable on your practice field
     * Replace these IDs based on your field assessment
     */
    private static final Set<Integer> DEFAULT_RELIABLE_TAGS = Set.of(
        1, 2, 3, 4, 5, 6, 7, 8, 9,     // Replace with verified tag IDs
        16, 17, 18                      // Add more as needed for your field
    );
    
    /**
     * Tags known to have placement/mounting issues
     * These will never be considered reliable
     */
    private static final Set<Integer> DEFAULT_UNRELIABLE_TAGS = Set.of(
        // Fill with tag IDs that you've identified as problematic
        // Example: 10, 11, 12  // Poorly mounted
        // Example: 25, 26      // Bad lighting
    );
    
    // Reliability thresholds
    private static final double RELIABILITY_ERROR_THRESHOLD = 0.2;     // 20cm - marks as potentially bad
    private static final double UNRELIABLE_ERROR_THRESHOLD = 0.5;      // 50cm - definitely bad
    private static final int MIN_SAMPLES_FOR_ASSESSMENT = 10;          // Need at least 10 measurements
    private static final int ACCEPT_UNKNOWN_TAGS = 0;                  // 0 = reject, 1 = accept if below threshold
    
    // ==================== Instance Fields ====================
    
    private final Set<Integer> reliableTags = new HashSet<>(DEFAULT_RELIABLE_TAGS);
    private final Set<Integer> unreliableTags = new HashSet<>(DEFAULT_UNRELIABLE_TAGS);
    private final Map<Integer, TagMetrics> tagMetrics = new HashMap<>();
    
    // ==================== Public Methods ====================
    
    /**
     * Determine if an AprilTag should be trusted for pose estimation.
     * 
     * Decision logic:
     * 1. If in whitelist → trusted
     * 2. If in blacklist → not trusted
     * 3. If unknown: check observed metrics (conservative approach)
     */
    public boolean isTagReliable(int tagID) {
        // Whitelist: always trust
        if (reliableTags.contains(tagID)) {
            return true;
        }
        
        // Blacklist: never trust
        if (unreliableTags.contains(tagID)) {
            return false;
        }
        
        // Unknown tag: check metrics if available
        TagMetrics metrics = tagMetrics.get(tagID);
        if (metrics == null || metrics.sampleCount < MIN_SAMPLES_FOR_ASSESSMENT) {
            // Conservative: reject unknown tags until proven good
            return ACCEPT_UNKNOWN_TAGS == 1;
        }
        
        // Use metrics-based assessment
        return metrics.getAverageError() < RELIABILITY_ERROR_THRESHOLD;
    }
    
    /**
     * Record a measurement from an AprilTag for reliability assessment.
     * Call this whenever your vision system produces a pose estimate.
     * 
     * @param tagID The AprilTag ID
     * @param visionPose Pose estimated by vision system
     * @param odometryPose Ground truth pose from odometry
     */
    public void recordMeasurement(int tagID, Pose2d visionPose, Pose2d odometryPose) {
        // Calculate error between vision and odometry
        double positionError = visionPose.getTranslation()
            .getDistance(odometryPose.getTranslation());
        double rotationError = Math.abs(
            visionPose.getRotation().minus(odometryPose.getRotation()).getDegrees()
        );
        
        // Update metrics for this tag
        TagMetrics metrics = tagMetrics.computeIfAbsent(tagID, k -> new TagMetrics());
        metrics.recordMeasurement(positionError, rotationError);
        
        // Flag problematic tags for operator attention
        if (metrics.getAverageError() > UNRELIABLE_ERROR_THRESHOLD && !unreliableTags.contains(tagID)) {
            SmartDashboard.putString("VisionCal/Warning", 
                String.format("Tag %d has high error: %.2fm (avg over %d samples)", 
                    tagID, metrics.getAverageError(), metrics.sampleCount));
        }
        
        // Publish real-time metrics
        SmartDashboard.putNumber("VisionCal/LastTagID", tagID);
        SmartDashboard.putNumber("VisionCal/LastPositionError", positionError);
    }
    
    /**
     * Publish comprehensive reliability assessment to SmartDashboard
     * Call this after calibration completes to review tag quality
     */
    public void publishReliabilityReport() {
        SmartDashboard.putNumber("VisionCal/ReliableTagCount", reliableTags.size());
        SmartDashboard.putNumber("VisionCal/UnreliableTagCount", unreliableTags.size());
        SmartDashboard.putNumber("VisionCal/TestedTagCount", tagMetrics.size());
        
        // Publish metrics for each tag
        for (Map.Entry<Integer, TagMetrics> entry : tagMetrics.entrySet()) {
            int tagID = entry.getKey();
            TagMetrics metrics = entry.getValue();
            
            String keyPrefix = "VisionCal/Tag" + tagID;
            SmartDashboard.putNumber(keyPrefix + "/AvgError", metrics.getAverageError());
            SmartDashboard.putNumber(keyPrefix + "/MaxError", metrics.maxError);
            SmartDashboard.putNumber(keyPrefix + "/Samples", metrics.sampleCount);
            SmartDashboard.putBoolean(keyPrefix + "/IsReliable", isTagReliable(tagID));
        }
    }
    
    /**
     * Manually add a tag to the reliable whitelist
     * Use SmartDashboard buttons to call this when operator approves a tag
     */
    public void addToReliableList(int tagID) {
        reliableTags.add(tagID);
        unreliableTags.remove(tagID);  // Remove from blacklist if present
        SmartDashboard.putString("VisionCal/Info", "✓ Tag " + tagID + " added to reliable list");
    }
    
    /**
     * Manually add a tag to the unreliable blacklist
     * Use SmartDashboard buttons to call this when operator identifies a bad tag
     */
    public void addToUnreliableList(int tagID) {
        unreliableTags.add(tagID);
        reliableTags.remove(tagID);  // Remove from whitelist if present
        SmartDashboard.putString("VisionCal/Info", "✗ Tag " + tagID + " added to unreliable list");
    }
    
    /**
     * Get current reliable tag set (for configuration export)
     */
    public Set<Integer> getReliableTags() {
        return new HashSet<>(reliableTags);
    }
    
    /**
     * Get current unreliable tag set (for configuration export)
     */
    public Set<Integer> getUnreliableTags() {
        return new HashSet<>(unreliableTags);
    }
    
    /**
     * Clear all collected metrics (useful when resetting calibration)
     */
    public void clearMetrics() {
        tagMetrics.clear();
        SmartDashboard.putString("VisionCal/Info", "Tag metrics cleared");
    }
    
    /**
     * Reset to default configuration
     */
    public void resetToDefaults() {
        reliableTags.clear();
        reliableTags.addAll(DEFAULT_RELIABLE_TAGS);
        unreliableTags.clear();
        unreliableTags.addAll(DEFAULT_UNRELIABLE_TAGS);
        clearMetrics();
        SmartDashboard.putString("VisionCal/Info", "AprilTag reliability reset to defaults");
    }
    
    // ==================== Inner Class ====================
    
    /**
     * Metrics collected for a single AprilTag during calibration
     */
    public static class TagMetrics {
        public double totalPositionError = 0;
        public double totalRotationError = 0;
        public int sampleCount = 0;
        public double maxError = 0;
        
        void recordMeasurement(double positionError, double rotationError) {
            totalPositionError += positionError;
            totalRotationError += rotationError;
            sampleCount++;
            maxError = Math.max(maxError, positionError);
        }
        
        /**
         * Get average position error in meters
         */
        public double getAverageError() {
            return sampleCount == 0 ? Double.MAX_VALUE : totalPositionError / sampleCount;
        }
        
        /**
         * Get average rotation error in degrees
         */
        public double getAverageRotationError() {
            return sampleCount == 0 ? Double.MAX_VALUE : totalRotationError / sampleCount;
        }
    }
}
