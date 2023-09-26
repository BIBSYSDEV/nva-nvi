package no.sikt.nva.nvi.events;

import static java.util.Objects.nonNull;
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
import java.util.Optional;
import no.sikt.nva.nvi.common.db.model.CandidateDao.CandidateData;
import no.sikt.nva.nvi.common.db.model.CandidateDao.Creator;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstitutionPoints;
import no.sikt.nva.nvi.common.db.model.CandidateDao.ChannelLevel;
import no.sikt.nva.nvi.common.db.model.CandidateDao.PublicationDate;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstanceType;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.model.InvalidNviMessageException;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpsertNviCandidateHandler implements RequestHandler<SQSEvent, Void> {

    public static final String INVALID_NVI_CANDIDATE_MESSAGE = "Invalid nvi candidate message";
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
            .forEach(this::upsertNviCandidate);

        return null;
    }

    private static CandidateData toDbCandidate(CandidateEvaluatedMessage message) {
        return CandidateData.builder()
                   .publicationBucketUri(message.publicationBucketUri())
                   .publicationId(message.candidateDetails().publicationId())
                   .applicable(isNviCandidate(message))
                   .creators(mapToVerifiedCreators(message.candidateDetails().verifiedCreators()))
                   .level(ChannelLevel.parse(message.candidateDetails().level()))
                   .instanceType(InstanceType.parse(message.candidateDetails().instanceType()))
                   .publicationDate(mapToPublicationDate(message.candidateDetails().publicationDate()))
                   .points(mapToInstitutionPoints(message.institutionPoints()))
                   .build();
    }

    private static List<InstitutionPoints> mapToInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return isNotEmpty(institutionPoints)
                   ? convertToPoints(institutionPoints)
                   : List.of();
    }

    private static List<InstitutionPoints> convertToPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.entrySet().stream()
                   .map(entry -> new InstitutionPoints(entry.getKey(), entry.getValue())).toList();
    }

    private static boolean isNotEmpty(Map<URI, BigDecimal> institutionPoints) {
        return nonNull(institutionPoints) && !institutionPoints.isEmpty();
    }

    private static boolean isNotEmpty(List<CandidateDetails.Creator> creators) {
        return nonNull(creators) && !creators.isEmpty();
    }

    private static PublicationDate mapToPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return Optional.ofNullable(publicationDate)
                   .map(UpsertNviCandidateHandler::toPublicationDate)
                   .orElse(null);
    }

    private static PublicationDate toPublicationDate(CandidateDetails.PublicationDate publicationDate1) {
        return new PublicationDate(publicationDate1.year(), publicationDate1.month(), publicationDate1.day());
    }

    private static List<Creator> mapToVerifiedCreators(List<CandidateDetails.Creator> creators) {
        return isNotEmpty(creators)
                   ? convertToCreators(creators)
                   : List.of();
    }

    private static List<Creator> convertToCreators(List<CandidateDetails.Creator> creators) {
        return creators.stream().map(UpsertNviCandidateHandler::toCreator).toList();
    }

    private static Creator toCreator(CandidateDetails.Creator verifiedCreatorDto) {
        return new Creator(verifiedCreatorDto.id(),
                           verifiedCreatorDto.nviInstitutions());
    }

    private static boolean isNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return CandidateStatus.CANDIDATE.equals(evaluatedCandidate.status());
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
        nviService.upsertCandidate(toDbCandidate(evaluatedCandidate));
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
