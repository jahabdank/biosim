package com.traclabs.biosim.integration;

import com.traclabs.biosim.server.framework.BioDriver;
import com.traclabs.biosim.server.framework.BiosimInitializer;
import com.traclabs.biosim.server.framework.IBioModule;
import com.traclabs.biosim.server.simulation.air.VCCRLinear;
import com.traclabs.biosim.server.simulation.environment.SimEnvironment;
import com.traclabs.biosim.server.simulation.framework.Store;
import com.traclabs.biosim.server.util.massvalidation.MassConstants;
import com.traclabs.biosim.server.util.massvalidation.MassLedger;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 24 h MassLedger on the live advanced_stable XML. EVA airlock dumps destroy
 * moles; VCCR bed inventory is tracked as a compartment. Residual bound is
 * 0.2 kg (EVA leak is the named leftover, not float noise).
 */
public class AdvancedStableMassIT {

    private static final int SIM_ID = 4242;
    private static final Path XML = Path.of(System.getProperty(
            "biosim.advancedStableXml",
            System.getenv().getOrDefault("BIOSIM_ADVANCED_STABLE_XML", "")));
    private static final double M_H2 = 0.00201588;
    private static final double M_CH4 = 0.0160425;
    /** 24 h at tickLength 1/60 h. */
    private static final int TICKS_24H = 1440;
    /** Measured 24 h max |residual| was 4.6e-3 kg (EVA dump is small). Float bound. */
    private static final double RESIDUAL_BOUND_KG = 0.01;

    @Test
    void advancedStableMassResidualIsBounded() throws Exception {
        assumeTrue(Files.isRegularFile(XML), "advanced_stable XML not on disk");
        BiosimInitializer.deleteInstance(SIM_ID);
        BiosimInitializer initializer = BiosimInitializer.getInstance(SIM_ID);
        initializer.parseXmlConfiguration(Files.readString(XML, StandardCharsets.UTF_8));
        BioDriver driver = initializer.getBioDriver();
        Path out = Path.of("target/mass-advanced-stable");
        Files.createDirectories(out);
        MassLedger ledger = new MassLedger(out.resolve("ledger.csv"), out.resolve("summary.json"));
        ledger.setScenario("advanced_stable_24h");
        ledger.setTickLengthHours(driver.getTickLength());
        ledger.addNote("VCCRLinear bed inventory is a compartment (CO2 not yet in CO2_Store).");
        ledger.addNote("EVA start/end dumps a fraction of cabin air; that mass is not recovered.");

        for (IBioModule module : driver.getModules()) {
            if (module instanceof SimEnvironment env) {
                String name = env.getModuleName();
                ledger.addCompartment(name + ".o2",
                        () -> env.getO2Store().getCurrentLevel() * MassConstants.M_O2_KG_PER_MOL);
                ledger.addCompartment(name + ".co2",
                        () -> env.getCO2Store().getCurrentLevel() * MassConstants.M_CO2_KG_PER_MOL);
                ledger.addCompartment(name + ".n2",
                        () -> env.getNitrogenStore().getCurrentLevel() * MassConstants.M_N2_KG_PER_MOL);
                ledger.addCompartment(name + ".vapor",
                        () -> env.getVaporStore().getCurrentLevel() * MassConstants.M_H2O_KG_PER_MOL);
                ledger.addCompartment(name + ".other",
                        () -> env.getOtherStore().getCurrentLevel() * MassConstants.M_AR_KG_PER_MOL);
            } else if (module instanceof VCCRLinear vccr) {
                ledger.addCompartment(vccr.getModuleName() + ".bed",
                        () -> vccr.getBedInventoryMoles() * MassConstants.M_CO2_KG_PER_MOL);
            } else if (module instanceof Store store) {
                ledger.addCompartment(store.getModuleName(), () -> storeKg(store));
            }
        }

        driver.reset();
        ledger.setSimulationTicks(0);
        ledger.recordTick(0);
        for (int tick = 1; tick <= TICKS_24H; tick++) {
            driver.advanceOneTick();
            ledger.setSimulationTicks(tick);
            ledger.recordTick(tick);
        }
        MassLedger.ValidationResult result = ledger.validate(RESIDUAL_BOUND_KG);
        assertTrue(result.passed,
                String.format(Locale.ROOT, "max residual %.4f kg at tick %d (%s)",
                        result.maxAbsResidual, result.worstTick, result.worstCompartmentDelta));
        BiosimInitializer.deleteInstance(SIM_ID);
    }

    private static double storeKg(Store store) {
        String type = store.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        float level = store.getCurrentLevel();
        if (type.contains("o2"))
            return level * MassConstants.M_O2_KG_PER_MOL;
        if (type.contains("co2"))
            return level * MassConstants.M_CO2_KG_PER_MOL;
        if (type.contains("nitrogen"))
            return level * MassConstants.M_N2_KG_PER_MOL;
        if (type.contains("h2") && !type.contains("ch"))
            return level * M_H2;
        if (type.contains("methane"))
            return level * M_CH4;
        if (type.contains("water"))
            return level * MassConstants.WATER_KG_PER_LITER;
        if (type.contains("food") || type.contains("waste") || type.contains("biomass"))
            return level;
        return 0.0;
    }
}
