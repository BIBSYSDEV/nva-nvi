package no.sikt.nva.nvi.events.evaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.queue.NviSendMessageBatchResponse;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@JacocoGenerated
public class FakeSqsClient implements QueueClient<NviSendMessageResponse, NviSendMessageBatchResponse> {

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

    private NviSendMessageResponse createResponse(
        software.amazon.awssdk.services.sqs.model.SendMessageResponse response) {
        return new NviSendMessageResponse(response.messageId());
    }

    private NviSendMessageBatchResponse createResponse(
        software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse response) {
        return new NviSendMessageBatchResponse(extractSuccessfulEntryIds(response), extractFailedEntryIds(response));
    }
}
