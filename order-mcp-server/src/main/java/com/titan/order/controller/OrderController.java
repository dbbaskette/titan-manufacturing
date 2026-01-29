package com.titan.order.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * REST API for Order Dashboard — serves OrderTracker UI.
 */
@RestController
@RequestMapping("/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final JdbcTemplate jdbcTemplate;

    public OrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get all orders with customer info.
     */
    @GetMapping
    public List<Map<String, Object>> getOrders() {
        log.info("Fetching all orders");
        return jdbcTemplate.queryForList("""
            SELECT o.order_id, o.customer_id, c.name as customer_name, c.tier,
                   o.order_date, o.required_date, o.status, o.priority, o.total_amount,
                   o.shipping_address, o.notes,
                   (SELECT COUNT(*) FROM order_lines ol WHERE ol.order_id = o.order_id) as line_count
            FROM orders o
            JOIN customers c ON o.customer_id = c.customer_id
            ORDER BY o.order_date DESC
            """);
    }

    /**
     * Get order status counts for summary cards.
     */
    @GetMapping("/counts")
    public Map<String, Object> getOrderCounts() {
        log.info("Fetching order counts");

        // Get counts by status
        List<Map<String, Object>> statusCounts = jdbcTemplate.queryForList("""
            SELECT
                LOWER(status) as status,
                COUNT(*) as count
            FROM orders
            GROUP BY status
            """);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("pending", 0);
        counts.put("validated", 0);
        counts.put("processing", 0);
        counts.put("shipped", 0);
        counts.put("delivered", 0);

        for (Map<String, Object> row : statusCounts) {
            String status = ((String) row.get("status")).toLowerCase();
            int count = ((Number) row.get("count")).intValue();

            // Map DB statuses to UI statuses
            switch (status) {
                case "pending" -> counts.merge("pending", count, Integer::sum);
                case "confirmed", "validated", "in_progress" -> counts.merge("processing", count, Integer::sum);
                case "expedite" -> counts.merge("processing", count, Integer::sum);
                case "shipped" -> counts.merge("shipped", count, Integer::sum);
                case "delivered" -> counts.merge("delivered", count, Integer::sum);
                default -> counts.merge("pending", count, Integer::sum);
            }
        }

        // Get total value
        BigDecimal totalValue = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status NOT IN ('DELIVERED', 'CANCELLED')",
            BigDecimal.class);

        return Map.of(
            "counts", counts,
            "totalOrders", statusCounts.stream().mapToInt(r -> ((Number) r.get("count")).intValue()).sum(),
            "totalActiveValue", totalValue != null ? totalValue : BigDecimal.ZERO
        );
    }

    /**
     * Get order details including lines, events, shipments, and contract info.
     */
    @GetMapping("/{orderId}")
    public Map<String, Object> getOrderDetails(@PathVariable String orderId) {
        log.info("Fetching details for order: {}", orderId);

        // Get order with customer info
        Map<String, Object> order;
        try {
            order = jdbcTemplate.queryForMap("""
                SELECT o.order_id, o.customer_id, c.name as customer_name, c.tier,
                       o.order_date, o.required_date, o.status, o.priority, o.total_amount,
                       o.shipping_address, o.notes
                FROM orders o
                JOIN customers c ON o.customer_id = c.customer_id
                WHERE o.order_id = ?
                """, orderId);
        } catch (Exception e) {
            return Map.of("error", "Order not found: " + orderId);
        }

        // Get order lines with product info
        List<Map<String, Object>> lines = jdbcTemplate.queryForList("""
            SELECT ol.line_id, ol.sku, p.name as product_name, p.category,
                   ol.quantity, ol.unit_price, ol.line_total,
                   0 as qty_shipped
            FROM order_lines ol
            JOIN products p ON ol.sku = p.sku
            WHERE ol.order_id = ?
            ORDER BY ol.line_id
            """, orderId);

        // Get order events
        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
            SELECT event_id, event_type, event_timestamp, event_data, created_by, notes
            FROM order_events
            WHERE order_id = ?
            ORDER BY event_timestamp DESC
            """, orderId);

        // Get shipments
        List<Map<String, Object>> shipments = getOrderShipments(orderId);

        // Get customer contract
        Map<String, Object> contract = getCustomerContract((String) order.get("customer_id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(order);
        result.put("lines", lines);
        result.put("events", events);
        result.put("shipments", shipments);
        result.put("contract", contract);

        return result;
    }

    private List<Map<String, Object>> getOrderShipments(String orderId) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT s.shipment_id, s.tracking_number, s.status,
                       s.ship_date, s.delivery_date, s.origin_facility,
                       c.name as carrier_name, c.service_type, c.tracking_url_template
                FROM shipments s
                JOIN carriers c ON s.carrier_id = c.carrier_id
                WHERE s.order_id = ?
                ORDER BY s.ship_date
                """, orderId);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> getCustomerContract(String customerId) {
        try {
            return jdbcTemplate.queryForMap("""
                SELECT contract_id, contract_type, priority_level, discount_percent,
                       payment_terms, credit_limit, valid_from, valid_to
                FROM customer_contracts
                WHERE customer_id = ? AND valid_from <= CURRENT_DATE AND valid_to >= CURRENT_DATE
                ORDER BY priority_level
                LIMIT 1
                """, customerId);
        } catch (Exception e) {
            return Map.of(
                "contract_type", "STANDARD",
                "priority_level", 5,
                "discount_percent", 0,
                "payment_terms", 30
            );
        }
    }

    /**
     * Get order events timeline for a specific order.
     */
    @GetMapping("/{orderId}/events")
    public List<Map<String, Object>> getOrderEvents(@PathVariable String orderId) {
        log.info("Fetching events for order: {}", orderId);
        return jdbcTemplate.queryForList("""
            SELECT event_id, event_type, event_timestamp, event_data, created_by, notes
            FROM order_events
            WHERE order_id = ?
            ORDER BY event_timestamp
            """, orderId);
    }

    /**
     * Add an event to an order.
     */
    @PostMapping("/{orderId}/events")
    public Map<String, Object> addOrderEvent(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {
        String eventType = body.get("eventType");
        String notes = body.get("notes");
        String createdBy = body.getOrDefault("createdBy", "DASHBOARD");

        log.info("Adding event {} to order {}", eventType, orderId);

        jdbcTemplate.update("""
            INSERT INTO order_events (order_id, event_type, created_by, notes)
            VALUES (?, ?, ?, ?)
            """, orderId, eventType, createdBy, notes);

        return Map.of("success", true, "message", "Event added");
    }

    /**
     * Update order status.
     */
    @PatchMapping("/{orderId}/status")
    public Map<String, Object> updateOrderStatus(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {
        String newStatus = body.get("status");

        log.info("Updating order {} status to {}", orderId, newStatus);

        int updated = jdbcTemplate.update(
            "UPDATE orders SET status = ? WHERE order_id = ?",
            newStatus.toUpperCase(), orderId);

        if (updated > 0) {
            // Record status change event
            jdbcTemplate.update("""
                INSERT INTO order_events (order_id, event_type, created_by, notes)
                VALUES (?, 'STATUS_CHANGED', 'DASHBOARD', ?)
                """, orderId, "Status changed to " + newStatus);
        }

        return Map.of("success", updated > 0, "status", newStatus);
    }
}
