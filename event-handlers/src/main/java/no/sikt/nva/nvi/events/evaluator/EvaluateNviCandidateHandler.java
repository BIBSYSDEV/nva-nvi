package no.sikt.nva.nvi.events.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.events.evaluator.aws.S3StorageReader;
import no.sikt.nva.nvi.events.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.events.evaluator.calculator.OrganizationRetriever;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.model.CandidateEvaluatedMessage;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.events.handlers.DestinationsEventBridgeEventHandler;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateNviCandidateHandler extends DestinationsEventBridgeEventHandler<EventReference, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
    private static final String BACKEND_CLIENT_AUTH_URL = "BACKEND_CLIENT_AUTH_URL";
    private static final String BACKEND_CLIENT_SECRET_NAME = "BACKEND_CLIENT_SECRET_NAME";
    private static final String ENV_CANDIDATE_QUEUE_URL = "CANDIDATE_QUEUE_URL";
    private static final String ENV_CANDIDATE_DLQ_URL = "CANDIDATE_DLQ_URL";
    private static final String EVALUATION_MESSAGE = "Nvi candidacy has been evaluated for publication: {}. Type: {}";
    private static final String FAILURE_MESSAGE = "Failure while calculating NVI Candidate: %s, ex: %s, msg: %s";
    private final EvaluatorService evaluatorService;
    private final QueueClient<NviSendMessageResponse> queueClient;
    private final String queueUrl;
    private final String dlqUrl;

    @JacocoGenerated
    public EvaluateNviCandidateHandler() {
        this(new EvaluatorService(new S3StorageReader(),
                                  new CandidateCalculator(defaultUriRetriever(new Environment())),
                                  new PointCalculator(
                                      new OrganizationRetriever(defaultUriRetriever(new Environment())))),
             new NviQueueClient(), new Environment());
    }

    public EvaluateNviCandidateHandler(EvaluatorService evaluatorService,
                                       QueueClient<NviSendMessageResponse> queueClient, Environment environment) {
        super(EventReference.class);
        this.evaluatorService = evaluatorService;
        this.queueClient = queueClient;
        this.queueUrl = environment.readEnv(ENV_CANDIDATE_QUEUE_URL);
        this.dlqUrl = environment.readEnv(ENV_CANDIDATE_DLQ_URL);
    }

    @Override
    protected Void processInputPayload(EventReference input,
                                       AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>> event,
                                       Context context) {
        try {
            var message = evaluatorService.evaluateCandidacy(input);
            sendMessage(message);
            LOGGER.info(EVALUATION_MESSAGE, message.candidate().publicationId(), message.candidate().getClass());
        } catch (Exception e) {
            var msg = FAILURE_MESSAGE.formatted(input.getUri(), e.getClass(), e.getMessage());
            LOGGER.error(msg, e);
            queueClient.sendMessage(msg, dlqUrl);
        }
        return null;
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultUriRetriever(Environment env) {
        return new AuthorizedBackendUriRetriever(env.readEnv(BACKEND_CLIENT_AUTH_URL),
                                                 env.readEnv(BACKEND_CLIENT_SECRET_NAME));
    }

    private void sendMessage(CandidateEvaluatedMessage evaluatedCandidate) {
        attempt(() -> dtoObjectMapper.writeValueAsString(evaluatedCandidate)).map(
            message -> queueClient.sendMessage(message, queueUrl)).orElseThrow();
    }
}
