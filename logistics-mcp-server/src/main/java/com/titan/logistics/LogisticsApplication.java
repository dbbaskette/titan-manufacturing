package com.titan.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan Logistics MCP Server
 *
 * Provides MCP tools for logistics management:
 * - get_carriers: List available carriers
 * - create_shipment: Create shipments
 * - track_shipment: Track shipment status
 * - estimate_shipping: Cost and time estimates
 */
@SpringBootApplication
public class LogisticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticsApplication.class, args);
    }
}
