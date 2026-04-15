package frc.robot;

import static edu.wpi.first.units.Units.Meter;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.pathplanner.lib.config.PIDConstants;

import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rectangle2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.numbers.N1;
import edu.wpi.first.math.numbers.N3;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.units.measure.Distance;

public final class Constants {
    /**
   * Annotate CAN ID fields with this annotation so we can detect duplicates in a
   * unit test
   */

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface CanId {
    /**
     * The type of device that this CAN ID is for.
     *
     * You can use the same CAN ID for two different devices of different types
     * (e.g.: a Spark MAX motor and a Spark FLEX motor, or a Spark MAX motor and an
     * encoder).
     * (This is because the real CAN ID is much larger, but WPILib gives us 6 bytes
     * for ID and uses the device ID
     * for the other bytes.)
     * We could be more specific than these types, but for now we expect to want to
     * use the same CAN ID for a motor
     * and a corresponding encoder, but not for two motors. This could change.
     */
    Type value();

    /**
     * The device types.
     */
    enum Type {
      MOTOR,
      ENCODER,
      PIGEON,
      PCM_CONTROLLER,
    }
  }

  public static final String CARNIVORE_BUS_NAME = "Sonic";
  public static final AprilTagFieldLayout apriltagLayout;
  public static final Translation2d fieldSize;
  
