package no.sikt.nva.nvi.common.queue;

import java.util.Map;

public record NviReceiveMessage(
    String body, String messageId, Map<String, String> messageAttributes, String receiptHandle) {}
