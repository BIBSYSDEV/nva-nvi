package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class NviService {

    public static final String INVALID_LENGTH_MESSAGE = "Provided period has invalid length!";
    public static final String PERIOD_NOT_NUMERIC_MESSAGE = "Period is not numeric!";
    public static final String NOT_SUPPORTED_REPORTING_DATE_MESSAGE = "Provided reporting date is not supported";
    public static final String INVALID_CANDIDATE_MESSAGE = "Candidate is missing mandatory fields";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;

    public NviService(DynamoDbClient dynamoDbClient) {
        this.candidateRepository = new CandidateRepository(dynamoDbClient);
        this.periodRepository = new PeriodRepository(dynamoDbClient);
    }

    public NviService(DynamoDbClient dynamoDbClient, PeriodRepository periodRepository) {
        this.candidateRepository = new CandidateRepository(dynamoDbClient);
        this.periodRepository = periodRepository;
    }

    @JacocoGenerated
    public static NviService defaultNviService() {
        return new NviService(defaultDynamoClient());
    }

    public Optional<Candidate> upsertCandidate(DbCandidate dbCandidate) {
        if (isNewCandidate(dbCandidate)) {
            validateCandidate(dbCandidate);
            return createCandidate(dbCandidate);
        }
        if (isUpdatableCandidate(dbCandidate)) {
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
        return periodRepository.save(period);
    }

    public DbNviPeriod updatePeriod(DbNviPeriod period) {
        var nviPeriod = injectCreatedBy(period);
        validatePeriod(nviPeriod);
        return periodRepository.save(nviPeriod);
    }

    public DbNviPeriod getPeriod(String publishingYear) {
        //TODO: Handle not-found. optional?
        return periodRepository.findByPublishingYear(publishingYear).orElseThrow();
    }

    public List<DbNviPeriod> getPeriods() {
        return periodRepository.getPeriods();
    }

    public DbApprovalStatus updateApproval(UUID candidateIdentifier, DbApprovalStatus newApproval) {
        candidateIsEditable(candidateIdentifier);
        return candidateRepository.updateApprovalStatus(candidateIdentifier, newApproval);
    }

    public DbApprovalStatus findApprovalStatus(URI institutionId, UUID candidateIdentifier) {
        return candidateRepository.findApprovalByIdAndInstitutionId(candidateIdentifier, institutionId)
                   .orElseThrow();
    }

    public Optional<Candidate> findApplicableCandidateById(UUID uuid) {
        var candidate = findCandidateById(uuid);
        return candidate.isPresent() && candidate.get().candidate().applicable() ? candidate : Optional.empty();
    }

    public Optional<Candidate> findCandidateById(UUID uuid) {
        return candidateRepository.findCandidateById(uuid).map(this::injectPeriodStatus);
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        return candidateRepository.findByPublicationId(publicationId).map(this::injectPeriodStatus);
    }

    public Candidate createNote(UUID candidateIdentifier, DbNote dbNote) {
        candidateIsEditable(candidateIdentifier);
        if (candidateRepository.exists(candidateIdentifier)) {
            candidateRepository.saveNote(candidateIdentifier, dbNote);
        }
        return candidateRepository.findCandidateById(candidateIdentifier)
                   .map(this::injectPeriodStatus)
                   .orElseThrow();
    }

    public Candidate deleteNote(UUID candidateIdentifier, UUID noteIdentifier, String username) {
        candidateIsEditable(candidateIdentifier);
        var note = candidateRepository.getNoteById(candidateIdentifier, noteIdentifier);
        if (isNoteOwner(username, note)) {
            candidateRepository.deleteNote(candidateIdentifier, noteIdentifier);
            return candidateRepository.findCandidateById(candidateIdentifier)
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
        return periodRepository.findByPublishingYear(candidate.candidate().publicationDate().year()).orElseThrow();
    }

    private PeriodStatus getPeriodStatus(Candidate candidate) {
        return Optional.of(candidate.candidate())
                   .map(DbCandidate::publicationDate)
                   .map(DbPublicationDate::year)
                   .flatMap(periodRepository::findByPublishingYear)
                   .map(PeriodStatus::fromPeriod)
                   .orElse(PeriodStatus.builder().withStatus(Status.NO_PERIOD).build());
    }

    private boolean shouldBeDeleted(DbCandidate dbCandidate) {
        return isExistingCandidate(dbCandidate.publicationId()) && !dbCandidate.applicable();
    }

    private DbNviPeriod injectCreatedBy(DbNviPeriod period) {
        return period.copy().createdBy(getPeriod(period.publishingYear()).createdBy()).build();
    }

    private boolean isUpdatableCandidate(DbCandidate dbCandidate) {
        return isExistingCandidate(dbCandidate.publicationId()) && dbCandidate.applicable();
    }

    private boolean isExistingCandidate(URI publicationId) {
        return candidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private boolean isNewCandidate(DbCandidate dbCandidate) {
        return !isExistingCandidate(dbCandidate.publicationId()) && dbCandidate.applicable();
    }

    private Optional<Candidate> createCandidate(DbCandidate candidate) {
        return Optional.of(createCandidate(candidate, generatePendingApprovalStatuses(candidate.points())));
    }

    private Candidate createCandidate(DbCandidate candidate, List<DbApprovalStatus> approvalStatuses) {
        var persistedCandidate = candidateRepository.create(candidate, approvalStatuses);
        return persistedCandidate.copy().withPeriodStatus(getPeriodStatus(persistedCandidate)).build();
    }

    private Optional<Candidate> updateCandidate(DbCandidate dbCandidate) {
        var existingCandidate = findByPublicationId(dbCandidate.publicationId()).orElseThrow();
        return Optional.of(updateCandidate(existingCandidate.identifier(), dbCandidate,
                                           generatePendingApprovalStatuses(dbCandidate.points())));
    }

    private Candidate updateCandidate(UUID identifier, DbCandidate candidate, List<DbApprovalStatus> approvalStatuses) {
        return candidateRepository.update(identifier, candidate, approvalStatuses);
    }

    private Optional<Candidate> updateCandidateToNotApplicable(DbCandidate candidate) {
        var existingCandidate = findByPublicationId(candidate.publicationId()).orElseThrow();
        return Optional.of(updateCandidateRemovingApprovals(existingCandidate.identifier(),
                                                            candidate,
                                                            existingCandidate.approvalStatuses()));
    }

    private Candidate updateCandidateRemovingApprovals(UUID identifier, DbCandidate candidate,
                                                       List<DbApprovalStatus> approvalStatuses) {
        return candidateRepository.updateCandidateRemovingApprovals(identifier, candidate, approvalStatuses);
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
