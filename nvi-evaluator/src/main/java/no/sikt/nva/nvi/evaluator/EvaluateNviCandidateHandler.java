package no.sikt.nva.nvi.evaluator;

import static no.sikt.nva.nvi.evaluator.calculator.NviCalculator.calculateCandidate;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.databind.JsonNode;
import no.sikt.nva.nvi.evaluator.aws.S3StorageReader;
import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class EvaluateNviCandidateHandler implements RequestHandler<S3Event, Void> {

    private final StorageReader<S3Event> storageReader;
    private final QueueClient<SendMessageResponse> queueClient;

    @JacocoGenerated
    protected EvaluateNviCandidateHandler() {
        this(new S3StorageReader(), new SqsMessageClient());
    }

    public EvaluateNviCandidateHandler(StorageReader<S3Event> storageReader,
                                       QueueClient<SendMessageResponse> queueClient) {
        super();
        this.storageReader = storageReader;
        this.queueClient = queueClient;
    }

    @Override
    public Void handleRequest(S3Event input, Context context) {
        var body = extractBodyFromContent(storageReader.read(input));

        var response = calculateCandidate(body);
        if (response.getKey()) {
            attempt(() -> dtoObjectMapper.writeValueAsString(response.getValue()))
                .map(queueClient::sendMessage)
                .orElseThrow();
        }
        return null;
    }

    private static JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content))
                   .map(json -> json.at("/body"))
                   .orElseThrow();
    }
}
