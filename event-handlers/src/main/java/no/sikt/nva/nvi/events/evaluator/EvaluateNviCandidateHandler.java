package no.sikt.nva.nvi.events.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.events.evaluator.calculator.CandidateCalculator;
import no.sikt.nva.nvi.events.evaluator.calculator.OrganizationRetriever;
import no.sikt.nva.nvi.events.evaluator.calculator.PointCalculator;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.PersistedResourceMessage;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.attempt.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateNviCandidateHandler implements RequestHandler<SQSEvent, SQSEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvaluateNviCandidateHandler.class);
    private static final String BACKEND_CLIENT_AUTH_URL = "BACKEND_CLIENT_AUTH_URL";
    private static final String BACKEND_CLIENT_SECRET_NAME = "BACKEND_CLIENT_SECRET_NAME";
    private static final String EVALUATION_MESSAGE = "Nvi candidacy has been evaluated for publication: {}. Type: {}";
    private static final String FAILURE_MESSAGE = "Failure while calculating NVI Candidate: %s, ex: %s, msg: %s";
    private final EvaluatorService evaluatorService;

    @JacocoGenerated
    public EvaluateNviCandidateHandler() {
        this(new EvaluatorService(new S3StorageReader(new Environment().readEnv("EXPANDED_RESOURCES_BUCKET")),
                                  new CandidateCalculator(defaultUriRetriever(new Environment())),
                                  new PointCalculator(
                                      new OrganizationRetriever(defaultUriRetriever(new Environment())))));
    }

    public EvaluateNviCandidateHandler(EvaluatorService evaluatorService) {
        this.evaluatorService = evaluatorService;
    }

    @Override
    public SQSEvent handleRequest(SQSEvent input, Context context) {
        return attempt(() -> createEvent(evaluateCandidacy(extractPersistedResourceMessage(input)))).orElseThrow(
            failure -> handleFailure(input, failure));
    }

    private static RuntimeException handleFailure(SQSEvent input, Failure<SQSEvent> failure) {
        LOGGER.error(String.format(FAILURE_MESSAGE, input.toString(), failure.getException(),
                                   failure.getException().getMessage()));
        return new RuntimeException(failure.getException());
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultUriRetriever(Environment env) {
        return new AuthorizedBackendUriRetriever(env.readEnv(BACKEND_CLIENT_AUTH_URL),
                                                 env.readEnv(BACKEND_CLIENT_SECRET_NAME));
    }

    private static SQSEvent createEvent(CandidateEvaluatedMessage messageBody) {
        var event = new SQSEvent();
        event.setRecords(
            List.of(createMessage(attempt(() -> dtoObjectMapper.writeValueAsString(messageBody)).orElseThrow())));
        return event;
    }

    private static SQSMessage createMessage(String messageBody) {
        var message = new SQSMessage();
        message.setBody(messageBody);
        return message;
    }

    private static PersistedResourceMessage parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, PersistedResourceMessage.class)).orElseThrow();
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
