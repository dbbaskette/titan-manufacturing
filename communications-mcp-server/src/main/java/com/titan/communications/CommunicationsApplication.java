package com.titan.communications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Titan Communications MCP Server
 *
 * Provides customer communication tools for the Titan Manufacturing AI platform.
 *
 * MCP Tools:
 * - send_notification: Send templated notification to customer
 * - handle_inquiry: Process customer inquiry with RAG-powered response
 * - draft_customer_update: Generate status update draft for approval
 */
@SpringBootApplication
public class CommunicationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunicationsApplication.class, args);
    }
}
