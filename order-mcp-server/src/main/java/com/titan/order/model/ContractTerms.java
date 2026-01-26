package com.titan.order.model;

import java.math.BigDecimal;

/**
 * Customer contract terms and conditions.
 */
public record ContractTerms(
    String contractId,
    String customerId,
    String customerName,
    String contractType,
    int priorityLevel,
    BigDecimal discountPercent,
    int paymentTermsDays,
    BigDecimal creditLimit,
    BigDecimal currentBalance,
    BigDecimal availableCredit,
    String validFrom,
    String validTo,
    boolean isActive,
    String summary
) {}
