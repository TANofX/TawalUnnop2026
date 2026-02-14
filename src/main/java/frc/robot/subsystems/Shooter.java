// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;


import edu.wpi.first.wpilibj2.command.SubsystemBase;

import java.util.ArrayList;
import java.util.List;

import com.ctre.phoenix6.StatusSignal;
import com.ctre.phoenix6.configs.CANcoderConfiguration;
import com.ctre.phoenix6.hardware.CANcoder;
import com.ctre.phoenix6.signals.SensorDirectionValue;
import com.revrobotics.AbsoluteEncoder;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.sim.SparkFlexSim;
import com.revrobotics.spark.ClosedLoopSlot;
import com.revrobotics.spark.SparkBase;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.REVLibError;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLimitSwitch;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.ClosedLoopConfig;
import com.revrobotics.spark.config.LimitSwitchConfig.Type;
import com.revrobotics.spark.config.MAXMotionConfig.MAXMotionPositionMode;
import com.revrobotics.spark.config.SparkBaseConfig;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkFlexConfig;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.spark.SparkBase;
import com.revrobotics.ResetMode;   // Import the new global ResetMode
import com.revrobotics.PersistMode;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.units.measure.Angle;
import edu.wpi.first.units.measure.AngularVelocity;
import edu.wpi.first.units.measure.Distance;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.PneumaticsModuleType;
import edu.wpi.first.wpilibj.Preferences;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.simulation.BatterySim;
import edu.wpi.first.wpilibj.simulation.RoboRioSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.subsystem.AdvancedSubsystem;

import frc.robot.Constants;
import com.revrobotics.sim.SparkFlexSim;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLimitSwitch;

import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.LimitSwitchConfig;
import com.revrobotics.spark.config.SparkFlexConfig;
import com.revrobotics.spark.config.LimitSwitchConfig.Behavior;
import com.revrobotics.spark.config.LimitSwitchConfig.Type;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;



public class Shooter extends SubsystemBase {
// view from shooter side
  private final SparkFlex shooterLeftBottomMotor; 
  private final SparkFlex shooterLeftTopMotor;
  private final SparkFlex shooterRightBottomMotor;
  private final SparkFlex shooterRightTopMotor;

private final RelativeEncoder shooterLeftBottomEncoder;
private final RelativeEncoder shooterLeftTopEncoder;
private final RelativeEncoder shooterRightBottomEncoder;
private final RelativeEncoder shooterRightTopEncoder;

  private final SparkFlexConfig shooterBottomConfig = new SparkFlexConfig();
  private final SparkFlexConfig shooterTopConfig = new SparkFlexConfig();
  private final SparkFlexConfig shooterTopFollowerConfig = new SparkFlexConfig();
private final SparkFlexConfig shooterBottomFollowerConfig = new SparkFlexConfig();
 private final SparkClosedLoopController shooterBottomController;
 private final SparkClosedLoopController shooterTopController;


 //RECOVERY TRACKING STUFF 
  
// Top shooter recovery tracking
private boolean topInTolerance = true;      // Is top motor currently within target RPM tolerance?
private long topRecoveryStart = 0;          // Time when top motor fell below tolerance
private final List<Long> topRecoveryTimes = new ArrayList<>();  // List of recorded recovery times

// Bottom shooter recovery tracking
private boolean bottomInTolerance = true;   // Is bottom motor currently within target RPM tolerance?
private long bottomRecoveryStart = 0;       // Time when bottom motor fell below tolerance
private final List<Long> bottomRecoveryTimes = new ArrayList<>(); // List of recorded recovery times
 
