package no.sikt.nva.nvi.common.queue;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganization;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class NviQueueClientTest {

  public static final String TEST_PAYLOAD = "{}";
  public static final String TEST_QUEUE_URL = "url";
  public static final String TEST_MESSAGE_ID = "some_test_id";
  public static final String MESSAGE_FAILED_ID = "some_failed_id";
  private static final String TEST_RECEIPT_HANDLE = "some_test_receipt_handle";
  public static final String MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER = "candidateIdentifier";
  public static final int MAX_NUMBER_OF_MESSAGES = 10;
  public static final String DATA_TYPE_STRING = "String";
  private SqsClient sqsClient;

  @BeforeEach
  public void setUp() {
    sqsClient = mock(SqsClient.class);
  }

  @Test
  void shouldSendSqsMessageAndReturnId() {
    when(sqsClient.sendMessage(any(SendMessageRequest.class)))
        .thenReturn(SendMessageResponse.builder().messageId(TEST_MESSAGE_ID).build());
    var client = new NviQueueClient(sqsClient);

    var result = client.sendMessage(TEST_PAYLOAD, TEST_QUEUE_URL);

    assertThat(result.messageId(), is(equalTo(TEST_MESSAGE_ID)));
  }

  @Test
  void shouldSendSqsMessageWithCandidateIdentifierAsMessageAttribute() {
    var candidateIdentifier = UUID.randomUUID();
    when(sqsClient.sendMessage(eq(expectedSendMessageRequest(candidateIdentifier))))
        .thenReturn(SendMessageResponse.builder().messageId(TEST_MESSAGE_ID).build());
    var client = new NviQueueClient(sqsClient);

    var result = client.sendMessage(TEST_PAYLOAD, TEST_QUEUE_URL, candidateIdentifier);

    assertThat(result.messageId(), is(equalTo(TEST_MESSAGE_ID)));
  }

  @Test
  void shouldSendCorrectMessageRequestForCustomQueueMessage() {
    when(sqsClient.sendMessage(any(SendMessageRequest.class)))
        .thenReturn(SendMessageResponse.builder().messageId(TEST_MESSAGE_ID).build());
    var client = new NviQueueClient(sqsClient);

    var message =
        QueueMessage.builder()
            .withBody(randomOrganization().build())
            .withCandidateIdentifier(UUID.randomUUID())
            .build();

    client.sendMessage(message, TEST_QUEUE_URL);

    verify(sqsClient).sendMessage(argThat(matchesSendMessageRequest(message, TEST_QUEUE_URL)));
  }

  private static ArgumentMatcher<SendMessageRequest> matchesSendMessageRequest(
      QueueMessage expectedMessage, String expectedQueueUrl) {
    return request ->
        nonNull(request)
            && request.queueUrl().equals(expectedQueueUrl)
            && request.messageBody().equals(expectedMessage.body().toJsonString())
            && request.messageAttributes().equals(expectedMessage.attributes());
  }

  @Test
  void shouldSendSqsMessageBatchAndReturnId() {
    when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
        .thenReturn(
            SendMessageBatchResponse.builder()
                .successful(SendMessageBatchResultEntry.builder().id(TEST_MESSAGE_ID).build())
                .failed(BatchResultErrorEntry.builder().id(MESSAGE_FAILED_ID).build())
                .build());
    var client = new NviQueueClient(sqsClient);

    var result = client.sendMessageBatch(List.of(TEST_PAYLOAD), TEST_QUEUE_URL);
    assertThat(result.successful(), containsInAnyOrder(TEST_MESSAGE_ID));
    assertThat(result.failed(), containsInAnyOrder(MESSAGE_FAILED_ID));
  }

  @Test
  void shouldReceiveSqsMessageBatch() {
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(
            ReceiveMessageResponse.builder()
                .messages(
                    Message.builder()
                        .messageId(TEST_MESSAGE_ID)
                        .body(TEST_PAYLOAD)
                        .receiptHandle(TEST_RECEIPT_HANDLE)
                        .build())
                .build());
    var client = new NviQueueClient(sqsClient);

    var result = client.receiveMessage(TEST_QUEUE_URL, MAX_NUMBER_OF_MESSAGES);
    assertEquals(1, result.messages().size());

    var message = result.messages().get(0);
    assertEquals(TEST_PAYLOAD, message.body());
    assertEquals(TEST_MESSAGE_ID, message.messageId());
    assertEquals(TEST_RECEIPT_HANDLE, message.receiptHandle());
  }

  @Test
  void receiveSqsMessageBatchShouldIncludeAttributes() {
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(
            ReceiveMessageResponse.builder()
                .messages(
                    Message.builder()
                        .messageId(TEST_MESSAGE_ID)
                        .messageAttributes(
                            Map.of(
                                "candidateIdentifier",
                                MessageAttributeValue.builder()
                                    .stringValue(UUID.randomUUID().toString())
                                    .build()))
                        .build())
                .build());
    var client = new NviQueueClient(sqsClient);

    var result = client.receiveMessage(TEST_QUEUE_URL, MAX_NUMBER_OF_MESSAGES);

    assertEquals(1, result.messages().get(0).messageAttributes().size());
  }

  @Test
  void shouldHandleSuccessfulDeleteCall() {
    var status = SdkHttpResponse.builder().statusCode(202).build();
    var response = DeleteMessageResponse.builder();
    response.sdkHttpResponse(status);

    when(sqsClient.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(response.build());
    var client = new NviQueueClient(sqsClient);

    assertDoesNotThrow(() -> client.deleteMessage(TEST_QUEUE_URL, TEST_RECEIPT_HANDLE));
  }

  @Test
  void shouldHandleFailedDeleteCall() {
    var status = SdkHttpResponse.builder().statusCode(500).build();
    var response = DeleteMessageResponse.builder();
    response.sdkHttpResponse(status);

    when(sqsClient.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(response.build());
    var client = new NviQueueClient(sqsClient);

    assertThrows(Exception.class, () -> client.deleteMessage(TEST_QUEUE_URL, TEST_RECEIPT_HANDLE));
  }

  private static SendMessageRequest expectedSendMessageRequest(UUID candidateIdentifier) {
    return SendMessageRequest.builder()
        .messageBody(TEST_PAYLOAD)
        .messageAttributes(
            Map.of(
                MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER,
                MessageAttributeValue.builder()
                    .stringValue(candidateIdentifier.toString())
                    .dataType(DATA_TYPE_STRING)
                    .build()))
        .queueUrl(TEST_QUEUE_URL)
        .build();
  }
}
