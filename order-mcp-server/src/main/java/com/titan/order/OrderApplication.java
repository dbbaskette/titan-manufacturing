package com.titan.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan Order MCP Server
 *
 * Provides order validation and fulfillment orchestration tools for the
 * Titan Manufacturing AI platform.
 *
 * MCP Tools:
 * - validate_order: Validate order against inventory, contracts, credit
 * - check_contract_terms: Get customer contract terms and priority
 * - initiate_fulfillment: Start fulfillment workflow
 * - get_order_status: Get order status with event timeline
 */
@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
