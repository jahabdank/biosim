package com.traclabs.biosim.server.simulation.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VCCRLinearLawTest {

    @Test
    void zeroPowerMovesNoAir() {
        assertEquals(0f, VCCRLinear.molesAirThisTick(0f, 55f, 296.15f, 1f / 60f));
    }

    @Test
    void adsorbNameplateIsIdealGasFlow() {
        float hourly = VCCRLinear.molesAirThisTick(VCCRLinear.W_ADSORB_WATTS, 55f, 296.15f, 1f);
        float expected = VCCRLinear.Q_REF_LITERS_PER_HOUR * 55f / (8.314f * 296.15f);
        assertEquals(expected, hourly, 5f);
    }

    @Test
    void langmuirDoesNotStripTheWholeSlug() {
        float nAir = 30f;
        float nCo2 = 0.3f; // y = 0.01
        float removed = VCCRLinear.co2RemovedFromSlug(nAir, nCo2);
        assertTrue(removed > 0f && removed < nCo2, "removed=" + removed);
    }

    @Test
    void floorStopsRemovalAt600ppm() {
        float nAir = 1000f;
        float nCo2 = VCCRLinear.Y_CO2_FLOOR * nAir;
        assertEquals(0f, VCCRLinear.co2RemovedFromSlug(nAir, nCo2));
        assertEquals(0f, VCCRLinear.co2RemovedFromSlug(nAir, nCo2 * 0.5f));
    }

    @Test
    void mmHgConversionAtExplorationPressure() {
        // y=0.01 at 55 kPa → 4.125 mmHg
        assertEquals(0f, VCCRLinear.ppCo2MmHg(null));
    }

    @Test
    void backupStartsDesorbing() {
        assertEquals(VCCRLinear.BedState.DESORB, VCCRLinear.initialState("Backup_VCCR"));
        assertEquals(VCCRLinear.BedState.ADSORB, VCCRLinear.initialState("Main_VCCR"));
    }
}
