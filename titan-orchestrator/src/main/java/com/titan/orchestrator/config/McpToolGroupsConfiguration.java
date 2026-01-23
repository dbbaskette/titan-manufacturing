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
}
