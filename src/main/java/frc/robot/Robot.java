// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import java.util.Optional;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.cscore.CvSink;
import edu.wpi.first.cscore.CvSource;
import edu.wpi.first.cscore.UsbCamera;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

public class Robot extends TimedRobot {
  private Command m_autonomousCommand;
  // Thread m_visionThread;
  private final RobotContainer m_robotContainer;

  public Optional<Alliance> alliance;

  public Robot() {
    m_robotContainer = new RobotContainer();
    // m_visionThread = new Thread(
    //     () -> {
    //       UsbCamera camera = CameraServer.startAutomaticCapture();
    //       camera.setResolution(640, 480);

    //       CvSink cvSink = CameraServer.getVideo();
    //       CvSource outputStream = CameraServer.putVideo("Driver's Cam", 640, 480);

    //       Mat mat = new Mat();

    //       while (!Thread.interrupted()) {
    //         if (cvSink.grabFrame(mat) == 0) {
    //           outputStream.notifyError(cvSink.getError());
    //           continue;
    //         }
    //         Imgproc.rectangle(
    //             mat, new Point(100, 100), new Point(400, 400), new Scalar(255, 255, 255), 5);
    //         outputStream.putFrame(mat);
    //       }
    //     });
    // m_visionThread.setDaemon(true);
    // m_visionThread.start();
  }

  @Override
  public void robotPeriodic() {
    CommandScheduler.getInstance().run();
  }

  @Override
  public void disabledInit() {
  }

  @Override
  public void disabledPeriodic() {
  }

  @Override
  public void disabledExit() {
  }

  @Override
  public void autonomousInit() {
    m_autonomousCommand = m_robotContainer.getAutonomousCommand();

    if (m_autonomousCommand != null) {
      CommandScheduler.getInstance().schedule(m_autonomousCommand);
    }
  }

  @Override
  public void autonomousPeriodic() {
  }

  @Override
  public void autonomousExit() {
  }

  @Override
  public void teleopInit() {
    if (m_autonomousCommand != null) {
      m_autonomousCommand.cancel();
    }
  }

  @Override
  public void teleopPeriodic() {
  }

  @Override
  public void teleopExit() {
  }

  @Override
  public void testInit() {
    CommandScheduler.getInstance().cancelAll();
  }

  @Override
  public void testPeriodic() {
  }

  @Override
  public void testExit() {
  }
}
