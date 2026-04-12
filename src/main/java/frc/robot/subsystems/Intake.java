// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.sim.SparkFlexSim;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLimitSwitch;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.LimitSwitchConfig;
import com.revrobotics.spark.config.SparkFlexConfig;
import com.revrobotics.spark.config.LimitSwitchConfig.Behavior;
import com.revrobotics.spark.config.LimitSwitchConfig.Type;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;

import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.simulation.BatterySim;
import edu.wpi.first.wpilibj.simulation.FlywheelSim;
import edu.wpi.first.wpilibj.simulation.RoboRioSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.lib.util.BatteryUsage;
import frc.lib.util.RollingAverage;
import frc.robot.Constants;

public class Intake extends AdvancedSubsystem {
  enum IntakeState {
    // External States
    STOPPED,
    INTAKE_FULL,
    EXTAKE,
    JOSTLE_INTAKE,
    JOSTLE_EXTAKE,

    // Internal States
    INTAKE_SLOW,
    JAM_CLEARING,
    JAM_CLEARING_STOP
  };

  enum LiftState {
    STOPPED,
    RAISED,
    LOWERED,
    RAISING,
    LOWERING,
    JOSTLE_RAISING,
    JOSTLE_LOWERING
  }

  private double powerLimit = 1;

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

  private IntakeState currentIntakeState = IntakeState.STOPPED;
  private LiftState currentLiftState = LiftState.STOPPED;

  private Timer jamTimer = new Timer();
  private Timer jostleTimer = new Timer();

  private RollingAverage fullCurrentAverage = new RollingAverage(10);
  private RollingAverage fullVelocityAverage = new RollingAverage(10);
  private RollingAverage slowCurrentAverage = new RollingAverage(10);
  private RollingAverage slowVelocityAverage = new RollingAverage(10);

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

