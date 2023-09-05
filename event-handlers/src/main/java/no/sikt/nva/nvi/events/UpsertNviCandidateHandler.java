package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.common.service.NviService.defaultNviService;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Objects;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import no.sikt.nva.nvi.common.service.NviService;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpsertNviCandidateHandler.class);
    private final NviService nviService;

    @JacocoGenerated
    public UpsertNviCandidateHandler() {
        this.nviService = defaultNviService();
    }

    public UpsertNviCandidateHandler(NviService nviService) {
        this.nviService = nviService;
    }

    @Override
    public Void handleRequest(SQSEvent input, Context context) {
        input.getRecords()
            .stream()
            .map(SQSMessage::getBody)
            .map(this::parseBody)
            .filter(Objects::nonNull)
            .map(this::validate)
            .filter(Objects::nonNull)
            .forEach(this::upsertNviCandidate);

        return null;
    }

    private static CandidateEvaluatedMessage validateRequiredFields(CandidateEvaluatedMessage request) {
        Objects.requireNonNull(request.publicationBucketUri());
        Objects.requireNonNull(request.status());
        Objects.requireNonNull(request.institutionPoints());
        validateRequiredCandidateDetails(request.candidateDetails());
        return request;
    }

    private static void validateRequiredCandidateDetails(CandidateDetails candidateDetails) {
        Objects.requireNonNull(candidateDetails);
        Objects.requireNonNull(candidateDetails.publicationId());
        Objects.requireNonNull(candidateDetails.instanceType());
        Objects.requireNonNull(candidateDetails.publicationDate());
        Objects.requireNonNull(candidateDetails.level());
        Objects.requireNonNull(candidateDetails.verifiedCreators());
    }

    private void upsertNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        nviService.upsertCandidate(evaluatedCandidate);
    }

    private CandidateEvaluatedMessage parseBody(String body) {
        return attempt(() -> dtoObjectMapper.readValue(body, CandidateEvaluatedMessage.class))
                   .orElse(failure -> {
                       logInvalidMessageBody(body);
                       return null;
                   });
    }

    private CandidateEvaluatedMessage validate(CandidateEvaluatedMessage request) {
        return attempt(() -> validateRequiredFields(request)).orElse(failure -> {
            logInvalidMessageBody(request.toString());
            return null;
        });
    }

    private void logInvalidMessageBody(String body) {
        LOGGER.error("Message body invalid: {}", body);
    }
}
