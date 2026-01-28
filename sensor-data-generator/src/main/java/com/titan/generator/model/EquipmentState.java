package com.titan.generator.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the current simulation state of equipment.
 */
public class EquipmentState {
    private final String equipmentId;
    private final String facilityId;
    private final String equipmentType;

    // Current pattern and progression
    private DegradationPattern pattern = DegradationPattern.NORMAL;
    private Instant patternStartTime = Instant.now();
    private final AtomicInteger cycleCount = new AtomicInteger(0);

    // Original default baselines (never modified)
    private static final double DEFAULT_VIBRATION = 2.0;
    private static final double DEFAULT_TEMPERATURE = 50.0;
    private static final double DEFAULT_RPM = 8500.0;
    private static final double DEFAULT_TORQUE = 45.0;
    private static final double DEFAULT_PRESSURE = 6.0;
    private static final double DEFAULT_POWER = 15.0;

    // Sensor baselines (normal operating values, may be adjusted)
    private double vibrationBaseline = DEFAULT_VIBRATION;
    private double temperatureBaseline = DEFAULT_TEMPERATURE;
    private double rpmBaseline = DEFAULT_RPM;
    private double torqueBaseline = DEFAULT_TORQUE;
    private double pressureBaseline = DEFAULT_PRESSURE;
    private double powerBaseline = DEFAULT_POWER;

    // Current values (modified by degradation)
    private double currentVibration;
    private double currentTemperature;
    private double currentRpm;
    private double currentTorque;
    private double currentPressure;
    private double currentPower;

    public EquipmentState(String equipmentId, String facilityId, String equipmentType) {
        this.equipmentId = equipmentId;
        this.facilityId = facilityId;
        this.equipmentType = equipmentType;
        resetToBaseline();
    }

    public void resetToBaseline() {
        this.currentVibration = vibrationBaseline;
        this.currentTemperature = temperatureBaseline;
        this.currentRpm = rpmBaseline;
        this.currentTorque = torqueBaseline;
        this.currentPressure = pressureBaseline;
        this.currentPower = powerBaseline;
    }

    /** Reset baselines to factory defaults and current values to those defaults. */
    public void resetToDefaults() {
        this.vibrationBaseline = DEFAULT_VIBRATION;
        this.temperatureBaseline = DEFAULT_TEMPERATURE;
        this.rpmBaseline = DEFAULT_RPM;
        this.torqueBaseline = DEFAULT_TORQUE;
        this.pressureBaseline = DEFAULT_PRESSURE;
        this.powerBaseline = DEFAULT_POWER;
        resetToBaseline();
    }

    public void setPattern(DegradationPattern pattern) {
        this.pattern = pattern;
        this.patternStartTime = Instant.now();
        this.cycleCount.set(0);
        // Always reset to baseline so degradation starts from known good values
        resetToBaseline();
    }

    public int incrementCycle() {
        return cycleCount.incrementAndGet();
    }

    // Getters and setters
    public String getEquipmentId() { return equipmentId; }
    public String getFacilityId() { return facilityId; }
    public String getEquipmentType() { return equipmentType; }
    public DegradationPattern getPattern() { return pattern; }
    public Instant getPatternStartTime() { return patternStartTime; }
    public int getCycleCount() { return cycleCount.get(); }

    public double getVibrationBaseline() { return vibrationBaseline; }
    public void setVibrationBaseline(double v) { this.vibrationBaseline = v; }
    public double getTemperatureBaseline() { return temperatureBaseline; }
    public void setTemperatureBaseline(double t) { this.temperatureBaseline = t; }
    public double getRpmBaseline() { return rpmBaseline; }
    public void setRpmBaseline(double r) { this.rpmBaseline = r; }
    public double getTorqueBaseline() { return torqueBaseline; }
    public void setTorqueBaseline(double t) { this.torqueBaseline = t; }
    public double getPressureBaseline() { return pressureBaseline; }
    public void setPressureBaseline(double p) { this.pressureBaseline = p; }
    public double getPowerBaseline() { return powerBaseline; }
    public void setPowerBaseline(double p) { this.powerBaseline = p; }

    public double getCurrentVibration() { return currentVibration; }
    public void setCurrentVibration(double v) { this.currentVibration = v; }
    public double getCurrentTemperature() { return currentTemperature; }
    public void setCurrentTemperature(double t) { this.currentTemperature = t; }
    public double getCurrentRpm() { return currentRpm; }
    public void setCurrentRpm(double r) { this.currentRpm = r; }
    public double getCurrentTorque() { return currentTorque; }
    public void setCurrentTorque(double t) { this.currentTorque = t; }
    public double getCurrentPressure() { return currentPressure; }
    public void setCurrentPressure(double p) { this.currentPressure = p; }
    public double getCurrentPower() { return currentPower; }
    public void setCurrentPower(double p) { this.currentPower = p; }

    @Override
    public String toString() {
        return "EquipmentState{" +
               "equipmentId='" + equipmentId + '\'' +
               ", pattern=" + pattern +
               ", cycles=" + cycleCount.get() +
               ", vibration=" + String.format("%.2f", currentVibration) +
               ", temp=" + String.format("%.1f", currentTemperature) +
               '}';
    }
}
