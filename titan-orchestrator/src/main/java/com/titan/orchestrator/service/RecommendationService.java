package com.titan.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.titan.orchestrator.model.AnomalyEvent;
import com.titan.orchestrator.model.AnomalyResponse.HighAnomalyResponse;
import com.titan.orchestrator.model.AnomalyResponse.ReservedPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing maintenance recommendations (HIGH risk alerts).
 * Recommendations are created when HIGH risk is detected and await human approval.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a new maintenance recommendation from a HIGH anomaly event.
     */
    public String create(AnomalyEvent event, HighAnomalyResponse response) {
        String recommendationId = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Instant expiresAt = Instant.now().plus(48, ChronoUnit.HOURS);

        try {
            String partsJson = objectMapper.writeValueAsString(response.partsReserved());
            double estimatedCost = calculateEstimatedCost(response.partsReserved());

            jdbcTemplate.update("""
                INSERT INTO maintenance_recommendations
                (recommendation_id, equipment_id, facility_id, risk_level, failure_probability,
                 probable_cause, recommended_action, recommended_parts, estimated_cost,
                 status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'PENDING', NOW(), ?)
                """,
                recommendationId,
                event.equipmentId(),
                event.facilityId(),
                event.prediction().riskLevel(),
                event.prediction().failureProbability(),
                event.prediction().probableCause(),
                response.recommendedAction(),
                partsJson,
                estimatedCost,
                Timestamp.from(expiresAt)
            );

            log.info("Created recommendation {} for equipment {}", recommendationId, event.equipmentId());
            return recommendationId;

        } catch (Exception e) {
            log.error("Failed to create recommendation for {}: {}", event.equipmentId(), e.getMessage());
            throw new RuntimeException("Failed to create recommendation", e);
        }
    }

    /**
     * Cancel pending recommendations for equipment (when superseded by CRITICAL).
     */
    public int cancelPending(String equipmentId, String reason) {
        int updated = jdbcTemplate.update("""
            UPDATE maintenance_recommendations
            SET status = 'SUPERSEDED', notes = ?
            WHERE equipment_id = ? AND status = 'PENDING'
            """, reason, equipmentId);

        if (updated > 0) {
            log.info("Superseded {} pending recommendation(s) for {}", updated, equipmentId);
        }
        return updated;
    }

    /**
     * Approve a recommendation - triggers the maintenance workflow.
     */
    public void approve(String recommendationId, String approvedBy) {
        jdbcTemplate.update("""
            UPDATE maintenance_recommendations
            SET status = 'APPROVED', approved_at = NOW(), approved_by = ?
            WHERE recommendation_id = ? AND status = 'PENDING'
            """, approvedBy, recommendationId);

        log.info("Recommendation {} approved by {}", recommendationId, approvedBy);
    }

    /**
     * Update recommendation with work order ID after maintenance is scheduled.
     */
    public void setWorkOrderId(String recommendationId, String workOrderId) {
        jdbcTemplate.update("""
            UPDATE maintenance_recommendations
            SET work_order_id = ?, status = 'COMPLETED'
            WHERE recommendation_id = ?
            """, workOrderId, recommendationId);
    }

    /**
     * Dismiss a recommendation - releases reserved parts.
     */
    public void dismiss(String recommendationId, String reason) {
        jdbcTemplate.update("""
            UPDATE maintenance_recommendations
            SET status = 'DISMISSED', notes = ?
            WHERE recommendation_id = ? AND status = 'PENDING'
            """, reason, recommendationId);

        log.info("Recommendation {} dismissed: {}", recommendationId, reason);
    }

    /**
     * Check if there's already a pending recommendation for this equipment.
     */
    public boolean hasPendingRecommendation(String equipmentId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM maintenance_recommendations
            WHERE equipment_id = ? AND status = 'PENDING'
            """, Integer.class, equipmentId);
        return count != null && count > 0;
    }

    /**
     * Get all pending recommendations.
     */
    public List<Map<String, Object>> getPendingRecommendations() {
        return jdbcTemplate.queryForList("""
            SELECT recommendation_id, equipment_id, facility_id, risk_level,
                   failure_probability, probable_cause, recommended_action,
                   recommended_parts, estimated_cost, status, created_at, expires_at
            FROM maintenance_recommendations
            WHERE status = 'PENDING'
            ORDER BY failure_probability DESC, created_at ASC
            """);
    }

    /**
     * Get recommendation by ID.
     */
    public Map<String, Object> getRecommendation(String recommendationId) {
        return jdbcTemplate.queryForMap("""
            SELECT * FROM maintenance_recommendations WHERE recommendation_id = ?
            """, recommendationId);
    }

    /**
     * Get resolved recommendations (APPROVED, COMPLETED, DISMISSED, SUPERSEDED).
     */
    public List<Map<String, Object>> getResolvedRecommendations(int limit) {
        return jdbcTemplate.queryForList("""
            SELECT recommendation_id, equipment_id, facility_id, risk_level,
                   failure_probability, probable_cause, recommended_action,
                   recommended_parts, estimated_cost, status, created_at, expires_at,
                   approved_at, approved_by, work_order_id, notes
            FROM maintenance_recommendations
            WHERE status IN ('APPROVED', 'COMPLETED', 'DISMISSED', 'SUPERSEDED')
            ORDER BY COALESCE(approved_at, created_at) DESC
            LIMIT ?
            """, limit);
    }

    private double calculateEstimatedCost(List<ReservedPart> parts) {
        if (parts == null || parts.isEmpty()) return 0.0;
        double partsCost = parts.stream()
            .mapToDouble(p -> p.unitPrice() * p.quantity())
            .sum();
        double laborCost = 6.0 * 75.0; // 6 hours @ $75/hr default
        return partsCost + laborCost;
    }
}
