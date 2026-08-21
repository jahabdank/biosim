package com.traclabs.biosim.server.simulation.environment;

import com.traclabs.biosim.server.framework.MalfunctionIntensity;
import com.traclabs.biosim.server.framework.MalfunctionLength;
import com.traclabs.biosim.server.simulation.framework.SimBioModule;
import com.traclabs.biosim.server.simulation.power.PowerConsumer;
import com.traclabs.biosim.server.simulation.power.PowerConsumerDefinition;

/**
 * The basic Fan implementation.
 *
 * @author Scott Bell
 */

public class Fan extends SimBioModule implements AirConsumer, PowerConsumer, AirProducer {
    //  in kPA assuming 101 kPa total pressure and air temperature of 23C and relative humidity of 80%
    public static final float OPTIMAL_MOISTURE_CONCENTRATION = 0.0218910f;
    //Consumers, Producers
    private final AirConsumerDefinition myAirConsumerDefinition;
    private final PowerConsumerDefinition myPowerConsumerDefinition;
    private final AirProducerDefinition myAirProducerDefinition;
    private float currentPowerConsumed = 0f;

    private float currentMolesOfAirConsumed = 0f;

    private Air currentAirConsumed;

    public Fan(int pID, String pName) {
        super(pID, pName);
        myAirConsumerDefinition = new AirConsumerDefinition(this);
        myPowerConsumerDefinition = new PowerConsumerDefinition(this);
        myAirProducerDefinition = new AirProducerDefinition(this);
    }

    public Fan() {
        this(0, "Unnamed Fan");
    }

    public AirConsumerDefinition getAirConsumerDefinition() {
        return myAirConsumerDefinition;
    }

    public AirProducerDefinition getAirProducerDefinition() {
        return myAirProducerDefinition;
    }

    public PowerConsumerDefinition getPowerConsumerDefinition() {
        return myPowerConsumerDefinition;
    }

    public void tick() {
        super.tick();
        getAndPushAir();
    }

    private void getAndPushAir() {
        float uCmd = commandedDamper();
        float wattsNeed = wattsForDamper(uCmd);
        currentPowerConsumed = myPowerConsumerDefinition.takeAmount(wattsNeed);
        float uEff = uCmd;
        if (wattsNeed > 0f && currentPowerConsumed + 1e-6f < wattsNeed)
            uEff = (float) Math.cbrt(Math.max(0f, currentPowerConsumed / W_DES_WATTS));
        currentMolesOfAirConsumed = calculateAirToConsume(uEff);
        currentAirConsumed = myAirConsumerDefinition.getAirFromEnvironment(currentMolesOfAirConsumed, 0);
        myAirProducerDefinition.pushAirToEnvironment(currentAirConsumed, 0);
    }

    /**
     * Command is damper u in [0,1] on the power port (desiredFlowRate).
     * Power drawn is W_des * u^3 (fan affinity). Air: n = (u Q_des) P/(R T) * tickLength.
     * Q_des = 108000 L/h ≈ 10 min mixing in 18 m³ at 55 kPa / 23 C (~2410 mol/h).
     * W_des = 120 W. Zero damper moves no air and takes no power.
     */
    static final float W_DES_WATTS = 120f;
    static final float Q_DES_LITERS_PER_HOUR = 108000f;
    private static final float R_KPA_LITERS = 8.314f;

    static float clampDamper(float commanded) {
        if (commanded <= 0f)
            return 0f;
        if (commanded >= 1f)
            return 1f;
        return commanded;
    }

    static float wattsForDamper(float u) {
        if (u <= 0f)
            return 0f;
        float x = clampDamper(u);
        return W_DES_WATTS * x * x * x;
    }

    static float molesThisTick(float damper, float pressureKpa,
                               float temperatureKelvin, float tickLengthHours) {
        float u = clampDamper(damper);
        if (u <= 0f || pressureKpa <= 0f || temperatureKelvin <= 0f
                || tickLengthHours <= 0f)
            return 0f;
        float qLitersPerHour = Q_DES_LITERS_PER_HOUR * u;
        float molesPerHour = qLitersPerHour * pressureKpa
                / (R_KPA_LITERS * temperatureKelvin);
        return molesPerHour * tickLengthHours;
    }

    private float commandedDamper() {
        if (myPowerConsumerDefinition.getDesiredFlowRates().length < 1)
            return 0f;
        return clampDamper(myPowerConsumerDefinition.getDesiredFlowRate(0));
    }

    private float calculateAirToConsume(float damper) {
        SimEnvironment[] sources = myAirConsumerDefinition.getEnvironments();
        if (sources == null || sources.length < 1 || sources[0] == null)
            return 0f;
        SimEnvironment src = sources[0];
        return molesThisTick(damper, src.getTotalPressure(),
                src.getTemperatureInKelvin(), getTickLength());
    }

    protected String getMalfunctionName(MalfunctionIntensity pIntensity,
                                        MalfunctionLength pLength) {
        StringBuffer returnBuffer = new StringBuffer();
        if (pIntensity == MalfunctionIntensity.SEVERE_MALF)
            returnBuffer.append("Severe ");
        else if (pIntensity == MalfunctionIntensity.MEDIUM_MALF)
            returnBuffer.append("Medium ");
        else if (pIntensity == MalfunctionIntensity.LOW_MALF)
            returnBuffer.append("Low ");
        if (pLength == MalfunctionLength.TEMPORARY_MALF)
            returnBuffer.append("Temporary Production Reduction");
        else if (pLength == MalfunctionLength.PERMANENT_MALF)
            returnBuffer.append("Permanent Production Reduction");
        return returnBuffer.toString();
    }

    public void log() {
        myLogger.debug("power_consumed=" + currentPowerConsumed);
    }

    protected void performMalfunctions() {
    }

    public void reset() {
        super.reset();
        myAirConsumerDefinition.reset();
        myAirProducerDefinition.reset();
        myPowerConsumerDefinition.reset();
    }
}