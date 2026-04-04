package frc.lib.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RollingAverageTest {

    private RollingAverage currentAverage;

    @BeforeEach
    public void resetRollingAverage() {
        currentAverage = new RollingAverage(5);
    }
    
    @Test
    public void testRollingAverageToString() {
        System.out.println(currentAverage.toString());
    }

    @Test
    public void testFullAverage() {
        currentAverage.add(2.0);
        currentAverage.add(2.0);
        currentAverage.add(3.0);
        currentAverage.add(4.0);
        currentAverage.add(4.0);
        assertEquals(3.0, currentAverage.getAverage());

        currentAverage.add(7.0);
        assertEquals(4.0, currentAverage.getAverage());
    }

    @Test
    public void testAverageShort() {
        currentAverage.add(2);
        currentAverage.add(4);

        assertEquals(3.0, currentAverage.getAverage());
    }
}
