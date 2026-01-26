package com.titan.governance.model;

import java.math.BigDecimal;

/**
 * Record of material batch usage in orders.
 */
public record UsageRecord(
    String orderId,
    String customerName,
    String productSku,
    BigDecimal quantityUsed,
    String usageDate
) {}
