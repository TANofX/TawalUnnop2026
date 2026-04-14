// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.sim.SparkFlexSim;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLimitSwitch;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.LimitSwitchConfig;
import com.revrobotics.spark.config.SparkFlexConfig;
import com.revrobotics.spark.config.LimitSwitchConfig.Behavior;
import com.revrobotics.spark.config.LimitSwitchConfig.Type;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.simulation.BatterySim;
import edu.wpi.first.wpilibj.simulation.FlywheelSim;
import edu.wpi.first.wpilibj.simulation.RoboRioSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.lib.util.BatteryUsage;
import frc.robot.Constants;

public class Intake extends AdvancedSubsystem {
  private double powerLimit = 1;

  // assuming these are singletons
  private final SparkFlex liftMotor;
  private final SparkFlex leftIntakeMotor;
  private final SparkFlex rightIntakeMotor;
  private final SparkLimitSwitch liftLimitSwitchUp;
  private final SparkLimitSwitch liftLimitSwitchDown;

  private final SparkFlexConfig leftIntakeMotorConfig;
  private final SparkFlexConfig rightIntakeMotorConfig;
  private final LimitSwitchConfig liftLimitSwitchConfig;
  private final SparkFlexConfig liftMotorConfig;

  private final double intakeLiftSpeed;
  private final double intakeSpeed;
  private boolean hardwareFollowConfigured = false;
  private final SparkFlexSim flexSim;
  private final SparkFlexSim intakeMotorSim;

  private final SingleJointedArmSim liftSimulator = new SingleJointedArmSim(
      DCMotor.getNeoVortex(1),
      Constants.Intake.LIFT_MOTOR_GEARING,
      Constants.Intake.LIFT_JKMETERS_SQUARED,
      Constants.Intake.INTAKE_REACH_METERS,
      Constants.Intake.LIFT_MIN_RADIANS,
      Constants.Intake.LIFT_MAX_RADIANS,
      true,
      Constants.Intake.LIFT_MIN_RADIANS);
  private final FlywheelSim intakeWheelSimulator = new FlywheelSim(
      LinearSystemId.createFlywheelSystem(
          DCMotor.getNeoVortex(1),
          Constants.Intake.WHEEL_MOMENT_OF_INERTIA,
          Constants.Intake.INTAKE_GEAR_RATIO),
      DCMotor.getNeoVortex(1));

  public Intake(int liftMotorID, int leftIntakeMotorID, int rightIntakeMotorID) {
    super("Intake");
    BatteryUsage.registerDevice(getName(), 2);
    liftMotor = new SparkFlex(liftMotorID, MotorType.kBrushless);
    leftIntakeMotor = new SparkFlex(leftIntakeMotorID, MotorType.kBrushless);
    rightIntakeMotor = new SparkFlex(rightIntakeMotorID, MotorType.kBrushless);


    liftLimitSwitchConfig = new LimitSwitchConfig();
    liftLimitSwitchConfig
        .forwardLimitSwitchTriggerBehavior(Behavior.kStopMovingMotorAndSetPosition)
        .forwardLimitSwitchType(Type.kNormallyOpen)
        .forwardLimitSwitchPosition(25)
        .reverseLimitSwitchTriggerBehavior(Behavior.kStopMovingMotorAndSetPosition)
        .reverseLimitSwitchType(Type.kNormallyOpen)
        .reverseLimitSwitchPosition(0);

    leftIntakeMotorConfig = new SparkFlexConfig();
    leftIntakeMotorConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Intake.CURRENT_LIMIT)
        .inverted(true)
        .voltageCompensation(Constants.Intake.VOLTAGE_LIMIT);
    rightIntakeMotorConfig = new SparkFlexConfig();
    rightIntakeMotorConfig
        .idleMode(IdleMode.kCoast)        
        .inverted(false)
        .follow(leftIntakeMotor); 
        
