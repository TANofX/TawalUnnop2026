package frc.lib.util;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.DataLogManager;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Turret;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.FireControl;
import edu.wpi.first.wpilibj.Timer;

public class RobotLogger {

    private final Shooter shooter;
    private final Turret turret;
    private final FireControl fireControl;
    private final CommandSwerveDrivetrain swerve;


    public RobotLogger(Shooter shooter, Turret turret, FireControl fireControl, CommandSwerveDrivetrain swerve) {
        this.shooter = shooter;
        this.turret = turret;
        this.fireControl = fireControl;
        this.swerve = swerve;
    }

   
    public void logSnapshot() {
        double topRPM = shooter.getTopSetpoint();
        //double bottomRPM = shooter.getBottomSetpoint();
        double turretAngle = turret.getAngle().getDegrees();
        double distance = fireControl.getDistanceFromTarget();
        double time = Timer.getFPGATimestamp();
      
    
        //odmetry stuff :) 
         Pose2d pose = swerve.getState().Pose; // Get current robot pose yippee
         double robotX = pose.getX();
         double robotY = pose.getY();
         double robotRotation = pose.getRotation().getDegrees();

       
        String line = time +"," + topRPM + "," + turretAngle + "," + distance + "," + robotX + "," + robotY + "," + robotRotation;
        DataLogManager.log("SCORE" + line);
    }
}