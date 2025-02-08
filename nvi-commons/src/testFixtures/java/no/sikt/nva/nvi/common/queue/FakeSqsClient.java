package no.sikt.nva.nvi.common.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nva.commons.core.JacocoGenerated;
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

@JacocoGenerated
public class FakeSqsClient implements QueueClient {

  public static final String MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER_QUEUE = "candidateIdentifier";
  public static final String DATA_TYPE_STRING = "String";

  private final List<SendMessageRequest> sentMessages = new ArrayList<>();

  private final List<SendMessageBatchRequest> sentBatches = new ArrayList<>();

  private final List<ReceiveMessageRequest> receivedMessages = new ArrayList<>();

  private final List<DeleteMessageRequest> deleteMessages = new ArrayList<>();

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
    return createResponse(
        SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build());
  }

  @Override
  public NviSendMessageResponse sendMessage(
      String message, String queueUrl, UUID candidateIdentifier) {
    var request = createRequest(message, queueUrl, candidateIdentifier);
    sentMessages.add(request);
    return createResponse(
        SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build());
  }

  @Override
  public NviSendMessageBatchResponse sendMessageBatch(
      Collection<String> messages, String queueUrl) {
    var request = createBatchRequest(messages, queueUrl);
    sentBatches.add(request);
    return createResponse(SendMessageBatchResponse.builder().build());
  }

  @Override
  public NviReceiveMessageResponse receiveMessage(String queueUrl, int maxNumberOfMessages) {
    receivedMessages.add(createReceiveRequest(queueUrl, maxNumberOfMessages));
    return createResponse(ReceiveMessageResponse.builder().build());
  }

  @Override
  public void deleteMessage(String queueUrl, String receiptHandle) {
    var request =
        DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build();
    deleteMessages.add(request);
  }

  private ReceiveMessageRequest createReceiveRequest(String queueUrl, int maxNumberOfMessages) {
    return ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .maxNumberOfMessages(maxNumberOfMessages)
        .build();
  }

  private NviReceiveMessageResponse createResponse(ReceiveMessageResponse receiveMessageResponse) {
    return new NviReceiveMessageResponse(
        receiveMessageResponse.messages().stream()
            .map(
                m ->
                    new NviReceiveMessage(
                        m.body(), m.messageId(), m.attributesAsStrings(), m.receiptHandle()))
            .toList());
  }

  private SendMessageBatchRequest createBatchRequest(Collection<String> messages, String queueUrl) {
    return SendMessageBatchRequest.builder()
        .queueUrl(queueUrl)
        .entries(createBatchEntries(messages))
        .build();
  }

  private NviSendMessageBatchResponse createResponse(SendMessageBatchResponse response) {
    return new NviSendMessageBatchResponse(
        extractSuccessfulEntryIds(response), extractFailedEntryIds(response));
  }

  private Collection<SendMessageBatchRequestEntry> createBatchEntries(Collection<String> messages) {
    return messages.stream().map(this::createBatchEntry).toList();
  }

  private static List<String> extractSuccessfulEntryIds(SendMessageBatchResponse response) {
    return response.successful().stream().map(SendMessageBatchResultEntry::id).toList();
  }

  private static List<String> extractFailedEntryIds(SendMessageBatchResponse response) {
    return response.failed().stream().map(BatchResultErrorEntry::id).toList();
  }

  private SendMessageBatchRequestEntry createBatchEntry(String message) {
    return SendMessageBatchRequestEntry.builder().messageBody(message).build();
  }

  private SendMessageRequest createRequest(String body, String queueUrl, UUID candidateIdentifier) {
    return SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageAttributes(
            Map.of(
                MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER_QUEUE,
                MessageAttributeValue.builder()
                    .stringValue(candidateIdentifier.toString())
                    .dataType(DATA_TYPE_STRING)
                    .build()))
        .messageBody(body)
        .build();
  }

  private SendMessageRequest createRequest(String body, String queueUrl) {
    return SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build();
  }

  private NviSendMessageResponse createResponse(SendMessageResponse response) {
    return new NviSendMessageResponse(response.messageId());
  }
}
