package no.sikt.nva.nvi.events;

import static no.sikt.nva.nvi.common.service.NviService.defaultNviService;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbCreator;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbLevel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.model.CandidateEvaluatedMessage;
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

    private static DbCandidate toDbCandidate(CandidateEvaluatedMessage message) {
        return DbCandidate.builder()
                   .publicationBucketUri(message.publicationBucketUri())
                   .publicationId(message.candidateDetails().publicationId())
                   .applicable(isNviCandidate(message))
                   .creators(mapToVerifiedCreators(message.candidateDetails().verifiedCreators()))
                   .level(DbLevel.parse(message.candidateDetails().level()))
                   .instanceType(message.candidateDetails().instanceType())
                   .publicationDate(mapToPublicationDate(message.candidateDetails()
                                                             .publicationDate()))
                   .points(mapToInstitutionPoints(message.institutionPoints()))
                   .build();
    }

    private static List<DbInstitutionPoints> mapToInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.entrySet().stream()
                   .map(entry -> new DbInstitutionPoints(entry.getKey(), entry.getValue())).toList();
    }

    private static DbPublicationDate mapToPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return new DbPublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private static List<DbCreator> mapToVerifiedCreators(List<CandidateDetails.Creator> creators) {
        return creators.stream()
                   .map(
                       verifiedCreatorDto -> new DbCreator(verifiedCreatorDto.id(),
                                                           verifiedCreatorDto.nviInstitutions()))
                   .toList();
    }

    private static boolean isNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return CandidateStatus.CANDIDATE == evaluatedCandidate.status();
    }

    private void upsertNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        nviService.upsertCandidate(toDbCandidate(evaluatedCandidate));
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
