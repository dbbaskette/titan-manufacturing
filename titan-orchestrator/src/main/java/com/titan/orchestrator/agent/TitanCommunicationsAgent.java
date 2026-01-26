package com.titan.orchestrator.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.titan.orchestrator.model.CommunicationsData.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Titan Communications Agent
 *
 * Provides customer communication capabilities including notifications
 * and intelligent inquiry handling with RAG support.
 *
 * Key capabilities:
 * - Send templated notifications to customers
 * - Process customer inquiries with context-aware responses
 * - Draft customer updates for review and approval
 */
@Agent(description = "Titan Communications Agent - Handles customer communications including notifications, " +
       "inquiry handling with RAG-powered responses, and status update drafting.")
@Component
public class TitanCommunicationsAgent {

    private static final Logger log = LoggerFactory.getLogger(TitanCommunicationsAgent.class);

    /**
     * Send a notification to a customer using a template.
     */
    @Action(
        description = "Send a templated notification to a customer (order confirmation, shipment notice, delay alert).",
        toolGroups = {"communications-tools"}
    )
    public NotificationResult sendNotification(String customerId, String templateType, String variables, Ai ai) {
        log.info(">>> TitanCommunicationsAgent.sendNotification to {}, template: {}", customerId, templateType);

        NotificationResult result = ai.withAutoLlm().createObject(
            """
            Use the send_notification tool to send a notification to customer %s.
            Template type: %s
            Variables: %s
            Return the notification result with delivery status.
            """.formatted(customerId, templateType, variables != null ? variables : "{}"),
            NotificationResult.class
        );

        log.info("<<< sendNotification complete: sent={}", result.sent());
        return result;
    }

    /**
     * Handle a customer inquiry with RAG-powered response.
     */
    @Action(
        description = "Process a customer inquiry and generate a context-aware response using RAG.",
        toolGroups = {"communications-tools"}
    )
    public InquiryResult handleInquiry(String customerId, String inquiryText, String orderId, Ai ai) {
        log.info(">>> TitanCommunicationsAgent.handleInquiry from {}, order: {}", customerId, orderId);

        InquiryResult result = ai.withAutoLlm().createObject(
            """
            Use the handle_inquiry tool to process this customer inquiry:
            - Customer ID: %s
            - Inquiry: "%s"
            %s
            Find similar past inquiries and generate a helpful response.
            """.formatted(customerId, inquiryText,
                         orderId != null ? "- Related Order: " + orderId : ""),
            InquiryResult.class
        );

        log.info("<<< handleInquiry complete: type={}, status={}",
                 result.inquiryType(), result.status());
        return result;
    }

    /**
     * Draft a customer update for review.
     */
    @Action(
        description = "Generate a draft customer update for an order, ready for review and approval before sending.",
        toolGroups = {"communications-tools"}
    )
    public DraftResult draftCustomerUpdate(String orderId, String updateType, Ai ai) {
        log.info(">>> TitanCommunicationsAgent.draftCustomerUpdate for order: {}, type: {}", orderId, updateType);

        DraftResult result = ai.withAutoLlm().createObject(
            """
            Use the draft_customer_update tool to create a draft update for order %s.
            Update type: %s
            Generate a professional communication ready for review.
            """.formatted(orderId, updateType),
            DraftResult.class
        );

        log.info("<<< draftCustomerUpdate complete: action={}", result.recommendedAction());
        return result;
    }

    /**
     * Answer natural language communication queries.
     */
    @AchievesGoal(description = "Handle customer communication requests and inquiries")
    @Action(
        description = "Process natural language communication requests using available tools",
        toolGroups = {"communications-tools"}
    )
    public CommunicationsQueryResponse answerCommunicationsQuery(String query, Ai ai) {
        log.info(">>> TitanCommunicationsAgent.answerCommunicationsQuery: {}", query);

        String response = ai.withAutoLlm().generateText(
            """
            You are a customer communications assistant for Titan Manufacturing.
            Answer the following query using the communications tools available to you:
            - send_notification: Send templated notification to customer
            - handle_inquiry: Process customer inquiry with RAG-powered response
            - draft_customer_update: Generate status update draft for approval

            Query: %s

            Provide a helpful, professional response based on the data from the tools.
            """.formatted(query)
        );

        log.info("<<< answerCommunicationsQuery complete");
        return new CommunicationsQueryResponse(query, response);
    }
}