    leftIntakeMotorConfig.closedLoop.pid(Constants.Intake.INTAKE_P, Constants.Intake.INTAKE_I, Constants.Intake.INTAKE_D);
    leftIntakeMotorConfig.closedLoop.feedForward.sva(Constants.Intake.INTAKE_kS, Constants.Intake.INTAKE_kV,
        Constants.Intake.INTAKE_kA);
    leftIntakeMotor.configure(leftIntakeMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

   

    liftMotorConfig = new SparkFlexConfig();
    liftMotorConfig
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(Constants.Intake.CURRENT_LIMIT)
        .voltageCompensation(Constants.Intake.VOLTAGE_LIMIT)
        .inverted(true)
        .apply(liftLimitSwitchConfig);
        
        
    liftMotor.configure(liftMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    

  
   

    liftLimitSwitchUp = liftMotor.getReverseLimitSwitch();
    liftLimitSwitchDown = liftMotor.getForwardLimitSwitch();
    intakeLiftSpeed = Constants.Intake.INTAKE_LIFT_SPEED;
    intakeSpeed = Constants.Intake.INTAKE_SPEED; // Using constant for now

    flexSim = new SparkFlexSim(liftMotor, DCMotor.getNeoVortex(1));
    intakeMotorSim = new SparkFlexSim(leftIntakeMotor, DCMotor.getNeoVortex(1));
  }

  @Override
  public void simulationPeriodic() {
    liftSimulator.setInput(flexSim.getAppliedOutput() * RoboRioSim.getVInVoltage());
    liftSimulator.update(0.02);

    if (liftSimulator.hasHitUpperLimit()) {
      flexSim.getForwardLimitSwitchSim().setPressed(true);
    } else {
      flexSim.getForwardLimitSwitchSim().setPressed(false);
    }

    if (liftSimulator.hasHitLowerLimit()) {
      flexSim.getReverseLimitSwitchSim().setPressed(true);
    } else {
      flexSim.getReverseLimitSwitchSim().setPressed(false);
    }

    flexSim.iterate(
        Units.radiansPerSecondToRotationsPerMinute(
            liftSimulator.getVelocityRadPerSec() * Constants.Intake.LIFT_MOTOR_GEARING),
        RoboRioSim.getVInVoltage(),
        0.02);

    intakeWheelSimulator.setInput(intakeMotorSim.getAppliedOutput() * RoboRioSim.getVInVoltage());
    intakeWheelSimulator.update(0.02);

    intakeMotorSim.iterate(
        intakeWheelSimulator.getAngularVelocityRPM() * Constants.Intake.INTAKE_GEAR_RATIO,
        RoboRioSim.getVInVoltage(),
        0.02);

    RoboRioSim.setVInVoltage(BatterySim.calculateDefaultBatteryLoadedVoltage(
        liftSimulator.getCurrentDrawAmps() + intakeWheelSimulator.getCurrentDrawAmps()));

    SmartDashboard.putNumber("Intake/simulatedAngle", Units.radiansToDegrees(liftSimulator.getAngleRads()));
    SmartDashboard.putNumber("Intake/wheelSpeed", intakeWheelSimulator.getAngularVelocityRPM());
  }

  public void lowerIntake() {
    liftMotor.set(intakeLiftSpeed);
  }

  public void raiseIntake() {
    liftMotor.set(intakeLiftSpeed * -1);
  }

  public void intakeForward() {
    leftIntakeMotor.getClosedLoopController().setSetpoint(Constants.Intake.INTAKE_RPM * powerLimit * -1.0, ControlType.kVelocity);
    //intakeMotor.set(intakeSpeed * powerLimit * -1);
  }
  public void raiseIntakeToJostle() {
    liftMotor.set(-0.1);
  }
  public void intakeToJostle() {
    leftIntakeMotor.getClosedLoopController().setSetpoint(Constants.Intake.INTAKE_RPM * 0.25, ControlType.kVelocity);
    //intakeMotor.set(-0.2);
    
  } 
  public void lowerIntakeManually() {
    liftMotor.set(-.1);
  }

  public void intakeBackward() {
    leftIntakeMotor.getClosedLoopController().setSetpoint(Constants.Intake.INTAKE_RPM * powerLimit, ControlType.kVelocity);
    //intakeMotor.set((intakeSpeed * powerLimit));
  }

  public void stopLift() {
    liftMotor.stopMotor();
  }

  public void stopIntake() {
  leftIntakeMotor.stopMotor();
  }

  public boolean isIntakeUp() {
    return liftLimitSwitchUp.isPressed();
  }

  public boolean isIntakeDown() {
    return liftLimitSwitchDown.isPressed();
  }
  

  @Override
  public void periodic() {
    SmartDashboard.putBoolean("Intake/isDown", isIntakeDown());
    SmartDashboard.putBoolean("Intake/isUp", isIntakeUp());

    SmartDashboard.putNumber("Intake/liftApplied", liftMotor.getAppliedOutput());

    reportPowerUsage(getName(), getTotalVoltage(), getTotalCurrent());
          }
        
          private double getTotalCurrent() {
        return liftMotor.getOutputCurrent()
        + leftIntakeMotor.getOutputCurrent();
      }
    
          private double getTotalVoltage() {
        double total = 0;
        total += liftMotor.getAppliedOutput() * liftMotor.getBusVoltage();
        total += leftIntakeMotor.getAppliedOutput() * liftMotor.getBusVoltage();

        return total/2;
      }
    
      public Command intakeFuel() {
    return Commands.startEnd(() -> intakeForward(), () -> stopIntake(), this);
  }

  public Command extakeFuel() {
    return Commands.startEnd(() -> intakeBackward(), () -> stopIntake(), this);
  }

  public Command putDownIntake() {
    return Commands.sequence(Commands.startEnd(() -> lowerIntake(), () -> stopLift(), this).until(() -> isIntakeDown()));
  }

  public Command putUpIntake() {
    return Commands.sequence(Commands.startEnd(() -> raiseIntake(), () -> stopLift(), this).until(() -> isIntakeUp()));
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