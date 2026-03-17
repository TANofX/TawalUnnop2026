package frc.robot.subsystems;

import edu.wpi.first.wpilibj2.command.SubsystemBase;

import com.revrobotics.RelativeEncoder;
import com.revrobotics.spark.SparkBase.ControlType;
import com.revrobotics.spark.SparkClosedLoopController;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.config.SparkBaseConfig.IdleMode;
import com.revrobotics.spark.config.SparkMaxConfig;
import com.revrobotics.ResetMode; // Import the new global ResetMode

import edu.wpi.first.wpilibj.Preferences;

import java.util.ArrayList;
import com.revrobotics.PersistMode;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.robot.Constants;

public class Turret extends SubsystemBase {
    private FireControl controllerOfFire;
    private Transform3d robotToTurret;
    private double numRotations;
    private Rotation2d targetAngle = Rotation2d.fromDegrees(0);

    private ArrayList<TurretSwitch> switchList = new ArrayList<TurretSwitch>(1);

    private SparkMax turretMotor;
    private RelativeEncoder turretEncoder;
    private SparkClosedLoopController turretMotorController;
    private SparkMaxConfig turretMotorConfig;
    private boolean calibrated;

    public Turret(String turretName, final int TURRET_MOTOR_ID, final int TURRET_CALI_SWITCH_ID, Transform3d roboToTur) {
        super(turretName);
        calibrated = false;
        robotToTurret = roboToTur;
        turretMotor = new SparkMax(TURRET_MOTOR_ID, MotorType.kBrushless);
        // calibrationSwitch = new DigitalInput(TURRET_CALI_SWITCH_ID);
        switchList.add(new TurretSwitch(TURRET_CALI_SWITCH_ID, getName() + "thing 1"));

        turretMotorConfig = new SparkMaxConfig();
        turretMotorConfig
                .idleMode(IdleMode.kBrake).voltageCompensation(Constants.Turret.VOLTAGE)
                .smartCurrentLimit(Constants.Turret.CURRENT)
                .inverted(true);

        turretMotorConfig.closedLoop.pid(Constants.Turret.TURRET_P, Constants.Turret.TURRET_I,
                Constants.Turret.TURRET_D); // TODO find neede PIDs
        turretMotorConfig.closedLoop.feedForward.sva(Constants.Turret.TURRET_kS, Constants.Turret.TURRET_kV,
                Constants.Turret.TURRET_kA);
        
        turretMotor.configure(turretMotorConfig, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
        turretEncoder = turretMotor.getEncoder();
        turretMotorController = turretMotor.getClosedLoopController();
    }

    public boolean isOnLimitSwitch() {
        boolean isTrue = false;
        for (TurretSwitch p : switchList) {
            isTrue = !p.isSwitchOn();
            if (isTrue)
                break;
        }
        return isTrue;
    }

    public void turnClockwise() {
        turretMotor.set(-(Constants.CALISPEED));
    }

    public void calibrateClock() {
        for (TurretSwitch p : switchList) {
            if (!p.isSwitchOn()) {
                turretEncoder.setPosition(p.getClock());
                calibrated = true;
                break;
            }
        }
    }

    public boolean isCalibrated() {
        return calibrated;
    }

    public void turnCounterClockwise() {
        turretMotor.set((Constants.CALISPEED));
    }

    public void stopTurret() {
        turretMotor.set(0.0);
    }

    public ArrayList<TurretSwitch> getSwitches() {
        return switchList;
    }

    public double getCurrentRot() {
        return turretEncoder.getPosition();
    }

    public void zero() {
        turretEncoder.setPosition(0.0);
    }

    public double getNumRotations() {
        return numRotations;
    }

    private Rotation2d getAngle() {
        Rotation2d currentAngle = Rotation2d.fromRotations(turretEncoder.getPosition() / Constants.Turret.TURRET_GEAR_RATIO_IO);
        Rotation2d finalAngle = Rotation2d.fromRadians(MathUtil.angleModulus(currentAngle.getRadians()));
        return finalAngle;   
    }

    public void pointToTarget(Rotation2d targetAngle) {
        this.targetAngle = targetAngle;
        Rotation2d angleToMove = targetAngle.minus(getAngle());
        numRotations = angleToMove.getRotations() * Constants.Turret.TURRET_GEAR_RATIO_IO;
        numRotations += turretEncoder.getPosition();
        turretMotorController.setSetpoint(numRotations, ControlType.kPosition);
    }

    public Rotation2d getTargetAngle() {
        return targetAngle;
    }

    public boolean isAtTarget() {
        return Math.abs(getAngle().minus(getTargetAngle()).getDegrees()) < 2;
    }
    @Override
    public void periodic() {
        SmartDashboard.putNumber(getName() + "/Turret Angle",getAngle().getDegrees());
        SmartDashboard.putNumber(getName() + "/Target Turret Angle", targetAngle.getDegrees());
        SmartDashboard.putNumber(getName() + "/Encoder Position", turretEncoder.getPosition());
        SmartDashboard.putNumber(getName() + "/Target Encoder Position", getNumRotations());
        SmartDashboard.putBoolean(getName() + "/Switch", isOnLimitSwitch());
        SmartDashboard.putBoolean(getName() + "/isCalibrated", calibrated);
        SmartDashboard.putBoolean(getName() + "/isAtTarget", isAtTarget());
        // pointToTarget(controllerOfFire.getCurrentTarget()); TODO
    }

    public class TurretSwitch {
        private DigitalInput switcher;
        private double clockPos;
        private double countClockPos;
        private String name;

        public TurretSwitch(int DIOchannel, String DIOName) {
            switcher = new DigitalInput(DIOchannel);
            name = DIOName;
            clockPos = Preferences.getDouble(name + "clock", 0);
            countClockPos = Preferences.getDouble(name + "counter clock", 0);
        }

        public void zero(double clock, double counterClock) {
            Preferences.setDouble(name + "counter clock", counterClock);
            countClockPos = counterClock;
            Preferences.setDouble(name + "clock", clock);
            clockPos = clock;
        }

        public boolean isSwitchOn() {
            return switcher.get();
        }

        public double getClock() {
            return clockPos;
        }

        public double getCountClock() {
            return countClockPos;
        }
    }
}