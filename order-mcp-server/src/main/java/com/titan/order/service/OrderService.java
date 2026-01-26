package com.titan.order.service;

import com.titan.order.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order MCP Service
 *
 * Provides tools for order validation, fulfillment orchestration, and status tracking.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final DateTimeFormatter DT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public OrderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @McpTool(description = "Validate an order against inventory availability, customer credit limits, and contract terms. Returns detailed validation results with any issues found.")
    public ValidationResult validateOrder(
        @McpToolParam(description = "Order ID to validate (e.g., TM-2024-45892)") String orderId
    ) {
        log.info(">>> validateOrder called for order: {}", orderId);

        // Get order and customer info
        Map<String, Object> order;
        try {
            order = jdbcTemplate.queryForMap("""
                SELECT o.order_id, o.customer_id, c.name as customer_name, o.status, o.order_date,
                       COALESCE(SUM(ol.quantity * ol.unit_price), 0) as order_total
                FROM orders o
                JOIN customers c ON o.customer_id = c.customer_id
                LEFT JOIN order_lines ol ON o.order_id = ol.order_id
                WHERE o.order_id = ?
                GROUP BY o.order_id, o.customer_id, c.name, o.status, o.order_date
                """, orderId);
        } catch (Exception e) {
            log.warn("Order not found: {}", orderId);
            return new ValidationResult(orderId, null, null, false, false, false, false,
                List.of(new ValidationIssue("OTHER", "ERROR", null, "Order not found: " + orderId)),
                BigDecimal.ZERO, BigDecimal.ZERO, null, 0, "Order " + orderId + " not found");
        }

        String customerId = (String) order.get("customer_id");
        String customerName = (String) order.get("customer_name");
        BigDecimal orderTotal = (BigDecimal) order.get("order_total");

        List<ValidationIssue> issues = new ArrayList<>();

        // Check inventory availability
        boolean inventoryAvailable = checkInventoryAvailability(orderId, issues);

        // Check contract and credit
        ContractInfo contractInfo = checkContractAndCredit(customerId, orderTotal, issues);

        boolean isValid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));

        String summary = isValid ?
            String.format("Order %s validated successfully. Total: $%,.2f. Customer: %s (%s account, priority %d)",
                orderId, orderTotal, customerName, contractInfo.contractType, contractInfo.priorityLevel) :
            String.format("Order %s has %d validation errors. Issues: %s",
                orderId, issues.stream().filter(i -> "ERROR".equals(i.severity())).count(),
                issues.stream().filter(i -> "ERROR".equals(i.severity())).map(ValidationIssue::message).toList());

        log.info("<<< validateOrder complete: valid={}", isValid);
        return new ValidationResult(
            orderId, customerId, customerName, isValid,
            inventoryAvailable, contractInfo.creditApproved, contractInfo.contractValid,
            issues, orderTotal, contractInfo.availableCredit,
            contractInfo.contractType, contractInfo.priorityLevel, summary
        );
    }

    private boolean checkInventoryAvailability(String orderId, List<ValidationIssue> issues) {
        List<Map<String, Object>> lines = jdbcTemplate.queryForList("""
            SELECT ol.sku, p.name, ol.quantity,
                   COALESCE(SUM(sl.quantity), 0) as available
            FROM order_lines ol
            JOIN products p ON ol.sku = p.sku
            LEFT JOIN stock_levels sl ON ol.sku = sl.sku
            WHERE ol.order_id = ?
            GROUP BY ol.sku, p.name, ol.quantity
            """, orderId);

        boolean allAvailable = true;
        for (Map<String, Object> line : lines) {
            String sku = (String) line.get("sku");
            int ordered = ((Number) line.get("quantity")).intValue();
            int available = ((Number) line.get("available")).intValue();

            if (available < ordered) {
                allAvailable = false;
                issues.add(new ValidationIssue("INVENTORY", "ERROR", sku,
                    String.format("Insufficient stock for %s: need %d, available %d", sku, ordered, available)));
            } else if (available < ordered * 1.2) {
                issues.add(new ValidationIssue("INVENTORY", "WARNING", sku,
                    String.format("Low stock warning for %s: %d available after order", sku, available - ordered)));
            }
        }
        return allAvailable;
    }

    private record ContractInfo(boolean contractValid, boolean creditApproved, String contractType,
                                int priorityLevel, BigDecimal availableCredit) {}

    private ContractInfo checkContractAndCredit(String customerId, BigDecimal orderTotal, List<ValidationIssue> issues) {
        try {
            Map<String, Object> contract = jdbcTemplate.queryForMap("""
                SELECT contract_id, contract_type, priority_level, credit_limit, valid_from, valid_to
                FROM customer_contracts
                WHERE customer_id = ? AND valid_from <= CURRENT_DATE AND valid_to >= CURRENT_DATE
                ORDER BY priority_level
                LIMIT 1
                """, customerId);

            String contractType = (String) contract.get("contract_type");
            int priorityLevel = ((Number) contract.get("priority_level")).intValue();
            BigDecimal creditLimit = (BigDecimal) contract.get("credit_limit");

            // Calculate current outstanding balance (simplified)
            BigDecimal outstanding = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ol.quantity * ol.unit_price), 0)
                FROM orders o
                JOIN order_lines ol ON o.order_id = ol.order_id
                WHERE o.customer_id = ? AND o.status IN ('PENDING', 'PROCESSING', 'SHIPPED')
                """, BigDecimal.class, customerId);

            BigDecimal availableCredit = creditLimit.subtract(outstanding != null ? outstanding : BigDecimal.ZERO);
            boolean creditApproved = availableCredit.compareTo(orderTotal) >= 0;

            if (!creditApproved) {
                issues.add(new ValidationIssue("CREDIT", "ERROR", null,
                    String.format("Insufficient credit: order $%,.2f, available $%,.2f", orderTotal, availableCredit)));
            }

            return new ContractInfo(true, creditApproved, contractType, priorityLevel, availableCredit);
        } catch (Exception e) {
            issues.add(new ValidationIssue("CONTRACT", "WARNING", null,
                "No active contract found for customer - using standard terms"));
            return new ContractInfo(false, true, "STANDARD", 5, BigDecimal.valueOf(100000));
        }
    }

    @McpTool(description = "Get customer contract terms including priority level, discount percentage, payment terms, and credit limits.")
    public ContractTerms checkContractTerms(
        @McpToolParam(description = "Customer ID (e.g., CUST-001)") String customerId
    ) {
        log.info(">>> checkContractTerms called for customer: {}", customerId);

        // Get customer info
        String customerName;
        try {
            customerName = jdbcTemplate.queryForObject(
                "SELECT name FROM customers WHERE customer_id = ?",
                String.class, customerId);
        } catch (Exception e) {
            log.warn("Customer not found: {}", customerId);
            return new ContractTerms(null, customerId, "Unknown", "NONE", 0,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, false, "Customer not found: " + customerId);
        }

        try {
            Map<String, Object> contract = jdbcTemplate.queryForMap("""
                SELECT contract_id, contract_type, priority_level, discount_percent,
                       payment_terms, credit_limit, valid_from, valid_to
                FROM customer_contracts
                WHERE customer_id = ? AND valid_from <= CURRENT_DATE AND valid_to >= CURRENT_DATE
                ORDER BY priority_level
                LIMIT 1
                """, customerId);

            String contractId = (String) contract.get("contract_id");
            String contractType = (String) contract.get("contract_type");
            int priorityLevel = ((Number) contract.get("priority_level")).intValue();
            BigDecimal discountPercent = (BigDecimal) contract.get("discount_percent");
            int paymentTerms = ((Number) contract.get("payment_terms")).intValue();
            BigDecimal creditLimit = (BigDecimal) contract.get("credit_limit");
            String validFrom = contract.get("valid_from").toString();
            String validTo = contract.get("valid_to").toString();

            // Calculate current balance
            BigDecimal currentBalance = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(ol.quantity * ol.unit_price), 0)
                FROM orders o
                JOIN order_lines ol ON o.order_id = ol.order_id
                WHERE o.customer_id = ? AND o.status IN ('PENDING', 'PROCESSING', 'SHIPPED')
                """, BigDecimal.class, customerId);
            if (currentBalance == null) currentBalance = BigDecimal.ZERO;

            BigDecimal availableCredit = creditLimit.subtract(currentBalance);

            String summary = String.format(
                "%s (%s): %s account with priority %d. %s%% discount, Net %d terms. " +
                "Credit: $%,.2f available of $%,.2f limit.",
                customerName, customerId, contractType, priorityLevel,
                discountPercent, paymentTerms, availableCredit, creditLimit);

            log.info("<<< checkContractTerms complete for {}", customerId);
            return new ContractTerms(contractId, customerId, customerName, contractType,
                priorityLevel, discountPercent, paymentTerms, creditLimit,
                currentBalance, availableCredit, validFrom, validTo, true, summary);

        } catch (Exception e) {
            String summary = String.format("%s (%s): No active contract. Standard terms apply.",
                customerName, customerId);
            return new ContractTerms(null, customerId, customerName, "STANDARD", 5,
                BigDecimal.ZERO, 30, BigDecimal.valueOf(50000), BigDecimal.ZERO,
                BigDecimal.valueOf(50000), null, null, false, summary);
        }
    }

    @McpTool(description = "Initiate fulfillment for an order. Reserves inventory, plans shipments, and creates order events. Supports expedited processing for priority customers.")
    public FulfillmentResult initiateFulfillment(
        @McpToolParam(description = "Order ID to fulfill (e.g., TM-2024-45892)") String orderId,
        @McpToolParam(description = "Set to true for expedited processing (faster shipping, priority handling)") Boolean expedite
    ) {
        log.info(">>> initiateFulfillment called for order: {}, expedite: {}", orderId, expedite);

        boolean isExpedited = expedite != null && expedite;

        // Get order info
        Map<String, Object> order;
        try {
            order = jdbcTemplate.queryForMap("""
                SELECT o.order_id, o.customer_id, c.name as customer_name, o.required_date
                FROM orders o
                JOIN customers c ON o.customer_id = c.customer_id
                WHERE o.order_id = ?
                """, orderId);
        } catch (Exception e) {
            log.warn("Order not found: {}", orderId);
            return new FulfillmentResult(orderId, null, false, "FAILED",
                List.of(), List.of(), false, null, BigDecimal.ZERO,
                "Order not found: " + orderId);
        }

        String customerId = (String) order.get("customer_id");

        // Get order lines and plan allocations
        List<Map<String, Object>> lines = jdbcTemplate.queryForList("""
            SELECT ol.sku, p.name, ol.quantity, ol.unit_price
            FROM order_lines ol
            JOIN products p ON ol.sku = p.sku
            WHERE ol.order_id = ?
            """, orderId);

        List<AllocationDetail> allocations = new ArrayList<>();
        List<PlannedShipment> shipments = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        // Group allocations by facility for shipment planning
        Map<String, List<AllocationDetail>> facilityAllocations = new java.util.HashMap<>();

        for (Map<String, Object> line : lines) {
            String sku = (String) line.get("sku");
            String productName = (String) line.get("name");
            int quantityNeeded = ((Number) line.get("quantity")).intValue();
            BigDecimal unitPrice = (BigDecimal) line.get("unit_price");
            totalCost = totalCost.add(unitPrice.multiply(BigDecimal.valueOf(quantityNeeded)));

            // Find best facility to allocate from
            List<Map<String, Object>> stockByFacility = jdbcTemplate.queryForList("""
                SELECT sl.facility_id, sl.quantity
                FROM stock_levels sl
                WHERE sl.sku = ? AND sl.quantity > 0
                ORDER BY sl.quantity DESC
                """, sku);

            int remaining = quantityNeeded;
            for (Map<String, Object> stock : stockByFacility) {
                if (remaining <= 0) break;

                String facilityId = (String) stock.get("facility_id");
                int available = ((Number) stock.get("quantity")).intValue();
                int toAllocate = Math.min(remaining, available);

                // Check for material batch
                String batchId = null;
                try {
                    batchId = jdbcTemplate.queryForObject("""
                        SELECT batch_id FROM material_batches
                        WHERE material_sku = ? AND status = 'AVAILABLE'
                        AND storage_location LIKE ?
                        LIMIT 1
                        """, String.class, sku, facilityId + "%");
                } catch (Exception ignored) {}

                AllocationDetail allocation = new AllocationDetail(
                    sku, productName, quantityNeeded, toAllocate,
                    facilityId, batchId,
                    toAllocate >= quantityNeeded ? "ALLOCATED" : "PARTIAL"
                );
                allocations.add(allocation);

                facilityAllocations.computeIfAbsent(facilityId, k -> new ArrayList<>()).add(allocation);
                remaining -= toAllocate;
            }

            if (remaining > 0) {
                allocations.add(new AllocationDetail(sku, productName, quantityNeeded, 0,
                    null, null, "BACKORDERED"));
            }
        }

        // Plan shipments from each facility
        String carrier = isExpedited ? "FedEx" : "UPS";
        String serviceLevel = isExpedited ? "EXPRESS" : "GROUND";
        int transitDays = isExpedited ? 2 : 5;

        for (Map.Entry<String, List<AllocationDetail>> entry : facilityAllocations.entrySet()) {
            String facilityId = entry.getKey();
            int itemCount = entry.getValue().stream().mapToInt(AllocationDetail::quantityAllocated).sum();

            String shipmentId = "SHIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            LocalDate shipDate = LocalDate.now().plusDays(isExpedited ? 1 : 2);
            LocalDate deliveryDate = shipDate.plusDays(transitDays);

            BigDecimal shippingCost = BigDecimal.valueOf(isExpedited ? 250 : 75);

            shipments.add(new PlannedShipment(
                shipmentId, facilityId, carrier, serviceLevel,
                shipDate.toString(), deliveryDate.toString(),
                itemCount, shippingCost
            ));
        }

        // Record fulfillment event
        String eventData = String.format(
            "{\"expedited\": %s, \"allocations\": %d, \"shipments\": %d}",
            isExpedited, allocations.size(), shipments.size());

        jdbcTemplate.update("""
            INSERT INTO order_events (order_id, event_type, event_data, created_by, notes)
            VALUES (?, 'FULFILLMENT_INITIATED', ?::jsonb, 'ORDER-SYSTEM', ?)
            """, orderId, eventData,
            isExpedited ? "Expedited fulfillment initiated" : "Standard fulfillment initiated");

        // Update order status
        jdbcTemplate.update("UPDATE orders SET status = 'PROCESSING' WHERE order_id = ?", orderId);

        String estimatedDelivery = shipments.isEmpty() ? "TBD" :
            shipments.stream().map(PlannedShipment::estimatedDeliveryDate).max(String::compareTo).orElse("TBD");

        boolean success = allocations.stream().noneMatch(a -> "BACKORDERED".equals(a.allocationStatus()));

        String summary = success ?
            String.format("Fulfillment initiated for %s. %d items allocated across %d shipments. " +
                "Estimated delivery: %s. %s",
                orderId, allocations.size(), shipments.size(), estimatedDelivery,
                isExpedited ? "EXPEDITED processing." : "Standard processing.") :
            String.format("Partial fulfillment for %s. Some items backordered.", orderId);

        log.info("<<< initiateFulfillment complete: success={}", success);
        return new FulfillmentResult(orderId, customerId, success,
            success ? "ALLOCATED" : "PARTIAL", allocations, shipments,
            isExpedited, estimatedDelivery, totalCost, summary);
    }

    @McpTool(description = "Get complete order status including current state, order lines, event timeline, and shipment tracking.")
    public OrderStatusResult getOrderStatus(
        @McpToolParam(description = "Order ID to check (e.g., TM-2024-45892)") String orderId
    ) {
        log.info(">>> getOrderStatus called for order: {}", orderId);

        // Get order info
        Map<String, Object> order;
        try {
            order = jdbcTemplate.queryForMap("""
                SELECT o.order_id, o.customer_id, c.name as customer_name,
                       o.order_date, o.required_date, o.status
                FROM orders o
                JOIN customers c ON o.customer_id = c.customer_id
                WHERE o.order_id = ?
                """, orderId);
        } catch (Exception e) {
            log.warn("Order not found: {}", orderId);
            return new OrderStatusResult(orderId, null, null, null, null,
                "NOT_FOUND", BigDecimal.ZERO, 0, List.of(), List.of(), List.of(),
                "Order not found: " + orderId);
        }

        String customerId = (String) order.get("customer_id");
        String customerName = (String) order.get("customer_name");
        String orderDate = order.get("order_date") != null ? order.get("order_date").toString() : null;
        String requiredDate = order.get("required_date") != null ? order.get("required_date").toString() : null;
        String status = (String) order.get("status");

        // Get order lines
        List<OrderLineStatus> lines = jdbcTemplate.query("""
            SELECT ol.line_number, ol.sku, p.name, ol.quantity, ol.unit_price,
                   COALESCE(ol.quantity_shipped, 0) as qty_shipped
            FROM order_lines ol
            JOIN products p ON ol.sku = p.sku
            WHERE ol.order_id = ?
            ORDER BY ol.line_number
            """, (rs, rowNum) -> {
            int ordered = rs.getInt("quantity");
            int shipped = rs.getInt("qty_shipped");
            BigDecimal unitPrice = rs.getBigDecimal("unit_price");
            String lineStatus = shipped >= ordered ? "SHIPPED" : (shipped > 0 ? "PARTIAL" : "PENDING");

            return new OrderLineStatus(
                rs.getInt("line_number"),
                rs.getString("sku"),
                rs.getString("name"),
                ordered, shipped, unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(ordered)),
                lineStatus
            );
        }, orderId);

        BigDecimal orderTotal = lines.stream()
            .map(OrderLineStatus::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get order events
        List<OrderEvent> events = jdbcTemplate.query("""
            SELECT event_type, event_timestamp, created_by, notes
            FROM order_events
            WHERE order_id = ?
            ORDER BY event_timestamp
            """, (rs, rowNum) -> new OrderEvent(
            rs.getString("event_type"),
            rs.getTimestamp("event_timestamp").toString(),
            rs.getString("created_by"),
            rs.getString("notes")
        ), orderId);

        // Get shipments
        List<ShipmentStatus> shipments = jdbcTemplate.query("""
            SELECT s.shipment_id, c.name as carrier_name, s.tracking_number,
                   s.status, s.ship_date, s.delivery_date, c.tracking_url_template
            FROM shipments s
            JOIN carriers c ON s.carrier_id = c.carrier_id
            WHERE s.order_id = ?
            """, (rs, rowNum) -> {
            String trackingNum = rs.getString("tracking_number");
            String urlTemplate = rs.getString("tracking_url_template");
            String trackingUrl = urlTemplate != null && trackingNum != null ?
                urlTemplate.replace("{tracking_number}", trackingNum) : null;

            return new ShipmentStatus(
                rs.getString("shipment_id"),
                rs.getString("carrier_name"),
                trackingNum,
                rs.getString("status"),
                rs.getDate("ship_date") != null ? rs.getDate("ship_date").toString() : null,
                rs.getDate("delivery_date") != null ? rs.getDate("delivery_date").toString() : null,
                trackingUrl
            );
        }, orderId);

        String summary = String.format(
            "Order %s for %s: Status %s. %d lines, total $%,.2f. %d events, %d shipments.",
            orderId, customerName, status, lines.size(), orderTotal,
            events.size(), shipments.size());

        log.info("<<< getOrderStatus complete");
        return new OrderStatusResult(orderId, customerId, customerName,
            orderDate, requiredDate, status, orderTotal, lines.size(),
            lines, events, shipments, summary);
    }
}
