package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodRepository;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import no.sikt.nva.nvi.common.model.business.DbCandidate;
import no.sikt.nva.nvi.common.model.business.DbInstitutionPoints;
import no.sikt.nva.nvi.common.model.business.DbNviPeriod;
import no.sikt.nva.nvi.common.model.business.DbStatus;
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

    @JacocoGenerated //TODO Temporary for coverage
    public Optional<Candidate> upsertCandidate(boolean isNviCandidate, DbCandidate candidate) {
        if (!isExistingCandidate(candidate.publicationId()) && isNviCandidate) {
            return Optional.of(
                createCandidate(candidate,
                                generatePendingApprovalStatuses(candidate.points())
                ));
        } else if (isExistingCandidate(candidate.publicationId()) && isNviCandidate) {
            var existing = findByPublicationId(candidate.publicationId()).orElseThrow();
            //TODO: Reset NVI Candidates here. See https://unit.atlassian.net/browse/NP-45113;
            return Optional.of(updateCandidate(existing.identifier(),
                                               candidate,
                                               generatePendingApprovalStatuses(
                                                   candidate.points())));
        }
        return Optional.empty();
    }

    public DbNviPeriod createPeriod(DbNviPeriod period) {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public DbNviPeriod updatePeriod(DbNviPeriod period) {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public DbNviPeriod getPeriod(String publishingYear) {
        //TODO: Handle not-found. optional?
        return nviPeriodRepository.findByPublishingYear(publishingYear).orElseThrow();
    }

    public Candidate updateApprovalStatus(UUID identifier, DbApprovalStatus newStatus) {
        Optional<DbApprovalStatus> approvalByIdAndInstitutionId =
            nviCandidateRepository.findApprovalByIdAndInstitutionId(
                identifier, newStatus.institutionId());
        nviCandidateRepository.updateApprovalStatus(
            identifier,
            approvalByIdAndInstitutionId
                .map(oldStatus -> toUpdatedApprovalStatus(oldStatus, newStatus))
                .orElseThrow());
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

    private static boolean hasInvalidLength(DbNviPeriod period) {
        return period.publishingYear().length() != 4;
    }

    private static List<DbApprovalStatus> generatePendingApprovalStatuses(List<DbInstitutionPoints> institutionUris) {
        return institutionUris.stream()
                   .map(institutionPoints -> DbApprovalStatus.builder()
                                                 .status(DbStatus.PENDING)
                                                 .institutionId(institutionPoints.institutionId())
                                                 .build())
                   .toList();
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
                                .status(DbStatus.PENDING)
                                .finalizedBy(null)
                                .finalizedDate(null)
                                .build();
        };
    }

    private Candidate createCandidate(DbCandidate pendingCandidate1, List<DbApprovalStatus> approvalStatuses) {
        return nviCandidateRepository.create(pendingCandidate1, approvalStatuses);
    }

    private Candidate updateCandidate(UUID identifier, DbCandidate candidate, List<DbApprovalStatus> approvalStatuses) {
        return nviCandidateRepository.update(identifier, candidate, approvalStatuses);
    }

    private void validatePeriod(DbNviPeriod period) {
        if (hasInvalidLength(period)) {
            throw new IllegalArgumentException(INVALID_LENGTH_MESSAGE);
        }
        if (!isInteger(period.publishingYear())) {
            throw new IllegalArgumentException(PERIOD_NOT_NUMERIC_MESSAGE);
        }
    }

    private boolean isExistingCandidate(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }
}
