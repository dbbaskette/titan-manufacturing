package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.InventoryData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Titan Inventory Agent
 *
 * Provides inventory management capabilities for Titan's 50,000+ SKUs
 * across 12 global facilities. Integrates with Inventory MCP Server tools.
 *
 * Key capabilities:
 * - Stock level checking across facilities
 * - Semantic product search using pgvector embeddings
 * - Alternative product/supplier finding
 * - Reorder quantity calculations
 */
@Agent(description = "Titan Inventory Agent - Manages 50,000+ SKUs across 12 global manufacturing facilities. " +
       "Provides stock checking, semantic product search, alternative finding, and reorder calculations.")
@Component
public class TitanInventoryAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanInventoryAgent.class);

    /**
     * Check stock levels for a product across facilities.
     */
    @Action(
        description = "Check inventory stock levels for a product. Returns quantity, reorder status, and availability across facilities.",
        toolGroups = {"inventory-tools"}
    )
    public StockCheckResult checkStock(String sku, Ai ai) {
        log.info(">>> TitanInventoryAgent.checkStock for SKU: {}", sku);

        StockCheckResult result = ai.withAutoLlm().createObject(
            """
            Use the check_stock tool to get inventory levels for product %s.
            Return the stock levels across all facilities, total quantity, and whether reorder is needed.
            """.formatted(sku),
            StockCheckResult.class
        );

        log.info("<<< checkStock complete: {} total units, reorder needed: {}",
                 result.totalQuantity(), result.needsReorder());
        return result;
    }

    /**
     * Search products using natural language query.
     */
    @Action(
        description = "Search for products using natural language. Uses pgvector semantic search for finding relevant products.",
        toolGroups = {"inventory-tools"}
    )
    public List<ProductSearchResult> searchProducts(String query, String division, Ai ai) {
        log.info(">>> TitanInventoryAgent.searchProducts query: '{}', division: {}", query, division);

        @SuppressWarnings("unchecked")
        List<ProductSearchResult> results = ai.withAutoLlm().createObject(
            """
            Use the search_products tool to find products matching: "%s"
            %s
            Return the list of matching products with their details.
            """.formatted(query, division != null ? "Filter by division: " + division : ""),
            List.class
        );

        log.info("<<< searchProducts complete: {} results", results != null ? results.size() : 0);
        return results;
    }

    /**
     * Find alternative products or suppliers.
     */
    @Action(
        description = "Find alternative products or suppliers for a given SKU. Useful for stockouts or supplier issues.",
        toolGroups = {"inventory-tools"}
    )
    public List<AlternativeProduct> findAlternatives(String sku, Integer quantityNeeded, Ai ai) {
        log.info(">>> TitanInventoryAgent.findAlternatives for SKU: {}, qty: {}", sku, quantityNeeded);

        @SuppressWarnings("unchecked")
        List<AlternativeProduct> alternatives = ai.withAutoLlm().createObject(
            """
            Use the find_alternatives tool to find alternative products or suppliers for SKU %s.
            %s
            Return the list of alternatives with supplier info, pricing, and availability.
            """.formatted(sku, quantityNeeded != null ? "Minimum quantity needed: " + quantityNeeded : ""),
            List.class
        );

        log.info("<<< findAlternatives complete: {} alternatives found",
                 alternatives != null ? alternatives.size() : 0);
        return alternatives;
    }

    /**
     * Calculate optimal reorder quantity and timing.
     */
    @Action(
        description = "Calculate optimal reorder quantity and timing for a product at a facility.",
        toolGroups = {"inventory-tools"}
    )
    public ReorderCalculation calculateReorder(String sku, String facilityId, Double dailyDemand, Ai ai) {
        log.info(">>> TitanInventoryAgent.calculateReorder for SKU: {} at {}", sku, facilityId);

        ReorderCalculation result = ai.withAutoLlm().createObject(
            """
            Use the calculate_reorder tool to determine optimal reorder quantity for:
            - SKU: %s
            - Facility: %s
            %s
            Return the reorder recommendation with quantity, timing, and cost estimate.
            """.formatted(sku, facilityId,
                         dailyDemand != null ? "- Daily demand estimate: " + dailyDemand : ""),
            ReorderCalculation.class
        );

        log.info("<<< calculateReorder complete: recommend {} units, urgent: {}",
                 result.recommendedOrder(), result.urgentReorder());
        return result;
    }

    /**
     * Answer natural language inventory queries.
     */
    @AchievesGoal(description = "Answer inventory-related questions about stock levels, products, and reorder needs")
    @Action(
        description = "Process natural language inventory queries using available tools",
        toolGroups = {"inventory-tools"}
    )
    public InventoryQueryResponse answerInventoryQuery(String query, Ai ai) {
        log.info(">>> TitanInventoryAgent.answerInventoryQuery: {}", query);

        String response = ai.withAutoLlm().generateText(
            """
            You are an inventory management assistant for Titan Manufacturing.
            Answer the following query using the inventory tools available to you:
            - check_stock: Check inventory levels for a product
            - search_products: Search products using natural language
            - find_alternatives: Find alternative products or suppliers
            - calculate_reorder: Calculate optimal reorder quantities

            Query: %s

            Provide a helpful, detailed response based on the data from the tools.
            """.formatted(query)
        );

        log.info("<<< answerInventoryQuery complete");
        return new InventoryQueryResponse(query, response);
    }
}
