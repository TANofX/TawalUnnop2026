
package frc.robot.subsystems;

import static edu.wpi.first.units.Units.Meters;
import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import frc.robot.Constants;

public class FireControlTest {
    @Test
    public void testGetCurrentTarget_initiallyNull() {
        Supplier<Pose2d> robotSupplier =  () -> new Pose2d();
        Supplier<Alliance> allianceSupplier = () -> Alliance.Blue;
        FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

        assertNull(fc.getCurrentTarget(), "currentTarget should be null before being set");
    }


    @Test
    public void testGetCurrentTarget_red_hub() {
        Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_RED.getX() + 2.0, Constants.HUB_RED.getY() + 2.0, new Rotation2d());
        Supplier<Alliance> allianceSupplier = () -> Alliance.Red;
        FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

        fc.periodic();
        Rotation2d target = fc.getCurrentTarget();
        Pose2d targetPose2d = fc.getTargetPose();
        double distanceFrom = fc.getDistanceFromTarget();

        assertNotNull(target, "currentTarget should not be null after update");
        assertEquals(Rotation2d.fromDegrees(-135.0), target, "currentTarget should be approximately -135 degrees");
        assertEquals(Constants.HUB_RED, targetPose2d, "The target pose should be the red hub");

        double expectedDistance = Math.sqrt(Math.pow(robotSupplier.get().getMeasureX().in(Meters) - Constants.HUB_RED.getMeasureX().in(Meters), 2) + Math.pow(robotSupplier.get().getMeasureY().in(Meters) - Constants.HUB_RED.getMeasureY().in(Meters), 2));

