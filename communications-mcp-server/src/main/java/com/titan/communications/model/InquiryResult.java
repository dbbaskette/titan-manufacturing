package com.titan.communications.model;

import java.util.List;

/**
 * Result of handling a customer inquiry with RAG-powered response.
 */
public record InquiryResult(
    int inquiryId,
    String customerId,
    String customerName,
    String orderId,
    String inquiryType,
    String inquiryText,
    String suggestedResponse,
    List<SimilarInquiry> similarInquiries,
    String orderContext,
    String status,
    String summary
) {}
