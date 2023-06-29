package no.sikt.nva.nvi.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import no.sikt.nva.nvi.common.QueueClient;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.evaluator.aws.S3StorageReader;
import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import no.sikt.nva.nvi.evaluator.calculator.CandidateType;
import no.sikt.nva.nvi.evaluator.calculator.NviCalculator;
import no.sikt.nva.nvi.evaluator.calculator.NviCandidate;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.events.handlers.DestinationsEventBridgeEventHandler;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

public class EvaluateNviCandidateHandler extends DestinationsEventBridgeEventHandler<EventReference, Void> {

    public static final String BACKEND_CLIENT_AUTH_URL = new Environment().readEnv("BACKEND_CLIENT_AUTH_URL");
    public static final String BACKEND_CLIENT_SECRET_NAME = new Environment().readEnv("BACKEND_CLIENT_SECRET_NAME");
    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
    private final StorageReader<EventReference> storageReader;
    private final QueueClient<SendMessageResponse> queueClient;
    private final NviCalculator calculator;

    @JacocoGenerated
    public EvaluateNviCandidateHandler() {
        this(new S3StorageReader(), new SqsMessageClient(),
             new NviCalculator(new AuthorizedBackendUriRetriever(BACKEND_CLIENT_AUTH_URL, BACKEND_CLIENT_SECRET_NAME)));
    }

    public EvaluateNviCandidateHandler(StorageReader<EventReference> storageReader,
                                       QueueClient<SendMessageResponse> queueClient,
                                       NviCalculator calculator) {
        super(EventReference.class);
        this.storageReader = storageReader;
        this.queueClient = queueClient;
        this.calculator = calculator;
    }

    @Override
    protected Void processInputPayload(EventReference input,
                                       AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>> event,
                                       Context context) {
        try {
            var readInput = storageReader.read(input);
            var jsonNode = extractBodyFromContent(readInput);
            var candidateType = calculator.calculateNvi(jsonNode);
            handleCandidateType(candidateType);
        } catch (Exception e) {
            LOGGER.error("Failure while calculating NVI Candidate: %s".formatted(input.getUri()),
                         e);
        }
        return null;
    }

    private void handleCandidateType(CandidateType candidateType) {
        if (candidateType instanceof NviCandidate nviCandidate) {
            sendMessage(nviCandidate);
            LOGGER.info("SentMessage");
        }
    }

    private JsonNode extractBodyFromContent(String content) {
        return attempt(() -> dtoObjectMapper.readTree(content))
                   .map(json -> json.at("/body"))
                   .orElseThrow();
    }

    private void sendMessage(NviCandidate c) {
        attempt(() -> dtoObjectMapper.writeValueAsString(c.response()))
            .map(queueClient::sendMessage)
            .orElseThrow();
    }
}
