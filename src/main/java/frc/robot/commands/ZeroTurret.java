// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import java.util.Iterator;
import java.util.NoSuchElementException;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Turret;

/* You should consider using the more terse Command factories API instead https://docs.wpilib.org/en/stable/docs/software/commandbased/organizing-command-based.html#defining-commands */
public class ZeroTurret extends Command {
  private Iterator<Turret.TurretSwitch> switches;
  private Turret.TurretSwitch currentSwitch = null;

  private boolean clockwise;
  private boolean switchedOn;
  private boolean measureSwitch;

  private Turret theTurret;

  private double clockSum = 0;
  private double clockCount = 0;
  private double counterSum = 0;
  private double counterCount = 0;

  private Timer zeroTimer;

  private int cycles;

  private static final int TOTAL_CYCLES = 8;

  /** Creates a new ZeroTurret. */
  public ZeroTurret(Turret turretToZero) {
    switches = turretToZero.getSwitches().iterator();

    theTurret = turretToZero;

    zeroTimer = new Timer();

    // Use addRequirements() here to declare subsystem dependencies.
    addRequirements(turretToZero);
  }

  // Called when the command is initially scheduled.
  @Override
  public void initialize() {
    clockwise = true;
    switchedOn = false;
    measureSwitch = false;
    theTurret.zero();
    cycles = 0;
    //Zero Turret - This sets the encoder position of the turret motor to 0

    // Get our first switch
    resetSwitch();
  }

  // Called every time the scheduler runs while the command is scheduled.
  @Override
  public void execute() {
    SmartDashboard.putNumber(getName() + "/clockCount", clockCount);
    SmartDashboard.putNumber(getName() + "/clockSum", clockSum);
    SmartDashboard.putBoolean(getName() + "/clockwise", clockwise);
    SmartDashboard.putBoolean(getName() + "/switchedOn", switchedOn);
    SmartDashboard.putBoolean(getName() + "/measureSwitch", measureSwitch);
    
    if(clockwise) {
      theTurret.turnClockwise();
      if(switchedOn  && currentSwitch.isSwitchOn()) {
        counterCount++;
        counterSum += theTurret.getCurrentRot();
        switchedOn = false;
        zeroTimer.reset();
      } else if(!currentSwitch.isSwitchOn()) {
        clockCount++;
        clockSum += theTurret.getCurrentRot();
        zeroTimer.reset();     
        switchedOn = true;
        measureSwitch = true; 
      } else if(zeroTimer.hasElapsed(0.2) && measureSwitch) {
        clockwise = false;
        measureSwitch = false;
        cycles++;
      }
    } else {
      theTurret.turnCounterClockwise();
      if(switchedOn && currentSwitch.isSwitchOn()) {
        clockCount++;
        clockSum += theTurret.getCurrentRot();
        switchedOn = false;
        zeroTimer.reset();
      } else if(!currentSwitch.isSwitchOn()) {
        counterCount++;
        counterSum += theTurret.getCurrentRot();
        zeroTimer.reset();     
        switchedOn = true;
        measureSwitch = true; 
      } else if(zeroTimer.hasElapsed(0.2) && measureSwitch) {
        clockwise = true;
        measureSwitch = false;
        cycles++;
      }
    } 
    if(cycles >= TOTAL_CYCLES) {
      resetSwitch();
    }
  }

  // Called once the command ends or is interrupted.
  @Override
  public void end(boolean interrupted) {
    theTurret.stopTurret();
  }

  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return (currentSwitch == null);
  }

  private void resetSwitch() {
    if (currentSwitch != null)
      currentSwitch.zero(clockSum / clockCount, counterSum / counterCount);
    try {
      currentSwitch = switches.next();
    } catch (NoSuchElementException nee) {
      currentSwitch = null;
    }
    clockSum = 0;
    clockCount = 0;
    counterSum = 0;
    counterCount = 0;
    zeroTimer.start();
  }
}
