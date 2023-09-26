package no.sikt.nva.nvi.common.service;

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
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviService {

    public static final String INVALID_LENGTH_MESSAGE = "Provided period has invalid length!";
    public static final String PERIOD_NOT_NUMERIC_MESSAGE = "Period is not numeric!";
    public static final String NOT_SUPPORTED_REPORTING_DATE_MESSAGE = "Provided reporting date is not supported";
    public static final String INVALID_CANDIDATE_MESSAGE = "Candidate is missing mandatory fields";
    private final NviCandidateRepository nviCandidateRepository;
    private final NviPeriodRepository nviPeriodRepository;

    public NviService(DynamoDbClient dynamoDbClient) {
        this.nviCandidateRepository = new NviCandidateRepository(dynamoDbClient);
        this.nviPeriodRepository = new NviPeriodRepository(dynamoDbClient);
    }

    public NviService(DynamoDbClient dynamoDbClient, NviPeriodRepository periodRepository) {
        this.nviCandidateRepository = new NviCandidateRepository(dynamoDbClient);
        this.nviPeriodRepository = periodRepository;
    }

    @JacocoGenerated
    public static NviService defaultNviService() {
        return new NviService(defaultDynamoClient());
    }

    @JacocoGenerated //TODO Temporary for coverage
    public Optional<Candidate> upsertCandidate(DbCandidate dbCandidate) {
        if (isNewCandidate(dbCandidate)) {
            validateCandidate(dbCandidate);
            return createCandidate(dbCandidate);
        }
        if (isExistingCandidate(dbCandidate)) {
            validateCandidate(dbCandidate);
            return updateCandidate(dbCandidate);
        }
        if (shouldBeDeleted(dbCandidate)) {
            return updateCandidateToNotApplicable(dbCandidate);
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

    public DbApprovalStatus updateApproval(UUID candidateIdentifier, DbApprovalStatus newApproval) {
        candidateIsEditable(candidateIdentifier);
        return nviCandidateRepository.updateApprovalStatus(candidateIdentifier, newApproval);
    }

    public DbApprovalStatus findApprovalStatus(URI institutionId, UUID candidateIdentifier) {
        return nviCandidateRepository.findApprovalByIdAndInstitutionId(candidateIdentifier, institutionId)
                   .orElseThrow();
    }

    public Optional<Candidate> findCandidateById(UUID uuid) {
        return nviCandidateRepository.findCandidateById(uuid).map(this::injectPeriodStatus);
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).map(this::injectPeriodStatus);
    }

    public Candidate createNote(UUID candidateIdentifier, DbNote dbNote) {
        candidateIsEditable(candidateIdentifier);
        if (nviCandidateRepository.exists(candidateIdentifier)) {
            nviCandidateRepository.saveNote(candidateIdentifier, dbNote);
        }
        return nviCandidateRepository.findCandidateById(candidateIdentifier)
                   .map(this::injectPeriodStatus)
                   .orElseThrow();
    }

    public Candidate deleteNote(UUID candidateIdentifier, UUID noteIdentifier, String username) {
        candidateIsEditable(candidateIdentifier);
        var note = nviCandidateRepository.getNoteById(candidateIdentifier, noteIdentifier);
        if (isNoteOwner(username, note)) {
            nviCandidateRepository.deleteNote(candidateIdentifier, noteIdentifier);
            return nviCandidateRepository.findCandidateById(candidateIdentifier)
                       .map(this::injectPeriodStatus).orElseThrow();
        }
        throw new IllegalArgumentException("User not allowed to remove others note.");
    }

    private static boolean isNoteOwner(String requestUsername, DbNote note) {
        return note.user().value().equals(requestUsername);
    }

    private static boolean isInteger(String value) {
        return attempt(() -> Integer.parseInt(value)).map(ignore -> true).orElse((ignore) -> false);
    }

    private static boolean hasInvalidLength(DbNviPeriod period) {
        return period.publishingYear().length() != 4;
    }

    private static List<DbApprovalStatus> generatePendingApprovalStatuses(List<DbInstitutionPoints> institutionPoints) {
        return institutionPoints.stream().map(NviService::toPendingApprovalStatus).toList();
    }

    private static DbApprovalStatus toPendingApprovalStatus(DbInstitutionPoints institutionPoints) {
        return DbApprovalStatus.builder()
                   .status(DbStatus.PENDING)
                   .institutionId(institutionPoints.institutionId())
                   .build();
    }

    private static void validateCandidate(DbCandidate candidate) {
        attempt(() -> {
            assertIsCandidate(candidate);
            Objects.requireNonNull(candidate.publicationBucketUri());
            Objects.requireNonNull(candidate.points());
            Objects.requireNonNull(candidate.publicationId());
            Objects.requireNonNull(candidate.creators());
            Objects.requireNonNull(candidate.level());
            Objects.requireNonNull(candidate.publicationDate());
            return candidate;
        }).orElseThrow(failure -> new InvalidNviCandidateException(INVALID_CANDIDATE_MESSAGE));
    }

    private static void assertIsCandidate(DbCandidate candidate) {
        if (InstanceType.NON_CANDIDATE.equals(candidate.instanceType())) {
            throw new InvalidNviCandidateException("Can not update invalid candidate");
        }
    }

    private void candidateIsEditable(UUID candidateIdentifier) {
        var candidate = findCandidateById(candidateIdentifier).orElseThrow();
        var period = fetchPeriodForCandidate(candidate);
        candidate.isEditableForPeriod(period);
    }

    private Candidate injectPeriodStatus(Candidate candidate) {
        return candidate.copy().withPeriodStatus(getPeriodStatus(candidate)).build();
    }

    private DbNviPeriod fetchPeriodForCandidate(Candidate candidate) {
        return nviPeriodRepository.findByPublishingYear(candidate.candidate().publicationDate().year()).orElseThrow();
    }

    private PeriodStatus getPeriodStatus(Candidate candidate) {
        return Optional.of(candidate.candidate())
                   .map(DbCandidate::publicationDate)
                   .map(DbPublicationDate::year)
                   .flatMap(nviPeriodRepository::findByPublishingYear)
                   .map(PeriodStatus::fromPeriod)
                   .orElse(PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
    }

    private boolean shouldBeDeleted(DbCandidate dbCandidate) {
        return isExistingCandidate(dbCandidate.publicationId()) && !dbCandidate.applicable();
    }

    private DbNviPeriod injectCreatedBy(DbNviPeriod period) {
        return period.copy().createdBy(getPeriod(period.publishingYear()).createdBy()).build();
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

    private Optional<Candidate> createCandidate(DbCandidate candidate) {
        return Optional.of(createCandidate(candidate, generatePendingApprovalStatuses(candidate.points())));
    }

    private Candidate createCandidate(DbCandidate candidate, List<DbApprovalStatus> approvalStatuses) {
        var persistedCandidate = nviCandidateRepository.create(candidate, approvalStatuses);
        return persistedCandidate.copy().withPeriodStatus(getPeriodStatus(persistedCandidate)).build();
    }

    private Optional<Candidate> updateCandidate(DbCandidate dbCandidate) {
        var existingCandidate = findByPublicationId(dbCandidate.publicationId()).orElseThrow();
        return Optional.of(updateCandidate(existingCandidate.identifier(), dbCandidate,
                                           generatePendingApprovalStatuses(dbCandidate.points())));
    }

    private Candidate updateCandidate(UUID identifier, DbCandidate candidate, List<DbApprovalStatus> approvalStatuses) {
        return nviCandidateRepository.update(identifier, candidate, approvalStatuses);
    }

    private Optional<Candidate> updateCandidateToNotApplicable(DbCandidate dbCandidate) {
        var existingCandidate = findByPublicationId(dbCandidate.publicationId()).orElseThrow();
        return Optional.of(updateCandidateRemovingApprovals(existingCandidate.identifier(), dbCandidate,
                                                            generatePendingApprovalStatuses(dbCandidate.points())));
    }

    private Candidate updateCandidateRemovingApprovals(UUID identifier, DbCandidate candidate,
                                                       List<DbApprovalStatus> approvalStatuses) {
        return nviCandidateRepository.updateCandidateRemovingApprovals(identifier, candidate, approvalStatuses);
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
