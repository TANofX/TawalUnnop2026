package frc.robot.subsystems;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.interpolation.InterpolatingDoubleTreeMap;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;



public class FireControl extends SubsystemBase{
    private Supplier<Pose2d> robotSupplier;
    private Supplier<Alliance> allianceSupplier;
    private ArrayList<Pose2d> redList = new ArrayList<>(2);
    private ArrayList<Pose2d> blueList = new ArrayList<>(2);
    private Rotation2d currentTarget;
    private double distanceFromTarget;
    private Pose2d target;
    private Supplier<ChassisSpeeds> speedSupplier;
    private InterpolatingDoubleTreeMap rpmFromDistance;
    private ChassisSpeeds currentChassisSpeeds;

    public FireControl(Supplier<Pose2d> robSupplier, Supplier<Alliance> allSupplier, Supplier<ChassisSpeeds> vSupplier) {
        this(robSupplier, allSupplier, vSupplier, "distance_rpm.csv");
    }

    public FireControl(Supplier<Pose2d> robSupplier, Supplier<Alliance> allSupplier, Supplier<ChassisSpeeds> vSupplier, String rpmFile) {
        robotSupplier = robSupplier;
        allianceSupplier = allSupplier;
        speedSupplier = vSupplier;
        blueList.add(Constants.BLUE_FEED_BOT); blueList.add(Constants.BLUE_FEED_TOP);
        redList.add(Constants.RED_FEED_BOT); redList.add(Constants.RED_FEED_TOP);
        File deployDirectory = Filesystem.getDeployDirectory();
        File csvFile = new File(deployDirectory, rpmFile);
        readCsv(csvFile.getAbsolutePath());
    }
    
    /**
     * @return Finds the target angle off of the robot position and the target position
     */
    private static Rotation2d getTargetRotation(Pose2d robotPose, Pose2d targetPose) {
        Translation2d toTarget = targetPose.getTranslation().minus(robotPose.getTranslation());
        Rotation2d targetAngle = toTarget.getAngle();
        Rotation2d relativeAngle = targetAngle.minus(robotPose.getRotation());

        return relativeAngle;
    }
    /**
     * @return A list of current available targets
     */
    private ArrayList<Pose2d> getValidTargets() {
        ArrayList<Pose2d> validTargets = new ArrayList<>();
        if (allianceSupplier.get() == Alliance.Blue) {
            if(Constants.BLUE_ALLIANCE_ZONE.contains(robotSupplier.get().getTranslation())) {
                validTargets.add(Constants.HUB_BLUE);
            } else {
                validTargets = blueList;
            }
        } else if (allianceSupplier.get() == Alliance.Red) {
            if(Constants.RED_ALLIANCE_ZONE.contains(robotSupplier.get().getTranslation())) {
                validTargets.add(Constants.HUB_RED);
            } else {
               validTargets = redList;
            }
        }
        return validTargets;
    }
    
    /**
     * @param robotPose
     * @return TargetLocation with lowest distance to robot
     */
    private Pose2d getClosestTarget(Pose2d robotPose) {
        ArrayList<Pose2d> targets = getValidTargets();
        Pose2d closestTarget = null;
        if(targets.size() == 1) {
            return targets.get(0);
        }
        double smallestValue = 1000.0;
        for (Pose2d p: targets) {
            double d = getDistance(p, robotPose);
            if (d < smallestValue) {
                smallestValue = d;
                closestTarget = p;
            }
        }
        return closestTarget;
    }
    /**
     * @param x
     * @param y
     * @return Get distance from the robot to the target
     */
    private double getDistance(Pose2d x, Pose2d y) {
        double d = x.getTranslation().getDistance(y.getTranslation());
        return d;
    } 

    //Checks every cycle for the correct target loctation, distance, and robot sped
    public void periodic() { 
        target = getClosestTarget(robotSupplier.get());
        currentTarget = getTargetRotation(robotSupplier.get(), target);
        distanceFromTarget = getDistance(target, robotSupplier.get());
        currentChassisSpeeds = speedSupplier.get();
    }   

    private void readCsv(String filePath) {
        TreeMap<Double, Double> treeMap = new TreeMap<>();

        String line = "";
        String delimiter = ",";
        int counter = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            while ((line = br.readLine()) != null) {
                String[] data = line.split(delimiter);

                if (data.length == 2 && counter > 0) {
                    treeMap.put(Double.parseDouble(data[0]), Double.parseDouble(data[1]));
                }
                counter++;
            }
            Set<Map.Entry<Double, Double>> entrySet = treeMap.entrySet();
            System.out.println(entrySet);
            rpmFromDistance = InterpolatingDoubleTreeMap.ofEntries(entrySet.toArray(new Map.Entry[entrySet.size()]));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public double getShooterRpm() {
        return rpmFromDistance.get(getDistanceFromTarget());
    }

    /**
     * @return The offset needed to aim while moving
     */
    public double getOffset() { //TODO
        double offset;
        offset = 0.0;
        return offset;
    }

    /**
     * @return The current target on field
     */
    public Rotation2d getCurrentTarget() {
        return currentTarget;
    }
    
    /**
     * @return The distance from the current target
     */
    public double getDistanceFromTarget() {
        return distanceFromTarget;
    }

    /**
     * @return The Pose2d of the current target
     */
    public Pose2d getTargetPose() {
        return target;
    }

} 