// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.sim.SparkFlexSim;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLimitSwitch;
import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
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
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;

public class Intake extends SubsystemBase {
  // assuming these are singletons
  private final SparkFlex liftMotor;
  private final SparkFlex intakeMotor;
  private final SparkLimitSwitch liftLimitSwitchUp;
  private final SparkLimitSwitch liftLimitSwitchDown;

  private final SparkFlexConfig intakeMotorConfig;
  private final LimitSwitchConfig liftLimitSwitchConfig;
  private final SparkFlexConfig liftMotorConfig;

  private final double intakeLiftSpeed;
  private final double intakeSpeed;

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
    Constants.Intake.LIFT_MIN_RADIANS
  );
  private final FlywheelSim intakeWheelSimulator = new FlywheelSim(
    LinearSystemId.createFlywheelSystem(
      DCMotor.getNeoVortex(1),
      Constants.Intake.WHEEL_MOMENT_OF_INERTIA,
      Constants.Intake.INTAKE_GEAR_RATIO),
      DCMotor.getNeoVortex(1)
  );

  public Intake() {
    liftMotor = new SparkFlex(Constants.Intake.INTAKE_LIFT_MOTOR_ID, MotorType.kBrushless);
    intakeMotor = new SparkFlex(Constants.Intake.INTAKE_MOTOR_ID, MotorType.kBrushless);

    liftLimitSwitchConfig = new LimitSwitchConfig();
    liftLimitSwitchConfig
      .forwardLimitSwitchTriggerBehavior(Behavior.kStopMovingMotorAndSetPosition)
      .forwardLimitSwitchType(Type.kNormallyClosed)
      .forwardLimitSwitchPosition(25)
      .reverseLimitSwitchTriggerBehavior(Behavior.kStopMovingMotorAndSetPosition)
      .reverseLimitSwitchType(Type.kNormallyClosed)
      .reverseLimitSwitchPosition(0);

    intakeMotorConfig = new SparkFlexConfig();
    intakeMotorConfig
      .idleMode(IdleMode.kCoast)
      .smartCurrentLimit(Constants.Intake.CURRENT_LIMIT)
      .voltageCompensation(Constants.Intake.VOLTAGE_LIMIT);
    intakeMotor.configure(intakeMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    liftMotorConfig = new SparkFlexConfig();
    liftMotorConfig
      .idleMode(IdleMode.kBrake)
      .smartCurrentLimit(Constants.Intake.CURRENT_LIMIT)
      .voltageCompensation(Constants.Intake.VOLTAGE_LIMIT)
      .apply(liftLimitSwitchConfig);
    liftMotor.configure(liftMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    liftLimitSwitchUp = liftMotor.getForwardLimitSwitch();
    liftLimitSwitchDown = liftMotor.getReverseLimitSwitch();
    intakeLiftSpeed = Constants.Intake.INTAKE_LIFT_SPEED;
    intakeSpeed = Constants.Intake.INTAKE_SPEED; // Using constant for now

    flexSim = new SparkFlexSim(liftMotor, DCMotor.getNeoVortex(1));
    intakeMotorSim = new SparkFlexSim(intakeMotor, DCMotor.getNeoVortex(1));
  }

  @Override
  public void simulationPeriodic(){
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
      Units.radiansPerSecondToRotationsPerMinute(liftSimulator.getVelocityRadPerSec() * Constants.Intake.LIFT_MOTOR_GEARING),
      RoboRioSim.getVInVoltage(),
      0.02);

    intakeWheelSimulator.setInput(intakeMotorSim.getAppliedOutput() * RoboRioSim.getVInVoltage());
    intakeWheelSimulator.update(0.02);

    intakeMotorSim.iterate(
      intakeWheelSimulator.getAngularVelocityRPM() * Constants.Intake.INTAKE_GEAR_RATIO,
      RoboRioSim.getVInVoltage(),
      0.02);
    
    RoboRioSim.setVInVoltage(BatterySim.calculateDefaultBatteryLoadedVoltage(liftSimulator.getCurrentDrawAmps() + intakeWheelSimulator.getCurrentDrawAmps()));

    SmartDashboard.putNumber("Intake/simulatedAngle", Units.radiansToDegrees(liftSimulator.getAngleRads()));
    SmartDashboard.putNumber("Intake/wheelSpeed", intakeWheelSimulator.getAngularVelocityRPM());
  }

  public void lowerIntake(){
    liftMotor.set(intakeLiftSpeed * -1);
  }

  public void raiseIntake(){
    liftMotor.set(intakeLiftSpeed);
  }

  public void intakeForward(){
    intakeMotor.set(intakeSpeed);
  }

  public void intakeBackward(){
    intakeMotor.set(intakeSpeed * -1);
  }

  public void stopIntake(){
    intakeMotor.stopMotor();
  }

  public boolean isIntakeUp(){
    return liftLimitSwitchUp.isPressed();
  }

  public boolean isIntakeDown(){
    return liftLimitSwitchDown.isPressed();
  }

  @Override
  public void periodic() {
    // This method will be called once per scheduler run
  }

  public Command intakeFuel(){
    return Commands.startEnd(() -> intakeForward(), () -> stopIntake(), this);
  }

  public Command extakeFuel(){
    return Commands.startEnd(() -> intakeBackward(), () -> stopIntake(), this);
  }

  public Command putDownIntake(){
    return Commands.runOnce(() -> lowerIntake(), this);
  }

  public Command putUpIntake(){
    return Commands.runOnce(() -> raiseIntake(), this);
  }
}