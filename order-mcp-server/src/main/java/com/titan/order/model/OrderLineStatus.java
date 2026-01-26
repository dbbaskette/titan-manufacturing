package com.titan.order.model;

import java.math.BigDecimal;

/**
 * Status of an individual order line.
 */
public record OrderLineStatus(
    int lineNumber,
    String sku,
    String productName,
    int quantityOrdered,
    int quantityShipped,
    BigDecimal unitPrice,
    BigDecimal lineTotal,
    String status
) {}
