package no.sikt.nva.nvi.evaluator.aws;

import static no.sikt.nva.nvi.evaluator.aws.RegionUtil.acquireAwsRegion;
import java.time.Duration;
import no.sikt.nva.nvi.common.QueueClient;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class SqsMessageClient implements QueueClient<SendMessageResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsMessageClient.class);
    private static final String QUEUE_NAME = new Environment().readEnv("CANDIDATE_QUEUE_NAME");
    private static final String DLQ_QUEUE_NAME = new Environment().readEnv("CANDIDATE_QUEUE_NAME");
    private static final int MAX_CONNECTIONS = 10_000;
    private static final int IDLE_TIME = 30;
    private static final int TIMEOUT_TIME = 30;
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final String dlqUrl;

    @JacocoGenerated
    public SqsMessageClient() {
        this(defaultSqsClient());
    }

    public SqsMessageClient(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
        this.queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(QUEUE_NAME).build()).queueUrl();
        this.dlqUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(DLQ_QUEUE_NAME).build()).queueUrl();
    }

    @Override
    public SendMessageResponse sendMessage(String message) {
        var candidate = createCandidate(message);
        LOGGER.info("Candidate Message: {}", candidate.messageBody());
        return sqsClient.sendMessage(candidate);
    }

    @Override
    public SendMessageResponse sendDlq(String message) {
        var dlq = createDlq(message);
        return sqsClient.sendMessage(dlq);
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

    private SendMessageRequest createDlq(String body) {
        return SendMessageRequest.builder()
                   .queueUrl(dlqUrl)
                   .messageBody(body)
                   .build();
    }

    private SendMessageRequest createCandidate(String body) {
        return SendMessageRequest.builder()
                   .queueUrl(queueUrl)
                   .messageBody(body).build();
    }
}