        assertEquals(expectedDistance, distanceFrom, 0.02, "The distance to the target is incorrect");
    }


    @Test 
    public void testGetCurrentTarget_blue_hub() {
        Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_BLUE.getX() - 2.0, Constants.HUB_BLUE.getY() - 2.0, new Rotation2d());
        Supplier<Alliance> allianceSupplier = () -> Alliance.Blue;
        FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

        fc.periodic();
        Rotation2d target = fc.getCurrentTarget();
        Pose2d targetPose2d = fc.getTargetPose();
        double distanceFrom = fc.getDistanceFromTarget();

        assertNotNull(target, "currentTarget should not be null after update");
        assertEquals(Rotation2d.fromDegrees(45.0), target, "currentTarget should be appromximately 45 degrees");
        assertEquals(Constants.HUB_BLUE, targetPose2d, "The target pose should be the blue hub");

        double expectedDistance = Math.sqrt(Math.pow(robotSupplier.get().getMeasureX().in(Meters) - Constants.HUB_BLUE.getMeasureX().in(Meters), 2) + Math.pow(robotSupplier.get().getMeasureY().in(Meters) - Constants.HUB_BLUE.getMeasureY().in(Meters), 2));

        assertEquals(expectedDistance, distanceFrom, 0.02, "The distance to the target is incorrect");
    }


    @Test
    public void testGetCurrentTarget_red_top_feed() {
        Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_RED.getX() - 3.5, Constants.RED_FEED_TOP.getMeasureY().in(Meters), new Rotation2d());
        Supplier<Alliance> allianceSupplier = () -> Alliance.Red;
        FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

        fc.periodic();
        Rotation2d target = fc.getCurrentTarget();
        Pose2d targetPose2d = fc.getTargetPose();
        double distanceFrom = fc.getDistanceFromTarget();

        assertNotNull(target, "currentTarget should not be null after update");
        assertEquals(Rotation2d.fromDegrees(0.0), target, "currentTarget should be appromximately 0 degrees");
        assertEquals(Constants.RED_FEED_TOP, targetPose2d, "The target pose should be the red top feed");

        double expectedDistance = Constants.HUB_BLUE.getX() + 3.5 - Constants.X_CLEAR_OFFSET.in(Meters);

        assertEquals(expectedDistance, distanceFrom, 0.02, "The distance to the target is incorrect");
    } 


    @Test
    public void testGetCurrentTarget_red_bot_feed() {
        Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_RED.getX() - 3.5, Constants.RED_FEED_BOT.getMeasureY().in(Meters), new Rotation2d());
        Supplier<Alliance> allianceSupplier = () -> Alliance.Red;
        FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

        fc.periodic();
        Rotation2d target = fc.getCurrentTarget();
        Pose2d targetPose2d = fc.getTargetPose();
        double distanceFrom = fc.getDistanceFromTarget();

        assertNotNull(target, "currentTarget should not be null after update");
        assertEquals(Rotation2d.fromDegrees(0.0), target, "currentTarget should be appromximately 0 degrees");
        assertEquals(Constants.RED_FEED_BOT, targetPose2d, "The target pose should be the red bottom feed");

        double expectedDistance = Constants.HUB_BLUE.getX() + 3.5 - Constants.X_CLEAR_OFFSET.in(Meters);

        assertEquals(expectedDistance, distanceFrom, 0.02, "The distance to the target is incorrect");
    }


    @Test
    public void testGetCurrentTarget_blue_top_feed() {
        Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_BLUE.getX() + 3.5, Constants.BLUE_FEED_TOP.getMeasureY().in(Meters), new Rotation2d());
        Supplier<Alliance> allianceSupplier = () -> Alliance.Blue;
        FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

        fc.periodic();
        Rotation2d target = fc.getCurrentTarget();
        Pose2d targetPose2d = fc.getTargetPose();
        double distanceFrom = fc.getDistanceFromTarget();

        assertNotNull(target, "currentTarget should not be null after update");
        assertEquals(Rotation2d.fromDegrees(180.0), target, "currentTarget should be appromximately 180 degrees");
        assertEquals(Constants.BLUE_FEED_TOP, targetPose2d, "The target pose should be the blue top feed");

        
        double expectedDistance = Constants.HUB_BLUE.getX() + 3.5 - Constants.X_CLEAR_OFFSET.in(Meters);

        assertEquals(expectedDistance, distanceFrom, 0.02, "The distance to the target is incorrect");
    }


    @Test
    public void testGetCurrentTarget_blue_bot_feed() {
        Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_BLUE.getX() + 3.5, Constants.BLUE_FEED_BOT.getMeasureY().in(Meters), new Rotation2d());
        Supplier<Alliance> allianceSupplier = () -> Alliance.Blue;
        FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

        fc.periodic();
        Rotation2d target = fc.getCurrentTarget();
        Pose2d targetPose2d = fc.getTargetPose();
        double distanceFrom = fc.getDistanceFromTarget();

        assertNotNull(target, "currentTarget should not be null after update");
        assertEquals(Rotation2d.fromDegrees(180.0), target, "currentTarget should be appromximately 0 degrees");
        assertEquals(Constants.BLUE_FEED_BOT, targetPose2d, "The target pose should be the blue bottom feed");

        double expectedDistance = Constants.HUB_BLUE.getX() + 3.5 - Constants.X_CLEAR_OFFSET.in(Meters);

        assertEquals(expectedDistance, distanceFrom, 0.02, "The distance to the target is incorrect");
    }
   
    // @Test TODO
    // public void testRpmFromDistance() {
    //     Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_BLUE.getX() - 1.0, Constants.HUB_BLUE.getY(), new Rotation2d());
    //     Supplier<Alliance> allianceSupplier = () -> Alliance.Blue;
    //     FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

    //     fc.periodic();
    //     System.out.println(fc.getShooterRpm());
    //     assertEquals(1000.0, fc.getShooterRpm(), 0.02, "Rpm is incorrect.");
    // }


    // @Test
    // // public void testRpmFromDistance_Odd() {
    // //     Supplier<Pose2d> robotSupplier = () -> new Pose2d(Constants.HUB_BLUE.getX() - 1.5, Constants.HUB_BLUE.getY(), new Rotation2d());
    // //     Supplier<Alliance> allianceSupplier = () -> Alliance.Blue;
    // //     FireControl fc = new FireControl(robotSupplier, allianceSupplier, () -> new ChassisSpeeds());

    // //     fc.periodic();
    // //     System.out.println(fc.getShooterRpm());
    // //     assertEquals(1000.0, fc.getShooterRpm(), 0.02, "Rpm is incorrect.");
    // // }
}