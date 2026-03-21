// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import java.util.ArrayList;
import java.util.List;

import com.revrobotics.PersistMode;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.ResetMode; // Import the new global ResetMode
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkFlexConfig;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;

public class Shooter extends SubsystemBase {
  // view from shooter side
  private final SparkFlex shooterLeftBottomMotor;
  private final SparkFlex shooterLeftTopMotor;
  private final SparkFlex shooterRightBottomMotor;
  private final SparkFlex shooterRightTopMotor;
  private SparkFlex shooterBittyBottomMotor = null;

  private final RelativeEncoder shooterLeftBottomEncoder;
  private final RelativeEncoder shooterLeftTopEncoder;
  private final RelativeEncoder shooterRightBottomEncoder;
  private final RelativeEncoder shooterRightTopEncoder;
  private RelativeEncoder shooterBittyBottomEncoder;

  private final SparkClosedLoopController shooterBottomController;
  private final SparkClosedLoopController shooterTopController;
  private SparkClosedLoopController shooterBittyBottomController;
  private boolean hardwareFollowConfigured = false;
  private double topTargetRPM = 0.0;
  private double bottomTargetRPM = 0.0;

  // RECOVERY TRACKING STUFF

  // Top shooter recovery tracking
  private boolean topInTolerance = true; // Is top motor currently within target RPM tolerance?
  private long topRecoveryStart = 0; // Time when top motor fell below tolerance
  private final List<Long> topRecoveryTimes = new ArrayList<>(); // List of recorded recovery times

  // Bottom shooter recovery tracking
  private boolean bottomInTolerance = true; // Is bottom motor currently within target RPM tolerance?
  private long bottomRecoveryStart = 0; // Time when bottom motor fell below tolerance
  private final List<Long> bottomRecoveryTimes = new ArrayList<>(); // List of recorded recovery times

