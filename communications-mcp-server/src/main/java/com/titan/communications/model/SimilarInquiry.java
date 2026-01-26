package com.titan.communications.model;

/**
 * A similar past inquiry found via RAG search.
 */
public record SimilarInquiry(
    int inquiryId,
    String inquiryType,
    String inquiryText,
    String responseText,
    double similarityScore,
    String resolvedAt
) {}
