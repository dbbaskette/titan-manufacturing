package com.titan.maintenance.controller;

import com.titan.maintenance.service.GemFireScoringService;
import com.titan.maintenance.service.GemFireService;
import com.titan.maintenance.service.ModelExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for ML pipeline data — serves the ML Dashboard UI.
 */
@RestController
@RequestMapping("/ml")
@CrossOrigin(origins = "*")
public class MLController {

    private static final Logger log = LoggerFactory.getLogger(MLController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ModelExportService modelExportService;
    private final GemFireService gemFireService;
    private final GemFireScoringService scoringService;

    public MLController(JdbcTemplate jdbcTemplate,
                        ModelExportService modelExportService,
                        GemFireService gemFireService,
                        GemFireScoringService scoringService) {
        this.jdbcTemplate = jdbcTemplate;
        this.modelExportService = modelExportService;
        this.gemFireService = gemFireService;
        this.scoringService = scoringService;
    }

    /**
     * Get model coefficients from Greenplum.
     */
    @GetMapping("/model")
    public Map<String, Object> getModel() {
        List<Map<String, Object>> coefficients = jdbcTemplate.queryForList("""
            SELECT feature_name, coefficient, description
            FROM ml_model_coefficients
            WHERE model_id = 'failure_predictor_v1'
            ORDER BY feature_name
            """);

        Integer trainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ml_training_data", Integer.class);

        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ml_training_data WHERE failed = 1", Integer.class);

        return Map.of(
                "modelId", "failure_predictor_v1",
                "method", "MADlib logregr_train (IRLS)",
                "coefficients", coefficients,
                "trainingObservations", trainingCount != null ? trainingCount : 0,
                "failureObservations", failureCount != null ? failureCount : 0
        );
    }

    /**
     * Get real-time predictions from GemFire.
     */
    @GetMapping("/predictions")
    public Map<String, Object> getPredictions() {
        return scoringService.getGemFirePredictions();
    }

    /**
     * Get GemFire deployment status.
     */
    @GetMapping("/gemfire/status")
    public Map<String, Object> getGemFireStatus() {
        boolean connected = gemFireService.isConnected();
        Map<String, Object> deployed = connected ? gemFireService.getDeployedModels() : Map.of();

        return Map.of(
                "connected", connected,
                "deployedModels", deployed
        );
    }

    /**
     * Get PMML export for the model.
     */
    @GetMapping("/pmml")
    public Map<String, Object> getPmml() {
        return modelExportService.exportModelAsPMML("failure_predictor_v1");
    }

    /**
     * Retrain the model.
     */
    @PostMapping("/retrain")
    public Map<String, Object> retrain() {
        log.info("ML Dashboard triggered model retrain");
        return modelExportService.retrainModel();
    }

    /**
     * Export PMML and deploy to GemFire.
     */
    @PostMapping("/deploy")
    public Map<String, Object> deploy() {
        log.info("ML Dashboard triggered model deploy to GemFire");
        return gemFireService.deployModelToGemFire("failure_predictor_v1");
    }

    /**
     * Clear all sensor windows and GemFire predictions.
     * Called after a simulation reset to flush stale data from the scoring pipeline.
     */
    @PostMapping("/predictions/reset")
    public Map<String, Object> resetPredictions() {
        log.info("Clearing all predictions and sensor windows");
        return scoringService.clearAllPredictions();
    }
}
