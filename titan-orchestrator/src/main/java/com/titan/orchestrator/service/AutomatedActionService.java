package com.titan.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.orchestrator.model.AnomalyEvent;
import com.titan.orchestrator.model.AnomalyResponse.CriticalAnomalyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for recording automated actions taken for CRITICAL alerts.
 * Provides audit trail for all autonomous maintenance scheduling.
 */
@Service
public class AutomatedActionService {

    private static final Logger log = LoggerFactory.getLogger(AutomatedActionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AutomatedActionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Record an automated action taken in response to a CRITICAL anomaly.
     */
    public String record(AnomalyEvent event, CriticalAnomalyResponse response) {
        String actionId = "ACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            String partsJson = objectMapper.writeValueAsString(response.partsReserved());

            jdbcTemplate.update("""
                INSERT INTO automated_actions
                (action_id, event_id, equipment_id, facility_id, action_type,
                 risk_level, failure_probability, probable_cause, work_order_id,
                 parts_reserved, notification_sent, status, executed_at, execution_summary)
                VALUES (?, ?, ?, ?, 'CRITICAL_RESPONSE', ?, ?, ?, ?, ?::jsonb, ?, 'COMPLETED', NOW(), ?)
                """,
                actionId,
                event.eventId(),
                event.equipmentId(),
                event.facilityId(),
                event.prediction().riskLevel(),
                event.prediction().failureProbability(),
                event.prediction().probableCause(),
                response.workOrderId(),
                partsJson,
                response.notificationSent(),
                response.summary()
            );

            log.info("Recorded automated action {} for equipment {} (WO: {})",
                     actionId, event.equipmentId(), response.workOrderId());
            return actionId;

        } catch (Exception e) {
            log.error("Failed to record automated action for {}: {}", event.equipmentId(), e.getMessage());
            throw new RuntimeException("Failed to record automated action", e);
        }
    }

    /**
     * Get recent automated actions.
     */
    public List<Map<String, Object>> getRecentActions(int limit) {
        return jdbcTemplate.queryForList("""
            SELECT action_id, event_id, equipment_id, facility_id, action_type,
                   risk_level, failure_probability, probable_cause, work_order_id,
                   parts_reserved, notification_sent, status, executed_at, execution_summary
            FROM automated_actions
            ORDER BY executed_at DESC
            LIMIT ?
            """, limit);
    }

    /**
     * Get automated actions for a specific equipment.
     */
    public List<Map<String, Object>> getActionsForEquipment(String equipmentId) {
        return jdbcTemplate.queryForList("""
            SELECT * FROM automated_actions
            WHERE equipment_id = ?
            ORDER BY executed_at DESC
            """, equipmentId);
    }

    /**
     * Get action by ID.
     */
    public Map<String, Object> getAction(String actionId) {
        return jdbcTemplate.queryForMap("""
            SELECT * FROM automated_actions WHERE action_id = ?
            """, actionId);
    }
}
