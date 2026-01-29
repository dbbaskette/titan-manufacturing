package com.titan.communications.service;

import com.titan.communications.model.*;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Communications MCP Service
 *
 * Provides tools for customer notifications (real email via SMTP),
 * inquiry handling (LLM classification + response), and draft generation (LLM).
 */
@Service
public class CommunicationsService {

    private static final Logger log = LoggerFactory.getLogger(CommunicationsService.class);
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final JavaMailSender mailSender;
    private final ChatClient chatClient;

    @Value("${titan.admin-email:}")
    private String adminEmail;

    @Value("${titan.from-email:}")
    private String fromEmail;

    @Value("${spring.mail.username:}")
    private String smtpUser;

    public CommunicationsService(JdbcTemplate jdbcTemplate, JavaMailSender mailSender, ChatClient.Builder chatClientBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
        this.chatClient = chatClientBuilder.build();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // sendNotification — Real email via SMTP (with simulation fallback)
    // ═════════════════════════════════════════════════════════════════════════

    @McpTool(description = "Send a notification email. For customer notifications use a customer ID (e.g., CUST-BOEING). " +
            "For internal maintenance alerts, use a facility ID (e.g., ATL, PHX, DET) — the email will be sent to the plant admin. " +
            "Template types: ORDER_CONFIRMATION, SHIPMENT_NOTICE, DELAY_ALERT, DELIVERY_CONFIRMATION, MAINTENANCE_ALERT.")
    public NotificationResult sendNotification(
        @McpToolParam(description = "Customer ID (e.g., CUST-BOEING) or facility ID (e.g., ATL, PHX) for maintenance alerts") String recipientId,
        @McpToolParam(description = "Template type: ORDER_CONFIRMATION, SHIPMENT_NOTICE, DELAY_ALERT, DELIVERY_CONFIRMATION, MAINTENANCE_ALERT") String templateType,
        @McpToolParam(description = "Template variables as JSON object (e.g., {\"equipment_id\": \"ATL-CNC-001\", \"probable_cause\": \"Bearing degradation\"})") String variablesJson
    ) {
        log.info(">>> sendNotification called for recipient: {}, template: {}", recipientId, templateType);

        // If recipientId is null/empty, try to extract facility from variables for maintenance alerts
        if (recipientId == null || recipientId.isBlank()) {
            Map<String, String> vars = parseVariables(variablesJson);
            recipientId = vars.getOrDefault("facility_id",
                    vars.getOrDefault("facilityId", "UNKNOWN"));
            log.info("recipientId was null, extracted from variables: {}", recipientId);
        }

        String recipientName;
        String contactEmail;

        // Check if this is a facility ID (internal maintenance alert) or customer ID
        boolean isFacilityAlert = !recipientId.startsWith("CUST-");
        if (isFacilityAlert) {
            // Facility-based notification — send to admin email
            String facilityName = recipientId;
            try {
                Map<String, Object> facility = jdbcTemplate.queryForMap(
                    "SELECT name FROM titan_facilities WHERE facility_id = ?", recipientId);
                facilityName = (String) facility.get("name");
            } catch (Exception e) {
                log.debug("Facility {} not found in DB, using ID as name", recipientId);
            }
            recipientName = "Plant Manager — " + facilityName;
            contactEmail = (adminEmail != null && !adminEmail.isBlank()) ? adminEmail : fromEmail;
            if (contactEmail == null || contactEmail.isBlank()) {
                log.warn("No admin email configured for facility alerts");
                return new NotificationResult(null, recipientId, recipientName, templateType,
                    null, null, false, null, "No admin email configured (set TITAN_ADMIN_EMAIL)");
            }
        } else {
            // Customer notification
            Map<String, Object> customer;
            try {
                customer = jdbcTemplate.queryForMap(
                    "SELECT customer_id, name, contact_email FROM customers WHERE customer_id = ?",
                    recipientId);
            } catch (Exception e) {
                log.warn("Customer not found: {}", recipientId);
                return new NotificationResult(null, recipientId, "Unknown", templateType,
                    null, null, false, null, "Customer not found: " + recipientId);
            }
            recipientName = (String) customer.get("name");
            contactEmail = (String) customer.get("contact_email");
            if (contactEmail == null) contactEmail = "orders@" + recipientName.toLowerCase().replace(" ", "") + ".com";
        }

        // Parse variables early so we can use them for generated subjects/bodies
        Map<String, String> variables = parseVariables(variablesJson);
        variables.put("customer_name", recipientName);
        variables.put("recipient_name", recipientName);

        String subject;
        String body;

        // For MAINTENANCE_ALERT, generate subject/body from variables (no DB template needed)
        if ("MAINTENANCE_ALERT".equals(templateType) || "MAINTENANCE_SCHEDULING".equals(templateType)) {
            String equipmentId = variables.getOrDefault("equipment_id", "Unknown");
            String cause = variables.getOrDefault("probable_cause", "Unknown cause");
            String facilityId = variables.getOrDefault("facility_id", recipientId);
            String workOrderId = variables.getOrDefault("work_order_id", "");
            subject = "MAINTENANCE ALERT: " + equipmentId + " — " + cause;
            body = "Maintenance Alert\n\n"
                + "Equipment: " + equipmentId + "\n"
                + "Facility: " + facilityId + "\n"
                + "Probable Cause: " + cause + "\n"
                + (workOrderId.isEmpty() ? "" : "Work Order: " + workOrderId + "\n")
                + "\nThis alert was generated by the Titan Manufacturing AI platform.\n";
        } else {
            // Get template from DB
            Map<String, Object> template;
            try {
                template = jdbcTemplate.queryForMap("""
                    SELECT subject_template, body_template, variables
                    FROM communication_templates
                    WHERE template_id = ? AND is_active = TRUE
                    """, templateType);
            } catch (Exception e) {
                log.warn("Template not found: {}", templateType);
                return new NotificationResult(null, recipientId, recipientName, templateType,
                    null, contactEmail, false, null, "Template not found: " + templateType);
            }

            String subjectTemplate = (String) template.get("subject_template");
            String bodyTemplate = (String) template.get("body_template");
            subject = applyTemplate(subjectTemplate, variables);
            body = applyTemplate(bodyTemplate, variables);
        }

        // Determine "from" address: use communications_email from settings, fall back to env
        String senderEmail = getCommsEmailFromSettings();
        if (senderEmail == null || senderEmail.isBlank()) {
            senderEmail = fromEmail;
        }

        String notificationId = "NOTIF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sentAt = LocalDateTime.now().format(DT_FORMAT);
        boolean sent = false;

        // Send real email if SMTP is configured
        if (smtpUser != null && !smtpUser.isBlank()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                if (senderEmail != null && !senderEmail.isBlank()) {
                    helper.setFrom(senderEmail);
                }
                helper.setTo(contactEmail);
                helper.setSubject(subject);
                helper.setText(body, false);
                // BCC admin email
                if (adminEmail != null && !adminEmail.isBlank()) {
                    helper.setBcc(adminEmail);
                }
                mailSender.send(message);
                sent = true;
                log.info("Email sent to {} (BCC: {})", contactEmail, adminEmail);
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", contactEmail, e.getMessage());
                sent = false;
            }
        } else {
            log.warn("SMTP not configured (SMTP_USER is empty) — simulating email send");
            sent = true; // simulated success
        }

        log.info("<<< sendNotification complete: {} sent to {}", notificationId, contactEmail);

        String summary = String.format("Notification %s %s to %s (%s). Template: %s. Subject: %s",
            notificationId, sent ? "sent" : "FAILED", recipientName, contactEmail, templateType, subject);

        return new NotificationResult(notificationId, recipientId, recipientName,
            templateType, subject, contactEmail, sent, sentAt, summary);
    }

