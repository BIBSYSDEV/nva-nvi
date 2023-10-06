package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.InvalidNviMessageException;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String INVALID_NVI_CANDIDATE_MESSAGE = "Invalid nvi candidate message";
    private static final Logger LOGGER = LoggerFactory.getLogger(UpsertNviCandidateHandler.class);
    public static final String PERSISTANCE_MESSAGE = "Nvi candidate has been persisted for publication: {}";
    private final CandidateRepository repository;
    private final PeriodRepository periodRepository;

    @JacocoGenerated
    public UpsertNviCandidateHandler() {
        var dynamoDbClient = defaultDynamoClient();
        this.repository = new CandidateRepository(dynamoDbClient);
        this.periodRepository = new PeriodRepository(dynamoDbClient);
    }

    public UpsertNviCandidateHandler(CandidateRepository repository, PeriodRepository periodRepository) {
        this.repository = repository;
        this.periodRepository = periodRepository;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::parseBody)
            .filter(Objects::nonNull)
            .forEach(this::upsertNviCandidate);

        return null;
    }

    private static void validateMessage(CandidateEvaluatedMessage message) {
        attempt(() -> {
            Objects.requireNonNull(message.publicationBucketUri());
            Objects.requireNonNull(message.candidateDetails().publicationId());
            return message;
        }).orElseThrow(failure -> new InvalidNviMessageException(INVALID_NVI_CANDIDATE_MESSAGE));
    }

    private void upsertNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        validateMessage(evaluatedCandidate);
        CandidateBO.fromRequest(evaluatedCandidate, repository, periodRepository);
        LOGGER.info(PERSISTANCE_MESSAGE, evaluatedCandidate.candidateDetails().publicationId());
    }

    private CandidateEvaluatedMessage parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, CandidateEvaluatedMessage.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error("Message body invalid: {}", body);
    }
}
