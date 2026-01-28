package com.titan.maintenance.service;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MCP Tools for deploying ML models to GemFire for real-time scoring.
 */
@Service
public class GemFireService {

    private static final Logger log = LoggerFactory.getLogger(GemFireService.class);

    private final ModelExportService modelExportService;

    @Value("${gemfire.locator.host:gemfire}")
    private String locatorHost;

    @Value("${gemfire.locator.port:10334}")
    private int locatorPort;

    private ClientCache clientCache;
    private Region<String, String> pmmlModelsRegion;
    private Region<String, String> sensorPredictionsRegion;

    public GemFireService(ModelExportService modelExportService) {
        this.modelExportService = modelExportService;
    }

    @PostConstruct
    public void initialize() {
        try {
            log.info("Connecting to GemFire locator at {}:{}", locatorHost, locatorPort);
            clientCache = new ClientCacheFactory()
                    .addPoolLocator(locatorHost, locatorPort)
                    .set("log-level", "warning")
                    .create();

            pmmlModelsRegion = clientCache
                    .<String, String>createClientRegionFactory(ClientRegionShortcut.PROXY)
                    .create("PmmlModels");

            sensorPredictionsRegion = clientCache
                    .<String, String>createClientRegionFactory(ClientRegionShortcut.PROXY)
                    .create("SensorPredictions");

            log.info("Connected to GemFire. PmmlModels and SensorPredictions regions ready.");
        } catch (Exception e) {
            log.warn("GemFire not available ({}). Deploy tools will retry on use.", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (clientCache != null && !clientCache.isClosed()) {
            clientCache.close();
        }
    }

    private void ensureConnected() {
        if (clientCache == null || clientCache.isClosed()) {
            initialize();
        }
        if (clientCache == null || clientCache.isClosed()) {
            throw new IllegalStateException("Cannot connect to GemFire at " + locatorHost + ":" + locatorPort);
        }
    }

    public Region<String, String> getSensorPredictionsRegion() {
        ensureConnected();
        return sensorPredictionsRegion;
    }

    public boolean isConnected() {
        return clientCache != null && !clientCache.isClosed();
    }

    /**
     * Export a PMML model and deploy it to GemFire for real-time scoring.
     */
    @McpTool(description = "Deploy an ML model to GemFire for real-time scoring. Exports the model as PMML and stores it in the GemFire PmmlModels region.")
    public Map<String, Object> deployModelToGemFire(
            @McpToolParam(description = "Model ID to deploy (default: failure_predictor_v1)")
            String modelId
    ) {
        String targetModelId = (modelId != null && !modelId.isBlank()) ? modelId : "failure_predictor_v1";
        log.info("Deploying model {} to GemFire", targetModelId);

        List<Map<String, String>> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            // Step 1: Query coefficients
            steps.add(step("sql", "SELECT feature_name, coefficient FROM ml_model_coefficients WHERE model_id = '" + targetModelId + "'"));

            // Step 2: Export PMML
            steps.add(step("exec", "Building PMML 4.4 XML document from model coefficients"));
            Map<String, Object> exportResult = modelExportService.exportModelAsPMML(targetModelId);
            if (!Boolean.TRUE.equals(exportResult.get("success"))) {
                steps.add(step("error", "PMML export failed: " + exportResult.get("error")));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", false);
                result.put("error", "PMML export failed: " + exportResult.get("error"));
                result.put("steps", steps);
                return result;
            }

            String pmmlXml = (String) exportResult.get("pmml");
            int featureCount = ((Number) exportResult.get("featureCount")).intValue();
            steps.add(step("result", "PMML generated: " + featureCount + " features, " + pmmlXml.length() + " bytes"));
            steps.add(step("xml", pmmlXml.substring(0, Math.min(pmmlXml.length(), 200)) + "..."));

            // Step 3: Connect to GemFire
            steps.add(step("exec", "Connecting to GemFire locator at " + locatorHost + ":" + locatorPort));
            ensureConnected();
            steps.add(step("result", "GemFire client connected"));

            // Step 4: Deploy to region
            steps.add(step("exec", "region.put('" + targetModelId + "', pmmlXml) → PmmlModels region"));
            pmmlModelsRegion.put(targetModelId, pmmlXml);
            steps.add(step("result", "Model deployed to PmmlModels region (" + pmmlXml.length() + " bytes)"));

            long elapsed = System.currentTimeMillis() - start;
            steps.add(step("done", "Deployment complete in " + elapsed + "ms"));

            log.info("Model {} deployed to GemFire PmmlModels region ({} bytes)", targetModelId, pmmlXml.length());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("modelId", targetModelId);
            result.put("region", "PmmlModels");
            result.put("pmmlSize", pmmlXml.length());
            result.put("gemfireLocator", locatorHost + ":" + locatorPort);
            result.put("message", "Model deployed to GemFire. Available for real-time scoring.");
            result.put("steps", steps);
            return result;
        } catch (Exception e) {
            log.error("Failed to deploy model to GemFire: {}", e.getMessage());
            steps.add(step("error", e.getMessage()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("steps", steps);
            return result;
        }
    }

    /**
     * List models currently deployed in GemFire.
     */
    @McpTool(description = "List ML models currently deployed in the GemFire PmmlModels region.")
    public Map<String, Object> getDeployedModels() {
        log.info("Listing deployed models in GemFire");

        try {
            ensureConnected();

            Set<String> keys = pmmlModelsRegion.keySetOnServer();
            List<Map<String, Object>> models = new ArrayList<>();

            for (String key : keys) {
                String pmml = pmmlModelsRegion.get(key);
                models.add(Map.of(
                        "modelId", key,
                        "pmmlSize", pmml != null ? pmml.length() : 0,
                        "deployed", true
                ));
            }

            return Map.of(
                    "success", true,
                    "gemfireLocator", locatorHost + ":" + locatorPort,
                    "region", "PmmlModels",
                    "modelCount", models.size(),
                    "models", models
            );
        } catch (Exception e) {
            log.error("Failed to list GemFire models: {}", e.getMessage());
            return Map.of(
                    "success", false,
                    "error", e.getMessage()
            );
        }
    }

    private Map<String, String> step(String type, String message) {
        return Map.of(
                "type", type,
                "message", message,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        );
    }
}
