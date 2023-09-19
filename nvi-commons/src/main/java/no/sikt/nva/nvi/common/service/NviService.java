package no.sikt.nva.nvi.common.service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodRepository;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.model.ReportStatus;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviService {

    public static final String INVALID_LENGTH_MESSAGE = "Provided period has invalid length!";
    public static final String PERIOD_NOT_NUMERIC_MESSAGE = "Period is not numeric!";
    public static final String NOT_SUPPORTED_REPORTING_DATE_MESSAGE = "Provided reporting date is not supported";
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
    public Optional<Candidate> upsertCandidate(DbCandidate dbCandidate) {
        if (isNewCandidate(dbCandidate)) {
            return createCandidate(dbCandidate);
        } else if (isExistingCandidate(dbCandidate)) {
            return updateCandidate(dbCandidate);
        }
        return Optional.empty();
    }

    public DbNviPeriod createPeriod(DbNviPeriod period) {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public DbNviPeriod updatePeriod(DbNviPeriod period) {
        var nviPeriod = injectCreatedBy(period);
        validatePeriod(nviPeriod);
        return nviPeriodRepository.save(nviPeriod);
    }

    public DbNviPeriod getPeriod(String publishingYear) {
        //TODO: Handle not-found. optional?
        return nviPeriodRepository.findByPublishingYear(publishingYear).orElseThrow();
    }

    public List<DbNviPeriod> getPeriods() {
        return nviPeriodRepository.getPeriods();
    }

    public Candidate updateApprovalStatus(UUID identifier, DbApprovalStatus newStatus) {
        var approvalByIdAndInstitutionId =
            nviCandidateRepository.findApprovalByIdAndInstitutionId(identifier, newStatus.institutionId())
                .map(oldStatus -> toUpdatedApprovalStatus(oldStatus, newStatus))
                .orElseThrow();
        nviCandidateRepository.updateApprovalStatus(identifier, approvalByIdAndInstitutionId);
        var candidate = nviCandidateRepository.getCandidateById(identifier);
        return candidate.copy().withReportStatus(getReportStatus(candidate)).build();
    }

    public Optional<Candidate> findById(UUID uuid) {
        return nviCandidateRepository.findCandidateById(uuid)
                   .map(value -> value.copy().withReportStatus(getReportStatus(value)).build());
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        var candidate = nviCandidateRepository.findByPublicationId(publicationId);
        return candidate.map(value -> value.copy().withReportStatus(getReportStatus(value)).build());
    }

    public Candidate createNote(UUID identifier, DbNote dbNote) {
        if (nviCandidateRepository.exists(identifier)) {
            nviCandidateRepository.saveNote(identifier, dbNote);
        }
        var candidate = nviCandidateRepository.findCandidateById(identifier).orElseThrow();
        return candidate.copy().withReportStatus(getReportStatus(candidate)).build();
    }

    public Candidate deleteNote(UUID candidateIdentifier, UUID noteIdentifier, String requestUsername) {
        DbNote note = nviCandidateRepository.getNoteById(candidateIdentifier, noteIdentifier);
        if (isNoteOwner(requestUsername, note)) {
            nviCandidateRepository.deleteNote(candidateIdentifier, noteIdentifier);
            var candidate = nviCandidateRepository.findCandidateById(candidateIdentifier).orElseThrow();
            return candidate.copy().withReportStatus(getReportStatus(candidate)).build();
        }
        throw new IllegalArgumentException("User not allowed to remove others note.");
    }

    private static boolean isNoteOwner(String requestUsername, DbNote note) {
        return note.user().value().equals(requestUsername);
    }

    private static boolean isInteger(String value) {
        return attempt(() -> Integer.parseInt(value))
                   .map(ignore -> true)
                   .orElse((ignore) -> false);
    }

    private static boolean hasInvalidLength(DbNviPeriod period) {
        return period.publishingYear().length() != 4;
    }

    private static List<DbApprovalStatus> generatePendingApprovalStatuses(List<DbInstitutionPoints> institutionPoints) {
        return institutionPoints.stream()
                   .map(NviService::toPendingApprovalStatus)
                   .toList();
    }

    private static DbApprovalStatus toPendingApprovalStatus(DbInstitutionPoints institutionPoints) {
        return DbApprovalStatus.builder()
                   .status(DbStatus.PENDING)
                   .institutionId(institutionPoints.institutionId())
                   .build();
    }

    private static DbApprovalStatus resetStatus(DbApprovalStatus oldApprovalStatus) {
        return oldApprovalStatus.copy()
                   .status(DbStatus.PENDING)
                   .assignee(oldApprovalStatus.assignee())
                   .finalizedBy(null)
                   .finalizedDate(null)
                   .build();
    }

    private static DbApprovalStatus finalizeStatus(DbApprovalStatus oldApprovalStatus,
                                                   DbApprovalStatus newApprovalStatus) {
        return oldApprovalStatus.copy()
                   .status(newApprovalStatus.status())
                   .finalizedBy(newApprovalStatus.finalizedBy())
                   .assignee(newApprovalStatus.assignee())
                   .finalizedDate(Instant.now())
                   .build();
    }

    private static DbApprovalStatus updateStatus(DbApprovalStatus oldApprovalStatus,
                                                 DbApprovalStatus newApprovalStatus) {
        return switch (newApprovalStatus.status()) {
            case APPROVED, REJECTED -> finalizeStatus(oldApprovalStatus, newApprovalStatus);
            case PENDING -> resetStatus(oldApprovalStatus);
        };
    }

    private static boolean updateIsAssignee(DbApprovalStatus oldApprovalStatus, DbApprovalStatus newApprovalStatus) {
        return oldApprovalStatus.status().equals(newApprovalStatus.status());
    }

    private DbNviPeriod injectCreatedBy(DbNviPeriod period) {
        return period.copy()
                   .createdBy(getPeriod(period.publishingYear()).createdBy())
                   .build();
    }

    private boolean isExistingCandidate(DbCandidate dbCandidate) {
        return isExistingCandidate(dbCandidate.publicationId()) && dbCandidate.applicable();
    }

    private boolean isExistingCandidate(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private boolean isNewCandidate(DbCandidate dbCandidate) {
        return !isExistingCandidate(dbCandidate.publicationId()) && dbCandidate.applicable();
    }

    private DbApprovalStatus toUpdatedApprovalStatus(DbApprovalStatus oldApprovalStatus,
                                                     DbApprovalStatus newApprovalStatus) {
        if (updateIsAssignee(oldApprovalStatus, newApprovalStatus)) {
            return updateAssignee(oldApprovalStatus, newApprovalStatus);
        } else {
            return updateStatus(oldApprovalStatus, newApprovalStatus);
        }
    }

    private DbApprovalStatus updateAssignee(DbApprovalStatus oldApprovalStatus, DbApprovalStatus newApprovalStatus) {
        return oldApprovalStatus.copy()
                   .status(newApprovalStatus.status())
                   .finalizedBy(null)
                   .assignee(Optional.of(newApprovalStatus).map(DbApprovalStatus::assignee).orElse(null))
                   .finalizedDate(null)
                   .build();
    }

    private Optional<Candidate> createCandidate(DbCandidate dbCandidate) {
        var candidate = createCandidate(dbCandidate, generatePendingApprovalStatuses(dbCandidate.points()));
        return Optional.of(candidate.copy().withReportStatus(getReportStatus(candidate)).build());
    }

    private Candidate createCandidate(DbCandidate dbCandidate, List<DbApprovalStatus> approvalStatuses) {
        var candidate = nviCandidateRepository.create(dbCandidate, approvalStatuses);
        return candidate.copy().withReportStatus(getReportStatus(candidate)).build();
    }

    private ReportStatus getReportStatus(Candidate candidate) {
        var period = nviPeriodRepository.findByPublishingYear(candidate.candidate().publicationDate().year());
        return period.isEmpty() || isClosed(period.get())
                   ? ReportStatus.NOT_REPORTABLE
                   : ReportStatus.REPORTABLE;

    }

    private boolean isClosed(DbNviPeriod dbNviPeriod) {
        return dbNviPeriod.reportingDate().isBefore(Instant.now());
    }

    private Optional<Candidate> updateCandidate(DbCandidate dbCandidate) {
        var existingCandidate = findByPublicationId(dbCandidate.publicationId()).orElseThrow();
        //TODO: Reset NVI Candidates here. See https://unit.atlassian.net/browse/NP-45113;
        var updatedCandidate = updateCandidate(existingCandidate.identifier(), dbCandidate,
                                          generatePendingApprovalStatuses(dbCandidate.points()));
        return Optional.of(updatedCandidate.copy().withReportStatus(getReportStatus(updatedCandidate)).build());
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
        if (period.reportingDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException(NOT_SUPPORTED_REPORTING_DATE_MESSAGE);
        }
    }
}