  /** Creates a new Shooter. */
  public Shooter(
   final int FRONT_BOTTOM_SHOOTER_ID, 
   final int FRONT_TOP_SHOOTER_ID,
   final int BACK_BOTTOM_SHOOTER_ID, 
   final int BACK_TOP_SHOOTER_ID,
   final int FRONT_BOTTOM_ENCODER_ID,
   final int FRONT_TOP_ENCODER_ID,
   final int BACK_BOTTOM_ENCODER_ID,
   final int BACK_TOP_ENCODER_ID) {
   //configuring motors
  shooterLeftBottomMotor = new SparkFlex(FRONT_BOTTOM_SHOOTER_ID, MotorType.kBrushless);
  shooterLeftTopMotor = new SparkFlex(FRONT_TOP_SHOOTER_ID, MotorType.kBrushless);
  shooterRightBottomMotor = new SparkFlex(BACK_BOTTOM_SHOOTER_ID, MotorType.kBrushless);
  shooterRightTopMotor = new SparkFlex(BACK_TOP_SHOOTER_ID, MotorType.kBrushless);
   
  
  //configurating encoders
  shooterLeftBottomEncoder = shooterLeftBottomMotor.getEncoder();
shooterLeftTopEncoder = shooterLeftTopMotor.getEncoder();
shooterRightBottomEncoder = shooterRightBottomMotor.getEncoder();
shooterRightTopEncoder = shooterRightTopMotor.getEncoder();
  




//CONTROLLER STUFF
  shooterBottomController = shooterLeftBottomMotor.getClosedLoopController();
   SparkFlexConfig shooterBottomConfig = new SparkFlexConfig();
 shooterBottomConfig.closedLoop.feedForward
    .kS(Constants.Shooter.BOTTOM_kS)  // need values 
    .kV(Constants.Shooter.BOTTOM_kV) 
    .kA(Constants.Shooter.BOTTOM_kA);

  shooterTopController = shooterLeftTopMotor.getClosedLoopController();
 SparkFlexConfig shooterTopConfig = new SparkFlexConfig();
 shooterTopConfig.closedLoop.feedForward
    .kS(Constants.Shooter.TOP_kS)  // need values 
    .kV(Constants.Shooter.TOP_kV) 
    .kA(Constants.Shooter.TOP_kA);


shooterTopFollowerConfig.follow(shooterRightTopMotor, true);
shooterBottomFollowerConfig.follow(shooterRightBottomMotor, true);

  //PID CONFIG
  shooterBottomConfig.closedLoop
  .p(Constants.Shooter.BOTTOM_SHOOTER_P)
  .i(Constants.Shooter.BOTTOM_SHOOTER_I)
  .d(Constants.Shooter.BOTTOM_SHOOTER_D);
 
  shooterTopConfig.closedLoop
  .p(Constants.Shooter.TOP_SHOOTER_P)
  .i(Constants.Shooter.TOP_SHOOTER_I)
  .d(Constants.Shooter.TOP_SHOOTER_D);

  //configuring the motor
  shooterBottomConfig
    .idleMode(IdleMode.kCoast)
    .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_LIMIT)
    .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT);

shooterTopConfig
    .idleMode(IdleMode.kCoast)
    .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_LIMIT)
    .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT);

