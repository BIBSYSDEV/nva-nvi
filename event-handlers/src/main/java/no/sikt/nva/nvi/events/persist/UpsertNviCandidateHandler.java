package no.sikt.nva.nvi.events.persist;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.queue.NviQueueClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.events.model.InvalidNviMessageException;
import no.sikt.nva.nvi.events.model.NonNviCandidate;
import no.sikt.nva.nvi.events.model.NviCandidate;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String PUBLICATION_ID_NOT_FOUND = "publicationId not found";
    private static final Logger LOGGER = LoggerFactory.getLogger(UpsertNviCandidateHandler.class);
    private static final String INVALID_NVI_CANDIDATE_MESSAGE = "Invalid nvi candidate message";
    private static final String PERSISTENCE_MESSAGE = "Nvi candidate has been persisted for publication: {}";
    private static final String UPSERT_CANDIDATE_DLQ_QUEUE_URL = "UPSERT_CANDIDATE_DLQ_QUEUE_URL";
    private static final String UPSERT_CANDIDATE_FAILED_MESSAGE = "Failed to upsert candidate for publication: {}";
    private final CandidateRepository repository;
    private final PeriodRepository periodRepository;

    private final QueueClient queueClient;
    private final String dlqUrl;

    @JacocoGenerated
    public UpsertNviCandidateHandler() {
        this(new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()),
             new NviQueueClient(), new Environment());
    }

    public UpsertNviCandidateHandler(CandidateRepository repository, PeriodRepository periodRepository,
                                     QueueClient queueClient,
                                     Environment environment) {
        this.repository = repository;
        this.periodRepository = periodRepository;
        this.queueClient = queueClient;
        this.dlqUrl = environment.readEnv(UPSERT_CANDIDATE_DLQ_QUEUE_URL);
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
            Objects.requireNonNull(message.candidate().publicationId());
            return message;
        }).orElseThrow(failure -> new InvalidNviMessageException(INVALID_NVI_CANDIDATE_MESSAGE));
    }

    private static String getStackTrace(Exception e) {
        var stringWriter = new StringWriter();
        e.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

    private static Optional<URI> extractPublicationId(CandidateEvaluatedMessage evaluatedCandidate) {
        return Optional.ofNullable(evaluatedCandidate.candidate().publicationId());
    }

    private static void logPersistence(CandidateEvaluatedMessage evaluatedCandidate) {
        LOGGER.info(PERSISTENCE_MESSAGE, extractPublicationId(evaluatedCandidate).map(URI::toString)
                                             .orElse(PUBLICATION_ID_NOT_FOUND));
    }

    private static void logError(CandidateEvaluatedMessage evaluatedCandidate) {
        LOGGER.error(UPSERT_CANDIDATE_FAILED_MESSAGE, extractPublicationId(evaluatedCandidate).map(URI::toString)
                                                          .orElse(PUBLICATION_ID_NOT_FOUND));
    }

    private void upsertNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        try {
            validateMessage(evaluatedCandidate);
            if (evaluatedCandidate.candidate() instanceof NviCandidate candidate) {
                Candidate.fromRequest(candidate, repository, periodRepository);
            } else {
                var nonNviCandidate = (NonNviCandidate) evaluatedCandidate.candidate();
                Candidate.fromRequest(nonNviCandidate, repository);
            }
            logPersistence(evaluatedCandidate);
        } catch (Exception e) {
            logError(evaluatedCandidate);
            attempt(() -> queueClient.sendMessage(dtoObjectMapper.writeValueAsString(Map.of(
                "exception", getStackTrace(e),
                "evaluatedMessage", dtoObjectMapper.writeValueAsString(evaluatedCandidate)
            )), dlqUrl));
        }
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
