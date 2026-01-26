package com.titan.logistics.service;

import com.titan.logistics.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Logistics MCP Service
 *
 * Provides tools for shipment management across Titan's global supply chain.
 */
@Service
public class LogisticsService {

    private static final Logger log = LoggerFactory.getLogger(LogisticsService.class);

    private final JdbcTemplate jdbcTemplate;

    public LogisticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @McpTool(description = "Get available shipping carriers and their services. Filter by service type (EXPRESS, GROUND, FREIGHT) or get all active carriers.")
    public List<Carrier> getCarriers(
        @McpToolParam(description = "Optional: Filter by service type: EXPRESS, GROUND, FREIGHT, AIR") String serviceType,
        @McpToolParam(description = "Only show active carriers (default true)") Boolean activeOnly
    ) {
        log.info(">>> getCarriers called with serviceType: {}, activeOnly: {}", serviceType, activeOnly);

        boolean onlyActive = activeOnly == null || activeOnly;

        StringBuilder sql = new StringBuilder("""
            SELECT carrier_id, name, service_type, tracking_url_template,
                   contact_email, contact_phone, is_active
            FROM carriers
            WHERE 1=1
            """);

        List<Object> params = new ArrayList<>();

        if (onlyActive) {
            sql.append(" AND is_active = TRUE");
        }
        if (serviceType != null && !serviceType.isBlank()) {
            sql.append(" AND service_type = ?");
            params.add(serviceType.toUpperCase());
        }
        sql.append(" ORDER BY service_type, name");

        List<Carrier> carriers = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new Carrier(
            rs.getString("carrier_id"),
            rs.getString("name"),
            rs.getString("service_type"),
            rs.getString("tracking_url_template"),
            rs.getString("contact_email"),
            rs.getString("contact_phone"),
            rs.getBoolean("is_active")
        ), params.toArray());

