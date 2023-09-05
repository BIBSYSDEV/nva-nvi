package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.common.model.events.CandidateStatus.CANDIDATE;
import static nva.commons.core.attempt.Try.attempt;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodRepository;
import no.sikt.nva.nvi.common.model.ApprovalStatus;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Creator;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbCandidate;
import no.sikt.nva.nvi.common.model.business.InstitutionPoints;
import no.sikt.nva.nvi.common.model.business.Level;
import no.sikt.nva.nvi.common.model.business.NviPeriod;
import no.sikt.nva.nvi.common.model.business.PublicationDate;
import no.sikt.nva.nvi.common.model.business.Status;
import no.sikt.nva.nvi.common.model.events.CandidateEvaluatedMessage;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviService {

    public static final String INVALID_LENGTH_MESSAGE = "Provided period has invalid length!";
    public static final String PERIOD_NOT_NUMERIC_MESSAGE = "Period is not numeric!";
    private final NviCandidateRepository nviCandidateRepository;
    private final NviPeriodRepository nviPeriodRepository;

    public NviService(DynamoDbClient dynamoDbClient) {
        this.nviCandidateRepository = new NviCandidateRepository(dynamoDbClient);
        this.nviPeriodRepository = new NviPeriodRepository(dynamoDbClient);
    }

    @JacocoGenerated
    public static NviService defaultNviService() {
        return new NviService(defaultDynamoClient());
    }

    //TODO: Remove JacocoGenerated when other if/else cases are implemented
    @JacocoGenerated
    public Optional<Candidate> upsertCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        if (!isExistingCandidate(evaluatedCandidate) && isNviCandidate(evaluatedCandidate)) {
            return Optional.of(createCandidate(evaluatedCandidate));
        } else if (isExistingCandidate(evaluatedCandidate) && isNviCandidate(evaluatedCandidate)) {
            var existing = findByPublicationId(evaluatedCandidate.candidateDetails().publicationId()).orElseThrow();
            //TODO: Reset NVI Candidates here. See https://unit.atlassian.net/browse/NP-45113;
            return Optional.of(updateCandidate(existing.identifier(), evaluatedCandidate));
        }
        return Optional.empty();
    }

    public NviPeriod createPeriod(NviPeriod period) {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public NviPeriod updatePeriod(NviPeriod period) {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public NviPeriod getPeriod(String publishingYear) {
        //TODO: Handle not-found. optional?
        return nviPeriodRepository.findByPublishingYear(publishingYear).orElseThrow();
    }

    public Candidate updateApprovalStatus(UUID identifier, DbApprovalStatus approvalStatus) {
        Optional<ApprovalStatus> approvalByIdAndInstitutionId =
            nviCandidateRepository.findApprovalByIdAndInstitutionId(
                identifier, approvalStatus.institutionId());
        DbApprovalStatus newStatus = approvalByIdAndInstitutionId.map(
                a -> toUpdatedApprovalStatus(a.approvalStatus(), approvalStatus))
                                         .orElseThrow();
        nviCandidateRepository.updateApprovalStatus(identifier, newStatus);
        return nviCandidateRepository.getById(identifier);
    }

    public Optional<Candidate> findById(UUID uuid) {
        return nviCandidateRepository.findById(uuid);
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId);
    }

    private static boolean isInteger(String value) {
        return attempt(() -> Integer.parseInt(value))
                   .map(ignore -> true)
                   .orElse((ignore) -> false);
    }

    private static boolean hasInvalidLength(NviPeriod period) {
        return period.publishingYear().length() != 4;
    }

    private static List<Creator> mapToVerifiedCreators(List<CandidateDetails.Creator> creators) {
        return creators.stream()
                   .map(
                       verifiedCreatorDto -> new Creator(verifiedCreatorDto.id(), verifiedCreatorDto.nviInstitutions()))
                   .toList();
    }

    private static boolean isNviCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return CANDIDATE.equals(evaluatedCandidate.status());
    }

    private static PublicationDate mapToPublicationDate(CandidateDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private static List<DbApprovalStatus> generatePendingApprovalStatuses(Set<URI> institutionUris) {
        return institutionUris.stream()
                   .map(uri -> DbApprovalStatus.builder()
                                   .status(Status.PENDING)
                                   .institutionId(uri)
                                   .build())
                   .toList();
    }

    private static DbCandidate toPendingCandidate(CandidateEvaluatedMessage candidateEvaluatedMessage
    ) {
        return DbCandidate.builder()
                   .withPublicationBucketUri(candidateEvaluatedMessage.publicationBucketUri())
                   .withPublicationId(candidateEvaluatedMessage.candidateDetails().publicationId())
                   .withIsApplicable(true)
                   .withCreators(mapToVerifiedCreators(candidateEvaluatedMessage.candidateDetails().verifiedCreators()))
                   .withLevel(Level.parse(candidateEvaluatedMessage.candidateDetails().level()))
                   .withInstanceType(candidateEvaluatedMessage.candidateDetails().instanceType())
                   .withPublicationDate(mapToPublicationDate(candidateEvaluatedMessage.candidateDetails()
                                                                 .publicationDate()))
                   .withPoints(mapToInstitutionPoints(candidateEvaluatedMessage.institutionPoints()))
                   .build();
    }

    private static List<InstitutionPoints> mapToInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.entrySet().stream()
                   .map(entry -> new InstitutionPoints(entry.getKey(), entry.getValue())).toList();
    }

    @JacocoGenerated // bug in jacoco report that is unable to exhaust the switch. Should be fixed in version 0.8.11
    private DbApprovalStatus toUpdatedApprovalStatus(DbApprovalStatus oldApprovalStatus,
                                                     DbApprovalStatus newApprovalStatus) {
        return switch (newApprovalStatus.status()) {
            case APPROVED, REJECTED -> oldApprovalStatus.copy()
                                           .status(newApprovalStatus.status())
                                           .finalizedBy(newApprovalStatus.finalizedBy())
                                           .finalizedDate(Instant.now())
                                           .build();
            case PENDING -> oldApprovalStatus.copy()
                                .status(Status.PENDING)
                                .finalizedBy(null)
                                .finalizedDate(null)
                                .build();
        };
    }

    @JacocoGenerated
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private boolean exists(UUID uuid) {
        return nviCandidateRepository.findById(uuid).isPresent();
    }

    private boolean existsByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private Candidate createCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        var approvalStatuses = generatePendingApprovalStatuses(evaluatedCandidate.institutionPoints().keySet());
        var pendingCandidate = toPendingCandidate(evaluatedCandidate);
        return nviCandidateRepository.create(pendingCandidate, approvalStatuses);
    }

    private Candidate updateCandidate(UUID identifier, CandidateEvaluatedMessage evaluatedCandidate) {
        var approvalStatuses = generatePendingApprovalStatuses(evaluatedCandidate.institutionPoints().keySet());
        var pendingCandidate = toPendingCandidate(evaluatedCandidate);
        return nviCandidateRepository.update(identifier, pendingCandidate, approvalStatuses);
    }

    private void validatePeriod(NviPeriod period) {
        if (hasInvalidLength(period)) {
            throw new IllegalArgumentException(INVALID_LENGTH_MESSAGE);
        }
        if (!isInteger(period.publishingYear())) {
            throw new IllegalArgumentException(PERIOD_NOT_NUMERIC_MESSAGE);
        }
    }

    //TODO: Remove JacocoGenerated when case for existing candidate is implemented
    @JacocoGenerated
    private boolean isExistingCandidate(CandidateEvaluatedMessage evaluatedCandidate) {
        return existsByPublicationId(evaluatedCandidate.candidateDetails().publicationId());
    }
}
