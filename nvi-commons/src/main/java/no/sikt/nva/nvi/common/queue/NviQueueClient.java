package no.sikt.nva.nvi.common.queue;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import no.sikt.nva.nvi.common.utils.ApplicationConstants;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class NviQueueClient implements QueueClient<NviSendMessageResponse, NviSendMessageBatchResponse> {

    private static final int MAX_CONNECTIONS = 10_000;
    private static final int IDLE_TIME = 30;
    private static final int TIMEOUT_TIME = 30;
    protected final SqsClient sqsClient;

    @JacocoGenerated
    public NviQueueClient() {
        this(defaultSqsClient());
    }

    public NviQueueClient(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Override
    public NviSendMessageResponse sendMessage(String message, String queueUrl) {
        return createResponse(sqsClient.sendMessage(createRequest(message, queueUrl)));
    }

    @Override
    public NviSendMessageBatchResponse sendMessageBatch(Collection<String> messages, String queueUrl) {
        return createResponse(sqsClient.sendMessageBatch(createBatchRequest(messages, queueUrl)));
    }

    @JacocoGenerated
    protected static SqsClient defaultSqsClient() {
        return SqsClient.builder()
                   .region(ApplicationConstants.REGION)
                   .httpClient(httpClientForConcurrentQueries())
                   .build();
    }

    @JacocoGenerated
    private static SdkHttpClient httpClientForConcurrentQueries() {
        return ApacheHttpClient.builder()
                   .useIdleConnectionReaper(true)
                   .maxConnections(MAX_CONNECTIONS)
                   .connectionMaxIdleTime(Duration.ofMinutes(IDLE_TIME))
                   .connectionTimeout(Duration.ofMinutes(TIMEOUT_TIME))
                   .build();
    }

    private SendMessageRequest createRequest(String body, String queueUrl) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .build();
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
        return SendMessageBatchRequestEntry.builder().id(UUID.randomUUID().toString()).messageBody(message).build();
    }

    private NviSendMessageResponse createResponse(software.amazon.awssdk.services.sqs.model.SendMessageResponse response) {
        return new NviSendMessageResponse(response.messageId());
    }

    private NviSendMessageBatchResponse createResponse(
        software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse response) {
        return new NviSendMessageBatchResponse(
            response.successful().stream().map(SendMessageBatchResultEntry::id).toList(),
            response.failed().stream().map(BatchResultErrorEntry::id).toList());
    }
}
