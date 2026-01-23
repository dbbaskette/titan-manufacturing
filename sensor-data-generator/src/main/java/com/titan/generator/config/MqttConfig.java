package com.titan.generator.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT client configuration for publishing sensor data to RabbitMQ.
 */
@Configuration
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:titan-sensor-generator}")
    private String clientId;

    @Value("${mqtt.username:titan}")
    private String username;

    @Value("${mqtt.password:titan5.0}")
    private String password;

    @Bean
    public MqttClient mqttClient() throws MqttException {
        log.info("Connecting to MQTT broker at: {}", brokerUrl);

        MqttClient client = new MqttClient(brokerUrl, clientId + "-" + System.currentTimeMillis(),
                                            new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        try {
            client.connect(options);
            log.info("Connected to MQTT broker successfully");
        } catch (MqttException e) {
            log.warn("Failed to connect to MQTT broker: {}. Will retry on first publish.", e.getMessage());
        }

        return client;
    }
}
