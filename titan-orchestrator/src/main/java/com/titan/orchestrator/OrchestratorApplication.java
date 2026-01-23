package com.titan.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan 5.0 Orchestrator - Central coordinator for multi-agent AI platform.
 *
 * Uses Embabel Agent Framework with GOAP (Goal Oriented Action Planning)
 * to dynamically coordinate specialized MCP agents for manufacturing operations.
 *
 * Agents:
 * - Sensor Agent (8081): IoT monitoring for 600+ CNC machines
 * - Maintenance Agent (8082): Predictive maintenance and RUL estimation
 * - Inventory Agent (8083): 50K+ SKU management with pgvector search
 * - Logistics Agent (8084): Global shipping optimization
 * - Order Agent (8085): B2B fulfillment via RabbitMQ
 * - Communications Agent (8086): Customer notifications
 * - Governance Agent (8087): OpenMetadata integration
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
