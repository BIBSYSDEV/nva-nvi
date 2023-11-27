package no.sikt.nva.nvi.common.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class NviQueueClientTest {

    public static final String TEST_PAYLOAD = "{}";
    public static final String TEST_QUEUE_URL = "url";
    public static final String TEST_MESSAGE_ID = "some_test_id";

    @Test
    void shouldSendSqsMessageAndReturnId() {
        var sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(
            SendMessageResponse.builder().messageId(TEST_MESSAGE_ID).build());
        var client = new NviQueueClient(sqsClient);

        var result = client.sendMessage(TEST_PAYLOAD, TEST_QUEUE_URL);

        assertThat(result.messageIds().get(0), is(equalTo(TEST_MESSAGE_ID)));
    }

    @Test
    void shouldSendSqsMessageBatchAndReturnId() {
        var sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).thenReturn(
            SendMessageBatchResponse.builder().successful(
                SendMessageBatchResultEntry.builder().id(TEST_MESSAGE_ID).build()).build());
        var client = new NviQueueClient(sqsClient);

        var result = client.sendMessageBatch(List.of(TEST_PAYLOAD), TEST_QUEUE_URL);

        assertThat(result.messageIds().get(0), is(equalTo(TEST_MESSAGE_ID)));
    }
}
