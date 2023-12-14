package no.sikt.nva.nvi.common.notification;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

class NviNotificationClientTest {

    private static final String TEST_TOPIC = "topic";
    private static final String TEST_MESSAGE_ID = "some_message_id";
    private static final String TEST_PAYLOAD = "{}";

    @Test
    void shouldPublishMessageAndReturnMessageId() {
        var client = mock(SnsClient.class);
        when(client.publish(any(PublishRequest.class))).thenReturn(
            PublishResponse.builder().messageId(TEST_MESSAGE_ID).build());
        var notificationClient = new NviNotificationClient(client);

        var result = notificationClient.publish(TEST_PAYLOAD, TEST_TOPIC);

        assertThat(result.messageId(), is(equalTo(TEST_MESSAGE_ID)));
    }
}