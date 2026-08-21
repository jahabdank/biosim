package com.traclabs.biosim.server.simulation.environment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FanLawTest {

    @Test
    void fullDamperAtExplorationPressureIsIdealGasFlow() {
        float hourly = Fan.molesThisTick(1f, 55f, 23f + 273.15f, 1f);
        float expected = Fan.Q_DES_LITERS_PER_HOUR * 55f / (8.314f * 296.15f);
        assertEquals(expected, hourly, 20f);
        float molesInRoom = 55f * 18000f / (8.314f * 296.15f);
        float mixingHours = molesInRoom / hourly;
        assertTrue(mixingHours > 0.1f && mixingHours < 0.3f, "mixingHours=" + mixingHours);
    }

    @Test
    void zeroDamperMovesNoAir() {
        assertEquals(0f, Fan.molesThisTick(0f, 55f, 296.15f, 1f / 60f));
        assertEquals(0f, Fan.wattsForDamper(0f));
    }

    @Test
    void affinityIsCubic() {
        assertEquals(Fan.W_DES_WATTS, Fan.wattsForDamper(1f), 0.1f);
        assertEquals(Fan.W_DES_WATTS * 0.125f, Fan.wattsForDamper(0.5f), 0.2f);
    }
}