  static {
    try {
      apriltagLayout = AprilTagFieldLayout.loadFromResource(AprilTagFields.k2026RebuiltAndymark.m_resourceFile);
      fieldSize = new Translation2d(apriltagLayout.getFieldLength(), apriltagLayout.getFieldWidth());
      apriltagLayout.getFieldLength();
      apriltagLayout.getFieldWidth();
      apriltagLayout.getFieldLength();
      apriltagLayout.getFieldWidth();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static final Distance X_CLEAR_OFFSET = Distance.ofBaseUnits(Math.abs(
      apriltagLayout.getTagPose(12).get().getMeasureX().in(Meter)
          - apriltagLayout.getTagPose(13).get().getMeasureX().in(Meter))
      / 2.0,
      Meter);

  public static final Distance Y_CLEAR_OFFSET = Distance.ofBaseUnits(Math.abs(
      apriltagLayout.getTagPose(7).get().getMeasureY().in(Meter)
          - apriltagLayout.getTagPose(8).get().getMeasureY().in(Meter))
      / 2.0,
      Meter);

  public static final Rectangle2d RED_ALLIANCE_BUMP = new Rectangle2d(
      new Translation2d(Units.inchesToMeters(445.61 - 13.0), Units.inchesToMeters(49.84)),
      new Translation2d(Units.inchesToMeters(492.61 + 13.0), Units.inchesToMeters(267.85)));

  public static final Rectangle2d BLUE_ALLIANCE_BUMP = new Rectangle2d(
      new Translation2d(Units.inchesToMeters(158.61 - 13.0), Units.inchesToMeters(49.84)),
      new Translation2d(Units.inchesToMeters(205.61 + 13.0), Units.inchesToMeters(267.85)));
  // Bump Field Constants manipulated to work with 45 degree robot lock^^ (keep when merging branches plz)
  
  public static final Pose2d HUB_RED = new Pose2d(apriltagLayout.getTagPose(5).get().getMeasureX(),
      apriltagLayout.getTagPose(9).get().getMeasureY(), new Rotation2d(0.0));

      public static final Pose2d HUB_BLUE = new Pose2d(apriltagLayout.getTagPose(18).get().getMeasureX(),
      apriltagLayout.getTagPose(26).get().getMeasureY(), new Rotation2d(0.0));

  public static final Pose2d RED_FEED_TOP = new Pose2d(
      apriltagLayout.getTagPose(7).get().getMeasureX().plus(X_CLEAR_OFFSET),
      apriltagLayout.getTagPose(7).get().getMeasureY().plus(Y_CLEAR_OFFSET), new Rotation2d(0.0));

      public static final Pose2d RED_FEED_BOT = new Pose2d(
      apriltagLayout.getTagPose(12).get().getMeasureX().plus(X_CLEAR_OFFSET),
      apriltagLayout.getTagPose(12).get().getMeasureY().minus(Y_CLEAR_OFFSET), new Rotation2d(0.0));

  public static final Pose2d BLUE_FEED_TOP = new Pose2d(
      apriltagLayout.getTagPose(28).get().getMeasureX().minus(X_CLEAR_OFFSET),
      apriltagLayout.getTagPose(28).get().getMeasureY().plus(Y_CLEAR_OFFSET), new Rotation2d(0.0));

  public static final Pose2d BLUE_FEED_BOT = new Pose2d(
      apriltagLayout.getTagPose(23).get().getMeasureX().minus(X_CLEAR_OFFSET),
      apriltagLayout.getTagPose(23).get().getMeasureY().minus(Y_CLEAR_OFFSET), new Rotation2d(0.0));

  public static final Rectangle2d BLUE_ALLIANCE_ZONE = new Rectangle2d(new Translation2d(0.0, 0.0),
      new Translation2d(Units.inchesToMeters(182.11), Units.inchesToMeters(317.69)));
  
  public static final Rectangle2d RED_ALLIANCE_ZONE = new Rectangle2d(
      new Translation2d(Units.inchesToMeters(469.11), 0.0),
      new Translation2d(Units.inchesToMeters(651.22), Units.inchesToMeters(317.69)));
  public static final double HOOD_ANGLE = 0; //TODO hood angle

  public static final class Joystick {
    // TODO PID Constants for bump angle constraint
    public static final double kP = 0.04;
    public static final double kI = 0;
    public static final double kD = 0;
  }

  public static final class LEDs {
    public static final int PWM_PIN = 0;
    public static final int LENGTH = 150;
  }
  public static final class Turret {
    public static final int TURRET_MOTOR_ID = 50;
    public static final int Turret_HALL_EFFECT_ID = 0;
    public static final double VOLTAGE = 10.0;
    public static final int CURRENT = 50;
    public static final double TURRET_GEAR_RATIO_IO = 20 * 200 / 28;

    public static final Rotation2d NO_TURRET_ANGLE = Rotation2d.fromDegrees(180);

    public static final double TURRET_kV = 0.0010522;
    public static final double TURRET_kA = 0.00010721;
    public static final double TURRET_kS = 0.017925;
    
    public static final double TURRET_P = 0.24301;
    public static final double TURRET_I = 0.0;
    public static final double TURRET_D = 0.0;

    public static final double CALISPEED = .2;

    //TODO change robot transforms to camera and shooter
    public static final Transform3d ROBOT_TO_SHOOTER = new Transform3d(
                                                          new Translation3d(Units.inchesToMeters(-7.486), 0.0, Units.inchesToMeters(17.938)),
                                                          new Rotation3d(0.0, 0.0, 0.0)
                                                          );
  }

  public static final class Vision {
    public static final Transform3d robotToHeart = new Transform3d(
                                                        new Translation3d( Units.inchesToMeters(-0.300), //CAD is correct for signs
                                                                           Units.inchesToMeters(-8.414), 
                                                                           Units.inchesToMeters(20.743)),
                                                        new Rotation3d(0.0, 
                                                                        Units.degreesToRadians(-10.0), 
                                                                        Units.degreesToRadians(0.0))                                                     
                                                        );

    public static final Transform3d robotToDiamond = new Transform3d( //CAD is correct for signs
                                                        new Translation3d( Units.inchesToMeters(-12.655), //-7.486
                                                                           Units.inchesToMeters(-9.979), 
                                                                           Units.inchesToMeters(20.743)),
                                                        new Rotation3d(0.0, 
                                                                        Units.degreesToRadians(-10.0), 
                                                                        Units.degreesToRadians(-90.0))                                                     
                                                        );

    public static final Transform3d robotToClub = new Transform3d( //CAD is incorrect for x/y signs
                                                        new Translation3d( Units.inchesToMeters(-14.230), //5.486, 7.486
                                                                           Units.inchesToMeters(8.386), //-9.921 //-21/545
                                                                           Units.inchesToMeters(20.743)),
                                                        new Rotation3d(0.0, //0.0 
                                                                        Units.degreesToRadians(-10.0), //10.0
                                                                        Units.degreesToRadians(180.0))   //-67.269                                                  
                                                        );

    public static final Transform3d robotToSpade = new Transform3d( //the CAD is incorrect for x/y signs
                                                        new Translation3d( Units.inchesToMeters(-1.865), 
                                                                           Units.inchesToMeters(9.950), 
                                                                           Units.inchesToMeters(20.743)),
                                                        new Rotation3d(0.0, 
                                                                        Units.degreesToRadians(-10.0), 
                                                                        Units.degreesToRadians(90.0))                                                     
                                                        );

    public static final Matrix<N3, N1> singleTagStdDevs = VecBuilder.fill(0.5, 0.5, 999999.0); // TODO emperically tune Single Tag StdDevs
    public static final Matrix<N3, N1> multiTagStdDevs = VecBuilder.fill(0.005, 0.005, 999999.0); // TODO emperically tune Multi Tag StdDevs
  }

  public static final class Intake {
    @CanId(CanId.Type.MOTOR)
    public static final int INTAKE_LIFT_MOTOR_ID = 21;
    @CanId(CanId.Type.MOTOR)
    public static final int LEFT_INTAKE_MOTOR_ID = 20;
    public static final int RIGHT_INTAKE_MOTOR_ID = 22;
    public static final double INTAKE_LIFT_SPEED = 0.2;

    public static final int CURRENT_LIMIT = 50;
    public static final int VOLTAGE_LIMIT = 10;
    public static final double INTAKE_SPEED = 0.85;
    public static final double INTAKE_RPM = 3003.0;
    public static final double LIFT_JKMETERS_SQUARED = 0.00006;
    public static final double LIFT_MOTOR_GEARING = 1.0 / 100.0;
    public static final double INTAKE_REACH_METERS = 0.30;
    public static final double LIFT_MIN_RADIANS = 0;
    public static final double LIFT_MAX_RADIANS = Math.PI / 2.0;
    public static final double WHEEL_MOMENT_OF_INERTIA = 0.00006;
    public static final double INTAKE_GEAR_RATIO = 1.0 / 3.0;

    public static final double INTAKE_P = 0.0002611;
    public static final double INTAKE_I = 0.0;
    public static final double INTAKE_D = 0.0;

    public static final double INTAKE_kV = 0.0018851;
    public static final double INTAKE_kA = 0.00021825;
    public static final double INTAKE_kS = 0.046312;

    //public static final double JOSTLE_UP_POSITION = 
  }

  public static final class Indexer {
    public static final int INDEXER_MOTOR_ID = 40;
    public static final int AGITATOR_MOTOR_ID = 41;
    public static final int CURRENT_LIMIT = 50;
    public static final double VOLTAGE_LIMIT = 10;
    public static final double SPEED = 0.5;
    public static final double WHEEL_MOMENT_OF_INERTIA = 3.8;
    public static final double INDEXER_GEAR_RATIO = 1.0 / 10.0;

    public static final double INDEXER_kS = 0.35936;
    public static final double INDEXER_kV = 0.0023692;
    public static final double INDEXER_kA = 0.00078017;

    public static final double INDEXER_P = 0.0018638;
    public static final double INDEXER_I = 0.00000;
    public static final double INDEXER_D = 0.0000;

    public static final double AGITATOR_kS = 0.023338;
    public static final double AGITATOR_kV = 0.0017887;
    public static final double AGITATOR_kA = 0.00011598;

    public static final double AGITATOR_kP = 0.000085973;
    public static final double AGITATOR_kI = 0.00000;
    public static final double AGITATOR_kD = 0.00;
    
    public static final double AGITATOR_RAMP_RATE = 0.5;
    public static final double INDEXER_RAMP_RATE = 1.0;
  }

  public static final class Swerve {
    @CanId(CanId.Type.PIGEON)
    public static final int IMU_ID = 3;
    public static final double TELEOP_MAX_VELOCITY = 4.6;
    public static final double TELEOP_MAX_ACCELERATION = 5.5; // todo
    public static final double TELEOP_MAX_ANGULAR_VELOCITY = Units.degreesToRadians(180);
    public static final double TELEOP_MAX_ANGULAR_ACCELERATION = Units.degreesToRadians(540);
    public static final double TELEOP_ANGLE_HOLD_FACTOR = 3.0;

    public static final class PathFollowing {
      public static final PIDConstants TRANSLATION_CONSTANTS = new PIDConstants(4.0, 0.0, 0.0);
      public static final PIDConstants ROTATION_CONSTANTS = new PIDConstants(8.0, 0.0, 0.8);
    }
  }

  public static final class Shooter {
    // front top motor
    @CanId(CanId.Type.MOTOR)
    public static final int TOP_LEFT_SHOOTER_ID = 30;

    // front bottom motor
    @CanId(CanId.Type.MOTOR)
    public static final int BOTTOM_LEFT_SHOOTER_ID = 31;

    // back top motor
    @CanId(CanId.Type.MOTOR)
    public static final int TOP_RIGHT_SHOOTER_ID = 32;

    // back bottom motor
    @CanId(CanId.Type.MOTOR)
    public static final int BOTTOM_RIGHT_SHOOTER_ID = 33;
   
    // back bottom motor
    @CanId(CanId.Type.MOTOR)
    public static final int TRANSFER_SHOOTER_ID = 34;

    public static final double TOP_LEFT_SHOOTER_P = 0.0008;//0.00065;
    public static final double TOP_LEFT_SHOOTER_I = 0.0;
    public static final double TOP_LEFT_SHOOTER_D = 0.0;

    public static final double TOP_RIGHT_SHOOTER_P = 0.0;
    public static final double TOP_RIGHT_SHOOTER_I = 0.0;
    public static final double TOP_RIGHT_SHOOTER_D = 0.0;

    // public static final double TOP_LEFT_kV = 0.12886;
    public static final double TOP_LEFT_kV = 0.00200;//0.0019305; //0.0023348;
    public static final double TOP_LEFT_kA = 0.00039146; //0.00062445; //0.00043609;
    public static final double TOP_LEFT_kS = 0.22632;//0.0; //0.18531;

    // public static final double TOP_RIGHT_kV = 0.1274;
    public static final double TOP_RIGHT_kV = 0.12603/60;
    public static final double TOP_RIGHT_kA = 0.02656/60;
    public static final double TOP_RIGHT_kS = 0.074052;

    public static final double RAMP_RATE = 0.5;

    public static final double TRANSFER_kV = 0.0018718;
    public static final double TRANSFER_kA = 0.00015177;
    public static final double TRANSFER_kS = 0.089733;

    public static final double TRANSFER_BOTTOM_P = 0.0001;
    public static final double TRANSFER_BOTTOM_I = 0.000001;
    public static final double TRANSFER_BOTTOM_D = 0.0005;
    
    public static final double shooterMotorTolerance = 75.0;
    public static final int SHOOTER_CURRENT_STALL_LIMIT = 25;
    public static final int SHOOTER_CURRENT_FREE_LIMIT = 80;
    public static final double SHOOTER_VOLTAGE_LIMIT = 10.0;
  }

  public static final class SetPoints {
    public static final Rotation2d trenchRightTurretAngle = Rotation2d.fromDegrees(-110);
    public static final double trenchRightTargetRPM = 2830;

    public static final Rotation2d trenchLeftTurretAngle = Rotation2d.fromDegrees(106);
    public static final double trenchLeftTargetRPM = 2830;

    public static final Rotation2d climbRightTurretAngle = Rotation2d.fromDegrees(-179);
    public static final double climbRightTargetRPM = 2970;

    public static final Rotation2d climbLeftTurretAngle = Rotation2d.fromDegrees(170);
    public static final double climbLeftTargetRPM = 2910;
  }
}
