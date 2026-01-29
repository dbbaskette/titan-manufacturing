package com.titan.orchestrator.config;

import com.embabel.agent.core.ToolGroup;
import com.embabel.agent.core.ToolGroupDescription;
import com.embabel.agent.core.ToolGroupPermission;
import com.embabel.agent.tools.mcp.McpToolGroup;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * Configuration class that exposes MCP server tools as Embabel ToolGroups.
 *
 * This bridges Spring AI MCP clients with Embabel's tool resolution system,
 * allowing @Action methods to access MCP tools via toolGroups parameter.
 *
 * IMPORTANT: The second parameter to ToolGroupDescription.Companion.invoke(description, role)
 * sets the ROLE which must match the toolGroups value in @Action annotations.
 * For example, @Action(toolGroups = {"sensor-tools"}) requires
 * ToolGroupDescription with role "sensor-tools".
 */
@Configuration
public class McpToolGroupsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpToolGroupsConfiguration.class);

    private final List<McpSyncClient> mcpSyncClients;

    public McpToolGroupsConfiguration(List<McpSyncClient> mcpSyncClients) {
        log.info(">>> McpToolGroupsConfiguration initialized with {} MCP clients",
                 mcpSyncClients != null ? mcpSyncClients.size() : 0);

        // Log available tools from each client
        if (mcpSyncClients != null) {
            for (McpSyncClient client : mcpSyncClients) {
                try {
                    var tools = client.listTools();
                    log.info("MCP Client tools available: {}",
                             tools.tools().stream()
                                  .map(t -> t.name())
                                  .toList());
                } catch (Exception e) {
                    log.warn("Could not list tools from MCP client: {}", e.getMessage());
                }
            }
        }

        this.mcpSyncClients = mcpSyncClients;
    }

    /**
     * Sensor tools from the Sensor MCP Server.
     * Provides access to equipment listing, status, readings, facility overview, and anomaly detection.
     */
    @Bean
    public ToolGroup sensorToolGroup() {
        log.info(">>> Creating sensor-tools ToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Titan Manufacturing sensor tools for monitoring 600+ CNC machines across 12 global facilities. " +
                "Provides equipment listing, health status, sensor readings, facility overview, and anomaly detection.",
                "sensor-tools"  // role - must match @Action toolGroups value
            ),
            "sensor-tools",           // name
            "TITAN-SENSOR-MCP",       // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                // Include all sensor-related tools
                return toolName.equals("list_equipment") ||
                       toolName.equals("get_equipment_status") ||
                       toolName.equals("get_sensor_readings") ||
                       toolName.equals("get_facility_status") ||
                       toolName.equals("detect_anomaly") ||
                       // Also match camelCase variants just in case
                       toolName.equals("listEquipment") ||
                       toolName.equals("getEquipmentStatus") ||
                       toolName.equals("getSensorReadings") ||
                       toolName.equals("getFacilityStatus") ||
                       toolName.equals("detectAnomaly");
            }
        );
    }

    /**
     * Maintenance tools from the Maintenance MCP Server.
     * Provides predictive maintenance, RUL estimation, and work order scheduling.
     * Enables the Phoenix Incident demo scenario.
     */
    @Bean
    public ToolGroup maintenanceToolGroup() {
        log.info(">>> Creating maintenance-tools ToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Titan Manufacturing predictive maintenance tools for failure prediction, " +
                "remaining useful life (RUL) estimation, and maintenance scheduling. " +
                "Enables Phoenix Incident scenario with 73% failure probability detection on PHX-CNC-007.",
                "maintenance-tools"  // role - must match @Action toolGroups value
            ),
            "maintenance-tools",           // name
            "TITAN-MAINTENANCE-MCP",       // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                // Include all maintenance-related tools
                return toolName.equals("predict_failure") ||
                       toolName.equals("estimate_rul") ||
                       toolName.equals("schedule_maintenance") ||
                       toolName.equals("get_maintenance_history") ||
                       // Also match camelCase variants
                       toolName.equals("predictFailure") ||
                       toolName.equals("estimateRul") ||
                       toolName.equals("scheduleMaintenance") ||
                       toolName.equals("getMaintenanceHistory");
            }
        );
    }

    /**
     * Inventory tools from the Inventory MCP Server.
     * Provides stock checking, semantic product search, alternatives, and reorder calculations.
     */
    @Bean
    public ToolGroup inventoryToolGroup() {
        log.info(">>> Creating inventory-tools ToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Titan Manufacturing inventory tools for 50,000+ SKUs across 12 facilities. " +
                "Provides stock checking, semantic product search using pgvector, " +
                "alternative product finding, and reorder calculations.",
                "inventory-tools"  // role - must match @Action toolGroups value
            ),
            "inventory-tools",           // name
            "TITAN-INVENTORY-MCP",       // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("check_stock") ||
                       toolName.equals("search_products") ||
                       toolName.equals("find_alternatives") ||
                       toolName.equals("calculate_reorder") ||
                       // Also match camelCase variants
                       toolName.equals("checkStock") ||
                       toolName.equals("searchProducts") ||
                       toolName.equals("findAlternatives") ||
                       toolName.equals("calculateReorder") ||
                       toolName.equals("get_compatible_parts") ||
                       toolName.equals("getCompatibleParts");
            }
        );
    }

    /**
     * Logistics tools from the Logistics MCP Server.
     * Provides carrier management, shipment creation, tracking, and cost estimation.
     */
    @Bean
    public ToolGroup logisticsToolGroup() {
        log.info(">>> Creating logistics-tools ToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Titan Manufacturing logistics tools for global shipment management. " +
                "Provides carrier selection, shipment creation, real-time tracking, " +
                "and shipping cost estimation across FedEx, UPS, DHL, and Maersk.",
                "logistics-tools"  // role - must match @Action toolGroups value
            ),
            "logistics-tools",           // name
            "TITAN-LOGISTICS-MCP",       // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("get_carriers") ||
                       toolName.equals("create_shipment") ||
                       toolName.equals("track_shipment") ||
                       toolName.equals("estimate_shipping") ||
                       // Also match camelCase variants
                       toolName.equals("getCarriers") ||
                       toolName.equals("createShipment") ||
                       toolName.equals("trackShipment") ||
                       toolName.equals("estimateShipping");
            }
        );
    }

    /**
     * Order tools from the Order MCP Server.
     * Provides order validation, contract terms lookup, fulfillment orchestration, and status tracking.
     */
    @Bean
    public ToolGroup orderToolGroup() {
        log.info(">>> Creating order-tools ToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Titan Manufacturing order tools for order validation and fulfillment. " +
                "Provides order validation against inventory/credit/contracts, contract terms lookup, " +
                "fulfillment workflow initiation, and order status tracking.",
                "order-tools"  // role - must match @Action toolGroups value
            ),
            "order-tools",           // name
            "TITAN-ORDER-MCP",       // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("validate_order") ||
                       toolName.equals("check_contract_terms") ||
                       toolName.equals("initiate_fulfillment") ||
                       toolName.equals("get_order_status") ||
                       // Also match camelCase variants
                       toolName.equals("validateOrder") ||
                       toolName.equals("checkContractTerms") ||
                       toolName.equals("initiateFulfillment") ||
                       toolName.equals("getOrderStatus");
            }
        );
    }

    /**
     * Communications tools from the Communications MCP Server.
     * Provides customer notification, inquiry handling with RAG, and update drafting.
     */
    @Bean
    public ToolGroup communicationsToolGroup() {
        log.info(">>> Creating communications-tools ToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Titan Manufacturing communications tools for customer interactions. " +
                "Provides templated notifications, RAG-powered inquiry handling, " +
                "and customer update drafting for order status communications.",
                "communications-tools"  // role - must match @Action toolGroups value
            ),
            "communications-tools",           // name
            "TITAN-COMMUNICATIONS-MCP",       // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("send_notification") ||
                       toolName.equals("handle_inquiry") ||
                       toolName.equals("draft_customer_update") ||
                       // Also match camelCase variants
                       toolName.equals("sendNotification") ||
                       toolName.equals("handleInquiry") ||
                       toolName.equals("draftCustomerUpdate");
            }
        );
    }

    /**
     * Governance tools from the Governance MCP Server.
     * Provides data governance, lineage tracking, quality monitoring, and compliance reporting.
     */
    @Bean
    public ToolGroup governanceToolGroup() {
        log.info(">>> Creating governance-tools ToolGroup bean");
        return new McpToolGroup(
            ToolGroupDescription.Companion.invoke(
                "Titan Manufacturing data governance tools integrating with OpenMetadata. " +
                "Provides metadata access, data lineage tracing, quality monitoring, " +
                "material batch traceability, and regulatory compliance reporting (FAA, ISO).",
                "governance-tools"  // role - must match @Action toolGroups value
            ),
            "governance-tools",           // name
            "TITAN-GOVERNANCE-MCP",       // provider
            Set.of(ToolGroupPermission.HOST_ACCESS),
            mcpSyncClients,
            tool -> {
                String toolName = tool.getToolDefinition().name();
                return toolName.equals("get_table_metadata") ||
                       toolName.equals("trace_data_lineage") ||
                       toolName.equals("check_data_quality") ||
                       toolName.equals("search_data_assets") ||
                       toolName.equals("get_glossary_term") ||
                       toolName.equals("trace_material_batch") ||
                       toolName.equals("get_compliance_report") ||
                       // Also match camelCase variants
                       toolName.equals("getTableMetadata") ||
                       toolName.equals("traceDataLineage") ||
                       toolName.equals("checkDataQuality") ||
                       toolName.equals("searchDataAssets") ||
                       toolName.equals("getGlossaryTerm") ||
                       toolName.equals("traceMaterialBatch") ||
                       toolName.equals("getComplianceReport");
            }
        );
    }
}
