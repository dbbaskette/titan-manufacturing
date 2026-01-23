package com.titan.generator.model;

/**
 * Equipment degradation patterns for realistic failure simulation.
 * Based on NASA C-MAPSS and industrial failure mode research.
 */
public enum DegradationPattern {
    /**
     * Normal operation - baseline values with random noise.
     * Vibration: 1.5-2.5 mm/s, Temperature: 45-55°C
     */
    NORMAL,

    /**
     * Bearing degradation pattern (like PHX-CNC-007 Phoenix Incident).
     * Vibration increases exponentially from 2.5 to 5.0+ mm/s over days.
     * Temperature correlates and increases from 50 to 70+ °C.
     * Triggers 73% failure probability in ML model.
     */
    BEARING_DEGRADATION,

    /**
     * Motor burnout pattern.
     * Temperature spikes rapidly from normal to critical (85°C+).
     * Vibration shows sudden irregular patterns.
     * Short time to failure (hours, not days).
     */
    MOTOR_BURNOUT,

    /**
     * Spindle wear pattern.
     * Gradual RPM decrease (inability to maintain speed).
     * Vibration increases slowly.
     * Torque fluctuations increase.
     */
    SPINDLE_WEAR,

    /**
     * Coolant system failure.
     * Temperature increases due to cooling inefficiency.
     * Pressure drops in coolant system.
     */
    COOLANT_FAILURE,

    /**
     * Electrical fault pattern.
     * Power consumption spikes.
     * Intermittent sensor dropouts.
     */
    ELECTRICAL_FAULT
}