        log.info("<<< getCarriers returning {} carriers", carriers.size());
        return carriers;
    }

    @McpTool(description = "Create a new shipment for an order. Assigns carrier, generates tracking number, and calculates shipping cost.")
    public ShipmentCreateResult createShipment(
        @McpToolParam(description = "Order ID to create shipment for (e.g., TM-2024-45892)") String orderId,
        @McpToolParam(description = "Carrier ID to use (e.g., FEDEX-EXPRESS, UPS-GROUND)") String carrierId,
        @McpToolParam(description = "Origin facility ID (e.g., PHX, MUC)") String originFacility,
        @McpToolParam(description = "Service level: STANDARD, EXPRESS, PRIORITY (default EXPRESS)") String serviceLevel
    ) {
        log.info(">>> createShipment for order: {}, carrier: {}, origin: {}", orderId, carrierId, originFacility);

        // Verify order exists
        Map<String, Object> order;
        try {
            order = jdbcTemplate.queryForMap(
                "SELECT order_id, customer_id, shipping_address FROM orders WHERE order_id = ?",
                orderId
            );
        } catch (Exception e) {
            log.warn("Order not found: {}", orderId);
            return new ShipmentCreateResult(
                false, null, null, null, null, null, null,
                "Order '" + orderId + "' not found"
            );
        }

        // Verify carrier exists
        Map<String, Object> carrier;
        try {
            carrier = jdbcTemplate.queryForMap(
                "SELECT carrier_id, name, tracking_url_template FROM carriers WHERE carrier_id = ? AND is_active = TRUE",
                carrierId
            );
        } catch (Exception e) {
            log.warn("Carrier not found or inactive: {}", carrierId);
            return new ShipmentCreateResult(
                false, null, null, null, null, null, null,
                "Carrier '" + carrierId + "' not found or inactive"
            );
        }

        // Verify facility
        String facilityName;
        try {
            facilityName = jdbcTemplate.queryForObject(
                "SELECT name FROM titan_facilities WHERE facility_id = ?",
                String.class, originFacility
            );
        } catch (Exception e) {
            log.warn("Facility not found: {}", originFacility);
            return new ShipmentCreateResult(
                false, null, null, null, null, null, null,
                "Facility '" + originFacility + "' not found"
            );
        }

        // Calculate weight from order lines
        BigDecimal totalWeight = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(ol.quantity * p.weight_kg), 10.0)
            FROM order_lines ol
            JOIN products p ON ol.sku = p.sku
            WHERE ol.order_id = ?
            """, BigDecimal.class, orderId);

        // Get customer info for destination
        Map<String, Object> customer = jdbcTemplate.queryForMap(
            "SELECT c.name, c.city, c.country FROM customers c JOIN orders o ON c.customer_id = o.customer_id WHERE o.order_id = ?",
            orderId
        );

        // Get shipping rate
        String level = (serviceLevel != null && !serviceLevel.isBlank()) ? serviceLevel.toUpperCase() : "EXPRESS";
        String facilityRegion = getFacilityRegion(originFacility);
        String destRegion = getCountryRegion((String) customer.get("country"));

        Map<String, Object> rate;
        try {
            rate = jdbcTemplate.queryForMap("""
                SELECT cost_per_kg, base_cost, transit_days_min, transit_days_max
                FROM shipping_rates
                WHERE carrier_id = ? AND origin_region = ? AND dest_region = ?
                  AND ? >= weight_min_kg AND ? < weight_max_kg
                  AND service_level = ?
                ORDER BY effective_date DESC
                LIMIT 1
                """, carrierId, facilityRegion, destRegion, totalWeight, totalWeight, level);
        } catch (Exception e) {
            // Default rate if specific rate not found
            rate = Map.of(
                "cost_per_kg", new BigDecimal("5.00"),
                "base_cost", new BigDecimal("50.00"),
                "transit_days_min", 3,
                "transit_days_max", 7
            );
        }

        BigDecimal costPerKg = (BigDecimal) rate.get("cost_per_kg");
        BigDecimal baseCost = (BigDecimal) rate.get("base_cost");
        int transitMin = ((Number) rate.get("transit_days_min")).intValue();
        int transitMax = ((Number) rate.get("transit_days_max")).intValue();

        BigDecimal shippingCost = baseCost.add(costPerKg.multiply(totalWeight)).setScale(2, RoundingMode.HALF_UP);
        LocalDate estimatedDelivery = LocalDate.now().plusDays(transitMin);

        // Generate shipment ID and tracking number
        String shipmentId = "SHIP-" + LocalDate.now().getYear() + "-" + String.format("%03d", getNextShipmentNumber());
        String trackingNumber = generateTrackingNumber(carrierId);
        String trackingUrlTemplate = (String) carrier.get("tracking_url_template");
        String trackingUrl = trackingUrlTemplate != null ?
            trackingUrlTemplate.replace("{tracking}", trackingNumber) : null;

        // Insert shipment
        jdbcTemplate.update("""
            INSERT INTO shipments (shipment_id, order_id, carrier_id, tracking_number, status,
                                   origin_facility, destination_address, destination_city, destination_country,
                                   estimated_delivery, weight_kg, shipping_cost, created_at)
            VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, NOW())
            """,
            shipmentId, orderId, carrierId, trackingNumber, originFacility,
            order.get("shipping_address"), customer.get("city"), customer.get("country"),
            java.sql.Date.valueOf(estimatedDelivery), totalWeight, shippingCost
        );

        String message = String.format(
            "Shipment %s created for order %s. Carrier: %s, Tracking: %s. " +
            "Shipping from %s to %s, %s. Weight: %.1f kg, Cost: $%.2f. " +
            "Estimated delivery: %s",
            shipmentId, orderId, carrier.get("name"), trackingNumber,
            facilityName, customer.get("city"), customer.get("country"),
            totalWeight, shippingCost, estimatedDelivery
        );

        log.info("<<< createShipment created: {}", shipmentId);
        return new ShipmentCreateResult(
            true, shipmentId, trackingNumber, trackingUrl,
            (String) carrier.get("name"), estimatedDelivery.toString(),
            shippingCost, message
        );
    }

    @McpTool(description = "Track a shipment by shipment ID or tracking number. Returns current status, location, and delivery estimate.")
    public TrackingResult trackShipment(
        @McpToolParam(description = "Shipment ID (e.g., SHIP-2024-001) or tracking number") String shipmentIdOrTracking
    ) {
        log.info(">>> trackShipment for: {}", shipmentIdOrTracking);

        Map<String, Object> shipment;
        try {
            shipment = jdbcTemplate.queryForMap("""
                SELECT s.*, c.name as carrier_name, c.tracking_url_template,
                       f.name as facility_name
                FROM shipments s
                JOIN carriers c ON s.carrier_id = c.carrier_id
                JOIN titan_facilities f ON s.origin_facility = f.facility_id
                WHERE s.shipment_id = ? OR s.tracking_number = ?
                """, shipmentIdOrTracking, shipmentIdOrTracking);
        } catch (Exception e) {
            log.warn("Shipment not found: {}", shipmentIdOrTracking);
            return new TrackingResult(
                false, null, null, null, null,
                null, "Shipment not found", null, null,
                null, null, null, 0,
                "No shipment found with ID or tracking number: " + shipmentIdOrTracking
            );
        }

        String status = (String) shipment.get("status");
        String trackingNumber = (String) shipment.get("tracking_number");
        String trackingUrlTemplate = (String) shipment.get("tracking_url_template");
        String trackingUrl = trackingUrlTemplate != null ?
            trackingUrlTemplate.replace("{tracking}", trackingNumber) : null;

        String shipDate = shipment.get("ship_date") != null ?
            shipment.get("ship_date").toString() : null;
        String estDelivery = shipment.get("estimated_delivery") != null ?
            shipment.get("estimated_delivery").toString() : null;
        String actualDelivery = shipment.get("actual_delivery") != null ?
            shipment.get("actual_delivery").toString() : null;

        int daysInTransit = 0;
        if (shipDate != null) {
            LocalDate shipDateParsed = LocalDate.parse(shipDate.split(" ")[0]);
            LocalDate endDate = actualDelivery != null ?
                LocalDate.parse(actualDelivery.split(" ")[0]) : LocalDate.now();
            daysInTransit = (int) ChronoUnit.DAYS.between(shipDateParsed, endDate);
        }

        String statusDesc = getStatusDescription(status);
        String destination = String.format("%s, %s",
            shipment.get("destination_city"), shipment.get("destination_country"));

        String summary = String.format(
            "Shipment %s via %s - Status: %s. %s -> %s. %s",
            shipment.get("shipment_id"), shipment.get("carrier_name"),
            status, shipment.get("facility_name"), destination,
            status.equals("DELIVERED") ?
                "Delivered on " + actualDelivery :
                "Expected delivery: " + estDelivery
        );

        log.info("<<< trackShipment found, status: {}", status);
        return new TrackingResult(
            true,
            (String) shipment.get("shipment_id"),
            trackingNumber,
            (String) shipment.get("carrier_name"),
            trackingUrl,
            status, statusDesc,
            (String) shipment.get("facility_name"),
            destination,
            shipDate, estDelivery, actualDelivery,
            daysInTransit, summary
        );
    }

    @McpTool(description = "Estimate shipping cost and delivery time for a potential shipment. Returns options from multiple carriers.")
    public List<ShippingEstimate> estimateShipping(
        @McpToolParam(description = "Origin facility ID (e.g., PHX, MUC, SHA)") String originFacility,
        @McpToolParam(description = "Destination region: NA (North America), EU (Europe), APAC (Asia Pacific), LATAM (Latin America)") String destRegion,
        @McpToolParam(description = "Total weight in kg") Double weightKg,
        @McpToolParam(description = "Optional: Service level filter: STANDARD, EXPRESS, PRIORITY") String serviceLevel
    ) {
        log.info(">>> estimateShipping from {} to {}, weight: {} kg", originFacility, destRegion, weightKg);

        String facilityRegion = getFacilityRegion(originFacility);
        double weight = weightKg != null ? weightKg : 10.0;

        StringBuilder sql = new StringBuilder("""
            SELECT r.carrier_id, c.name as carrier_name, r.service_level, c.service_type,
                   r.cost_per_kg, r.base_cost, r.transit_days_min, r.transit_days_max
            FROM shipping_rates r
            JOIN carriers c ON r.carrier_id = c.carrier_id
            WHERE r.origin_region = ? AND r.dest_region = ?
              AND ? >= r.weight_min_kg AND ? < r.weight_max_kg
              AND c.is_active = TRUE
            """);

        List<Object> params = new ArrayList<>();
        params.add(facilityRegion);
        params.add(destRegion.toUpperCase());
        params.add(weight);
        params.add(weight);

        if (serviceLevel != null && !serviceLevel.isBlank()) {
            sql.append(" AND r.service_level = ?");
            params.add(serviceLevel.toUpperCase());
        }

        sql.append(" ORDER BY r.service_level, (r.base_cost + r.cost_per_kg * ?)");
        params.add(weight);

        List<ShippingEstimate> estimates = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            BigDecimal costPerKg = rs.getBigDecimal("cost_per_kg");
            BigDecimal baseCost = rs.getBigDecimal("base_cost");
            BigDecimal totalCost = baseCost.add(costPerKg.multiply(BigDecimal.valueOf(weight)))
                .setScale(2, RoundingMode.HALF_UP);

            int transitMin = rs.getInt("transit_days_min");
            int transitMax = rs.getInt("transit_days_max");
            LocalDate earliestDelivery = LocalDate.now().plusDays(transitMin);
            LocalDate latestDelivery = LocalDate.now().plusDays(transitMax);

            String level = rs.getString("service_level");
            boolean recommended = level.equals("EXPRESS"); // Default recommendation

            String notes = String.format(
                "%d-%d business days. Cost breakdown: $%.2f base + $%.2f/kg",
                transitMin, transitMax, baseCost, costPerKg
            );

            return new ShippingEstimate(
                rs.getString("carrier_id"),
                rs.getString("carrier_name"),
                level,
                rs.getString("service_type"),
                totalCost,
                transitMin, transitMax,
                earliestDelivery.toString(),
                latestDelivery.toString(),
                recommended,
                notes
            );
        }, params.toArray());

        // Mark the cheapest EXPRESS option as recommended if available
        estimates.stream()
            .filter(e -> e.serviceLevel().equals("EXPRESS"))
            .min((a, b) -> a.estimatedCost().compareTo(b.estimatedCost()))
            .ifPresent(cheapest -> {
                for (int i = 0; i < estimates.size(); i++) {
                    ShippingEstimate e = estimates.get(i);
                    if (e.carrierId().equals(cheapest.carrierId()) && e.serviceLevel().equals("EXPRESS")) {
                        estimates.set(i, new ShippingEstimate(
                            e.carrierId(), e.carrierName(), e.serviceLevel(), e.serviceType(),
                            e.estimatedCost(), e.transitDaysMin(), e.transitDaysMax(),
                            e.estimatedDeliveryEarliest(), e.estimatedDeliveryLatest(),
                            true, "RECOMMENDED: Best value for express shipping"
                        ));
                    } else {
                        estimates.set(i, new ShippingEstimate(
                            e.carrierId(), e.carrierName(), e.serviceLevel(), e.serviceType(),
                            e.estimatedCost(), e.transitDaysMin(), e.transitDaysMax(),
                            e.estimatedDeliveryEarliest(), e.estimatedDeliveryLatest(),
                            false, e.notes()
                        ));
                    }
                }
            });

        log.info("<<< estimateShipping returning {} options", estimates.size());
        return estimates;
    }

    // Helper methods

    private String getFacilityRegion(String facilityId) {
        Map<String, String> regionMap = Map.ofEntries(
            Map.entry("PHX", "NA"), Map.entry("DET", "NA"), Map.entry("ATL", "NA"),
            Map.entry("SEA", "NA"), Map.entry("CHI", "NA"), Map.entry("DAL", "NA"),
            Map.entry("MUC", "EU"), Map.entry("LON", "EU"),
            Map.entry("SHA", "APAC"), Map.entry("TOK", "APAC"), Map.entry("SYD", "APAC"),
            Map.entry("SAO", "LATAM")
        );
        return regionMap.getOrDefault(facilityId, "NA");
    }

    private String getCountryRegion(String country) {
        if (country == null) return "NA";
        String upper = country.toUpperCase();
        if (upper.contains("USA") || upper.contains("CANADA") || upper.contains("MEXICO")) return "NA";
        if (upper.contains("GERMANY") || upper.contains("UK") || upper.contains("FRANCE") ||
            upper.contains("ITALY") || upper.contains("SPAIN")) return "EU";
        if (upper.contains("CHINA") || upper.contains("JAPAN") || upper.contains("KOREA") ||
            upper.contains("AUSTRALIA") || upper.contains("INDIA")) return "APAC";
        if (upper.contains("BRAZIL") || upper.contains("ARGENTINA") || upper.contains("CHILE")) return "LATAM";
        return "NA";
    }

    private String getStatusDescription(String status) {
        return switch (status) {
            case "PENDING" -> "Shipment created, awaiting pickup";
            case "PICKED_UP" -> "Package picked up by carrier";
            case "IN_TRANSIT" -> "Package in transit to destination";
            case "OUT_FOR_DELIVERY" -> "Out for delivery";
            case "DELIVERED" -> "Successfully delivered";
            case "EXCEPTION" -> "Delivery exception - contact carrier";
            default -> "Unknown status";
        };
    }

    private int getNextShipmentNumber() {
        try {
            Integer max = jdbcTemplate.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(shipment_id FROM 11) AS INTEGER)) FROM shipments WHERE shipment_id LIKE 'SHIP-%-___'",
                Integer.class
            );
            return (max != null ? max : 0) + 1;
        } catch (Exception e) {
            return (int) (System.currentTimeMillis() % 1000);
        }
    }

    private String generateTrackingNumber(String carrierId) {
        String prefix = switch (carrierId.split("-")[0]) {
            case "FEDEX" -> "FX";
            case "UPS" -> "1Z";
            case "DHL" -> "DHL";
            case "MAERSK" -> "MAEU";
            case "XPO" -> "XPO";
            default -> "TRK";
        };
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
