package no.sikt.nva.nvi.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.queue.NviSendMessageBatchResponse;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@JacocoGenerated
public class FakeSqsClient implements QueueClient<NviSendMessageResponse, NviSendMessageBatchResponse> {

    public static final String MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER_QUEUE = "candidateIdentifier";

    private final List<SendMessageRequest> sentMessages = new ArrayList<>();

    private final List<SendMessageBatchRequest> sentBatches = new ArrayList<>();

    public List<SendMessageRequest> getSentMessages() {
        return sentMessages;
    }

    public List<SendMessageBatchRequest> getSentBatches() {
        return sentBatches;
    }

    @Override
    public NviSendMessageResponse sendMessage(String message, String queueUrl) {
        var request = createRequest(message, queueUrl);
        sentMessages.add(request);
        return createResponse(SendMessageResponse.builder()
                                  .messageId(UUID.randomUUID().toString())
                                  .build());
    }

    @Override
    public NviSendMessageResponse sendMessage(String message, String queueUrl, UUID candidateIdentifier) {
        var request = createRequest(message, queueUrl, candidateIdentifier);
        sentMessages.add(request);
        return createResponse(SendMessageResponse.builder()
                                  .messageId(UUID.randomUUID().toString())
                                  .build());
    }

    @Override
    public NviSendMessageBatchResponse sendMessageBatch(Collection<String> messages, String queueUrl) {
        var request = createBatchRequest(messages, queueUrl);
        sentBatches.add(request);
        return createResponse(SendMessageBatchResponse.builder().build());
    }

    private static List<String> extractSuccessfulEntryIds(SendMessageBatchResponse response) {
        return response.successful().stream().map(SendMessageBatchResultEntry::id).toList();
    }

    private static List<String> extractFailedEntryIds(SendMessageBatchResponse response) {
        return response.failed().stream().map(BatchResultErrorEntry::id).toList();
    }

    private SendMessageBatchRequest createBatchRequest(Collection<String> messages, String queueUrl) {
        return SendMessageBatchRequest.builder()
                   .queueUrl(queueUrl)
                   .entries(createBatchEntries(messages))
                   .build();
    }

    private Collection<SendMessageBatchRequestEntry> createBatchEntries(Collection<String> messages) {
        return messages.stream().map(this::createBatchEntry).toList();
    }

    private SendMessageBatchRequestEntry createBatchEntry(String message) {
        return SendMessageBatchRequestEntry.builder().messageBody(message).build();
    }

    private SendMessageRequest createRequest(String body, String queueUrl) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .build();
    }

    private SendMessageRequest createRequest(String body, String queueUrl, UUID candidateIdentifier) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageAttributes(Map.of(
                       MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER_QUEUE,
                       MessageAttributeValue.builder()
                           .stringValue(candidateIdentifier.toString())
                           .build()))
                   .messageBody(body)
                   .build();
    }

    private NviSendMessageResponse createResponse(
        software.amazon.awssdk.services.sqs.model.SendMessageResponse response) {
        return new NviSendMessageResponse(response.messageId());
    }

    private NviSendMessageBatchResponse createResponse(
        software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse response) {
        return new NviSendMessageBatchResponse(extractSuccessfulEntryIds(response), extractFailedEntryIds(response));
    }
}
