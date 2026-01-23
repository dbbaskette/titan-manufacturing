package com.titan.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Titan Sensor Data Generator
 *
 * IoT sensor data simulator that generates realistic sensor readings for
 * manufacturing equipment and publishes them via MQTT to RabbitMQ.
 *
 * Supports configurable degradation patterns:
 * - NORMAL: Baseline values with random noise
 * - BEARING_DEGRADATION: Exponential vibration increase (like PHX-CNC-007)
 * - MOTOR_BURNOUT: Temperature spike pattern
 * - SPINDLE_WEAR: Gradual RPM decrease with vibration increase
 *
 * Data is consumed by the sensor-mcp-server and written to Greenplum.
 */
@SpringBootApplication
@EnableScheduling
public class GeneratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
    }
}
