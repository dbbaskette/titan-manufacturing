package com.titan.governance.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Material batch traceability result for compliance audits.
 */
public record BatchTraceResult(
    String batchId,
    String materialSku,
    String materialName,
    String supplierName,
    String supplierCountry,
    String receivedDate,
    BigDecimal quantity,
    String unitOfMeasure,
    String storageLocation,
    String lotNumber,
    String status,
    List<CertificationInfo> certifications,
    List<UsageRecord> usageHistory,
    String summary
) {}
