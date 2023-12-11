package no.sikt.nva.nvi.common.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
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
    public static final String MESSAGE_ATTRIBUTE_CANDIDATE_IDENTIFIER = "candidateIdentifier";

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