    private String getCommsEmailFromSettings() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT setting_value FROM app_settings WHERE setting_key = 'communications_email'",
                String.class);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseVariables(String json) {
        Map<String, String> vars = new java.util.HashMap<>();
        if (json == null || json.isBlank()) return vars;

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

    // ═════════════════════════════════════════════════════════════════════════
    // handleInquiry — LLM-based classification + response generation
    // ═════════════════════════════════════════════════════════════════════════

    @McpTool(description = "Process a customer inquiry and generate a context-aware response. Uses LLM to classify the inquiry and generate a professional response.")
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

        // LLM-based classification
        String inquiryType = classifyInquiry(inquiryText);

        // Find similar past inquiries using text search
        List<SimilarInquiry> similarInquiries = findSimilarInquiries(inquiryText, inquiryType);

        // Get order context if order ID provided
        String orderContext = null;
        if (orderId != null && !orderId.isBlank()) {
            orderContext = getOrderContext(orderId);
        }

        // LLM-generated response
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
        try {
            String result = chatClient.prompt()
                .user("""
                    Classify this customer inquiry into exactly one category: STATUS, EXPEDITE, QUALITY, COMPLAINT, GENERAL.
                    Inquiry: "%s"
                    Respond with only the category name, nothing else.
                    """.formatted(text))
                .call()
                .content();
            String classification = result.trim().toUpperCase();
            if (List.of("STATUS", "EXPEDITE", "QUALITY", "COMPLAINT", "GENERAL").contains(classification)) {
                log.info("LLM classified inquiry as: {}", classification);
                return classification;
            }
            log.warn("LLM returned unexpected classification '{}', defaulting to GENERAL", result);
            return "GENERAL";
        } catch (Exception e) {
            log.warn("LLM classification failed, falling back to keyword heuristics: {}", e.getMessage());
            return classifyInquiryFallback(text);
        }
    }

    private String classifyInquiryFallback(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("status") || lower.contains("where is") || lower.contains("tracking")) return "STATUS";
        if (lower.contains("expedite") || lower.contains("urgent") || lower.contains("rush")) return "EXPEDITE";
        if (lower.contains("quality") || lower.contains("defect") || lower.contains("damaged")) return "QUALITY";
        if (lower.contains("complaint") || lower.contains("unhappy") || lower.contains("wrong")) return "COMPLAINT";
        return "GENERAL";
    }

    private List<SimilarInquiry> findSimilarInquiries(String inquiryText, String inquiryType) {
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
            0.0, // no real similarity score without pgvector
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
        String similarSummary = similar.isEmpty() ? "None" :
            similar.stream()
                .map(s -> "- Type: %s, Inquiry: \"%s\", Response: \"%s\"".formatted(
                    s.inquiryType(), s.inquiryText(), s.responseText()))
                .collect(Collectors.joining("\n"));

        try {
            return chatClient.prompt()
                .user("""
                    You are a customer service representative at Titan Manufacturing, a precision CNC parts manufacturer.
                    Draft a professional response to this customer inquiry.

                    Customer: %s
                    Inquiry type: %s
                    Inquiry: "%s"
                    Order context: %s
                    Similar past inquiries and responses:
                    %s

                    Write a concise, helpful response addressed to the customer. Sign off as "Titan Manufacturing Customer Service".
                    """.formatted(customerName, type, inquiry,
                        orderContext != null ? orderContext : "N/A",
                        similarSummary))
                .call()
                .content();
        } catch (Exception e) {
            log.warn("LLM response generation failed, using fallback: {}", e.getMessage());
            return generateResponseFallback(inquiry, type, similar, orderContext, customerName);
        }
    }

    private String generateResponseFallback(String inquiry, String type, List<SimilarInquiry> similar,
                                            String orderContext, String customerName) {
        StringBuilder response = new StringBuilder();
        response.append("Dear ").append(customerName).append(",\n\n");
        response.append("Thank you for reaching out to Titan Manufacturing.\n\n");

        switch (type) {
            case "STATUS" -> {
                if (orderContext != null) {
                    response.append("Regarding your inquiry about order status:\n");
                    response.append(orderContext).append("\n\n");
                } else {
                    response.append("To check your order status, please provide your order number.\n");
                }
            }
            case "EXPEDITE" -> response.append("We understand your need for expedited delivery and will review your request within 24 hours.\n");
            case "QUALITY" -> response.append("We take quality concerns very seriously. Our QA team will contact you within 24 hours.\n");
            case "COMPLAINT" -> response.append("We apologize for any inconvenience. A service manager will reach out within 4 hours.\n");
            default -> response.append("We've received your inquiry and will respond within 1-2 business days.\n");
        }

        response.append("\nBest regards,\nTitan Manufacturing Customer Service");
        return response.toString();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // draftCustomerUpdate — LLM-generated drafts with real order data
    // ═════════════════════════════════════════════════════════════════════════

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
        String orderDate = orderInfo.get("order_date") != null ?
            orderInfo.get("order_date").toString() : "N/A";

        // Get order line items
        String lineItemsSummary = getOrderLinesSummary(orderId);

        // Get shipment info if available
        String shipmentInfo = getShipmentInfo(orderId);

        // Generate draft via LLM
        String subject;
        String body;
        String recommendedAction;

        try {
            String llmResponse = chatClient.prompt()
                .user("""
                    You are drafting a customer communication for Titan Manufacturing, a precision CNC parts manufacturer.

                    Update type: %s
                    Customer: %s
                    Order: %s, Status: %s, Ordered: %s, Required by: %s
                    Line items: %s
                    Shipment info: %s

                    Write a professional email for this %s. Return the response in this exact format:
                    SUBJECT: <subject line>
                    BODY:
                    <email body>

                    Do NOT use placeholders like [PLEASE SPECIFY] — use the real data provided.
                    If specific data is unavailable, phrase it naturally (e.g., "tracking details will follow shortly").
                    Sign off as "Titan Manufacturing".
                    """.formatted(updateType, customerName, orderId, status, orderDate,
                        requiredDate, lineItemsSummary, shipmentInfo, updateType))
                .call()
                .content();

            // Parse subject and body from LLM response
            String[] parsed = parseLlmDraft(llmResponse, orderId, updateType);
            subject = parsed[0];
            body = parsed[1];
            recommendedAction = "Review LLM-generated draft and send";
        } catch (Exception e) {
            log.warn("LLM draft generation failed, using fallback: {}", e.getMessage());
            subject = updateType.replace("_", " ") + " — Order " + orderId;
            body = generateDraftFallback(updateType, customerName, orderId, status, requiredDate);
            recommendedAction = "LLM unavailable — review template draft and customize before sending";
        }

        String summary = String.format(
            "Draft %s for order %s (%s) created. Status: %s. Action: %s",
            updateType, orderId, customerName, status, recommendedAction);

        log.info("<<< draftCustomerUpdate complete");
        return new DraftResult(orderId, customerId, customerName, updateType,
            subject, body, status, recommendedAction, summary);
    }

    private String getOrderLinesSummary(String orderId) {
        try {
            List<Map<String, Object>> lines = jdbcTemplate.queryForList("""
                SELECT ol.line_number, p.name as product_name, ol.quantity, ol.unit_price
                FROM order_lines ol
                JOIN products p ON ol.sku = p.sku
                WHERE ol.order_id = ?
                ORDER BY ol.line_number
                """, orderId);
            if (lines.isEmpty()) return "No line items";
            return lines.stream()
                .map(l -> "%s x%d ($%,.2f each)".formatted(
                    l.get("product_name"),
                    ((Number) l.get("quantity")).intValue(),
                    ((Number) l.get("unit_price")).doubleValue()))
                .collect(Collectors.joining(", "));
        } catch (Exception e) {
            return "Unable to retrieve line items";
        }
    }

    private String getShipmentInfo(String orderId) {
        try {
            List<Map<String, Object>> shipments = jdbcTemplate.queryForList("""
                SELECT s.shipment_id, s.tracking_number, c.name as carrier_name,
                       s.status, s.ship_date, s.estimated_delivery
                FROM shipments s
                JOIN carriers c ON s.carrier_id = c.carrier_id
                WHERE s.order_id = ?
                ORDER BY s.ship_date DESC
                """, orderId);
            if (shipments.isEmpty()) return "No shipment yet";
            return shipments.stream()
                .map(s -> "Shipment %s via %s (tracking: %s, status: %s, ETA: %s)".formatted(
                    s.get("shipment_id"), s.get("carrier_name"),
                    s.get("tracking_number"), s.get("status"),
                    s.get("estimated_delivery") != null ? s.get("estimated_delivery").toString() : "TBD"))
                .collect(Collectors.joining("; "));
        } catch (Exception e) {
            return "No shipment data available";
        }
    }

    private String[] parseLlmDraft(String llmResponse, String orderId, String updateType) {
        String subject = updateType.replace("_", " ") + " — Order " + orderId;
        String body = llmResponse;

        if (llmResponse.contains("SUBJECT:")) {
            int subjectStart = llmResponse.indexOf("SUBJECT:") + "SUBJECT:".length();
            int bodyStart = llmResponse.indexOf("BODY:");
            if (bodyStart > subjectStart) {
                subject = llmResponse.substring(subjectStart, bodyStart).trim();
                body = llmResponse.substring(bodyStart + "BODY:".length()).trim();
            }
        }
        return new String[]{subject, body};
    }

    private String generateDraftFallback(String updateType, String customerName,
                                         String orderId, String status, String requiredDate) {
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(customerName).append(",\n\n");
        switch (updateType.toUpperCase()) {
            case "STATUS_UPDATE" -> {
                body.append("We wanted to provide you with an update on your order ").append(orderId).append(".\n\n");
                body.append("Current Status: ").append(status).append("\n");
                body.append("Expected Delivery: ").append(requiredDate).append("\n\n");
                body.append("Your order is progressing as planned.\n");
            }
            case "DELAY_NOTICE" -> {
                body.append("We regret to inform you that there has been a delay affecting your order ").append(orderId).append(".\n\n");
                body.append("Original Expected Date: ").append(requiredDate).append("\n");
                body.append("We are working to minimize the delay and will update you with a revised timeline shortly.\n");
            }
            case "SHIPMENT_UPDATE" -> {
                body.append("Your order ").append(orderId).append(" has been shipped.\n\n");
                body.append("Tracking details will follow shortly.\n");
            }
            case "DELIVERY_CONFIRMATION" -> {
                body.append("We're pleased to confirm that your order ").append(orderId).append(" has been delivered.\n\n");
                body.append("We hope everything meets your expectations.\n");
            }
            default -> body.append("This is regarding your order ").append(orderId).append(".\n");
        }
        body.append("\nBest regards,\nTitan Manufacturing");
        return body.toString();
    }
}
