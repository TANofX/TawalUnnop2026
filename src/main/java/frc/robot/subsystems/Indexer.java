// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.PersistMode;
import com.revrobotics.ResetMode;
import com.revrobotics.sim.SparkFlexSim;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkFlex;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkFlexConfig;

import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.system.plant.LinearSystemId;
import edu.wpi.first.wpilibj.simulation.BatterySim;
import edu.wpi.first.wpilibj.simulation.FlywheelSim;
import edu.wpi.first.wpilibj.simulation.RoboRioSim;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.lib.util.BatteryUsage;
import frc.robot.Constants;

public class Indexer extends AdvancedSubsystem {
  private final SparkFlex indexerMotor;
  private final SparkFlexConfig indexerMotorConfig;
  private final SparkClosedLoopController indexerMotorController;
  private final SparkFlex agitatorMotor;
  private final SparkFlexConfig agitatorMotorConfig;
  private final SparkClosedLoopController agitatorMotorController;

  private final SparkFlexSim flexSim;

  private final FlywheelSim indexerWheelSimulator = new FlywheelSim(
      LinearSystemId.createFlywheelSystem(
          DCMotor.getNeoVortex(1),
          Constants.Indexer.WHEEL_MOMENT_OF_INERTIA,
          Constants.Indexer.INDEXER_GEAR_RATIO),
      DCMotor.getNeoVortex(1));

  public Indexer(int indexerMotorID, int agitatorMotorID) {
    super("Indexer");
    BatteryUsage.registerDevice(getName(), 1);
    indexerMotor = new SparkFlex(indexerMotorID, MotorType.kBrushless);
    indexerMotorController = indexerMotor.getClosedLoopController();
    indexerMotorConfig = new SparkFlexConfig();
    indexerMotorConfig.closedLoop.feedForward.sva(Constants.Indexer.INDEXER_kS, Constants.Indexer.INDEXER_kV,
        Constants.Indexer.INDEXER_kA);
    indexerMotorConfig.closedLoop.pid(Constants.Indexer.INDEXER_P, Constants.Indexer.INDEXER_I,
        Constants.Indexer.INDEXER_D);
    indexerMotorConfig.closedLoopRampRate(Constants.Indexer.RAMP_RATE);
    indexerMotorConfig
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(Constants.Indexer.CURRENT_LIMIT)
        .voltageCompensation(Constants.Indexer.VOLTAGE_LIMIT)
        .inverted(true);
    indexerMotor.configure(indexerMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    flexSim = new SparkFlexSim(indexerMotor, DCMotor.getNeoVortex(1));

    agitatorMotor = new SparkFlex(agitatorMotorID, MotorType.kBrushless);
    agitatorMotorController = agitatorMotor.getClosedLoopController();
    agitatorMotorConfig = new SparkFlexConfig();
    agitatorMotorConfig.closedLoop.feedForward.sva(Constants.Indexer.INDEXER_kS, Constants.Indexer.INDEXER_kV,
        Constants.Indexer.INDEXER_kA);
    agitatorMotorConfig.closedLoop.pid(Constants.Indexer.INDEXER_P, Constants.Indexer.INDEXER_I,
        Constants.Indexer.INDEXER_D);
    agitatorMotorConfig
        .idleMode(IdleMode.kBrake)
        .smartCurrentLimit(Constants.Indexer.CURRENT_LIMIT)
        .voltageCompensation(Constants.Indexer.VOLTAGE_LIMIT)
        .inverted(true);
    agitatorMotor.configure(agitatorMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  @Override
  public void simulationPeriodic() {
    indexerWheelSimulator.setInput(flexSim.getAppliedOutput() * RoboRioSim.getVInVoltage());
    indexerWheelSimulator.update(0.02);

    flexSim.iterate(
        indexerWheelSimulator.getAngularVelocityRPM() * Constants.Indexer.INDEXER_GEAR_RATIO,
        RoboRioSim.getVInVoltage(),
        0.02);

    RoboRioSim
        .setVInVoltage(BatterySim.calculateDefaultBatteryLoadedVoltage(indexerWheelSimulator.getCurrentDrawAmps()));
    SmartDashboard.putNumber("Indexer/wheelSpeed", indexerWheelSimulator.getAngularVelocityRPM());
  }

  public void indexerForward() {
    indexerMotor.set(Constants.Indexer.SPEED);
    agitatorMotor.set(Constants.Indexer.SPEED);
    // indexerMotorController.setSetpoint(4000, ControlType.kVelocity);
    // agitatorMotorController.setSetpoint(4000, ControlType.kVelocity);
  }

  public void indexerBackward() {
    indexerMotor.set(Constants.Indexer.SPEED * -1);
    agitatorMotor.set(Constants.Indexer.SPEED * -1);
    // indexerMotorController.setSetpoint(-4000, ControlType.kVelocity);
    // agitatorMotorController.setSetpoint(-4000, ControlType.kVelocity);
  }

  public void stopIndexer() {
    indexerMotor.stopMotor();
    agitatorMotor.stopMotor();
  }

  public double getIndexerMotorRPM() {
    return indexerMotor.getEncoder().getVelocity();
  }

  public double getAgitatorMotorRPM() {
    return agitatorMotor.getEncoder().getVelocity();
  }
  public Command shootFuel() {
    return Commands.startEnd(() -> indexerForward(), () -> stopIndexer(), this);
  }

  public Command clearFuel() {
    return Commands.startEnd(() -> indexerBackward(), () -> stopIndexer(), this);
  }

  @Override
  public void periodic() {
    SmartDashboard.putNumber("Indexer/output", indexerMotor.getAppliedOutput());
    SmartDashboard.putNumber("Indexer/Indexer Current Speed", getIndexerMotorRPM());
    SmartDashboard.putNumber("Indexer/Indexer Current Speed", getAgitatorMotorRPM());

    reportPowerUsage(getName(), indexerMotor.getAppliedOutput() * indexerMotor.getBusVoltage(), indexerMotor.getOutputCurrent());
  }

  @Override
  protected Command systemCheckCommand() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'systemCheckCommand'");
  }

  @Override
  public void setPowerLimit(double limit) {
  }
}
