package no.sikt.nva.nvi.common.notification;

import java.time.Duration;
import no.sikt.nva.nvi.common.utils.ApplicationConstants;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class NviNotificationClient implements NotificationClient<NviPublishMessageResponse> {

    private static final int MAX_CONNECTIONS = 10_000;
    private static final int IDLE_TIME = 30;
    private static final int TIMEOUT_TIME = 30;

    private final SnsClient snsClient;

    @JacocoGenerated
    public NviNotificationClient() {
        this(defaultSnsClient());
    }

    public NviNotificationClient(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    @Override
    public NviPublishMessageResponse publish(String message, String topic) {
        var request = createRequest(message, topic);
        return new NviPublishMessageResponse(snsClient.publish(request).messageId());
    }

    private static PublishRequest createRequest(String message, String topic) {
        return PublishRequest.builder().message(message).topicArn(topic).build();
    }

    @JacocoGenerated
    private static SnsClient defaultSnsClient() {
        return SnsClient.builder()
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
}
