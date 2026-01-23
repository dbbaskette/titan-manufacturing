package com.titan.sensor.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MQTT consumer that receives sensor readings from the data generator
 * and writes them to Greenplum.
 *
 * Subscribes to: titan/sensors/#
 * Topic format: titan/sensors/{facility}/{equipment}/{sensor_type}
 */
@Service
public class SensorDataConsumer implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(SensorDataConsumer.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private MqttClient mqttClient;

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:sensor-mcp-consumer}")
    private String clientId;

    @Value("${mqtt.username:titan}")
    private String username;

    @Value("${mqtt.password:titan5.0}")
    private String password;

    @Value("${mqtt.topic:titan/sensors/#}")
    private String topic;

    @Value("${mqtt.enabled:true}")
    private boolean mqttEnabled;

    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesWritten = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);

    public SensorDataConsumer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void initialize() {
        if (!mqttEnabled) {
            log.info("MQTT consumer is disabled");
            return;
        }

        try {
            log.info("Connecting to MQTT broker: {}", brokerUrl);
            mqttClient = new MqttClient(brokerUrl, clientId + "-" + System.currentTimeMillis());
            mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);

            mqttClient.connect(options);
            mqttClient.subscribe(topic, 1);
            log.info("Subscribed to MQTT topic: {}", topic);

        } catch (MqttException e) {
            log.warn("Failed to connect to MQTT broker: {}. Consumer will not receive live data.", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT client disconnected");
            } catch (MqttException e) {
                log.warn("Error disconnecting MQTT client: {}", e.getMessage());
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        messagesReceived.incrementAndGet();

        try {
            String payload = new String(message.getPayload());
            JsonNode json = objectMapper.readTree(payload);

            String equipmentId = json.path("equipmentId").asText();
            String sensorType = json.path("sensorType").asText();
            double value = json.path("value").asDouble();
            String unit = json.path("unit").asText();
            String qualityFlag = json.path("qualityFlag").asText("GOOD");

            // Parse timestamp or use current time
            Instant timestamp;
            if (json.has("timestamp") && !json.path("timestamp").isNull()) {
                timestamp = Instant.parse(json.path("timestamp").asText());
            } else {
                timestamp = Instant.now();
            }

            // Insert into Greenplum
            jdbcTemplate.update("""
                INSERT INTO sensor_readings (time, equipment_id, sensor_type, value, unit, quality_flag)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                Timestamp.from(timestamp),
                equipmentId,
                sensorType,
                value,
                unit,
                qualityFlag
            );

            messagesWritten.incrementAndGet();

            // Log every 100 messages
            if (messagesWritten.get() % 100 == 0) {
                log.info("Processed {} sensor readings ({} errors)", messagesWritten.get(), errors.get());
            }

        } catch (Exception e) {
            errors.incrementAndGet();
            log.error("Error processing MQTT message: {}", e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used for consumers
    }

    // Metrics getters
    public long getMessagesReceived() { return messagesReceived.get(); }
    public long getMessagesWritten() { return messagesWritten.get(); }
    public long getErrors() { return errors.get(); }
    public boolean isConnected() { return mqttClient != null && mqttClient.isConnected(); }
}
