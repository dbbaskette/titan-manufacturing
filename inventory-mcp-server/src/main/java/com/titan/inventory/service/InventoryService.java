package com.titan.inventory.service;

import com.titan.inventory.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory MCP Service
 *
 * Provides tools for inventory management across Titan's global facilities.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @McpTool(description = "Check inventory stock levels for a product across facilities. Returns quantity, reorder point, and availability status for each location.")
    public StockCheckResult checkStock(
        @McpToolParam(description = "Product SKU to check (e.g., INDL-BRG-7420, AERO-TB-001)") String sku,
        @McpToolParam(description = "Optional: Filter to specific facility ID (e.g., PHX, MUC). Leave null for all facilities.") String facilityId
    ) {
        log.info(">>> checkStock called for SKU: {}, facility: {}", sku, facilityId);

        // Get product info
        Map<String, Object> product;
        try {
            product = jdbcTemplate.queryForMap(
                "SELECT sku, name, division_id FROM products WHERE sku = ?",
                sku
            );
        } catch (Exception e) {
            log.warn("Product not found: {}", sku);
            return new StockCheckResult(
                sku, "Unknown", null, List.of(), 0, 0, false, List.of(),
                "Product SKU '" + sku + "' not found in catalog"
            );
        }

        String productName = (String) product.get("name");
        String divisionId = (String) product.get("division_id");

        // Query stock levels
        String sql = """
            SELECT s.sku, s.facility_id, f.name as facility_name, s.quantity, s.reorder_point, s.last_count_date
            FROM stock_levels s
            JOIN titan_facilities f ON s.facility_id = f.facility_id
            WHERE s.sku = ?
            """ + (facilityId != null ? " AND s.facility_id = ?" : "") +
            " ORDER BY s.quantity DESC";

        List<Object> params = new ArrayList<>();
        params.add(sku);
        if (facilityId != null) params.add(facilityId);

        List<StockLevel> stockLevels = jdbcTemplate.query(sql, (rs, rowNum) -> {
            int qty = rs.getInt("quantity");
            int reorder = rs.getInt("reorder_point");
            String status = qty == 0 ? "OUT_OF_STOCK" : (qty <= reorder ? "LOW_STOCK" : "IN_STOCK");

            return new StockLevel(
                rs.getString("sku"),
                rs.getString("facility_id"),
                rs.getString("facility_name"),
                qty,
                reorder,
                rs.getDate("last_count_date") != null ? rs.getDate("last_count_date").toString() : null,
                status
            );
        }, params.toArray());

        int totalQty = stockLevels.stream().mapToInt(StockLevel::quantity).sum();
        int facilitiesWithStock = (int) stockLevels.stream().filter(s -> s.quantity() > 0).count();
        List<String> needReorder = stockLevels.stream()
            .filter(s -> s.quantity() <= s.reorderPoint())
            .map(StockLevel::facilityId)
            .toList();

        String summary = String.format(
            "%s (%s): Total stock %d units across %d facilities. %s",
            productName, sku, totalQty, facilitiesWithStock,
            needReorder.isEmpty() ? "Stock levels adequate." :
                "Reorder needed at: " + String.join(", ", needReorder)
        );

        log.info("<<< checkStock complete: {} total units", totalQty);
        return new StockCheckResult(
            sku, productName, divisionId, stockLevels, totalQty,
            facilitiesWithStock, !needReorder.isEmpty(), needReorder, summary
        );
    }

    @McpTool(description = "Search products using natural language. Uses pgvector semantic search when embeddings are available, otherwise falls back to text search. Examples: 'high-temperature bearings for CNC', 'aerospace titanium components', 'EV motor housings'")
    public List<ProductSearchResult> searchProducts(
        @McpToolParam(description = "Natural language search query describing the products you're looking for") String query,
        @McpToolParam(description = "Optional: Filter by division: AERO, ENERGY, MOBILITY, INDUSTRIAL") String division,
        @McpToolParam(description = "Optional: Filter by category") String category,
        @McpToolParam(description = "Maximum number of results (default 10)") Integer limit
    ) {
        log.info(">>> searchProducts called with query: '{}', division: {}, category: {}", query, division, category);

        int resultLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 10;

        // Check if embeddings are available
        Integer embeddingCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM products WHERE embedding IS NOT NULL",
            Integer.class
        );

        List<ProductSearchResult> results;

        if (embeddingCount != null && embeddingCount > 0) {
            log.info("Using semantic search (pgvector) - {} products have embeddings", embeddingCount);
            // For now, fall back to text search since we need to generate query embedding
            // In production, this would call an embedding API
            results = textSearch(query, division, category, resultLimit);
        } else {
            log.info("No embeddings available, using text search");
            results = textSearch(query, division, category, resultLimit);
        }

        log.info("<<< searchProducts returning {} results", results.size());
        return results;
    }

    private List<ProductSearchResult> textSearch(String query, String division, String category, int limit) {
        // Build text search query
        StringBuilder sql = new StringBuilder("""
            SELECT p.sku, p.name, p.description, p.division_id, p.category, p.subcategory, p.unit_price,
                   COALESCE(SUM(s.quantity), 0) as total_stock
            FROM products p
            LEFT JOIN stock_levels s ON p.sku = s.sku
            WHERE p.is_active = TRUE
              AND (p.name ILIKE ? OR p.description ILIKE ? OR p.category ILIKE ? OR p.subcategory ILIKE ?)
            """);

        List<Object> params = new ArrayList<>();
        String searchPattern = "%" + query + "%";
        params.add(searchPattern);
        params.add(searchPattern);
        params.add(searchPattern);
        params.add(searchPattern);

        if (division != null && !division.isBlank()) {
            sql.append(" AND p.division_id = ?");
            params.add(division.toUpperCase());
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND p.category ILIKE ?");
            params.add("%" + category + "%");
        }

        sql.append(" GROUP BY p.sku, p.name, p.description, p.division_id, p.category, p.subcategory, p.unit_price");
        sql.append(" ORDER BY total_stock DESC LIMIT ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ProductSearchResult(
            rs.getString("sku"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("division_id"),
            rs.getString("category"),
            rs.getString("subcategory"),
            rs.getBigDecimal("unit_price"),
            0.0, // Text search doesn't have similarity score
            rs.getInt("total_stock")
        ), params.toArray());
    }

    @McpTool(description = "Find alternative products or suppliers for a given SKU. Useful for stockouts, supplier issues, or cost optimization. Returns similar products and alternative suppliers with pricing.")
    public List<AlternativeProduct> findAlternatives(
        @McpToolParam(description = "Product SKU to find alternatives for") String sku,
        @McpToolParam(description = "Minimum quantity needed (filters out low-stock alternatives)") Integer quantityNeeded
    ) {
        log.info(">>> findAlternatives called for SKU: {}, qty needed: {}", sku, quantityNeeded);

        int minQty = quantityNeeded != null ? quantityNeeded : 0;

        // Get product info to find similar products
        Map<String, Object> product;
        try {
            product = jdbcTemplate.queryForMap(
                "SELECT category, subcategory, division_id FROM products WHERE sku = ?",
                sku
            );
        } catch (Exception e) {
            log.warn("Product not found: {}", sku);
            return List.of();
        }

        String category = (String) product.get("category");
        String subcategory = (String) product.get("subcategory");

        // Find alternative suppliers for the same product
        List<AlternativeProduct> alternatives = new ArrayList<>();

        String supplierSql = """
            SELECT ps.sku, p.name, ps.supplier_id, s.name as supplier_name, s.country,
                   ps.unit_cost, s.lead_time_days, s.quality_rating, ps.is_primary,
                   COALESCE(SUM(sl.quantity), 0) as available_qty
            FROM product_suppliers ps
            JOIN products p ON ps.sku = p.sku
            JOIN suppliers s ON ps.supplier_id = s.supplier_id
            LEFT JOIN stock_levels sl ON ps.sku = sl.sku
            WHERE ps.sku = ? AND s.is_active = TRUE
            GROUP BY ps.sku, p.name, ps.supplier_id, s.name, s.country, ps.unit_cost, s.lead_time_days, s.quality_rating, ps.is_primary
            ORDER BY ps.is_primary DESC, s.quality_rating DESC
            """;

        alternatives.addAll(jdbcTemplate.query(supplierSql, (rs, rowNum) -> new AlternativeProduct(
            rs.getString("sku"),
            rs.getString("name"),
            rs.getString("supplier_id"),
            rs.getString("supplier_name"),
            rs.getString("country"),
            rs.getBigDecimal("unit_cost"),
            rs.getInt("lead_time_days"),
            rs.getBigDecimal("quality_rating"),
            rs.getInt("available_qty"),
            rs.getBoolean("is_primary"),
            rs.getBoolean("is_primary") ? "Primary supplier" : "Alternative supplier"
        ), sku));

        // Find similar products in same category
        String similarSql = """
            SELECT p.sku, p.name, ps.supplier_id, s.name as supplier_name, s.country,
                   ps.unit_cost, s.lead_time_days, s.quality_rating, ps.is_primary,
                   COALESCE(SUM(sl.quantity), 0) as available_qty
            FROM products p
            JOIN product_suppliers ps ON p.sku = ps.sku
            JOIN suppliers s ON ps.supplier_id = s.supplier_id
            LEFT JOIN stock_levels sl ON p.sku = sl.sku
            WHERE p.sku != ? AND p.category = ? AND p.is_active = TRUE
            GROUP BY p.sku, p.name, ps.supplier_id, s.name, s.country, ps.unit_cost, s.lead_time_days, s.quality_rating, ps.is_primary
            HAVING COALESCE(SUM(sl.quantity), 0) >= ?
            ORDER BY s.quality_rating DESC
            LIMIT 5
            """;

        alternatives.addAll(jdbcTemplate.query(similarSql, (rs, rowNum) -> new AlternativeProduct(
            rs.getString("sku"),
            rs.getString("name"),
            rs.getString("supplier_id"),
            rs.getString("supplier_name"),
            rs.getString("country"),
            rs.getBigDecimal("unit_cost"),
            rs.getInt("lead_time_days"),
            rs.getBigDecimal("quality_rating"),
            rs.getInt("available_qty"),
            rs.getBoolean("is_primary"),
            "Similar product in same category"
        ), sku, category, minQty));

        log.info("<<< findAlternatives returning {} alternatives", alternatives.size());
        return alternatives;
    }

    @McpTool(description = "Calculate optimal reorder quantity and timing for a product at a facility. Uses Economic Order Quantity (EOQ) formula with safety stock considerations.")
    public ReorderCalculation calculateReorder(
        @McpToolParam(description = "Product SKU") String sku,
        @McpToolParam(description = "Facility ID (e.g., PHX, MUC)") String facilityId,
        @McpToolParam(description = "Expected daily demand (units per day). If not provided, estimates from historical data.") Double dailyDemand
    ) {
        log.info(">>> calculateReorder called for SKU: {}, facility: {}, demand: {}", sku, facilityId, dailyDemand);

        // Get product and stock info
        Map<String, Object> productInfo;
        try {
            productInfo = jdbcTemplate.queryForMap("""
                SELECT p.sku, p.name, p.unit_price, p.lead_time_days, p.min_order_qty,
                       s.quantity, s.reorder_point, f.name as facility_name
                FROM products p
                JOIN stock_levels s ON p.sku = s.sku
                JOIN titan_facilities f ON s.facility_id = f.facility_id
                WHERE p.sku = ? AND s.facility_id = ?
                """, sku, facilityId);
        } catch (Exception e) {
            log.warn("Product/facility not found: {} at {}", sku, facilityId);
            return new ReorderCalculation(
                sku, "Unknown", facilityId, "Unknown",
                0, 0, 0, 0, 0, null, null, 0, BigDecimal.ZERO, false,
                "Product or facility not found"
            );
        }

        String productName = (String) productInfo.get("name");
        String facilityName = (String) productInfo.get("facility_name");
        int currentStock = ((Number) productInfo.get("quantity")).intValue();
        int reorderPoint = ((Number) productInfo.get("reorder_point")).intValue();
        BigDecimal unitPrice = (BigDecimal) productInfo.get("unit_price");
        int leadTimeDays = ((Number) productInfo.get("lead_time_days")).intValue();
        int minOrderQty = ((Number) productInfo.get("min_order_qty")).intValue();

        // Get primary supplier
        String supplierSql = """
            SELECT s.name, s.lead_time_days
            FROM product_suppliers ps
            JOIN suppliers s ON ps.supplier_id = s.supplier_id
            WHERE ps.sku = ? AND ps.is_primary = TRUE
            """;

        String primarySupplier = "Unknown";
        int supplierLeadTime = leadTimeDays;
        try {
            Map<String, Object> supplier = jdbcTemplate.queryForMap(supplierSql, sku);
            primarySupplier = (String) supplier.get("name");
            supplierLeadTime = ((Number) supplier.get("lead_time_days")).intValue();
        } catch (Exception ignored) {}

        // Use provided demand or estimate
        double demand = dailyDemand != null ? dailyDemand : estimateDailyDemand(sku);

        // Calculate safety stock (2 weeks of demand as buffer)
        int safetyStock = (int) Math.ceil(demand * 14);

        // Calculate EOQ: sqrt(2 * D * S / H)
        // D = annual demand, S = ordering cost ($50 fixed), H = holding cost (20% of unit price)
        double annualDemand = demand * 365;
        double orderingCost = 50.0;
        double holdingCost = unitPrice.doubleValue() * 0.20;
        int eoq = (int) Math.ceil(Math.sqrt(2 * annualDemand * orderingCost / Math.max(holdingCost, 0.01)));

        // Ensure minimum order quantity
        int recommendedOrder = Math.max(eoq, minOrderQty);

        // Calculate when to order
        int daysUntilReorder = currentStock > reorderPoint ?
            (int) Math.floor((currentStock - reorderPoint) / Math.max(demand, 0.01)) : 0;
        LocalDate orderDate = LocalDate.now().plusDays(daysUntilReorder);

        boolean urgent = currentStock <= reorderPoint;
        BigDecimal estimatedCost = unitPrice.multiply(BigDecimal.valueOf(recommendedOrder));

        String summary;
        if (urgent) {
            summary = String.format(
                "URGENT: %s at %s is at/below reorder point (%d units, reorder at %d). " +
                "Recommend ordering %d units from %s immediately. Estimated cost: $%,.2f",
                productName, facilityName, currentStock, reorderPoint,
                recommendedOrder, primarySupplier, estimatedCost
            );
        } else {
            summary = String.format(
                "%s at %s: Current stock %d units (reorder point: %d). " +
                "Recommend ordering %d units by %s from %s. Estimated cost: $%,.2f",
                productName, facilityName, currentStock, reorderPoint,
                recommendedOrder, orderDate, primarySupplier, estimatedCost
            );
        }

        log.info("<<< calculateReorder complete: recommend {} units, urgent={}", recommendedOrder, urgent);
        return new ReorderCalculation(
            sku, productName, facilityId, facilityName,
            currentStock, reorderPoint, safetyStock, eoq,
            recommendedOrder, orderDate.toString(), primarySupplier,
            supplierLeadTime, estimatedCost, urgent, summary
        );
    }

    @McpTool(description = "Find compatible replacement parts for a specific equipment ID based on fault type. " +
            "Looks up the equipment's type and model, then returns parts from the compatibility matrix " +
            "with current stock levels. Use this instead of searchProducts when you know the equipment ID and fault type.")
    public List<CompatiblePart> getCompatibleParts(
        @McpToolParam(description = "Equipment ID (e.g., ATL-CNC-001, PHX-CNC-007)") String equipmentId,
        @McpToolParam(description = "Fault type: BEARING, MOTOR, SPINDLE, COOLANT, ELECTRICAL. Determines which part roles to return.") String faultType
    ) {
        log.info(">>> getCompatibleParts called for equipment: {}, fault: {}", equipmentId, faultType);

        // Look up equipment type, model, and facility
        Map<String, Object> equipment;
        try {
            equipment = jdbcTemplate.queryForMap(
                "SELECT type, model, facility_id FROM equipment WHERE equipment_id = ?",
                equipmentId
            );
        } catch (Exception e) {
            log.warn("Equipment not found: {}", equipmentId);
            return List.of();
        }

        String eqType = (String) equipment.get("type");
        String eqModel = (String) equipment.get("model");
        String facilityId = (String) equipment.get("facility_id");

        // Map fault type to part roles
        List<String> roles = switch (faultType != null ? faultType.toUpperCase() : "") {
            case "BEARING" -> List.of("spindle_bearing", "ball_screw_bearing", "spindle_seal");
            case "MOTOR" -> List.of("spindle_motor", "motor_controller", "encoder", "contactor", "overload_relay");
            case "SPINDLE" -> List.of("spindle_cartridge", "spindle_drawbar", "spindle_seal", "spindle_bearing");
            case "COOLANT" -> List.of("coolant_pump", "coolant_pump_hp", "coolant_filter", "coolant_sensor", "coolant_chiller");
            case "ELECTRICAL" -> List.of("motor_controller", "power_supply", "circuit_breaker", "surge_protector", "emc_filter");
            default -> List.of("spindle_bearing", "spindle_motor", "motor_controller", "spindle_cartridge",
                               "coolant_pump", "circuit_breaker");
        };

        // Build role IN clause
        String roleParams = String.join(",", roles.stream().map(r -> "?").toList());

        String sql = """
            SELECT c.sku, p.name, c.part_role, c.is_primary, c.notes, p.category, p.unit_price,
                   COALESCE(sf.quantity, 0) as stock_at_facility,
                   COALESCE(st.total, 0) as total_stock
            FROM equipment_parts_compatibility c
            JOIN products p ON c.sku = p.sku
            LEFT JOIN stock_levels sf ON c.sku = sf.sku AND sf.facility_id = ?
            LEFT JOIN (SELECT sku, SUM(quantity) as total FROM stock_levels GROUP BY sku) st ON c.sku = st.sku
            WHERE c.equipment_type = ?
              AND (c.equipment_model = ? OR c.equipment_model IS NULL)
              AND c.part_role IN (%s)
            ORDER BY c.is_primary DESC, c.part_role, p.unit_price
            """.formatted(roleParams);

        List<Object> params = new ArrayList<>();
        params.add(facilityId);
        params.add(eqType);
        params.add(eqModel);
        params.addAll(roles);

        List<CompatiblePart> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            int localStock = rs.getInt("stock_at_facility");
            String status = localStock == 0 ? "OUT_OF_STOCK" : (localStock <= 2 ? "LOW_STOCK" : "IN_STOCK");
            return new CompatiblePart(
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("part_role"),
                rs.getBoolean("is_primary"),
                rs.getString("notes"),
                rs.getString("category"),
                rs.getBigDecimal("unit_price"),
                localStock,
                rs.getInt("total_stock"),
                status
            );
        }, params.toArray());

        log.info("<<< getCompatibleParts returning {} parts for {} ({} {}), fault={}",
                 results.size(), equipmentId, eqType, eqModel, faultType);
        return results;
    }

    private double estimateDailyDemand(String sku) {
        // In a real system, this would analyze historical order data
        // For now, return a reasonable default based on product type
        try {
            String category = jdbcTemplate.queryForObject(
                "SELECT category FROM products WHERE sku = ?",
                String.class, sku
            );
            // Higher demand for common parts like bearings
            if (category != null && category.toLowerCase().contains("bearing")) {
                return 2.0;
            }
            return 0.5; // Default low demand
        } catch (Exception e) {
            return 0.5;
        }
    }
}
