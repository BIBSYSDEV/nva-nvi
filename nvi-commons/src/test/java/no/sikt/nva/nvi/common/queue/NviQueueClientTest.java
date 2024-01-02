package no.sikt.nva.nvi.common.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

public class NviQueueClientTest {

    public static final String TEST_PAYLOAD = "{}";
    public static final String TEST_QUEUE_URL = "url";
    public static final String TEST_MESSAGE_ID = "some_test_id";
    public static final String MESSAGE_FAILED_ID = "some_failed_id";
    private static final String TEST_RECEIPT_HANDLE = "some_test_receipt_handle";
    public static final String MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER = "candidateIdentifier";
    public static final int MAX_NUMBER_OF_MESSAGES = 10;

    @Test
    void shouldSendSqsMessageAndReturnId() {
        var sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(
            SendMessageResponse.builder().messageId(TEST_MESSAGE_ID).build());
        var client = new NviQueueClient(sqsClient);

        var result = client.sendMessage(TEST_PAYLOAD, TEST_QUEUE_URL);

        assertThat(result.messageId(), is(equalTo(TEST_MESSAGE_ID)));
    }

    @Test
    void shouldSendSqsMessageWithCandidateIdentifierAsMessageAttribute() {
        var sqsClient = mock(SqsClient.class);
        var candidateIdentifier = UUID.randomUUID();
        when(sqsClient.sendMessage(eq(expectedSendMessageRequest(candidateIdentifier)))).thenReturn(
            SendMessageResponse.builder().messageId(TEST_MESSAGE_ID).build());
        var client = new NviQueueClient(sqsClient);

        var result = client.sendMessage(TEST_PAYLOAD, TEST_QUEUE_URL, candidateIdentifier);

        assertThat(result.messageId(), is(equalTo(TEST_MESSAGE_ID)));
    }

    @Test
    void shouldSendSqsMessageBatchAndReturnId() {
        var sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenReturn(
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
        var sqsClient = mock(SqsClient.class);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
            ReceiveMessageResponse.builder()
                .messages(Message.builder()
                              .messageId(TEST_MESSAGE_ID)
                              .body(TEST_PAYLOAD)
                              .receiptHandle(TEST_RECEIPT_HANDLE).build())
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
        var sqsClient = mock(SqsClient.class);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
            ReceiveMessageResponse.builder()
                .messages(Message.builder()
                              .messageId(TEST_MESSAGE_ID)
                              .messageAttributes(
                                  Map.of("candidateIdentifier", MessageAttributeValue.builder().stringValue(
                                      UUID.randomUUID().toString()).build()))
                              .build())
                .build());
        var client = new NviQueueClient(sqsClient);

        var result = client.receiveMessage(TEST_QUEUE_URL, MAX_NUMBER_OF_MESSAGES);

        assertEquals(1, result.messages().get(0).messageAttributes().size());
    }

    @Test
    void shouldHandleSuccessfulDeleteCall() {
        var sqsClient = mock(SqsClient.class);

        var status = SdkHttpResponse.builder().statusCode(202).build();
        var response = DeleteMessageResponse.builder();
        response.sdkHttpResponse(status);

        when(sqsClient.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(response.build());
        var client = new NviQueueClient(sqsClient);

        assertDoesNotThrow(() -> client.deleteMessage(TEST_QUEUE_URL, TEST_RECEIPT_HANDLE));
    }

    @Test
    void shouldHandleFailedDeleteCall() {
        var sqsClient = mock(SqsClient.class);

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
                   .messageAttributes(Map.of(MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER,
                                             MessageAttributeValue.builder()
                                                 .stringValue(candidateIdentifier.toString())
                                                 .build()))
                   .queueUrl(TEST_QUEUE_URL).build();
    }
}
