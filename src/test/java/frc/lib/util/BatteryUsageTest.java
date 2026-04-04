package frc.lib.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BatteryUsageTest {

    @BeforeAll
    public static void resetBatteryUsage() {
        BatteryUsage.reset();
        BatteryUsage.registerDevice("partOne", 0);
        BatteryUsage.registerDevice("partTwo", 0);
        BatteryUsage.reportUsage("partOne", 5, 5);
        BatteryUsage.reportUsage("partTwo", 5, 5);
    }

    @Test
    public void testUsageSum() {
        assertEquals(10, BatteryUsage.getTotalVoltage(), "Voltage didn't match");
        assertEquals(10, BatteryUsage.getTotalAmps(), "Amperage didn't match");
    }

    @Test
    public void testUsagePerPart() {
        assertEquals(5, BatteryUsage.getAmpsAverage("partOne"));
        assertEquals(5, BatteryUsage.getAmpsAverage("partTwo"));
        
        assertEquals(5, BatteryUsage.getVoltageAverage("partOne"));
        assertEquals(5, BatteryUsage.getVoltageAverage("partTwo"));
    }
}
