// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.lib.util;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.SwerveRequest;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.filter.SlewRateLimiter;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import frc.robot.Constants;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.FireControl;

/** Add your docs here. */
public class DriveModes {
    private CommandXboxController controller;
    /* Setting up bindings for necessary control of the swerve drive platform */
    private final SwerveRequest.FieldCentric drive;
    private final double speed;
    private final double angularRate;
    private final CommandSwerveDrivetrain drivetrain;
    private final FireControl fireControl;
    private final PIDController pidController;
    private final SlewRateLimiter angularVelLimiter;

    private Modes currentMode = Modes.NORMAL_JOYSTICK;

    public DriveModes(CommandXboxController joystick, CommandSwerveDrivetrain swerve, FireControl fireControl,
            double maxSpeed,
            double maxAngularRate) {
        drive = new SwerveRequest.FieldCentric()
                .withDeadband(maxSpeed * 0.1).withRotationalDeadband(maxAngularRate * 0.1)
                .withDriveRequestType(DriveRequestType.OpenLoopVoltage);
        controller = joystick;
        drivetrain = swerve;
        this.fireControl = fireControl;
        speed = maxSpeed;
        angularRate = maxAngularRate;
        pidController = new PIDController(Constants.Joystick.kP, Constants.Joystick.kI, Constants.Joystick.kD);
        this.angularVelLimiter = new SlewRateLimiter(angularRate);
    }

    private boolean atBump() {
        if (Constants.RED_ALLIANCE_BUMP.contains(drivetrain.getState().Pose.getTranslation())
                || Constants.BLUE_ALLIANCE_BUMP.contains(drivetrain.getState().Pose.getTranslation())) {
            currentMode = Modes.BUMP;
            return true;
        } else
            currentMode = Modes.NORMAL_JOYSTICK;
        return false;
    }

    private double angularVelocityCalc(double currentAngle, double targetAngle) {
        double rot = pidController.calculate(currentAngle, targetAngle); // TODO tune 45 angle lock PID

        rot = Math.copySign(Math.min(Math.abs(rot), angularRate), rot);
        double angularVelocity = this.angularVelLimiter.calculate(rot);
        return angularVelocity;
    }

    public void setMode(Modes newMode) {
        switch (currentMode) {
            case BUMP:
                switch (newMode) {
                    case SHOOTING_ANGLE:
                        currentMode = Modes.BUMP;
                    break;
                default:
                    currentMode = newMode;
                }
        break;
            case SHOOTING_ANGLE:
            case NORMAL_JOYSTICK:
                currentMode = newMode;
            break;
            }
    }

    public SwerveRequest getDriveRequest() {
        double currentAngleDeg = drivetrain.getState().Pose.getRotation().getDegrees();
        double xVelocity = 0.0;
        double yVelocity = 0.0;
        double angularVelocity = 0.0;

        xVelocity = -controller.getLeftY() * speed; //based on joystick
        yVelocity = -controller.getLeftX() * speed;

        atBump();

        switch (currentMode) {
            case BUMP:
                double target45Deg = Math.round(currentAngleDeg / 45.0) * 45.0;
                angularVelocity = angularVelocityCalc(currentAngleDeg, target45Deg);

                break;
            case SHOOTING_ANGLE:
                double shootingTarget = fireControl.getRobotTarget().getDegrees();
                angularVelocity = angularVelocityCalc(currentAngleDeg, shootingTarget);

                break;
            default:
                angularVelocity = -controller.getRightX() * angularRate;
        }

        return drive.withVelocityX(xVelocity)
                .withVelocityY(yVelocity)
                .withRotationalRate(angularVelocity);
    }

    public enum Modes {
        NORMAL_JOYSTICK,
        BUMP,
        SHOOTING_ANGLE
    }
}
