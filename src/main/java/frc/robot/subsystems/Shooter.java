// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import java.util.ArrayList;
import java.util.List;

import com.revrobotics.RelativeEncoder;
import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkFlexConfig;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.lib.util.BatteryUsage;
import frc.robot.Constants;

public class Shooter extends AdvancedSubsystem {
  private double powerLimit = 1;

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

private final SparkClosedLoopController shooterLeftController;  // leftTop leader
private final SparkClosedLoopController shooterRightController; // rightTop leader
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
      final int BITTY_BOTTOM_SHOOTER_ID) {
    this(TOP_LEFT_SHOOTER_ID, BOTTOM_LEFT_SHOOTER_ID, TOP_RIGHT_SHOOTER_ID, BOTTOM_RIGHT_SHOOTER_ID);

    // CREATE EXTRA MOTOR
    shooterBittyBottomMotor = new SparkFlex(BITTY_BOTTOM_SHOOTER_ID, MotorType.kBrushless);

    // OBTAIN ENCODER
    shooterBittyBottomEncoder = shooterBittyBottomMotor.getEncoder();

    // CONFIG CONTROLLER
    shooterBittyBottomController = shooterBittyBottomMotor.getClosedLoopController();
    SparkFlexConfig shooterBittyBottomConfig = new SparkFlexConfig();
    shooterBittyBottomConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
        .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
        .inverted(false);

    shooterBittyBottomConfig.closedLoop.feedForward
        .kS(Constants.Shooter.BITTY_kS)
        .kV(Constants.Shooter.BITTY_kV)
        .kA(Constants.Shooter.BITTY_kA);

    // PID CONFIG
    shooterBittyBottomConfig.closedLoop
        .p(Constants.Shooter.BITTY_BOTTOM_P)
        .i(Constants.Shooter.BITTY_BOTTOM_I)
        .d(Constants.Shooter.BITTY_BOTTOM_D);

    shooterBittyBottomConfig
    .inverted(false)
    .follow(shooterRightTopMotor, false);

shooterBittyBottomMotor.configure(shooterBittyBottomConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  /** Creates a new Shooter. 4 Motors */
  public Shooter(
      final int TOP_LEFT_SHOOTER_ID,
      final int BOTTOM_LEFT_SHOOTER_ID,
      final int TOP_RIGHT_SHOOTER_ID,
      final int BOTTOM_RIGHT_SHOOTER_ID) {
    super("Shooter");

    BatteryUsage.registerDevice(getName(), 0);

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
    shooterLeftController = shooterLeftTopMotor.getClosedLoopController();
SparkFlexConfig shooterLeftConfig = new SparkFlexConfig();
shooterLeftConfig.closedLoop.feedForward
    .kS(Constants.Shooter.TOP_LEFT_kS)
    .kV(Constants.Shooter.TOP_LEFT_kV)
    .kA(Constants.Shooter.TOP_LEFT_kA);

shooterRightController = shooterRightTopMotor.getClosedLoopController();
SparkFlexConfig shooterRightConfig = new SparkFlexConfig();
shooterRightConfig.closedLoop.feedForward
    .kS(Constants.Shooter.TOP_RIGHT_kS)
    .kV(Constants.Shooter.TOP_RIGHT_kV)
    .kA(Constants.Shooter.TOP_RIGHT_kA);

shooterLeftConfig.closedLoop
    .p(Constants.Shooter.TOP_LEFT_SHOOTER_P)
    .i(Constants.Shooter.TOP_LEFT_SHOOTER_I)
    .d(Constants.Shooter.TOP_LEFT_SHOOTER_D);

shooterRightConfig.closedLoop
    .p(Constants.Shooter.TOP_RIGHT_SHOOTER_P)
    .i(Constants.Shooter.TOP_RIGHT_SHOOTER_I)
    .d(Constants.Shooter.TOP_RIGHT_SHOOTER_D);

shooterLeftConfig.closedLoopRampRate(Constants.Shooter.RAMP_RATE);
shooterRightConfig.closedLoopRampRate(Constants.Shooter.RAMP_RATE);

// REPLACE the entire shooterTopConfig idle/current/voltage/inverted block and the try{} block WITH:

shooterLeftConfig
    .idleMode(IdleMode.kCoast)
    .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
    .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
    .inverted(false); // LEFT LEADER (positive)

shooterLeftTopMotor.configure(shooterLeftConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

try {
    SparkFlexConfig leftBottomConfig = new SparkFlexConfig();
    leftBottomConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
        .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
        .follow(shooterLeftTopMotor, false); // same direction as left leader

    shooterRightConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
        .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
        .inverted(true); // RIGHT LEADER (negative)

    SparkFlexConfig rightBottomConfig = new SparkFlexConfig();
    rightBottomConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Shooter.SHOOTER_CURRENT_STALL_LIMIT, Constants.Shooter.SHOOTER_CURRENT_FREE_LIMIT)
        .voltageCompensation(Constants.Shooter.SHOOTER_VOLTAGE_LIMIT)
        .follow(shooterRightTopMotor, false); // same direction as right leader

    shooterLeftBottomMotor.configure(leftBottomConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    shooterRightTopMotor.configure(shooterRightConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    shooterRightBottomMotor.configure(rightBottomConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    hardwareFollowConfigured = true;
} catch (Exception ex) {
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
    return Math.abs(shooterLeftBottomEncoder.getVelocity() - topTargetRPM)
        < Constants.Shooter.shooterMotorTolerance;
}
public void setShooterRPM(double rpm) {
    topTargetRPM = rpm;
    shooterLeftController.setSetpoint(rpm, ControlType.kVelocity);
    shooterRightController.setSetpoint(rpm, ControlType.kVelocity); // inverted hardware handles direction
}

public double getBottomSetpoint() {
    return shooterRightController.getSetpoint();
}

  public void setShooter(double speed) {
    shooterLeftTopMotor.set(speed);
  

    if (shooterBittyBottomMotor != null) {
    
    }
  }

  public double getTopSetpoint() {
    return shooterRightController.getSetpoint();
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
  return topTargetRPM > 0;
}

  public boolean runIndexer(){
        // return hasTarget() && (topMotorsAtSpeed());
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

  public Command shootCommand(double rpm) {
    return 
        Commands.runOnce(() -> {
          setShooterRPM(rpm);
        }, this);
  }

  // Command to run the shooter backwards at a set speed
  public Command reverseShooter(double RPM) {
    return Commands.runOnce(() -> {
        shooterLeftController.setSetpoint(-RPM, ControlType.kVelocity);
        shooterRightController.setSetpoint(-RPM, ControlType.kVelocity);
    }, this);
}

  public Command spinUp(double rpm) {
    return Commands.run(
        () -> setShooterRPM(rpm),
        this);
  }

  public boolean shooterAtSpeed() {
    return bottomMotorsAtSpeed() && topMotorsAtSpeed();
  }

  public Command manualShooterTest() {
    return Commands.run(
        () -> {
          double rpm = SmartDashboard.getNumber("Shooter/Target Top RPM", 0);

          

          setShooterRPM(rpm);
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
    SmartDashboard.putNumber("Shooter/Bottom Target RPM", topTargetRPM);
    SmartDashboard.putNumber("Shooter/Top RPM", topRPM);
    SmartDashboard.putNumber("Shooter/Bottom RPM", bottomRPM);
    SmartDashboard.putNumber("Shooter/Top Applied", shooterRightTopMotor.getAppliedOutput());
    SmartDashboard.putNumber("Shooter/Bottom Applied", shooterRightBottomMotor.getAppliedOutput());

    reportPowerUsage(getName(), getTotalCurrent(), getTotalVoltage());
          }
        
          private double getTotalVoltage() {
        double total = 0;
        total += shooterLeftTopMotor.getAppliedOutput() * shooterLeftTopMotor.getBusVoltage();
        total += shooterRightTopMotor.getAppliedOutput() * shooterRightTopMotor.getBusVoltage();
        total += shooterLeftBottomMotor.getAppliedOutput() * shooterLeftBottomMotor.getBusVoltage();
        total += shooterRightBottomMotor.getAppliedOutput() * shooterRightBottomMotor.getBusVoltage();
        total += ((shooterBittyBottomMotor == null) ? 0 : shooterBittyBottomMotor.getAppliedOutput() * shooterBittyBottomMotor.getBusVoltage());

        return shooterBittyBottomMotor == null ? 4 : 5;
      }

          private double getTotalCurrent() {
        return shooterRightTopMotor.getOutputCurrent() 
        + shooterLeftTopMotor.getOutputCurrent()
        + shooterRightBottomMotor.getOutputCurrent()
        + shooterLeftBottomMotor.getOutputCurrent()
        + ((shooterBittyBottomMotor == null) ? 0 : shooterBittyBottomMotor.getOutputCurrent()); 
      }
    
      @Override
  protected Command systemCheckCommand() {
    throw new UnsupportedOperationException("Unimplemented method 'systemCheckCommand'");
  }

      @Override
      public void setPowerLimit(double limit) {
        this.powerLimit = limit;
      }
}