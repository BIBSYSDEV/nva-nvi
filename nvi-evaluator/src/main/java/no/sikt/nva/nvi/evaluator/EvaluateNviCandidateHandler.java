package no.sikt.nva.nvi.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.QueueClient;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.evaluator.aws.S3StorageReader;
import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import no.sikt.nva.nvi.evaluator.calculator.CandidateCalculator;
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
    private final EvaluatorService evaluatorService;
    private final QueueClient<SendMessageResponse> queueClient;

    @JacocoGenerated
    public EvaluateNviCandidateHandler() {
        this(new EvaluatorService(new S3StorageReader(), new CandidateCalculator(
                 new AuthorizedBackendUriRetriever(BACKEND_CLIENT_AUTH_URL, BACKEND_CLIENT_SECRET_NAME))),
             new SqsMessageClient()
        );
    }

    public EvaluateNviCandidateHandler(EvaluatorService evaluatorService,
                                       QueueClient<SendMessageResponse> queueClient) {
        super(EventReference.class);
        this.evaluatorService = evaluatorService;
        this.queueClient = queueClient;
    }

    @Override
    protected Void processInputPayload(EventReference input,
                                       AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>> event,
                                       Context context) {
        try {
            sendMessage(evaluatorService.getCandidateEvaluatedMessage(input));
        } catch (Exception e) {
            var msg = "Failure while calculating NVI Candidate: %s, ex: %s, msg: %s".formatted(input.getUri(),
                                                                                               e.getClass(),
                                                                                               e.getMessage());
            LOGGER.error(msg, e);
            queueClient.sendDlq(msg);
        }
        return null;
    }

    private void sendMessage(CandidateEvaluatedMessage c) {
        attempt(() -> dtoObjectMapper.writeValueAsString(c)).map(queueClient::sendMessage).orElseThrow();
    }
}
