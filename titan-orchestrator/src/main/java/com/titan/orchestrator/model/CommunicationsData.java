package com.titan.orchestrator.model;

import java.util.List;

/**
 * Data models for Communications Agent operations.
 */
public class CommunicationsData {

    public record NotificationResult(
        String notificationId,
        String customerId,
        String customerName,
        String templateType,
        String subject,
        String recipient,
        boolean sent,
        String summary
    ) {}

    public record InquiryResult(
        int inquiryId,
        String customerId,
        String customerName,
        String inquiryType,
        String inquiryText,
        String suggestedResponse,
        List<String> similarInquiries,
        String orderContext,
        String status,
        String summary
    ) {}

    public record DraftResult(
        String orderId,
        String customerId,
        String customerName,
        String updateType,
        String subject,
        String body,
        String orderStatus,
        String recommendedAction,
        String summary
    ) {}

    public record CommunicationsQueryResponse(
        String query,
        String response
    ) {}
}