shooterLeftBottomMotor.configure(shooterBottomConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

shooterRightTopMotor.configure(shooterTopConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
   
     }

//methods
//simple, just stopping motors
  public void stopTopShooterMotors() {
  shooterLeftTopMotor.set(0.0);
  shooterRightTopMotor.set(0.0); 
  }

  public void stopBottomShooterMotors() {
    shooterRightBottomMotor.set(0.0); 
    shooterLeftBottomMotor.set(0.0); 
  }


//find out if motors are at correct speeds :) will be important in testing trust
  // Check if both top motors are at speed
// Check if both TOP shooter motors are at speed
public boolean topMotorsAtSpeed(double topRPM) {


  return Math.abs(shooterLeftTopEncoder.getVelocity() - topRPM)
          < Constants.Shooter.shooterMotorTolerance
      && Math.abs(shooterRightTopEncoder.getVelocity() - topRPM)
          < Constants.Shooter.shooterMotorTolerance;
}

// Check if both BOTTOM shooter motors are at speed
public boolean bottomMotorsAtSpeed(double bottomRPM) {
 

  return Math.abs(shooterLeftBottomEncoder.getVelocity() - bottomRPM)
          < Constants.Shooter.shooterMotorTolerance;
     
}


 // setting the shoote rpm based off of the table 
  public void setShooterRPM(double topRPM, double bottomRPM) {

  shooterTopController.setSetpoint(topRPM, ControlType.kVelocity);
  
  shooterBottomController.setSetpoint(bottomRPM, ControlType.kVelocity);
}

public double getTopSetpoint() {
  return shooterTopController.getSetpoint();
}
public double getBottomSetpoint() {
  return shooterBottomController.getSetpoint();
}

//Starting shooter commands!!

 public Command stopMotors() {
    return Commands.sequence(
        Commands.runOnce(() -> {
          stopTopShooterMotors();
          stopBottomShooterMotors();
        }));
  }
 
 public Command shootCommand(double topRPM, double bottomRPM) {
    return Commands.sequence(
        // Spin up top motors
        Commands.runOnce(() -> {
            shooterTopController.setSetpoint(topRPM, ControlType.kVelocity);
        }, this),

        //  Wait until top motors are at target speed
        Commands.waitUntil(() -> topMotorsAtSpeed(topRPM)),

        //  Spin up bottom motors
        Commands.runOnce(() -> {
            shooterBottomController.setSetpoint(bottomRPM, ControlType.kVelocity);
        }, this)
    );
}

// Command to run the shooter backwards at a set speed
public Command reverseShooter(double RPM) {
    return Commands.runOnce(() -> {
        // Set negative RPM to leaders
        shooterTopController.setSetpoint(-RPM, ControlType.kVelocity);
        shooterBottomController.setSetpoint(-RPM, ControlType.kVelocity);
    }, this);
}

public Command spinUp(double topRPM, double bottomRPM) {
  return Commands.run(
      () -> setShooterRPM(topRPM,bottomRPM),
      this
  );
}
public boolean shooterAtSpeed(double topRPM, double bottomRPM) {
  return  bottomMotorsAtSpeed(bottomRPM)
          && topMotorsAtSpeed(topRPM);
}

 
@Override
public void periodic() {
    double topRPM = shooterLeftTopEncoder.getVelocity();
    double bottomRPM = shooterLeftBottomEncoder.getVelocity();
    double currentTime = System.currentTimeMillis();

    //top recovery 
    if (Math.abs(topRPM - getTopSetpoint()) > Constants.Shooter.shooterMotorTolerance) {
        if (topInTolerance) {  // just dropped below
            topInTolerance = false;
            topRecoveryStart = (long) currentTime;
        }
    } else {  // back within tolerance
        if (!topInTolerance) {
            topInTolerance = true;
            double recoveryTime = currentTime - topRecoveryStart;  // in ms
            topRecoveryTimes.add((long) recoveryTime);
            SmartDashboard.putNumber("Top Shooter Recovery Time (ms)", recoveryTime);
        }
    }

    // bottom recovey
    if (Math.abs(bottomRPM - getBottomSetpoint()) > Constants.Shooter.shooterMotorTolerance) {
        if (bottomInTolerance) { // just dropped below
            bottomInTolerance = false;
            bottomRecoveryStart = (long) currentTime;
        }
    } else { // back within tolerance
        if (!bottomInTolerance) {
            bottomInTolerance = true;
            double recoveryTime = currentTime - bottomRecoveryStart;  // in ms
            bottomRecoveryTimes.add((long) recoveryTime);
            SmartDashboard.putNumber("Bottom Shooter Recovery Time (ms)", recoveryTime);
        }
    }

    // Display current velocities
    SmartDashboard.putNumber("Top Shooter RPM", topRPM);
    SmartDashboard.putNumber("Bottom Shooter RPM", bottomRPM);
    SmartDashboard.putNumber("Shooter/Top RPM", shooterLeftTopEncoder.getVelocity());
   SmartDashboard.putNumber( "Shooter/Bottom RPM", shooterRightBottomEncoder.getVelocity());
}  
   
}
    // This method will be called once per scheduler run
    //putting motor velocities on SmartDashboard
  
   


  


