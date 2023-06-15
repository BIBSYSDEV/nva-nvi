package no.sikt.nva.nvi.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import no.sikt.nva.nvi.evaluator.calculator.NviCalculator;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class EvaluateNviCandidateHandler implements RequestHandler<S3Event, Void> {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String AWS_REGION_ENV_VARIABLE = "AWS_REGION";
    private static final int MAX_CONNECTIONS = 10_000;
    private static final int IDLE_TIME = 30;
    private static final int TIMEOUT_TIME = 30;
    private final S3Client s3Client;
    private final SqsClient sqsClient;

    @JacocoGenerated
    protected EvaluateNviCandidateHandler() {
        this(defaultS3Client(), defaultSqsClient());
    }

    public EvaluateNviCandidateHandler(S3Client s3Client, SqsClient sqsClient) {
        super();
        this.s3Client = s3Client;
        this.sqsClient = sqsClient;
    }

    @JacocoGenerated
    public static S3Client defaultS3Client() {
        var awsRegion =
            ENVIRONMENT
                .readEnvOpt(AWS_REGION_ENV_VARIABLE)
                .orElse(Region.EU_WEST_1.toString());
        return S3Client.builder()
                   .region(Region.of(awsRegion))
                   .httpClient(UrlConnectionHttpClient.builder().build())
                   .build();
    }

    @Override
    public Void handleRequest(S3Event input, Context context) {
        var body = extractBodyFromContent(getS3BucketContent(input));

        var response = NviCalculator.calculateCandidate(body);
        attempt(() -> dtoObjectMapper.writeValueAsString(response))
            .map(this::createCandidate)
            .map(sqsClient::sendMessage)
            .orElseThrow();
        return null;
    }

    private static JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content))
                   .map(json -> json.at("/body"))
                   .orElseThrow();
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

        var region = ENVIRONMENT.readEnvOpt(AWS_REGION_ENV_VARIABLE).
                         map(Region::of)
                         .orElse(Region.EU_WEST_1);
        return SqsClient.builder()
                   .region(region)
                   .httpClient(httpClientForConcurrentQueries())
                   .build();
    }

    private String getS3BucketContent(S3Event input) {
        var expectingSinglRecord = input.getRecords().get(0);
        var name = expectingSinglRecord.getS3().getBucket().getName();
        var key = expectingSinglRecord.getS3().getObject().getKey();
        var s3Driver = new S3Driver(s3Client, name);

        var s3bucketPath = UriWrapper.fromUri(
                URI.create(String.format("s3://%s/%s", name, key)))
                               .toS3bucketPath();
        return s3Driver.getFile(s3bucketPath);
    }

    private SendMessageRequest createCandidate(String body) {
        return SendMessageRequest.builder().messageBody(body).build();
    }
}
