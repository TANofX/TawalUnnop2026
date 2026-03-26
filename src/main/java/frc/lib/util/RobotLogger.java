package frc.lib.util;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.wpilibj.DataLogManager;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Turret;
import frc.robot.subsystems.FireControl;
import edu.wpi.first.wpilibj.Timer;
import frc.robot.subsystems.Swerve;



public class RobotLogger {

    private final Shooter shooter;
    private final Turret turret;
    private final FireControl fireControl;
    private final Swerve swerve;


    public RobotLogger(Shooter shooter, Turret turret, FireControl fireControl, Swerve swerve) {
        this.shooter = shooter;
        this.turret = turret;
        this.fireControl = fireControl;
        this.swerve = swerve;
        DataLogManager.start();
      
    }

   
    public void logSnapshot() {
        double topRPM = shooter.getTopSetpoint();
        double bottomRPM = shooter.getBottomSetpoint();
        double turretAngle = turret.getAngle().getDegrees();
        double distance = fireControl.getDistanceFromTarget();
        double time = Timer.getFPGATimestamp();
      
    
        //odmetry stuff :) 
         Pose2d pose = swerve.getPose(); // Get current robot pose yippee 
         double robotX = pose.getX();
         double robotY = pose.getY();
         double robotRotation = pose.getRotation().getDegrees();

       
        String line = time +"," + topRPM + "," + bottomRPM + "," + turretAngle + "," + distance + "," + robotX + "," + robotY + "," + robotRotation + "\n";
        DataLogManager.log(line);
    }
}