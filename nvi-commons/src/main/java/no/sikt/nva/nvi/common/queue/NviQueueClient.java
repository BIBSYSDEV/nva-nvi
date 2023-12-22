package no.sikt.nva.nvi.common.queue;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.utils.ApplicationConstants;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class NviQueueClient implements QueueClient {

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
        return createResponse(sqsClient.sendMessage(createSendRequest(message, queueUrl)));
    }

    @Override
    public NviSendMessageResponse sendMessage(String message, String queueUrl, UUID candidateIdentifier) {
        return createResponse(sqsClient.sendMessage(createSendRequest(message, queueUrl,
                                                                      getMessageAttributes(candidateIdentifier))));
    }

    @Override
    public NviSendMessageBatchResponse sendMessageBatch(Collection<String> messages, String queueUrl) {
        return createResponse(sqsClient.sendMessageBatch(createBatchRequest(messages, queueUrl)));
    }

    @Override
    public NviReceiveMessageResponse receiveMessage(String queueUrl, int maxNumberOfMessages) {
        return createResponse(sqsClient.receiveMessage(createReceiveRequest(queueUrl, maxNumberOfMessages)));
    }

    @Override
    public void deleteMessage(String queueUrl, String receiptHandle) {
        var response = sqsClient.deleteMessage(createDeleteRequest(queueUrl, receiptHandle));

        if (!response.sdkHttpResponse().isSuccessful()) {
            throw new RuntimeException(response.toString());
        }
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
                   .connectionMaxIdleTime(Duration.ofSeconds(IDLE_TIME))
                   .connectionTimeout(Duration.ofSeconds(TIMEOUT_TIME))
                   .build();
    }

    private static List<String> extractFailedEntryIds(SendMessageBatchResponse response) {
        return response.failed().stream().map(BatchResultErrorEntry::id).toList();
    }

    private static List<String> extractSuccessfulEntryIds(SendMessageBatchResponse response) {
        return response.successful().stream().map(SendMessageBatchResultEntry::id).toList();
    }

    private SendMessageRequest createSendRequest(String body, String queueUrl) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .build();
    }

    private SendMessageRequest createSendRequest(String body, String queueUrl,
                                                 Map<String, MessageAttributeValue> messageAttributes) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .messageAttributes(messageAttributes)
                   .build();
    }

    private ReceiveMessageRequest createReceiveRequest(String queueUrl, int maxNumberOfMessages) {
        return ReceiveMessageRequest
                   .builder()
                   .messageAttributeNames("All")
                   .queueUrl(queueUrl)
                   .maxNumberOfMessages(maxNumberOfMessages)
                   .build();
    }

    private SendMessageBatchRequest createBatchRequest(Collection<String> messages, String queueUrl) {
        return SendMessageBatchRequest.builder()
                   .queueUrl(queueUrl)
                   .entries(createBatchEntries(messages))
                   .build();
    }

    private DeleteMessageRequest createDeleteRequest(String queueUrl, String receiptHandle) {
        return DeleteMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .receiptHandle(receiptHandle)
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

    private NviReceiveMessageResponse createResponse(ReceiveMessageResponse receiveMessageResponse) {
        return new NviReceiveMessageResponse(receiveMessageResponse.messages()
                                                 .stream()
                                                 .map(m -> new NviReceiveMessage(m.body(),
                                                                                 m.messageId(),
                                                                                 m.messageAttributes().entrySet().stream()
                                                                                     .collect(Collectors.toMap(
                                                                                         Entry::getKey,
                                                                                         e -> e.getValue().stringValue()
                                                                                     )),
                                                                                 m.receiptHandle()))
                                                 .toList());
    }
}
