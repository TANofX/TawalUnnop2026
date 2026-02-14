// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.sim.SparkFlexSim;
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
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;

public class Indexer extends SubsystemBase {
  private final SparkFlex indexerMotor;
  private final SparkFlexConfig indexerMotorConfig;

  private final SparkFlexSim flexSim;

  private final FlywheelSim indexerWheelSimulator = new FlywheelSim(
    LinearSystemId.createFlywheelSystem(
      DCMotor.getNeoVortex(1),
      Constants.Indexer.WHEEL_MOMENT_OF_INERTIA,
      Constants.Indexer.INDEXER_GEAR_RATIO),
      DCMotor.getNeoVortex(1)
  );

  public Indexer() {
    indexerMotor = new SparkFlex(Constants.Indexer.INDEXER_MOTOR_ID, MotorType.kBrushless);
        indexerMotorConfig = new SparkFlexConfig();
   indexerMotorConfig
      .idleMode(IdleMode.kBrake)
      .smartCurrentLimit(Constants.Indexer.CURRENT_LIMIT)
      .voltageCompensation(Constants.Indexer.VOLTAGE_LIMIT);
    indexerMotor.configure(indexerMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);

    flexSim = new SparkFlexSim(indexerMotor, DCMotor.getNeoVortex(1));
  }

  @Override
    public void simulationPeriodic() {
        indexerWheelSimulator.setInput(flexSim.getAppliedOutput() * RoboRioSim.getVInVoltage());
    indexerWheelSimulator.update(0.02);

    flexSim.iterate(
      indexerWheelSimulator.getAngularVelocityRPM() * Constants.Indexer.INDEXER_GEAR_RATIO,
      RoboRioSim.getVInVoltage(),
      0.02);

      RoboRioSim.setVInVoltage(BatterySim.calculateDefaultBatteryLoadedVoltage(indexerWheelSimulator.getCurrentDrawAmps()));
      SmartDashboard.putNumber("Indexer/wheelSpeed", indexerWheelSimulator.getAngularVelocityRPM());
    }

  public void indexerForward(){
    indexerMotor.set(Constants.Indexer.SPEED);
  }

  public void indexerBackward(){
    indexerMotor.set(Constants.Indexer.SPEED * -1);
  }

   public void stopIndexer (){
    indexerMotor.stopMotor();
   }

  public Command shootFuel(){
    return Commands.startEnd(() -> indexerForward(), () -> stopIndexer(), this);
  }

  public Command clearFuel(){
    return Commands.startEnd(() -> indexerBackward(), () -> stopIndexer(), this);
  }

  @Override
  public void periodic() {
    
  }
}
