package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.Turret;

public class CalibrateTurret extends Command {
    private Turret theTurret;

    public CalibrateTurret(Turret calibrateTurret) {
        theTurret = calibrateTurret;
        addRequirements(theTurret);
    }

    @Override
    public void initialize() {
        if (!theTurret.isCalibrated()) {
            theTurret.turnClockwise();
        }
    }
   
    @Override
    public boolean isFinished() {
        return (theTurret.isOnLimitSwitch() || theTurret.isCalibrated());
    }

    @Override
    public void end(boolean interrupted) {
        theTurret.stopTurret();
        if (theTurret.isOnLimitSwitch()) {
        theTurret.calibrateClock();
        }
    }
}
