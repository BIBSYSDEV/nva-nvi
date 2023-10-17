package no.sikt.nva.nvi.common.queue;

import java.time.Duration;
import no.sikt.nva.nvi.common.utils.ApplicationConstants;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class NviQueueClient implements QueueClient<NviSendMessageResponse> {

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

    @JacocoGenerated
    protected static SqsClient defaultSqsClient() {
        return SqsClient.builder()
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

    private SendMessageRequest createRequest(String body, String queueUrl) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body)
                   .build();
    }

    private NviSendMessageResponse createResponse(SendMessageResponse response) {
        return new NviSendMessageResponse(response.messageId());
    }
}
