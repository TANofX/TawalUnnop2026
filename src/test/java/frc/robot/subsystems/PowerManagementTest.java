package frc.robot.subsystems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.wpi.first.wpilibj2.command.Command;
import frc.lib.subsystem.AdvancedSubsystem;
import frc.lib.util.BatteryUsage;

public class PowerManagementTest {

    @BeforeEach
    public void reset() {
        BatteryUsage.reset();
    }

    @Test
    public void testManagement() {
        LimitTest shooter = new LimitTest("shooter", 0, 80, 12);
        LimitTest intake = new LimitTest("intake", 1,  5, 10);
        LimitTest indexer = new LimitTest("indexer", 1, 5, 10);
        LimitTest drives = new LimitTest("swerve", 2, 60, 12);

        PowerManagement power = new PowerManagement(true);
        power.addSubsystem(drives);
        power.addSubsystem(indexer);
        power.addSubsystem(intake);
        power.addSubsystem(shooter);

        for (int i=0; i < 5; i++) {
            shooter.periodic();
            intake.periodic();
            indexer.periodic();
            drives.periodic();
        }

        power.periodic();

        assertEquals(1.0, shooter.getLimit(), "Shooter did not get priority");
        assertEquals(1.0, intake.getLimit(), "Intake did not recieve expected limit");
        assertEquals(1.0, indexer.getLimit(), "Indexer did not get expected limit");
        assertEquals(0.5, drives.getLimit(), "Expected drives to get half power");
    }

    @Test
    public void testOverage() {
        PowerManagement power = new PowerManagement(true);

        LimitTest shooter = new LimitTest("shooter", 0, 80, 12);
        LimitTest intake = new LimitTest("intake", 1,  20, 10);
        LimitTest indexer = new LimitTest("indexer", 1, 30, 10);
        LimitTest drives = new LimitTest("swerve", 2, 60, 12);

        power.addSubsystem(drives);
        power.addSubsystem(indexer);
        power.addSubsystem(intake);
        power.addSubsystem(shooter);

        for (int i=0; i < 5; i++) {
            shooter.periodic();
            intake.periodic();
            indexer.periodic();
            drives.periodic();
        }

        power.periodic();

        assertEquals(1.0, shooter.getLimit(), "Shooter did not get priority");
        assertEquals(0.8, intake.getLimit(), "Intake did not recieve expected limit");
        assertEquals(0.8, indexer.getLimit(), "Indexer did not get expected limit");
        assertEquals(0.0, drives.getLimit(), "Expected drives to get half power");
    }

    private class LimitTest extends AdvancedSubsystem {
        private double limitSet;
        private double voltage;
        private double current;

        public LimitTest(String name, int priority, double reportedCurrent, double reportedVoltage) {
            super(name);

            current = reportedCurrent;
            voltage = reportedVoltage;

            BatteryUsage.registerDevice(name, priority);
        }

        @Override
        public void setPowerLimit(double limit) {
            limitSet = limit;
        }

        @Override
        protected Command systemCheckCommand() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'systemCheckCommand'");
        }

        public double getLimit() {
            return limitSet;
        }

        public void periodic() {
            BatteryUsage.reportUsage(getName(), current, voltage);
        }
        
    }
}
