package no.sikt.nva.nvi.common.queue;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.QueueServiceTestUtils;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class FakeSqsClient implements QueueClient {

  private static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
  private static final String DATA_TYPE_STRING = "String";

  private final Set<String> destinationQueuesThatShouldFail = new HashSet<>();

  private final List<SendMessageRequest> sentMessages = new ArrayList<>();

  private final List<SendMessageBatchRequest> sentBatches = new ArrayList<>();

  private final List<ReceiveMessageRequest> receivedMessages = new ArrayList<>();

  private final List<DeleteMessageRequest> deleteMessages = new ArrayList<>();

  public List<SQSMessage> getAllSentSqsEvents(String queueUrl) {
    var singleMessages =
        sentMessages.stream()
            .filter(request -> request.queueUrl().equals(queueUrl))
            .map(QueueServiceTestUtils::from);
    var batchedMessages =
        sentBatches.stream()
            .filter(request -> request.queueUrl().equals(queueUrl))
            .map(QueueServiceTestUtils::from);
    return Stream.concat(singleMessages, batchedMessages)
        .map(SQSEvent::getRecords)
        .flatMap(List::stream)
        .toList();
  }

  public List<SendMessageRequest> getSentMessages() {
    return sentMessages;
  }

  public List<SendMessageBatchRequest> getSentBatches() {
    return sentBatches;
  }

  @Override
  public NviSendMessageResponse sendMessage(String message, String queueUrl) {
    validateQueueUrl(queueUrl);
    var request = createRequest(message, queueUrl);
    sentMessages.add(request);
    return createResponse(
        SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build());
  }

  @Override
  public NviSendMessageResponse sendMessage(QueueMessage message, String queueUrl) {
    validateQueueUrl(queueUrl);
    var request = createRequest(message, queueUrl);
    sentMessages.add(request);
    return createResponse(
        SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build());
  }

  @Override
  public NviSendMessageResponse sendMessage(
      String message, String queueUrl, UUID candidateIdentifier) {
    validateQueueUrl(queueUrl);
    var request = createRequest(message, queueUrl, candidateIdentifier);
    sentMessages.add(request);
    return createResponse(
        SendMessageResponse.builder().messageId(UUID.randomUUID().toString()).build());
  }

  @Override
  public NviSendMessageBatchResponse sendMessageBatch(
      Collection<String> messages, String queueUrl) {
    validateQueueUrl(queueUrl);
    if (messages.isEmpty()) {
      throw SqsException.builder().message("Empty batch of messages sent to queue").build();
    }
    var request = createBatchRequest(messages, queueUrl);
    sentBatches.add(request);
    return createResponse(SendMessageBatchResponse.builder().build());
  }

  @Override
  public NviReceiveMessageResponse receiveMessage(String queueUrl, int maxNumberOfMessages) {
    validateQueueUrl(queueUrl);
    var numberOfMessages = Math.min(maxNumberOfMessages, sentMessages.size());
    return new NviReceiveMessageResponse(
        sentMessages.subList(0, numberOfMessages).stream()
            .map(
                sendMessageRequest ->
                    new NviReceiveMessage(
                        sendMessageRequest.messageBody(),
                        null,
                        sendMessageRequest.messageAttributes().entrySet().stream()
                            .collect(
                                Collectors.toMap(
                                    Entry::getKey, entry -> entry.getValue().stringValue())),
                        null))
            .toList());
  }

  // TODO: Fix-me Should delete by receiptHandle
  @Override
  public void deleteMessage(String queueUrl, String receiptHandle) {
    validateQueueUrl(queueUrl);
    var request =
        DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build();
    sentMessages.remove(0);
    deleteMessages.add(request);
  }

  /**
   * Makes a queue destination throw an exception when interacted with.
   *
   * @param queueUrl the URL of the queue to disable
   */
  public void disableDestinationQueue(String queueUrl) {
    destinationQueuesThatShouldFail.add(queueUrl);
  }

  /**
   * Removes the exception throwing behavior from a queue destination.
   *
   * @param queueUrl the URL of the queue to enable
   */
  public void enableDestinationQueue(String queueUrl) {
    destinationQueuesThatShouldFail.remove(queueUrl);
  }

  private void validateQueueUrl(String queueUrl) {
    if (destinationQueuesThatShouldFail.contains(queueUrl)) {
      throw SqsException.builder().message("Queue is disabled").build();
    }
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
            .map(FakeSqsClient::mapToNviReceiveMessage)
            .toList());
  }

  private static NviReceiveMessage mapToNviReceiveMessage(Message m) {
    return new NviReceiveMessage(
        m.body(), m.messageId(), m.attributesAsStrings(), m.receiptHandle());
  }

  private SendMessageBatchRequest createBatchRequest(Collection<String> messages, String queueUrl) {
    return SendMessageBatchRequest.builder()
        .queueUrl(queueUrl)
        .entries(createBatchEntries(messages))
        .build();
  }

  private NviSendMessageBatchResponse createResponse(SendMessageBatchResponse response) {
    var successfulEntries =
        response.successful().stream().map(SendMessageBatchResultEntry::id).toList();
    var failedEntries = response.failed().stream().map(BatchResultErrorEntry::id).toList();
    return new NviSendMessageBatchResponse(successfulEntries, failedEntries);
  }

  private Collection<SendMessageBatchRequestEntry> createBatchEntries(Collection<String> messages) {
    return messages.stream().map(this::createBatchEntry).toList();
  }

  private SendMessageBatchRequestEntry createBatchEntry(String message) {
    return SendMessageBatchRequestEntry.builder().messageBody(message).build();
  }

  private SendMessageRequest createRequest(QueueMessage message, String queueUrl) {
    return SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(message.body().toJsonString())
        .messageAttributes(message.attributes())
        .build();
  }

  private SendMessageRequest createRequest(String body, String queueUrl, UUID candidateIdentifier) {
    return SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageAttributes(
            Map.of(
                CANDIDATE_IDENTIFIER,
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
