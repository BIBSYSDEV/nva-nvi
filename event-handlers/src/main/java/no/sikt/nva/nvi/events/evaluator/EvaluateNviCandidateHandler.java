package no.sikt.nva.nvi.events.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.NviSendMessageResponse;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.events.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.evaluator.client.OrganizationRetriever;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
    private static final String EVALUATED_CANDIDATE_QUEUE_URL = "CANDIDATE_QUEUE_URL";
    private static final String BACKEND_CLIENT_AUTH_URL = "BACKEND_CLIENT_AUTH_URL";
    private static final String BACKEND_CLIENT_SECRET_NAME = "BACKEND_CLIENT_SECRET_NAME";
    private static final String EVALUATION_MESSAGE = "Nvi candidacy has been evaluated for publication: {}. Type: {}";
    private static final String FAILURE_MESSAGE = "Failure while calculating NVI Candidate: %s, ex: %s, msg: %s";
    private final EvaluatorService evaluatorService;
    private final QueueClient queueClient;
    private final String queueUrl;

    @JacocoGenerated
    public EvaluateNviCandidateHandler() {
        this(new EvaluatorService(new S3StorageReader(new Environment().readEnv("EXPANDED_RESOURCES_BUCKET")),
                                  new CandidateCalculator(authorizedUriRetriever(new Environment()),
                                                          new UriRetriever()),
                                  new PointCalculator(new OrganizationRetriever(new UriRetriever()))),
             new NviQueueClient(), new Environment());
    }

    public EvaluateNviCandidateHandler(EvaluatorService evaluatorService,
                                       QueueClient queueClient,
                                       Environment environment) {
        this.evaluatorService = evaluatorService;
        this.queueClient = queueClient;
        this.queueUrl = environment.readEnv(EVALUATED_CANDIDATE_QUEUE_URL);
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        attempt(() -> sendEvent(evaluateCandidacy(extractPersistedResourceMessage(input)))).orElseThrow(
            failure -> handleFailure(input, failure));
        return null;
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever authorizedUriRetriever(Environment env) {
        return new AuthorizedBackendUriRetriever(env.readEnv(BACKEND_CLIENT_AUTH_URL),
                                                 env.readEnv(BACKEND_CLIENT_SECRET_NAME));
    }

    private static RuntimeException handleFailure(SQSEvent input, Failure<NviSendMessageResponse> failure) {
        LOGGER.error(String.format(FAILURE_MESSAGE, input.toString(), failure.getException(),
                                   failure.getException().getMessage()));
        return new RuntimeException(failure.getException());
    }

    private static PersistedResourceMessage parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, PersistedResourceMessage.class)).orElseThrow();
    }

    private NviSendMessageResponse sendEvent(CandidateEvaluatedMessage candidateEvaluatedMessage) {
        var messageBody = attempt(() -> dtoObjectMapper.writeValueAsString(candidateEvaluatedMessage)).orElseThrow();
        return queueClient.sendMessage(messageBody, queueUrl);
    }

    private PersistedResourceMessage extractPersistedResourceMessage(SQSEvent input) {
        return attempt(() -> parseBody(extractFirstMessage(input).getBody())).orElseThrow();
    }

    private SQSMessage extractFirstMessage(SQSEvent input) {
        return input.getRecords().stream().findFirst().orElseThrow();
    }

    private CandidateEvaluatedMessage evaluateCandidacy(PersistedResourceMessage message) {
        var evaluatedCandidate = evaluatorService.evaluateCandidacy(message.resourceFileUri());
        LOGGER.info(EVALUATION_MESSAGE, evaluatedCandidate.candidate().publicationId(),
                    evaluatedCandidate.candidate().getClass().getSimpleName());
        return evaluatedCandidate;
    }
}