  /** Creates a new Shooter. 5 Motors */
  public Shooter(
      final int TOP_LEFT_SHOOTER_ID,
      final int BOTTOM_LEFT_SHOOTER_ID,
      final int TOP_RIGHT_SHOOTER_ID,
      final int BOTTOM_RIGHT_SHOOTER_ID,
      final int TIPPY_TOP_SHOOTER_ID) {
    this(TOP_LEFT_SHOOTER_ID, BOTTOM_LEFT_SHOOTER_ID, TOP_RIGHT_SHOOTER_ID, BOTTOM_RIGHT_SHOOTER_ID);

    // CREATE EXTRA MOTOR
    shooterBittyBottomMotor = new SparkFlex(TIPPY_TOP_SHOOTER_ID, MotorType.kBrushless);

    // OBTAIN ENCODER
    shooterBittyBottomEncoder = shooterBittyBottomMotor.getEncoder();

    // CONFIG CONTROLLER
    shooterBittyBottomController = shooterBittyBottomMotor.getClosedLoopController();
    SparkFlexConfig shooterBittyBottomConfig = new SparkFlexConfig();
    shooterBittyBottomConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
        .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
        .inverted(true);

    shooterBittyBottomConfig.closedLoop.feedForward
        .kS(Constants.Shooter.BITTY_kS)
        .kV(Constants.Shooter.BITTY_kV)
        .kA(Constants.Shooter.BITTY_kA);

    // PID CONFIG
    shooterBittyBottomConfig.closedLoop
        .p(Constants.Shooter.BITTY_BOTTOM_P)
        .i(Constants.Shooter.BITTY_BOTTOM_I)
        .d(Constants.Shooter.BITTY_BOTTOM_D);

    shooterBittyBottomMotor.configure(shooterBittyBottomConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    
    try {
      shooterBittyBottomConfig.follow(shooterLeftBottomMotor); // TODO
      hardwareFollowConfigured = true;
    } catch (Exception ex) {
      hardwareFollowConfigured = false;
    }
  }

  /** Creates a new Shooter. 4 Motors */
  public Shooter(
      final int TOP_LEFT_SHOOTER_ID,
      final int BOTTOM_LEFT_SHOOTER_ID,
      final int TOP_RIGHT_SHOOTER_ID,
      final int BOTTOM_RIGHT_SHOOTER_ID) {
    // CREATE MOTORS
    shooterLeftBottomMotor = new SparkFlex(BOTTOM_LEFT_SHOOTER_ID, MotorType.kBrushless);
    shooterLeftTopMotor = new SparkFlex(TOP_LEFT_SHOOTER_ID, MotorType.kBrushless);
    shooterRightBottomMotor = new SparkFlex(BOTTOM_RIGHT_SHOOTER_ID, MotorType.kBrushless);
    shooterRightTopMotor = new SparkFlex(TOP_RIGHT_SHOOTER_ID, MotorType.kBrushless);

    // OBTAIN ENCODERS
    shooterLeftBottomEncoder = shooterLeftBottomMotor.getEncoder();
    shooterLeftTopEncoder = shooterLeftTopMotor.getEncoder();
    shooterRightBottomEncoder = shooterRightBottomMotor.getEncoder();
    shooterRightTopEncoder = shooterRightTopMotor.getEncoder();

    // CONFIG CONTROLLERS
    shooterBottomController = shooterLeftBottomMotor.getClosedLoopController();
    SparkFlexConfig shooterBottomConfig = new SparkFlexConfig();
    shooterBottomConfig.closedLoop.feedForward
        .kS(Constants.Shooter.BOTTOM_kS)
        .kV(Constants.Shooter.BOTTOM_kV)
        .kA(Constants.Shooter.BOTTOM_kA);

    shooterTopController = shooterLeftTopMotor.getClosedLoopController();
    SparkFlexConfig shooterTopConfig = new SparkFlexConfig();
    shooterTopConfig.closedLoop.feedForward
        .kS(Constants.Shooter.TOP_kS)
        .kV(Constants.Shooter.TOP_kV)
        .kA(Constants.Shooter.TOP_kA);

    // PID CONFIG
    shooterBottomConfig.closedLoop
        .p(Constants.Shooter.BOTTOM_SHOOTER_P)
        .i(Constants.Shooter.BOTTOM_SHOOTER_I)
        .d(Constants.Shooter.BOTTOM_SHOOTER_D);

    shooterTopConfig.closedLoop
        .p(Constants.Shooter.TOP_SHOOTER_P)
        .i(Constants.Shooter.TOP_SHOOTER_I)
        .d(Constants.Shooter.TOP_SHOOTER_D);

    shooterBottomConfig.closedLoopRampRate(Constants.Shooter.RAMP_RATE); //TODO do we want ramp rate?
    shooterTopConfig.closedLoopRampRate(Constants.Shooter.RAMP_RATE);

    shooterLeftTopMotor.configure(shooterTopConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    shooterLeftBottomMotor.configure(shooterBottomConfig, ResetMode.kResetSafeParameters,
        PersistMode.kPersistParameters);

    // configuring the motor
    shooterBottomConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
        .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
        .inverted(false);

    shooterTopConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
        .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
        .inverted(false);

    shooterLeftBottomMotor.configure(shooterBottomConfig, ResetMode.kResetSafeParameters,
        PersistMode.kPersistParameters);
    shooterLeftTopMotor.configure(shooterTopConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    try {
      SparkFlexConfig topFollower = new SparkFlexConfig();
      SparkFlexConfig bottomFollower = new SparkFlexConfig();
      // follow the leader and invert the output for the follower
      topFollower.follow(shooterLeftTopMotor, true);
      bottomFollower.follow(shooterLeftBottomMotor, true);
      // Don't reset previously-applied safe parameters; only enable follower mode
      shooterRightTopMotor.configure(topFollower, ResetMode.kNoResetSafeParameters, PersistMode.kPersistParameters);
      shooterRightBottomMotor.configure(bottomFollower, ResetMode.kNoResetSafeParameters,
          PersistMode.kPersistParameters);
      hardwareFollowConfigured = true;
    } catch (Exception ex) {
      // If the follow configuration isn't available in this REVLib version,
      // we'll fall back to software mirroring (below in periodic()).
      hardwareFollowConfigured = false;
    }
  }

  // methods
  // simple, just stopping motors
  public void stopTopShooterMotors() {
    shooterLeftTopMotor.stopMotor();
  }

  public void stopBottomShooterMotors() {
    shooterLeftBottomMotor.stopMotor();
    if (shooterBittyBottomMotor != null) {
      shooterBittyBottomMotor.stopMotor();
    }
  }

  // find out if motors are at correct speeds :) will be important in testing
  // trust
  // Check if both top motors are at speed
  // Check if both TOP shooter motors are at speed
  public boolean topMotorsAtSpeed() {
    return Math.abs(shooterLeftTopEncoder.getVelocity() - topTargetRPM) < Constants.Shooter.shooterMotorTolerance;
  }

  // Check if both BOTTOM shooter motors are at speed
  public boolean bottomMotorsAtSpeed() {
    return Math.abs(shooterLeftBottomEncoder.getVelocity() - bottomTargetRPM) < Constants.Shooter.shooterMotorTolerance;
  }

  // setting the shooter rpm based off of the table
  public void setShooterRPM(double topRPM, double bottomRPM) {
    topTargetRPM = topRPM;
    bottomTargetRPM = bottomRPM;

    shooterTopController.setSetpoint(topRPM, ControlType.kVelocity);
    shooterBottomController.setSetpoint(bottomRPM, ControlType.kVelocity);

    if (shooterBittyBottomMotor != null) {
      shooterBittyBottomController.setSetpoint(bottomRPM, ControlType.kVelocity);
    }
  }

  public void setShooter(double speed) {
    shooterLeftTopMotor.set(speed);
    shooterLeftBottomMotor.set(speed);

    if (shooterBittyBottomMotor != null) {
      shooterBittyBottomMotor.set(speed);
    }
  }

  public double getTopSetpoint() {
    return shooterTopController.getSetpoint();
  }

  public double getBottomSetpoint() {
    return shooterBottomController.getSetpoint();
  }

  public void stopShooterMotors() {
    shooterLeftTopMotor.stopMotor();
    shooterLeftBottomMotor.stopMotor();
    topTargetRPM = bottomTargetRPM = 0;

    if (shooterBittyBottomMotor != null) {
      shooterBittyBottomMotor.stopMotor();
    }
  }

  private boolean hasTarget(){
    return (topTargetRPM > 0) && (bottomTargetRPM > 0);
  }

  public boolean runIndexer(){
    return hasTarget() && (topMotorsAtSpeed() && bottomMotorsAtSpeed());
  }

  // Starting shooter commands!!

  public Command stopMotors() {
    return Commands.sequence(
        Commands.runOnce(() -> {
          stopShooterMotors();
        }));
  }
  
  // this stuff is for robotLogger frfr
  public double getTopRPM() {
    return shooterLeftTopEncoder.getVelocity();
}
public double getBottomRPM() {
    return shooterLeftBottomEncoder.getVelocity();
}

public double getTopCurrentDraw() {
    return shooterLeftTopMotor.getOutputCurrent();

}
public double getBottomCurrentDraw() {
    return shooterLeftBottomMotor.getOutputCurrent();

}
  // public Command runMotors() {
  // return Commands.sequence(
  // Commands.runOnce(() -> {

  // })
  // )
  // }

  public Command shootCommand(double topRPM, double bottomRPM) {
    return 
        Commands.runOnce(() -> {
          setShooterRPM(topRPM, bottomRPM);
        }, this);
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
        () -> setShooterRPM(topRPM, bottomRPM),
        this);
  }

  public boolean shooterAtSpeed() {
    return bottomMotorsAtSpeed() && topMotorsAtSpeed();
  }

  public Command manualShooterTest() {
    return Commands.run(
        () -> {
          double topRPM = SmartDashboard.getNumber("Shooter/Target Top RPM", 0);

          double bottomRPM = SmartDashboard.getNumber("Shooter/Target Bottom RPM", 0);

          setShooterRPM(topRPM, bottomRPM);
        }, this);
  }

  @Override
  public void periodic() {
    double topRPM = shooterLeftTopEncoder.getVelocity();
    double bottomRPM = shooterLeftBottomEncoder.getVelocity();
    double currentTime = System.currentTimeMillis();

    // top recovery
    if (Math.abs(topRPM - getTopSetpoint()) > Constants.Shooter.shooterMotorTolerance) {
      if (topInTolerance) { // just dropped below
        topInTolerance = false;
        topRecoveryStart = (long) currentTime;
      }
    } else { // back within tolerance
      if (!topInTolerance) {
        topInTolerance = true;
        double recoveryTime = currentTime - topRecoveryStart; // in ms
        topRecoveryTimes.add((long) recoveryTime);
        SmartDashboard.putNumber("Top Shooter Recovery Time (ms)", recoveryTime);
      }
    }

    // bottom recovery
    if (Math.abs(bottomRPM - getBottomSetpoint()) > Constants.Shooter.shooterMotorTolerance) {
      if (bottomInTolerance) { // just dropped below
        bottomInTolerance = false;
        bottomRecoveryStart = (long) currentTime;
      }
    } else { // back within tolerance
      if (!bottomInTolerance) {
        bottomInTolerance = true;
        double recoveryTime = currentTime - bottomRecoveryStart; // in ms
        bottomRecoveryTimes.add((long) recoveryTime);
        SmartDashboard.putNumber("Bottom Shooter Recovery Time (ms)", recoveryTime);
      }
    }
    SmartDashboard.putNumber("Shooter/Top Volts",
        shooterLeftTopMotor.getAppliedOutput() * shooterLeftTopMotor.getBusVoltage());

    // Display current velocities
    SmartDashboard.putNumber("Shooter/Top Target RPM", topTargetRPM);
    SmartDashboard.putNumber("Shooter/Bottom Target RPM", bottomTargetRPM);
    SmartDashboard.putNumber("Shooter/Top RPM", topRPM);
    SmartDashboard.putNumber("Shooter/Bottom RPM", bottomRPM);
    SmartDashboard.putNumber("Shooter/Top Applied", shooterRightTopMotor.getAppliedOutput());
    SmartDashboard.putNumber("Shooter/Bottom Applied", shooterRightBottomMotor.getAppliedOutput());

  }
}