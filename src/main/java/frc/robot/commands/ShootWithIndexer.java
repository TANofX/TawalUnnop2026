// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Shooter;

/* You should consider using the more terse Command factories API instead https://docs.wpilib.org/en/stable/docs/software/commandbased/organizing-command-based.html#defining-commands */
public class ShootWithIndexer extends Command {
  private Shooter shooter;
  private Indexer indexer;
  private Timer timer;

  private enum RollerState {
    STOPPED,
    FORWARD,
    BACKWARD
  }

  private RollerState rollerState;

  public ShootWithIndexer(Shooter shooter, Indexer indexer) {
    this.shooter = shooter;
    this.indexer = indexer;
    timer = new Timer();
    // this.turret = turret;
    addRequirements(this.indexer);
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    indexer.stopIndexer();
    timer.start();
    rollerState = RollerState.STOPPED;
  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {
    if (shooter.runIndexer()) {
      switch (rollerState) {
        case STOPPED:
          indexer.indexerForward();
          rollerState = RollerState.FORWARD;
          timer.reset();
          break;
        case FORWARD:
          if (timer.hasElapsed(2.0)) {
            indexer.indexerRollBackward();
            timer.reset();
            rollerState = RollerState.BACKWARD;
          }
          break;
        case BACKWARD:
          if (timer.hasElapsed(0.5)) {
            indexer.indexerForward();
            timer.reset();
            rollerState = RollerState.FORWARD;
          }
          break;
        default:
          break;
      }
      SmartDashboard.putString("Indexer Default Command", rollerState.name());
    } else {
      indexer.stopIndexer();
      rollerState = RollerState.STOPPED;
      SmartDashboard.putString("Indexer Default Command", rollerState.name());
    }
  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
    indexer.stopIndexer();
  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return false;
  }
}
