package com.titan.communications.service;

import com.titan.communications.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Communications MCP Service
 *
 * Provides tools for customer notifications and inquiry handling with RAG support.
 */
@Service
public class CommunicationsService {

    private static final Logger log = LoggerFactory.getLogger(CommunicationsService.class);
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public CommunicationsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @McpTool(description = "Send a templated notification to a customer. Supports order confirmation, shipment notice, delay alerts, and delivery confirmation templates.")
    public NotificationResult sendNotification(
        @McpToolParam(description = "Customer ID (e.g., CUST-001)") String customerId,
        @McpToolParam(description = "Template type: ORDER_CONFIRMATION, SHIPMENT_NOTICE, DELAY_ALERT, DELIVERY_CONFIRMATION") String templateType,
        @McpToolParam(description = "Template variables as JSON object (e.g., {\"order_id\": \"TM-2024-45892\", \"eta\": \"2024-08-01\"})") String variablesJson
    ) {
        log.info(">>> sendNotification called for customer: {}, template: {}", customerId, templateType);

        // Get customer info
        Map<String, Object> customer;
        try {
            customer = jdbcTemplate.queryForMap(
                "SELECT customer_id, name, contact_email FROM customers WHERE customer_id = ?",
                customerId);
        } catch (Exception e) {
            log.warn("Customer not found: {}", customerId);
            return new NotificationResult(null, customerId, "Unknown", templateType,
                null, null, false, null, "Customer not found: " + customerId);
        }

        String customerName = (String) customer.get("name");
        String contactEmail = (String) customer.get("contact_email");
        if (contactEmail == null) contactEmail = "orders@" + customerName.toLowerCase().replace(" ", "") + ".com";

        // Get template
        Map<String, Object> template;
        try {
            template = jdbcTemplate.queryForMap("""
                SELECT subject_template, body_template, variables
                FROM communication_templates
                WHERE template_id = ? AND is_active = TRUE
                """, templateType);
        } catch (Exception e) {
            log.warn("Template not found: {}", templateType);
            return new NotificationResult(null, customerId, customerName, templateType,
                null, contactEmail, false, null, "Template not found: " + templateType);
        }

        String subjectTemplate = (String) template.get("subject_template");
        String bodyTemplate = (String) template.get("body_template");

        // Parse and apply variables
        Map<String, String> variables = parseVariables(variablesJson);
        variables.put("customer_name", customerName);

        String subject = applyTemplate(subjectTemplate, variables);
        String body = applyTemplate(bodyTemplate, variables);

        // In production, this would actually send the email
        // For now, we simulate sending
        String notificationId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sentAt = LocalDateTime.now().format(DT_FORMAT);

        log.info("<<< sendNotification complete: {} sent to {}", notificationId, contactEmail);

        String summary = String.format("Notification %s sent to %s (%s). Template: %s. Subject: %s",
            notificationId, customerName, contactEmail, templateType, subject);

        return new NotificationResult(notificationId, customerId, customerName,
            templateType, subject, contactEmail, true, sentAt, summary);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseVariables(String json) {
        Map<String, String> vars = new java.util.HashMap<>();
        if (json == null || json.isBlank()) return vars;

        // Simple JSON parsing (in production, use Jackson)
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            for (String pair : json.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    vars.put(key, value);
                }
            }
        }
        return vars;
    }

    private String applyTemplate(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    @McpTool(description = "Process a customer inquiry and generate a context-aware response. Uses RAG to find similar past inquiries and gathers relevant order context.")
    public InquiryResult handleInquiry(
        @McpToolParam(description = "Customer ID (e.g., CUST-001)") String customerId,
        @McpToolParam(description = "The customer's inquiry text") String inquiryText,
        @McpToolParam(description = "Related order ID (optional, for order-specific inquiries)") String orderId
    ) {
        log.info(">>> handleInquiry called for customer: {}, order: {}", customerId, orderId);

        // Get customer info
        String customerName;
        try {
            customerName = jdbcTemplate.queryForObject(
                "SELECT name FROM customers WHERE customer_id = ?",
                String.class, customerId);
        } catch (Exception e) {
            customerName = "Unknown Customer";
        }

        // Classify inquiry type
        String inquiryType = classifyInquiry(inquiryText);

        // Find similar past inquiries using text search
        // (In production with embeddings, this would use pgvector similarity search)
        List<SimilarInquiry> similarInquiries = findSimilarInquiries(inquiryText, inquiryType);

        // Get order context if order ID provided
        String orderContext = null;
        if (orderId != null && !orderId.isBlank()) {
            orderContext = getOrderContext(orderId);
        }

        // Generate suggested response based on similar inquiries and order context
        String suggestedResponse = generateResponse(inquiryText, inquiryType, similarInquiries, orderContext, customerName);

        // Log the inquiry
        int inquiryId = jdbcTemplate.queryForObject("""
            INSERT INTO customer_inquiries (customer_id, order_id, inquiry_type, inquiry_text, status)
            VALUES (?, ?, ?, ?, 'IN_PROGRESS')
            RETURNING inquiry_id
            """, Integer.class, customerId, orderId, inquiryType, inquiryText);

        String summary = String.format(
            "Inquiry #%d from %s classified as %s. Found %d similar past inquiries. %s",
            inquiryId, customerName, inquiryType, similarInquiries.size(),
            orderContext != null ? "Order context included." : "No order context.");

        log.info("<<< handleInquiry complete: inquiry #{}", inquiryId);
        return new InquiryResult(inquiryId, customerId, customerName, orderId,
            inquiryType, inquiryText, suggestedResponse, similarInquiries,
            orderContext, "IN_PROGRESS", summary);
    }

    private String classifyInquiry(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("status") || lower.contains("where is") || lower.contains("tracking")) {
            return "STATUS";
        } else if (lower.contains("expedite") || lower.contains("urgent") || lower.contains("rush") || lower.contains("faster")) {
            return "EXPEDITE";
        } else if (lower.contains("quality") || lower.contains("defect") || lower.contains("damaged")) {
            return "QUALITY";
        } else if (lower.contains("complaint") || lower.contains("unhappy") || lower.contains("wrong")) {
            return "COMPLAINT";
        }
        return "GENERAL";
    }

    private List<SimilarInquiry> findSimilarInquiries(String inquiryText, String inquiryType) {
        // Text-based search for similar inquiries
        // In production with embeddings, this would use pgvector cosine similarity
        return jdbcTemplate.query("""
            SELECT inquiry_id, inquiry_type, inquiry_text, response_text, resolved_at
            FROM customer_inquiries
            WHERE status = 'RESOLVED'
              AND response_text IS NOT NULL
              AND (inquiry_type = ? OR inquiry_text ILIKE ?)
            ORDER BY resolved_at DESC
            LIMIT 3
            """, (rs, rowNum) -> new SimilarInquiry(
            rs.getInt("inquiry_id"),
            rs.getString("inquiry_type"),
            rs.getString("inquiry_text"),
            rs.getString("response_text"),
            0.85, // Simulated similarity score
            rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toString() : null
        ), inquiryType, "%" + inquiryText.split(" ")[0] + "%");
    }

    private String getOrderContext(String orderId) {
        try {
            Map<String, Object> order = jdbcTemplate.queryForMap("""
                SELECT o.order_id, o.status, o.order_date, o.required_date,
                       COUNT(ol.line_number) as line_count,
                       COALESCE(SUM(ol.quantity * ol.unit_price), 0) as total
                FROM orders o
                LEFT JOIN order_lines ol ON o.order_id = ol.order_id
                WHERE o.order_id = ?
                GROUP BY o.order_id, o.status, o.order_date, o.required_date
                """, orderId);

            return String.format(
                "Order %s: Status=%s, Ordered=%s, Required=%s, Lines=%d, Total=$%,.2f",
                orderId, order.get("status"), order.get("order_date"),
                order.get("required_date"), ((Number) order.get("line_count")).intValue(),
                ((Number) order.get("total")).doubleValue());
        } catch (Exception e) {
            return "Order " + orderId + " not found";
        }
    }

    private String generateResponse(String inquiry, String type, List<SimilarInquiry> similar,
                                    String orderContext, String customerName) {
        StringBuilder response = new StringBuilder();
        response.append("Dear ").append(customerName).append(",\n\n");
        response.append("Thank you for reaching out to Titan Manufacturing.\n\n");

        switch (type) {
            case "STATUS" -> {
                if (orderContext != null) {
                    response.append("Regarding your inquiry about order status:\n");
                    response.append(orderContext).append("\n\n");
                    response.append("Your order is progressing as expected. ");
                } else {
                    response.append("To check your order status, please provide your order number ");
                    response.append("and we'll be happy to provide detailed tracking information.\n");
                }
            }
            case "EXPEDITE" -> {
                response.append("We understand your need for expedited delivery. ");
                response.append("As a valued customer, we will review your request and ");
                response.append("contact you within 24 hours with available options ");
                response.append("and any applicable expedite charges.\n");
            }
            case "QUALITY" -> {
                response.append("We take quality concerns very seriously. ");
                response.append("A member of our Quality Assurance team will contact you ");
                response.append("within 24 hours to gather additional details and ");
                response.append("initiate our quality review process.\n");
            }
            case "COMPLAINT" -> {
                response.append("We apologize for any inconvenience you've experienced. ");
                response.append("Your feedback is important to us, and a customer service ");
                response.append("manager will personally reach out to you within 4 hours ");
                response.append("to address your concerns.\n");
            }
            default -> {
                response.append("We've received your inquiry and will respond ");
                response.append("within 1-2 business days. If your matter is urgent, ");
                response.append("please call our customer service line at 1-800-TITAN-MFG.\n");
            }
        }

        if (!similar.isEmpty()) {
            response.append("\nBased on similar inquiries, you may also find it helpful to know: ");
            response.append(similar.get(0).responseText().split("\\.")[0]).append(".\n");
        }

        response.append("\nBest regards,\nTitan Manufacturing Customer Service");
        return response.toString();
    }

    @McpTool(description = "Generate a draft customer update for a specific order. Creates a professional communication ready for review and approval before sending.")
    public DraftResult draftCustomerUpdate(
        @McpToolParam(description = "Order ID to create update for (e.g., TM-2024-45892)") String orderId,
        @McpToolParam(description = "Type of update: STATUS_UPDATE, DELAY_NOTICE, SHIPMENT_UPDATE, DELIVERY_CONFIRMATION") String updateType
    ) {
        log.info(">>> draftCustomerUpdate called for order: {}, type: {}", orderId, updateType);

        // Get order and customer info
        Map<String, Object> orderInfo;
        try {
            orderInfo = jdbcTemplate.queryForMap("""
                SELECT o.order_id, o.customer_id, c.name as customer_name, o.status,
                       o.order_date, o.required_date
                FROM orders o
                JOIN customers c ON o.customer_id = c.customer_id
                WHERE o.order_id = ?
                """, orderId);
        } catch (Exception e) {
            log.warn("Order not found: {}", orderId);
            return new DraftResult(orderId, null, null, updateType,
                null, null, "NOT_FOUND", null, "Order not found: " + orderId);
        }

        String customerId = (String) orderInfo.get("customer_id");
        String customerName = (String) orderInfo.get("customer_name");
        String status = (String) orderInfo.get("status");
        String requiredDate = orderInfo.get("required_date") != null ?
            orderInfo.get("required_date").toString() : "TBD";

        String subject;
        StringBuilder body = new StringBuilder();
        String recommendedAction;

        switch (updateType.toUpperCase()) {
            case "STATUS_UPDATE" -> {
                subject = "Update on Your Titan Order " + orderId;
                body.append("Dear ").append(customerName).append(",\n\n");
                body.append("We wanted to provide you with an update on your order ").append(orderId).append(".\n\n");
                body.append("Current Status: ").append(status).append("\n");
                body.append("Expected Delivery: ").append(requiredDate).append("\n\n");
                body.append("Your order is progressing as planned. We'll notify you when it ships.\n\n");
                body.append("Best regards,\nTitan Manufacturing");
                recommendedAction = "Review and send if status is accurate";
            }
            case "DELAY_NOTICE" -> {
                subject = "Important Update: Delay on Order " + orderId;
                body.append("Dear ").append(customerName).append(",\n\n");
                body.append("We regret to inform you that there has been a delay affecting your order ").append(orderId).append(".\n\n");
                body.append("Original Expected Date: ").append(requiredDate).append("\n");
                body.append("New Expected Date: [PLEASE SPECIFY]\n");
                body.append("Reason: [PLEASE SPECIFY]\n\n");
                body.append("We sincerely apologize for any inconvenience this may cause. ");
                body.append("Your account manager will follow up to discuss any adjustments needed.\n\n");
                body.append("Best regards,\nTitan Manufacturing Customer Service");
                recommendedAction = "Fill in delay reason and new date before sending";
            }
            case "SHIPMENT_UPDATE" -> {
                subject = "Your Titan Order " + orderId + " Has Shipped!";
                body.append("Dear ").append(customerName).append(",\n\n");
                body.append("Great news! Your order ").append(orderId).append(" has been shipped.\n\n");
                body.append("Tracking Number: [TRACKING NUMBER]\n");
                body.append("Carrier: [CARRIER]\n");
                body.append("Estimated Delivery: [ETA]\n\n");
                body.append("You can track your shipment using the link below:\n[TRACKING URL]\n\n");
                body.append("Thank you for your business!\n\n");
                body.append("Best regards,\nTitan Manufacturing");
                recommendedAction = "Add tracking details from shipment record before sending";
            }
            case "DELIVERY_CONFIRMATION" -> {
                subject = "Order " + orderId + " Delivered - Thank You!";
                body.append("Dear ").append(customerName).append(",\n\n");
                body.append("We're pleased to confirm that your order ").append(orderId).append(" has been delivered.\n\n");
                body.append("Delivered On: [DELIVERY DATE]\n");
                body.append("Signed By: [RECIPIENT]\n\n");
                body.append("We hope everything meets your expectations. If you have any questions ");
                body.append("or concerns about your order, please don't hesitate to contact us.\n\n");
                body.append("Thank you for choosing Titan Manufacturing!\n\n");
                body.append("Best regards,\nTitan Manufacturing");
                recommendedAction = "Verify delivery details and send as thank-you";
            }
            default -> {
                subject = "Update Regarding Order " + orderId;
                body.append("Dear ").append(customerName).append(",\n\n");
                body.append("[CUSTOM MESSAGE]\n\n");
                body.append("Best regards,\nTitan Manufacturing");
                recommendedAction = "Add custom message content";
            }
        }

        String summary = String.format(
            "Draft %s for order %s (%s) created. Status: %s. Action: %s",
            updateType, orderId, customerName, status, recommendedAction);

        log.info("<<< draftCustomerUpdate complete");
        return new DraftResult(orderId, customerId, customerName, updateType,
            subject, body.toString(), status, recommendedAction, summary);
    }
}
