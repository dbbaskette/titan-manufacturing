package com.titan.sensor.controller;

import com.titan.sensor.model.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Server-Sent Events (SSE) endpoint for real-time sensor data streaming.
 * Streams sensor readings to connected dashboard clients.
 */
@RestController
@RequestMapping("/api/sensors")
@CrossOrigin(origins = "*")
public class StreamingController {

    private static final Logger log = LoggerFactory.getLogger(StreamingController.class);
    private static final long SSE_TIMEOUT = 300_000L; // 5 minutes

    private final JdbcTemplate jdbcTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final CopyOnWriteArrayList<EmitterContext> emitters = new CopyOnWriteArrayList<>();

    public StreamingController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        startBroadcaster();
    }

    /**
     * SSE endpoint for streaming sensor readings.
     * Connect with: new EventSource('/api/sensors/stream?facilityId=PHX')
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamSensorData(
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String equipmentId
    ) {
        log.info("New SSE connection: facilityId={}, equipmentId={}", facilityId, equipmentId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        EmitterContext ctx = new EmitterContext(emitter, facilityId, equipmentId);

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed");
            emitters.remove(ctx);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out");
            emitters.remove(ctx);
        });

        emitter.onError(e -> {
            log.debug("SSE connection error: {}", e.getMessage());
            emitters.remove(ctx);
        });

        emitters.add(ctx);

        // Send initial data immediately
        try {
            List<SensorReading> initialData = fetchLatestReadings(facilityId, equipmentId);
            for (SensorReading reading : initialData) {
                emitter.send(SseEmitter.event()
                    .name("sensor-reading")
                    .data(reading));
            }
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("message", "Connected to sensor stream", "filters", Map.of(
                    "facilityId", facilityId != null ? facilityId : "all",
                    "equipmentId", equipmentId != null ? equipmentId : "all"
                ))));
        } catch (IOException e) {
            log.error("Error sending initial data: {}", e.getMessage());
            emitters.remove(ctx);
        }

        return emitter;
    }

    /**
     * Get current stream statistics.
     */
    @GetMapping("/stream/stats")
    public Map<String, Object> getStreamStats() {
        return Map.of(
            "activeConnections", emitters.size(),
            "serverTime", System.currentTimeMillis()
        );
    }

    private void startBroadcaster() {
        scheduler.scheduleAtFixedRate(() -> {
            if (emitters.isEmpty()) return;

            for (EmitterContext ctx : emitters) {
                try {
                    List<SensorReading> readings = fetchLatestReadings(ctx.facilityId, ctx.equipmentId);
                    for (SensorReading reading : readings) {
                        ctx.emitter.send(SseEmitter.event()
                            .name("sensor-reading")
                            .data(reading));
                    }
                } catch (Exception e) {
                    log.debug("Error broadcasting to client: {}", e.getMessage());
                    emitters.remove(ctx);
                    try {
                        ctx.emitter.complete();
                    } catch (Exception ignored) {}
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private List<SensorReading> fetchLatestReadings(String facilityId, String equipmentId) {
        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT ON (equipment_id, sensor_type)
                   equipment_id, sensor_type, value, unit, time, quality_flag
            FROM sensor_readings
            WHERE time >= NOW() - INTERVAL '30 seconds'
            """);

        if (equipmentId != null && !equipmentId.isBlank()) {
            sql.append(" AND equipment_id = '").append(equipmentId).append("'");
        } else if (facilityId != null && !facilityId.isBlank()) {
            sql.append(" AND equipment_id LIKE '").append(facilityId.toUpperCase()).append("-%'");
        }

        sql.append(" ORDER BY equipment_id, sensor_type, time DESC");
        sql.append(" LIMIT 100");

        return jdbcTemplate.query(sql.toString(),
            (rs, rowNum) -> new SensorReading(
                rs.getString("equipment_id"),
                rs.getString("sensor_type"),
                rs.getDouble("value"),
                rs.getString("unit"),
                rs.getString("time"),
                rs.getString("quality_flag")
            ));
    }

    private record EmitterContext(SseEmitter emitter, String facilityId, String equipmentId) {}
}
