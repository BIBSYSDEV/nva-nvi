package no.sikt.nva.nvi.common.queue;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.utils.ApplicationConstants;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class NviQueueClient implements QueueClient<NviSendMessageResponse, NviSendMessageBatchResponse> {

    public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
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
    public NviSendMessageResponse sendMessage(String message, String queueUrl, UUID candidateIdentifier) {
        return createResponse(sqsClient.sendMessage(createRequest(message, queueUrl,
                                                                  getMessageAttributes(candidateIdentifier))));
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

    private static Map<String, MessageAttributeValue> getMessageAttributes(UUID candidateIdentifier) {
        return Map.of(CANDIDATE_IDENTIFIER,
                      MessageAttributeValue.builder()
                          .stringValue(candidateIdentifier.toString())
                          .build());
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

    private static List<String> extractFailedEntryIds(SendMessageBatchResponse response) {
        return response.failed().stream().map(BatchResultErrorEntry::id).toList();
    }

    private static List<String> extractSuccessfulEntryIds(SendMessageBatchResponse response) {
        return response.successful().stream().map(SendMessageBatchResultEntry::id).toList();
    }

    private SendMessageRequest createRequest(String body, String queueUrl) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .build();
    }

    private SendMessageRequest createRequest(String body, String queueUrl,
                                             Map<String, MessageAttributeValue> messageAttributes) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .messageAttributes(messageAttributes)
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

    private NviSendMessageResponse createResponse(SendMessageResponse response) {
        return new NviSendMessageResponse(response.messageId());
    }

    private NviSendMessageBatchResponse createResponse(SendMessageBatchResponse response) {
        return new NviSendMessageBatchResponse(extractSuccessfulEntryIds(response), extractFailedEntryIds(response));
    }
}
