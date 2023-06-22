package no.sikt.nva.nvi.evaluator.aws;

import static no.sikt.nva.nvi.evaluator.aws.RegionUtil.acquireAwsRegion;
import java.time.Duration;
import no.sikt.nva.nvi.common.QueueClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class SqsMessageClient implements QueueClient<SendMessageResponse> {
    private static final int MAX_CONNECTIONS = 10_000;
    private static final int IDLE_TIME = 30;
    private static final int TIMEOUT_TIME = 30;
    private final SqsClient sqsClient;

    @JacocoGenerated
    public SqsMessageClient() {
        this(defaultSqsClient());
    }

    public SqsMessageClient(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }


    @Override
    public SendMessageResponse sendMessage(String message) {
        SendMessageResponse sendMessageResponse = sqsClient.sendMessage(createCandidate(message));
        return sendMessageResponse;
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

    @JacocoGenerated
    private static SqsClient defaultSqsClient() {
        return SqsClient.builder()
                   .region(acquireAwsRegion())
                   .httpClient(httpClientForConcurrentQueries())
                   .build();
    }

    private SendMessageRequest createCandidate(String body) {
        return SendMessageRequest.builder().messageBody(body).build();
    }
}