package com.traclabs.biosim.server.simulation.air;

import com.traclabs.biosim.server.framework.Malfunction;
import com.traclabs.biosim.server.simulation.framework.SimBioModule;
import com.traclabs.biosim.server.simulation.power.PowerConsumer;
import com.traclabs.biosim.server.simulation.power.PowerConsumerDefinition;
import com.traclabs.biosim.server.simulation.water.PotableWaterConsumer;
import com.traclabs.biosim.server.simulation.water.PotableWaterConsumerDefinition;

public class OGS extends SimBioModule implements PowerConsumer, PotableWaterConsumer, O2Producer, H2Producer {

    //Consumers, Producers
    private final PowerConsumerDefinition myPowerConsumerDefinition;

    private final PotableWaterConsumerDefinition myPotableWaterConsumerDefinition;

    private final O2ProducerDefinition myO2ProducerDefinition;

    private final H2ProducerDefinition myH2ProducerDefinition;
    private final float myProductionRate = 1f;
    private float currentH2OConsumed = 0;
    private float currentO2Produced = 0;
    private float currentH2Produced = 0;
    private float currentPowerConsumed = 0f;

    public OGS(int pID, String pName) {
        super(pID, pName);
        myPowerConsumerDefinition = new PowerConsumerDefinition(this);
        myPotableWaterConsumerDefinition = new PotableWaterConsumerDefinition(this);
        myO2ProducerDefinition = new O2ProducerDefinition(this);
        myH2ProducerDefinition = new H2ProducerDefinition(this);
    }

    @Override
    protected void performMalfunctions() {
        for (Malfunction malfunction : myMalfunctions.values()) {
            malfunction.setPerformed(true);
        }
        if (myMalfunctions.size() > 0) {
            myPowerConsumerDefinition.malfunction();
            myPotableWaterConsumerDefinition.malfunction();
            myO2ProducerDefinition.malfunction();
            myH2ProducerDefinition.malfunction();
        }
    }

    public PowerConsumerDefinition getPowerConsumerDefinition() {
        return myPowerConsumerDefinition;
    }

    public PotableWaterConsumerDefinition getPotableWaterConsumerDefinition() {
        return myPotableWaterConsumerDefinition;
    }

    public O2ProducerDefinition getO2ProducerDefinition() {
        return myO2ProducerDefinition;
    }

    public H2ProducerDefinition getH2ProducerDefinition() {
        return myH2ProducerDefinition;
    }

    /**
     * Resets production/consumption levels.
     */
    public void reset() {
        super.reset();
        currentH2OConsumed = 0;
        currentO2Produced = 0;
        currentH2Produced = 0;
        currentPowerConsumed = 0;
        myPowerConsumerDefinition.reset();
        myPotableWaterConsumerDefinition.reset();
        myO2ProducerDefinition.reset();
        myH2ProducerDefinition.reset();
    }

    private void gatherPower() {
        currentPowerConsumed = myPowerConsumerDefinition
                .getMostResourceFromStores();
    }

    public void tick() {
        super.tick();
        gatherPower();
        gatherWater();
        pushGasses();
    }

    public void log() {
        myLogger.debug("power_consumed=" + currentPowerConsumed);
    }

    /**
     * Faraday O2 from electrolysis. 2 H2O → 2 H2 + O2 (4 e− per mol O2).
     * Energy per mol O2 at the thermoneutral voltage 1.48 V.
     * η is voltage efficiency versus that thermoneutral number.
     */
    static final float FARADAY_C_PER_MOL = 96485f;
    static final float THERMONEUTRAL_VOLTS = 1.48f;
    static final float E_O2_J_PER_MOL = 4f * FARADAY_C_PER_MOL * THERMONEUTRAL_VOLTS;
    static final float ETA = 0.70f;
    static final float WATER_G_PER_MOL = 18.01524f;

    static float oxygenMolesFromPower(float watts, float tickLengthHours) {
        if (watts <= 0f || tickLengthHours <= 0f)
            return 0f;
        return ETA * watts * tickLengthHours * 3600f / E_O2_J_PER_MOL;
    }

    static float litersWaterForOxygenMoles(float o2Moles) {
        return (2f * o2Moles * WATER_G_PER_MOL) / 1000f;
    }

    private void gatherWater() {
        float o2FromPower = oxygenMolesFromPower(currentPowerConsumed, getTickLength());
        float waterToConsume = litersWaterForOxygenMoles(o2FromPower);
        currentH2OConsumed = myPotableWaterConsumerDefinition
                .getResourceFromStores(waterToConsume);
    }

    private void pushGasses() {
        // 2 H2O → 2 H2 + O2. Zero potable ⇒ zero O2.
        float molesOfWater = (currentH2OConsumed * 1000f) / WATER_G_PER_MOL;
        float o2FromWater = molesOfWater / 2f;
        float o2FromPower = oxygenMolesFromPower(currentPowerConsumed, getTickLength());
        currentO2Produced = Math.min(o2FromWater, o2FromPower) * myProductionRate;
        currentH2Produced = currentO2Produced * 2f;
        myO2ProducerDefinition.pushResourceToStores(currentO2Produced);
        myH2ProducerDefinition.pushResourceToStores(currentH2Produced);
    }
}