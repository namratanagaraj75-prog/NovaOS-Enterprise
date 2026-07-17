package com.novaos.api.service;

public record EmailDeliveryRequest(
        String idempotencyKey,
        String recipient,
        String subject,
        String textBody,
        String attachmentName,
        byte[] attachmentBytes) {
}
