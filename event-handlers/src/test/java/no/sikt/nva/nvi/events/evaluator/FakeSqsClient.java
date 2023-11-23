package no.sikt.nva.nvi.events.evaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@JacocoGenerated
public class FakeSqsClient implements QueueClient<NviSendMessageResponse> {

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
                                  .messageId(request.messageDeduplicationId())
                                  .build());
    }

    @Override
    public NviSendMessageResponse sendMessageBatch(Collection<String> messages, String queueUrl) {
        var request = createBatchRequest(messages, queueUrl);
        sentBatches.add(request);
        return createResponse(SendMessageResponse.builder().build());
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

    private NviSendMessageResponse createResponse(SendMessageResponse response) {
        return new NviSendMessageResponse(response.messageId());
    }
}
