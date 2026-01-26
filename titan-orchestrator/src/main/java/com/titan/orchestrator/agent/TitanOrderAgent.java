package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.OrderData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Titan Order Agent
 *
 * Provides order validation and fulfillment orchestration capabilities.
 * Integrates with Order MCP Server tools.
 *
 * Key capabilities:
 * - Order validation against inventory, credit, and contracts
 * - Contract terms lookup for priority customers
 * - Fulfillment workflow initiation (inventory allocation, shipment planning)
 * - Order status tracking with event timeline
 */
@Agent(description = "Titan Order Agent - Validates and processes customer orders for strategic accounts like Boeing and Airbus. " +
       "Provides order validation, contract terms lookup, fulfillment orchestration, and status tracking.")
@Component
public class TitanOrderAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanOrderAgent.class);

    /**
     * Validate an order against inventory, credit, and contracts.
     */
    @Action(
        description = "Validate an order against inventory availability, customer credit limits, and contract terms.",
        toolGroups = {"order-tools"}
    )
    public ValidationResult validateOrder(String orderId, Ai ai) {
        log.info(">>> TitanOrderAgent.validateOrder for order: {}", orderId);

        ValidationResult result = ai.withAutoLlm().createObject(
            """
            Use the validate_order tool to validate order %s.
            Check inventory availability, credit limits, and contract terms.
            Return the validation result with any issues found.
            """.formatted(orderId),
            ValidationResult.class
        );

        log.info("<<< validateOrder complete: valid={}", result.isValid());
        return result;
    }

    /**
     * Get customer contract terms.
     */
    @Action(
        description = "Get customer contract terms including priority level, discounts, payment terms, and credit limits.",
        toolGroups = {"order-tools"}
    )
    public ContractTerms checkContractTerms(String customerId, Ai ai) {
        log.info(">>> TitanOrderAgent.checkContractTerms for customer: {}", customerId);

        ContractTerms result = ai.withAutoLlm().createObject(
            """
            Use the check_contract_terms tool to get contract information for customer %s.
            Return the contract type, priority level, discount, payment terms, and credit limits.
            """.formatted(customerId),
            ContractTerms.class
        );

        log.info("<<< checkContractTerms complete: {} account, priority {}",
                 result.contractType(), result.priorityLevel());
        return result;
    }

    /**
     * Initiate fulfillment for an order.
     */
    @Action(
        description = "Initiate fulfillment workflow for an order. Reserves inventory and plans shipments.",
        toolGroups = {"order-tools"}
    )
    public FulfillmentResult initiateFulfillment(String orderId, Boolean expedite, Ai ai) {
        log.info(">>> TitanOrderAgent.initiateFulfillment for order: {}, expedite: {}", orderId, expedite);

        FulfillmentResult result = ai.withAutoLlm().createObject(
            """
            Use the initiate_fulfillment tool to start fulfillment for order %s.
            %s
            Return the fulfillment status, inventory allocations, and planned shipments.
            """.formatted(orderId, expedite != null && expedite ? "Request EXPEDITED processing." : ""),
            FulfillmentResult.class
        );

        log.info("<<< initiateFulfillment complete: success={}, expedited={}",
                 result.success(), result.isExpedited());
        return result;
    }

    /**
     * Get order status with event timeline.
     */
    @Action(
        description = "Get complete order status including current state, line items, event history, and shipments.",
        toolGroups = {"order-tools"}
    )
    public OrderStatusResult getOrderStatus(String orderId, Ai ai) {
        log.info(">>> TitanOrderAgent.getOrderStatus for order: {}", orderId);

        OrderStatusResult result = ai.withAutoLlm().createObject(
            """
            Use the get_order_status tool to get the complete status for order %s.
            Include the current status, order lines, event timeline, and any shipments.
            """.formatted(orderId),
            OrderStatusResult.class
        );

        log.info("<<< getOrderStatus complete: status={}", result.currentStatus());
        return result;
    }

    /**
     * Answer natural language order queries.
     */
    @AchievesGoal(description = "Answer order-related questions about validation, fulfillment, and status")
    @Action(
        description = "Process natural language order queries using available tools",
        toolGroups = {"order-tools"}
    )
    public OrderQueryResponse answerOrderQuery(String query, Ai ai) {
        log.info(">>> TitanOrderAgent.answerOrderQuery: {}", query);

        String response = ai.withAutoLlm().generateText(
            """
            You are an order management assistant for Titan Manufacturing.
            Answer the following query using the order tools available to you:
            - validate_order: Validate order against inventory, credit, contracts
            - check_contract_terms: Get customer contract terms and priority
            - initiate_fulfillment: Start fulfillment workflow
            - get_order_status: Get order status with event timeline

            Query: %s

            Provide a helpful, detailed response based on the data from the tools.
            """.formatted(query)
        );

        log.info("<<< answerOrderQuery complete");
        return new OrderQueryResponse(query, response);
    }
}
