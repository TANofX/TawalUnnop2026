package frc.robot.commands;

import java.util.Optional;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.filter.SlewRateLimiter;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants;
import frc.robot.RobotContainer;
import frc.robot.subsystems.Swerve;

public class SwerveDriveWithGamepad extends Command {
  private final SlewRateLimiter xVelLimiter;
  private final SlewRateLimiter yVelLimiter;
  private final SlewRateLimiter angularVelLimiter;
  private final PIDController pidController;
  private final Swerve swerve;
  private static double speedSet = 1;

  public SwerveDriveWithGamepad(Swerve swerve) {
    this.xVelLimiter = new SlewRateLimiter(Constants.Swerve.TELEOP_MAX_ACCELERATION);
    this.yVelLimiter = new SlewRateLimiter(Constants.Swerve.TELEOP_MAX_ACCELERATION);
    this.angularVelLimiter = new SlewRateLimiter(Constants.Swerve.TELEOP_MAX_ANGULAR_ACCELERATION);
    this.pidController = new PIDController(Constants.Joystick.kP, Constants.Joystick.kI, Constants.Joystick.kD);
    this.swerve = swerve;
    addRequirements(RobotContainer.swerve);
  }

  @Override
  public void initialize() {

    this.xVelLimiter.reset(0);
    this.yVelLimiter.reset(0);
    this.angularVelLimiter.reset(0);

    SmartDashboard.putNumber("Speed Dial", speedSet);
  }

  @Override
  public void execute() {

    double rot;
    double angularVel;
    speedSet = SmartDashboard.getNumber("Speed Dial", 0);
    double maxSpeedForChild = speedSet;
    double x = -RobotContainer.driver.getLeftY() * maxSpeedForChild;

    x = Math.copySign(x * x, x);
    double y = -RobotContainer.driver.getLeftX() * maxSpeedForChild;

    y = Math.copySign(y * y, y);
    SmartDashboard.putNumber("Swerve/X", x);
    SmartDashboard.putNumber("Swerve/Y", y);
    Optional<Alliance> currentAlliance = DriverStation.getAlliance();

    if (currentAlliance.isPresent() && currentAlliance.get() == Alliance.Red) {
      x = -1 * x;
      y = -1 * y;
    }

    if (Constants.RED_ALLIANCE_BUMP.contains(swerve.getPose().getTranslation())
        || Constants.BLUE_ALLIANCE_BUMP.contains(swerve.getPose().getTranslation())) {
      // Rotation lock: 45 degrees for the bump
      double currentAngleDeg = swerve.getPose().getRotation().getDegrees();
      double nearest45Deg = Math.round(currentAngleDeg / 45.0) * 45.0;
      double target45Deg = nearest45Deg;
      SmartDashboard.putNumber("Joystick/nearest45", nearest45Deg);
      SmartDashboard.putNumber("Joystick/CurrentAngle", currentAngleDeg);

      rot = pidController.calculate(currentAngleDeg, target45Deg); // TODO tune 45 angle lock PID

      rot = Math.copySign(Math.min(Math.abs(rot), Constants.Swerve.TELEOP_MAX_ANGULAR_VELOCITY), rot);
      angularVel = this.angularVelLimiter.calculate(rot);

    } else {
      rot = -RobotContainer.driver.getRightX() * maxSpeedForChild;
      rot = Math.copySign(rot * rot, rot);
      double targetAngularVel = rot * Constants.Swerve.TELEOP_MAX_ANGULAR_VELOCITY;
      angularVel = this.angularVelLimiter.calculate(targetAngularVel);
    }

    boolean stop = x == 0 && y == 0 && rot == 0;

    double xVel = this.xVelLimiter.calculate(x * Constants.Swerve.TELEOP_MAX_VELOCITY);
    double yVel = this.yVelLimiter.calculate(y * Constants.Swerve.TELEOP_MAX_VELOCITY);

    SmartDashboard.putNumber("Joystick/X", xVel);
    SmartDashboard.putNumber("Joystick/Y", yVel);
    SmartDashboard.putNumber("Joystick/Ang", angularVel);

    if (stop)
      RobotContainer.swerve.driveFieldRelative(new ChassisSpeeds(xVel, yVel, angularVel));

    RobotContainer.swerve.driveFieldRelative(new ChassisSpeeds(xVel, yVel, angularVel));

  }

  @Override
  public void end(boolean interrupted) {
  }

  @Override
  public boolean isFinished() {
    return false;
  }
}
