package com.titan.orchestrator.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${TITAN_ADMIN_EMAIL:}")
    private String adminEmail;

    public SettingsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllSettings() {
        List<Map<String, Object>> settings = jdbcTemplate.queryForList(
                "SELECT setting_key, setting_value, encrypted, updated_at, updated_by FROM app_settings ORDER BY setting_key");
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/{key}")
    public ResponseEntity<Map<String, Object>> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'value' field"));
        }
        int updated = jdbcTemplate.update(
                "UPDATE app_settings SET setting_value = ?, updated_at = CURRENT_TIMESTAMP, updated_by = 'dashboard' WHERE setting_key = ?",
                value, key);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO app_settings (setting_key, setting_value, updated_by) VALUES (?, ?, 'dashboard')",
                    key, value);
        }
        log.info("Setting '{}' updated", key);
        return ResponseEntity.ok(Map.of("setting_key", key, "setting_value", value));
    }

    @GetMapping("/llm-models")
    public ResponseEntity<List<Map<String, Object>>> getLlmModels() {
        List<Map<String, Object>> models = jdbcTemplate.queryForList(
                "SELECT model_id, provider, model_name, base_url, is_default, created_at FROM llm_models ORDER BY is_default DESC, provider, model_name");
        return ResponseEntity.ok(models);
    }

    @PutMapping("/llm-models/{modelId}/default")
    public ResponseEntity<Map<String, Object>> setDefaultModel(@PathVariable String modelId) {
        jdbcTemplate.update("UPDATE llm_models SET is_default = FALSE");
        int updated = jdbcTemplate.update("UPDATE llm_models SET is_default = TRUE WHERE model_id = ?", modelId);
        if (updated == 0) {
            return ResponseEntity.notFound().build();
        }
        log.info("Default LLM model set to: {}", modelId);
        return ResponseEntity.ok(Map.of("model_id", modelId, "is_default", true));
    }

    @GetMapping("/admin-email")
    public ResponseEntity<Map<String, Object>> getAdminEmail() {
        return ResponseEntity.ok(Map.of("admin_email", adminEmail != null ? adminEmail : ""));
    }
}
