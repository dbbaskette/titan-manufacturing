package com.titan.orchestrator.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for consuming anomaly events.
 *
 * Binds queues to the titan.anomaly exchange:
 * - orchestrator.critical: receives anomaly.critical events (>=70% failure)
 * - orchestrator.high: receives anomaly.high events (>=50% failure)
 */
@Configuration
public class RabbitConfig {

    @Value("${anomaly.exchange:titan.anomaly}")
    private String exchangeName;

    @Value("${anomaly.queues.critical:orchestrator.critical}")
    private String criticalQueue;

    @Value("${anomaly.queues.high:orchestrator.high}")
    private String highQueue;

    @Bean
    public TopicExchange anomalyExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue criticalQueue() {
        return QueueBuilder.durable(criticalQueue).build();
    }

    @Bean
    public Queue highQueue() {
        return QueueBuilder.durable(highQueue).build();
    }

    @Bean
    public Binding criticalBinding(Queue criticalQueue, TopicExchange anomalyExchange) {
        return BindingBuilder.bind(criticalQueue).to(anomalyExchange).with("anomaly.critical");
    }

    @Bean
    public Binding highBinding(Queue highQueue, TopicExchange anomalyExchange) {
        return BindingBuilder.bind(highQueue).to(anomalyExchange).with("anomaly.high");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
