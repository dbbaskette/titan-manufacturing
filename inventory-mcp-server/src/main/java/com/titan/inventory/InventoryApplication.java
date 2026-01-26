package com.titan.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan Inventory MCP Server
 *
 * Provides MCP tools for inventory management:
 * - check_stock: Multi-facility inventory levels
 * - search_products: pgvector semantic search
 * - find_alternatives: Alternative products/suppliers
 * - calculate_reorder: Optimal reorder quantities
 */
@SpringBootApplication
public class InventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
