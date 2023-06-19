package no.sikt.nva.nvi.evaluator;

import static no.sikt.nva.nvi.evaluator.calculator.NviCalculator.calculateNvi;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import no.sikt.nva.nvi.evaluator.aws.S3StorageReader;
import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import no.sikt.nva.nvi.evaluator.calculator.NviCandidate;
import no.unit.nva.events.models.EventReference;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class EvaluateNviCandidateHandler implements RequestHandler<EventReference, Void> {

    private final StorageReader<EventReference> storageReader;
    private final QueueClient<SendMessageResponse> queueClient;

    @JacocoGenerated
    protected EvaluateNviCandidateHandler() {
        this(new S3StorageReader(), new SqsMessageClient());
    }

    public EvaluateNviCandidateHandler(StorageReader<EventReference> storageReader,
                                       QueueClient<SendMessageResponse> queueClient) {
        super();
        this.storageReader = storageReader;
        this.queueClient = queueClient;
    }

    @Override
    public Void handleRequest(EventReference input, Context context) {
        var body = extractBodyFromContent(storageReader.read(input));

        var response = calculateNvi(body);
        if (response instanceof NviCandidate nviCandidate) {
            sendMessage(nviCandidate);
        }
        return null;
    }

    private static JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content))
                   .map(json -> json.at("/body"))
                   .orElseThrow();
    }

    private SendMessageResponse sendMessage(NviCandidate c) {
        return attempt(() -> dtoObjectMapper.writeValueAsString(c.response()))
                   .map(queueClient::sendMessage)
                   .orElseThrow();
    }
}