  public Intake(int liftMotorID, int intakeMotorID) {
    super("Intake");
    BatteryUsage.registerDevice(getName(), 2);
    liftMotor = new SparkFlex(liftMotorID, MotorType.kBrushless);
    intakeMotor = new SparkFlex(intakeMotorID, MotorType.kBrushless);

    liftLimitSwitchConfig = new LimitSwitchConfig();
    liftLimitSwitchConfig
        .forwardLimitSwitchTriggerBehavior(Behavior.kStopMovingMotorAndSetPosition)
        .forwardLimitSwitchType(Type.kNormallyOpen)
        .forwardLimitSwitchPosition(25)
        .reverseLimitSwitchTriggerBehavior(Behavior.kStopMovingMotorAndSetPosition)
        .reverseLimitSwitchType(Type.kNormallyOpen)
        .reverseLimitSwitchPosition(0);

    intakeMotorConfig = new SparkFlexConfig();
    intakeMotorConfig
        .idleMode(IdleMode.kCoast)
        .smartCurrentLimit(Constants.Intake.CURRENT_LIMIT)
        .inverted(true)
        .voltageCompensation(Constants.Intake.VOLTAGE_LIMIT);
    intakeMotorConfig.closedLoop.pid(Constants.Intake.INTAKE_P, Constants.Intake.INTAKE_I, Constants.Intake.INTAKE_D);
    intakeMotorConfig.closedLoop.feedForward.sva(Constants.Intake.INTAKE_kS, Constants.Intake.INTAKE_kV,
        Constants.Intake.INTAKE_kA);
    intakeMotor.configure(intakeMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

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
    intakeMotorSim = new SparkFlexSim(intakeMotor, DCMotor.getNeoVortex(1));

    jostleTimer.start();
    jamTimer.start();
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
    changeLiftState(LiftState.LOWERING);
  }

  public void raiseIntake() {
    changeLiftState(LiftState.RAISING);
  }

  public void intakeForward() {
    changeIntakeState(IntakeState.INTAKE_FULL);
  }

  public void raiseIntakeToJostle() {
    changeLiftState(LiftState.JOSTLE_RAISING);
  }

  public void intakeToJostle() {
    changeIntakeState(IntakeState.JOSTLE_INTAKE);

  }

  public void lowerIntakeManually() {
    changeLiftState(LiftState.JOSTLE_LOWERING);
  }

  public void intakeBackward() {
    changeIntakeState(IntakeState.EXTAKE);
  }

  public void stopLift() {
    changeLiftState(LiftState.STOPPED);
  }

  public void stopIntake() {
    changeIntakeState(IntakeState.STOPPED);
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

    checkStateTransitions();
  }

  private double getTotalCurrent() {
    return liftMotor.getOutputCurrent()
        + intakeMotor.getOutputCurrent();
  }

  private double getTotalVoltage() {
    double total = 0;
    total += liftMotor.getAppliedOutput() * liftMotor.getBusVoltage();
    total += intakeMotor.getAppliedOutput() * liftMotor.getBusVoltage();

    return total / 2;
  }

  public Command intakeFuel() {
    return Commands.startEnd(() -> intakeForward(), () -> stopIntake(), this);
  }

  public Command extakeFuel() {
    return Commands.startEnd(() -> intakeBackward(), () -> stopIntake(), this);
  }

  public Command putDownIntake() {
    return Commands
        .sequence(Commands.startEnd(() -> lowerIntake(), () -> stopLift(), this).until(() -> isIntakeDown()));
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

  private void checkJamStatus() {
    if (currentIntakeState == IntakeState.INTAKE_FULL) {
      if (fullCurrentAverage.getAverage() > Constants.Intake.CURRENT_LIMIT * 0.9
          && fullVelocityAverage.getAverage() < Constants.Intake.MAX_RPM * Constants.Intake.INTAKE_SPEED * 0.5) {
        changeIntakeState(IntakeState.JAM_CLEARING);
      }
    } else if (currentIntakeState == IntakeState.INTAKE_SLOW) {
      if (slowCurrentAverage.getAverage() > Constants.Intake.CURRENT_LIMIT * 0.9
          && slowVelocityAverage.getAverage() < Constants.Intake.MAX_RPM * Constants.Intake.INTAKE_SPEED * 0.25) {
        changeIntakeState(IntakeState.JAM_CLEARING);
      }
    }
  }

  private void checkStateTransitions() {
    checkJamStatus();

    switch (currentIntakeState) {
      case INTAKE_FULL:
        fullCurrentAverage.add(intakeMotor.getOutputCurrent());
        fullVelocityAverage.add(intakeMotor.getEncoder().getVelocity());
        if (jostleTimer.hasElapsed(Constants.Intake.JOSTLE_TIME_SECONDS)) {
          changeIntakeState(IntakeState.INTAKE_SLOW);
        }
        break;
      case INTAKE_SLOW:
        slowCurrentAverage.add(intakeMotor.getOutputCurrent());
        slowVelocityAverage.add(intakeMotor.getEncoder().getVelocity());
        if (jostleTimer.hasElapsed(Constants.Intake.JOSTLE_SLOW_TIME_SECONDS)) {
          changeIntakeState(IntakeState.INTAKE_FULL);
        }
        break;
      case JAM_CLEARING:
        if (jamTimer.hasElapsed(Constants.Intake.JAM_CLEARING_TIME_SECONDS)) {
          changeIntakeState(IntakeState.INTAKE_FULL);
        }
        break;
      case JAM_CLEARING_STOP:
        if (jamTimer.hasElapsed(Constants.Intake.JAM_CLEARING_TIME_SECONDS)) {
          changeIntakeState(IntakeState.STOPPED);
        }
        break;
      default:
        break;
    }

    switch (currentLiftState) {
      case RAISING:
        if (isIntakeUp()) {
          changeLiftState(LiftState.RAISED);
        }
        break;
      case LOWERING:
        if (isIntakeDown()) {
          changeLiftState(LiftState.LOWERED);
        }
        break;
      default:
        break;
    }
  }

  public void changeIntakeState(IntakeState state) {
    switch (state) {
      case INTAKE_FULL:
        if (currentIntakeState != IntakeState.INTAKE_FULL) {
          jostleTimer.reset();
          runIntake(-1.0);
          currentIntakeState = state;
        }
        break;
      case EXTAKE:
        if (currentIntakeState != IntakeState.EXTAKE) {
          runIntake(0.5);
          currentIntakeState = state;
        }
        break;
      case INTAKE_SLOW:
        if (currentIntakeState != IntakeState.INTAKE_SLOW) {
          jostleTimer.reset();
          runIntake(-0.25);
          currentIntakeState = state;
        }
        break;
      case JAM_CLEARING:
        if (currentIntakeState != IntakeState.JAM_CLEARING) {
          jamTimer.reset();
          runIntake(0.15);
          currentIntakeState = state;
        }
        break;
      case JAM_CLEARING_STOP:
        if (currentIntakeState != IntakeState.JAM_CLEARING_STOP) {
          currentIntakeState = state;
        }
        break;
      case JOSTLE_EXTAKE:
        if (currentIntakeState != IntakeState.JOSTLE_EXTAKE) {
          runIntake(0.1);
          currentIntakeState = state;
        }
        break;
      case JOSTLE_INTAKE:
        if (currentIntakeState != IntakeState.JOSTLE_INTAKE) {
          runIntake(-0.5);
          currentIntakeState = state;
        }
        break;
      default:
      case STOPPED:
        switch (currentIntakeState) {
          case JAM_CLEARING:
            currentIntakeState = IntakeState.JAM_CLEARING_STOP;
            break;
          case STOPPED:
            break;
          default:
            stopIntakeMotor();
            break;
        }
        break;
    }
  }

  public void changeLiftState(LiftState state) {
    switch (state) {
      case RAISING:
        if (currentLiftState != LiftState.RAISING) {
          runLift(-1.0);
          currentLiftState = state;
        }
        break;
      case LOWERING:
        if (currentLiftState != LiftState.LOWERING) {
          runLift(1.0);
          currentLiftState = state;
        }
        break;
      case JOSTLE_LOWERING:
        if (currentLiftState != LiftState.JOSTLE_LOWERING) {
          runLift(0.25);
          currentLiftState = state;
        }
        break;
      case JOSTLE_RAISING:
        if (currentLiftState != LiftState.JOSTLE_RAISING) {
          runLift(-0.25);
          currentLiftState = state;
        }
        break;
      default:
      case STOPPED:
        switch (currentLiftState) {
          case STOPPED:
          case RAISED:
          case LOWERED:
            break;
          default:
            stopLiftMotor();
            if (isIntakeDown()) {
              currentLiftState = LiftState.LOWERED;
            } else if (isIntakeUp()) {
              currentLiftState = LiftState.RAISED;
            } else {
              currentLiftState = LiftState.STOPPED;
            }
            break;
        }
        break;
    }
  }

  private void runIntake(double speedFraction) {
    // Negative speed intakes fuel, positive speed extakes fuel
    intakeMotor.set(speedFraction * intakeSpeed * powerLimit);
  }

  private void runLift(double speedFraction) {
    // Negative speed raises the intake, positive speed lowers it
    liftMotor.set(speedFraction * intakeLiftSpeed * powerLimit);
  }

  private void stopIntakeMotor() {
    intakeMotor.stopMotor();
  }

  private void stopLiftMotor() {
    liftMotor.stopMotor();
  }
}