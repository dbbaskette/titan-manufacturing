package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.LogisticsData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Titan Logistics Agent
 *
 * Provides logistics and shipping management capabilities for Titan's
 * global supply chain. Integrates with Logistics MCP Server tools.
 *
 * Key capabilities:
 * - Carrier selection and management
 * - Shipment creation and tracking
 * - Shipping cost estimation
 */
@Agent(description = "Titan Logistics Agent - Manages shipments across Titan's global supply chain. " +
       "Provides carrier selection, shipment creation, tracking, and cost estimation via FedEx, UPS, DHL, and Maersk.")
@Component
public class TitanLogisticsAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanLogisticsAgent.class);

    /**
     * Get available shipping carriers.
     */
    @Action(
        description = "Get available shipping carriers. Can filter by service type (EXPRESS, GROUND, FREIGHT).",
        toolGroups = {"logistics-tools"}
    )
    public List<Carrier> getCarriers(String serviceType, Ai ai) {
        log.info(">>> TitanLogisticsAgent.getCarriers serviceType: {}", serviceType);

        @SuppressWarnings("unchecked")
        List<Carrier> carriers = ai.withAutoLlm().createObject(
            """
            Use the get_carriers tool to list available shipping carriers.
            %s
            Return the list of carriers with their details.
            """.formatted(serviceType != null ? "Filter by service type: " + serviceType : ""),
            List.class
        );

        log.info("<<< getCarriers complete: {} carriers", carriers != null ? carriers.size() : 0);
        return carriers;
    }

    /**
     * Create a shipment for an order.
     */
    @Action(
        description = "Create a new shipment for an order. Assigns carrier and generates tracking number.",
        toolGroups = {"logistics-tools"}
    )
    public ShipmentCreateResult createShipment(String orderId, String carrierId, String originFacility, Ai ai) {
        log.info(">>> TitanLogisticsAgent.createShipment order: {}, carrier: {}, origin: {}",
                 orderId, carrierId, originFacility);

        ShipmentCreateResult result = ai.withAutoLlm().createObject(
            """
            Use the create_shipment tool to create a shipment for:
            - Order ID: %s
            - Carrier: %s
            - Origin Facility: %s

            Return the shipment details including tracking number and cost.
            """.formatted(orderId, carrierId, originFacility),
            ShipmentCreateResult.class
        );

        log.info("<<< createShipment complete: success={}, shipmentId={}",
                 result.success(), result.shipmentId());
        return result;
    }

    /**
     * Track a shipment.
     */
    @Action(
        description = "Track a shipment by shipment ID or tracking number. Returns current status and ETA.",
        toolGroups = {"logistics-tools"}
    )
    public TrackingResult trackShipment(String shipmentIdOrTracking, Ai ai) {
        log.info(">>> TitanLogisticsAgent.trackShipment: {}", shipmentIdOrTracking);

        TrackingResult result = ai.withAutoLlm().createObject(
            """
            Use the track_shipment tool to get tracking information for: %s
            Return the current status, location, and delivery estimate.
            """.formatted(shipmentIdOrTracking),
            TrackingResult.class
        );

        log.info("<<< trackShipment complete: status={}", result.status());
        return result;
    }

    /**
     * Estimate shipping costs.
     */
    @Action(
        description = "Estimate shipping cost and delivery time for a potential shipment.",
        toolGroups = {"logistics-tools"}
    )
    public List<ShippingEstimate> estimateShipping(String originFacility, String destRegion,
                                                    Double weightKg, String serviceLevel, Ai ai) {
        log.info(">>> TitanLogisticsAgent.estimateShipping from {} to {}, weight: {} kg",
                 originFacility, destRegion, weightKg);

        @SuppressWarnings("unchecked")
        List<ShippingEstimate> estimates = ai.withAutoLlm().createObject(
            """
            Use the estimate_shipping tool to get shipping options:
            - Origin facility: %s
            - Destination region: %s
            - Weight: %s kg
            %s

            Return the list of shipping options with costs and delivery times.
            """.formatted(originFacility, destRegion,
                         weightKg != null ? weightKg : "10",
                         serviceLevel != null ? "- Service level: " + serviceLevel : ""),
            List.class
        );

        log.info("<<< estimateShipping complete: {} options", estimates != null ? estimates.size() : 0);
        return estimates;
    }

    /**
     * Answer natural language logistics queries.
     */
    @AchievesGoal(description = "Answer logistics-related questions about shipments, carriers, and delivery")
    @Action(
        description = "Process natural language logistics queries using available tools",
        toolGroups = {"logistics-tools"}
    )
    public LogisticsQueryResponse answerLogisticsQuery(String query, Ai ai) {
        log.info(">>> TitanLogisticsAgent.answerLogisticsQuery: {}", query);

        String response = ai.withAutoLlm().generateText(
            """
            You are a logistics management assistant for Titan Manufacturing.
            Answer the following query using the logistics tools available to you:
            - get_carriers: List available shipping carriers
            - create_shipment: Create a new shipment for an order
            - track_shipment: Track shipment status
            - estimate_shipping: Get shipping cost and time estimates

            Query: %s

            Provide a helpful, detailed response based on the data from the tools.
            """.formatted(query)
        );

        log.info("<<< answerLogisticsQuery complete");
        return new LogisticsQueryResponse(query, response);
    }
}
