package com.traclabs.biosim.server.simulation.air;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CRSConversionTest {

    @Test
    void etaOfStoichMin() {
        assertEquals(0.85f, CRS.convertedMoles(1f, 4f), 1e-5f);
        assertEquals(0.85f, CRS.convertedMoles(2f, 4f), 1e-5f);
        assertEquals(0.425f, CRS.convertedMoles(1f, 2f), 1e-5f);
    }

    @Test
    void starvesWithoutHydrogen() {
        assertEquals(0f, CRS.convertedMoles(10f, 0f));
        assertEquals(0f, CRS.convertedMoles(0f, 40f));
    }
}
