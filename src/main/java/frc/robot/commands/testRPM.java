// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.RobotContainer;

/* You should consider using the more terse Command factories API instead https://docs.wpilib.org/en/stable/docs/software/commandbased/organizing-command-based.html#defining-commands */
public class testRPM extends Command {
  public double speed = 0.0;
  public boolean adding = true;
  private Timer increaseSpeed = new Timer();

  /** Creates a new testRPM. */
  public testRPM() {
    // Use addRequirements() here to declare subsystem dependencies.
    addRequirements(RobotContainer.shooter);
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    speed = 0.0;
    increaseSpeed.start();
  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {
    RobotContainer.shooter.setShooter(speed);
 
    if (increaseSpeed.hasElapsed(0.2))
    {
      if (adding) {
            speed += 0.01;
      } else{ speed -= 0.01;} 


      if ((speed >= 1.0) || (speed <= -1.0)) {
        adding =! adding;
      }

      increaseSpeed.reset();
    }
  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
    increaseSpeed.stop();
    RobotContainer.shooter.setShooter(0.0);
  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return false;
  }
}
