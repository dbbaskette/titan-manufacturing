package com.titan.governance.model;

/**
 * Certification information for a material batch.
 */
public record CertificationInfo(
    String certType,
    String certNumber,
    String issuedDate,
    String expiryDate,
    String issuingAuthority,
    String documentUrl,
    String verifiedBy,
    String verifiedAt
) {}
