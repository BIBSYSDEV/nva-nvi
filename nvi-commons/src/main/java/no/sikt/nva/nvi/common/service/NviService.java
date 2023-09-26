package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.model.ApprovalStatusDao.ApprovalStatusData;
import no.sikt.nva.nvi.common.db.model.ApprovalStatusDao.Status;
import no.sikt.nva.nvi.common.db.model.CandidateDao.CandidateData;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstitutionPoints;
import no.sikt.nva.nvi.common.db.model.CandidateDao.PublicationDate;
import no.sikt.nva.nvi.common.db.model.NoteDao.NoteData;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.model.PeriodDao.PeriodData;
import no.sikt.nva.nvi.common.db.NviPeriodRepository;
import no.sikt.nva.nvi.common.db.model.CandidateDao.InstanceType;
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
    public Optional<Candidate> upsertCandidate(CandidateData candidateData) {
        if (isNewCandidate(candidateData)) {
            validateCandidate(candidateData);
            return createCandidate(candidateData);
        }
        if (isExistingCandidate(candidateData)) {
            validateCandidate(candidateData);
            return updateCandidate(candidateData);
        }
        if (shouldBeDeleted(candidateData)) {
            return updateCandidateToNotApplicable(candidateData);
        }
        return Optional.empty();
    }

    public PeriodData createPeriod(PeriodData period) {
        validatePeriod(period);
        return nviPeriodRepository.save(period);
    }

    public PeriodData updatePeriod(PeriodData period) {
        var nviPeriod = injectCreatedBy(period);
        validatePeriod(nviPeriod);
        return nviPeriodRepository.save(nviPeriod);
    }

    public PeriodData getPeriod(String publishingYear) {
        //TODO: Handle not-found. optional?
        return nviPeriodRepository.findByPublishingYear(publishingYear).orElseThrow();
    }

    public List<PeriodData> getPeriods() {
        return nviPeriodRepository.getPeriods();
    }

    public ApprovalStatusData updateApproval(UUID candidateIdentifier, ApprovalStatusData newApproval) {
        candidateIsEditable(candidateIdentifier);
        return nviCandidateRepository.updateApprovalStatus(candidateIdentifier, newApproval);
    }

    public ApprovalStatusData findApprovalStatus(URI institutionId, UUID candidateIdentifier) {
        return nviCandidateRepository.findApprovalByIdAndInstitutionId(candidateIdentifier, institutionId)
                   .orElseThrow();
    }

    public Optional<Candidate> findCandidateById(UUID uuid) {
        return nviCandidateRepository.findCandidateById(uuid).map(this::injectPeriodStatus);
    }

    public Optional<Candidate> findByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).map(this::injectPeriodStatus);
    }

    public Candidate createNote(UUID candidateIdentifier, NoteData noteData) {
        candidateIsEditable(candidateIdentifier);
        if (nviCandidateRepository.exists(candidateIdentifier)) {
            nviCandidateRepository.saveNote(candidateIdentifier, noteData);
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

    private static boolean isNoteOwner(String requestUsername, NoteData note) {
        return note.user().value().equals(requestUsername);
    }

    private static boolean isInteger(String value) {
        return attempt(() -> Integer.parseInt(value)).map(ignore -> true).orElse((ignore) -> false);
    }

    private static boolean hasInvalidLength(PeriodData period) {
        return period.publishingYear().length() != 4;
    }

    private static List<ApprovalStatusData> generatePendingApprovalStatuses(List<InstitutionPoints> institutionPoints) {
        return institutionPoints.stream().map(NviService::toPendingApprovalStatus).toList();
    }

    private static ApprovalStatusData toPendingApprovalStatus(InstitutionPoints institutionPoints) {
        return ApprovalStatusData.builder()
                   .status(Status.PENDING)
                   .institutionId(institutionPoints.institutionId())
                   .build();
    }

    private static void validateCandidate(CandidateData candidate) {
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

    private static void assertIsCandidate(CandidateData candidate) {
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

    private PeriodData fetchPeriodForCandidate(Candidate candidate) {
        return nviPeriodRepository.findByPublishingYear(candidate.candidate().publicationDate().year()).orElseThrow();
    }

    private PeriodStatus getPeriodStatus(Candidate candidate) {
        return Optional.of(candidate.candidate())
                   .map(CandidateData::publicationDate)
                   .map(PublicationDate::year)
                   .flatMap(nviPeriodRepository::findByPublishingYear)
                   .map(PeriodStatus::fromPeriod)
                   .orElse(PeriodStatus.builder().withStatus(PeriodStatus.Status.NO_PERIOD).build());
    }

    private boolean shouldBeDeleted(CandidateData candidateData) {
        return isExistingCandidate(candidateData.publicationId()) && !candidateData.applicable();
    }

    private PeriodData injectCreatedBy(PeriodData period) {
        return period.copy().createdBy(getPeriod(period.publishingYear()).createdBy()).build();
    }

    private boolean isExistingCandidate(CandidateData candidateData) {
        return isExistingCandidate(candidateData.publicationId()) && candidateData.applicable();
    }

    private boolean isExistingCandidate(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId).isPresent();
    }

    private boolean isNewCandidate(CandidateData candidateData) {
        return !isExistingCandidate(candidateData.publicationId()) && candidateData.applicable();
    }

    private Optional<Candidate> createCandidate(CandidateData candidate) {
        return Optional.of(createCandidate(candidate, generatePendingApprovalStatuses(candidate.points())));
    }

    private Candidate createCandidate(CandidateData candidate, List<ApprovalStatusData> approvalStatuses) {
        var persistedCandidate = nviCandidateRepository.create(candidate, approvalStatuses);
        return persistedCandidate.copy().withPeriodStatus(getPeriodStatus(persistedCandidate)).build();
    }

    private Optional<Candidate> updateCandidate(CandidateData candidateData) {
        var existingCandidate = findByPublicationId(candidateData.publicationId()).orElseThrow();
        return Optional.of(updateCandidate(existingCandidate.identifier(), candidateData,
                                           generatePendingApprovalStatuses(candidateData.points())));
    }

    private Candidate updateCandidate(UUID identifier, CandidateData candidate,
                                      List<ApprovalStatusData> approvalStatuses) {
        return nviCandidateRepository.update(identifier, candidate, approvalStatuses);
    }

    private Optional<Candidate> updateCandidateToNotApplicable(CandidateData candidateData) {
        var existingCandidate = findByPublicationId(candidateData.publicationId()).orElseThrow();
        return Optional.of(updateCandidateRemovingApprovals(existingCandidate.identifier(), candidateData,
                                                            generatePendingApprovalStatuses(candidateData.points())));
    }

    private Candidate updateCandidateRemovingApprovals(UUID identifier, CandidateData candidate,
                                                       List<ApprovalStatusData> approvalStatuses) {
        return nviCandidateRepository.updateCandidateRemovingApprovals(identifier, candidate, approvalStatuses);
    }

    private void validatePeriod(PeriodData period) {
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
