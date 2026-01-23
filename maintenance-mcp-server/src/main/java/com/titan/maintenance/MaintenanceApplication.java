package com.titan.maintenance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan Maintenance MCP Server - Predictive maintenance agent for manufacturing equipment.
 *
 * Provides MCP tools for:
 * - Failure prediction based on sensor trend analysis
 * - Remaining Useful Life (RUL) estimation
 * - Maintenance scheduling and work order creation
 * - Maintenance history retrieval
 *
 * Enables the Phoenix Incident demo scenario:
 * - PHX-CNC-007 with 73% failure probability within 48 hours
 * - Vibration trending from 2.5 to 4.2 mm/s
 * - Recommends SKU-BRG-7420 bearing replacement
 */
@SpringBootApplication
public class MaintenanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaintenanceApplication.class, args);
    }
}
