package com.titan.maintenance.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for anomaly event publishing.
 *
 * Creates a topic exchange for anomaly events with routing keys:
 * - anomaly.critical: Equipment with >=70% failure probability
 * - anomaly.high: Equipment with >=50% failure probability
 *
 * The titan-orchestrator consumes these events to trigger Embabel workflows.
 */
@Configuration
public class RabbitConfig {

    @Value("${anomaly.exchange:titan.anomaly}")
    private String exchangeName;

    @Bean
    public TopicExchange anomalyExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setExchange(exchangeName);
        return template;
    }
}
