package com.titan.sensor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan Sensor MCP Server - IoT sensor data agent for manufacturing facilities.
 *
 * Provides MCP tools for:
 * - Listing equipment across 12 global facilities
 * - Querying sensor readings (vibration, temperature, RPM, torque, pressure)
 * - Detecting anomalies like the PHX-CNC-007 vibration degradation
 * - Facility-wide health status overviews
 */
@SpringBootApplication
public class SensorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensorApplication.class, args);
    }
}
