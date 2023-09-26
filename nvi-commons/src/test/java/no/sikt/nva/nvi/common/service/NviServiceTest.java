package no.sikt.nva.nvi.common.service;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus.APPROVED;
import static no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus.REJECTED;
import static no.sikt.nva.nvi.common.db.PeriodStatus.Status.CLOSED_PERIOD;
import static no.sikt.nva.nvi.common.db.PeriodStatus.Status.NO_PERIOD;
import static no.sikt.nva.nvi.common.db.PeriodStatus.Status.OPEN_PERIOD;
import static no.sikt.nva.nvi.test.TestUtils.generatePublicationId;
import static no.sikt.nva.nvi.test.TestUtils.generateS3BucketUri;
import static no.sikt.nva.nvi.test.TestUtils.nviServiceReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateWithPublicationYear;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceType;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
import static no.sikt.nva.nvi.test.TestUtils.randomPublicationDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.NoteDao.DbNote;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.NviPeriodDao.DbNviPeriod;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class NviServiceTest extends LocalDynamoTest {

    public static final int YEAR = Calendar.getInstance().getWeekYear();
    private NviService nviService;
    private NviCandidateRepository nviCandidateRepository;

    public static Stream<Arguments> statusProvider() {
        return Stream.of(Arguments.of(DbStatus.PENDING, DbStatus.REJECTED),
                         Arguments.of(DbStatus.PENDING, DbStatus.APPROVED),
                         Arguments.of(DbStatus.APPROVED, DbStatus.PENDING),
                         Arguments.of(DbStatus.APPROVED, DbStatus.REJECTED),
                         Arguments.of(DbStatus.REJECTED, DbStatus.PENDING),
                         Arguments.of(DbStatus.APPROVED, DbStatus.APPROVED));
    }

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
        nviService = TestUtils.nviServiceReturningOpenPeriod(localDynamo, YEAR);
    }

    @Test
    void shouldCreateAndFetchPublicationById() {
        var identifier = randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var candidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                publicationDate, institutionPoints, true);

        var createdCandidate = nviService.upsertCandidate(candidate).orElseThrow();
        var createdCandidateId = createdCandidate.identifier();

        var fetchedCandidate = nviService.findCandidateById(createdCandidateId).orElseThrow().candidate();

        assertThat(fetchedCandidate, is(equalTo(candidate)));
    }

    @Test
    void shouldUpdateExistingCandidateWhenUpsertIsCalledAndTheCandidateExists() {
        var bucketIdentifier = randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());

        var expectedCandidate = createExpectedCandidate(bucketIdentifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints, true);

        var newInstanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var updatedCandidate = createExpectedCandidate(bucketIdentifier, verifiedCreators, newInstanceType, randomLevel,
                                                       publicationDate, institutionPoints, true);

        var originalUpserted = nviService.upsertCandidate(expectedCandidate).orElseThrow();
        var updatedUpserted = nviService.upsertCandidate(updatedCandidate).orElseThrow();
        assertThat(updatedUpserted, is(not(equalTo(originalUpserted))));

        var createdCandidateId = originalUpserted.identifier();

        var fetchedCandidate = nviService.findCandidateById(createdCandidateId).orElseThrow().candidate();

        assertThat(fetchedCandidate, is(equalTo(updatedCandidate)));
    }

    @Test
    void shouldCreateAndFetchPublicationByPublicationId() {
        var identifier = randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints, true);
        nviService.upsertCandidate(expectedCandidate);

        var fetchedCandidate = nviService.findByPublicationId(generatePublicationId(identifier))
                                   .orElseThrow()
                                   .candidate();

        assertThat(fetchedCandidate, is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldCreateUniquenessIdentifierWhenCreatingCandidate() {
        var identifier = randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints, true);
        nviService.upsertCandidate(expectedCandidate);

        var items = scanDB().items().size();

        assertThat(items, is(equalTo(3)));
    }

    @Test
    void shouldCreateCandidateWithPendingInstitutionApprovals() {
        var identifier = randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints, true);

        nviService.upsertCandidate(expectedCandidate);

        var fetchedCandidate = nviCandidateRepository.findByPublicationId(generatePublicationId(identifier))
                                   .map(Candidate::candidate);

        assertThat(fetchedCandidate.orElseThrow(), is(equalTo(expectedCandidate)));
    }

    @Test
    void shouldNotUpsertCandidateWhenNotNviCandidate() {
        var identifier = randomUUID();
        var institutionId = randomUri();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(institutionId)));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = randomPublicationDate();
        var institutionPoints = Map.of(institutionId, randomBigDecimal());
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, institutionPoints, false);

        var candidate = nviService.upsertCandidate(expectedCandidate);

        assertThat(candidate, is(Optional.empty()));
    }

    @Test
    void nviServiceShouldHandleDatesWithoutDayOrMonthValues() {
        var identifier = randomUUID();
        var verifiedCreators = List.of(new DbCreator(randomUri(), List.of(randomUri())),
                                       new DbCreator(randomUri(), List.of()));
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var randomLevel = randomElement(DbLevel.values());
        var publicationDate = new DbPublicationDate(null, null, "2022");
        var expectedCandidate = createExpectedCandidate(identifier, verifiedCreators, instanceType, randomLevel,
                                                        publicationDate, Map.of(), true);

        assertDoesNotThrow(() -> nviService.upsertCandidate(expectedCandidate));
    }

    @Test
    void shouldCreateNviPeriod() {
        var period = createPeriod("2050");
        var nviService = new NviService(localDynamo);
        nviService.createPeriod(period);
        assertThat(nviService.getPeriod(period.publishingYear()), is(equalTo(period)));
    }

    @Test
    void shouldUpdateNviPeriod() {
        var originalPeriod = createPeriod("2014");
        nviService.createPeriod(originalPeriod);
        nviService.updatePeriod(originalPeriod.copy().reportingDate(new Date(2060, 03, 25).toInstant()).build());
        var fetchedPeriod = nviService.getPeriod(originalPeriod.publishingYear());
        assertThat(fetchedPeriod, is(not(equalTo(originalPeriod))));
    }

    @Test
    void shouldReturnBadRequestWhenPublishingYearIsNotAYear() {
        var period = createPeriod(randomString());
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnBadRequestWhenPublishingYearHasInvalidLength() {
        var period = createPeriod("22");
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenReportingDateIsBeforeNow() {
        var period = DbNviPeriod.builder()
                         .reportingDate(Instant.MIN)
                         .publishingYear("2023")
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldReturnIllegalArgumentWhenPublishingYearIsNotAValidYear() {
        var period = DbNviPeriod.builder()
                         .reportingDate(Instant.MIN)
                         .publishingYear("now!")
                         .createdBy(Username.builder().value("me").build())
                         .build();
        assertThrows(IllegalArgumentException.class, () -> nviService.createPeriod(period));
    }

    @Test
    void shouldUpdateCandidate() {
        var institutionUri = randomUri();
        var candidateData = createDbCandidate(institutionUri);
        var dbApprovalStatus = List.of(createDbApprovalStatus(institutionUri));
        var fullCandidate = nviCandidateRepository.create(candidateData, dbApprovalStatus);
        var updatedCandidate = createDbCandidate(institutionUri);
        nviCandidateRepository.update(fullCandidate.identifier(), updatedCandidate, fullCandidate.approvalStatuses());
        var candidate1 = nviCandidateRepository.findCandidateById(fullCandidate.identifier());
        assertThat(candidate1.orElseThrow().candidate(), is(not(fullCandidate.candidate())));
    }

    @Test
    void shouldBeAbleToAddNotesWheNoteIsValid() {
        var institutionUri = randomUri();
        var candidateData = createDbCandidate(institutionUri);
        var dbApprovalStatus = List.of(createDbApprovalStatus(institutionUri));
        var fullCandidate = nviCandidateRepository.create(candidateData, dbApprovalStatus);
        var dbNote = new DbNote(randomUUID(), randomUsername(), randomString(), Instant.now());
        var candidate = nviService.createNote(fullCandidate.identifier(), dbNote);
        assertThat(candidate.notes(), hasSize(1));
    }

    @Test
    void shouldBeAbleToAddMultipleNotesWhenNotesExist() {
        var institutionUri = randomUri();
        var candidateData = createDbCandidate(institutionUri);
        var dbApprovalStatus = List.of(createDbApprovalStatus(institutionUri));
        var fullCandidate = nviCandidateRepository.create(candidateData, dbApprovalStatus);
        var dbNote = new DbNote(randomUUID(), randomUsername(), randomString(), Instant.now());
        nviService.createNote(fullCandidate.identifier(), dbNote);
        dbNote = new DbNote(randomUUID(), randomUsername(), randomString(), Instant.now());
        var candidate = nviService.createNote(fullCandidate.identifier(), dbNote);
        assertThat(candidate.notes(), hasSize(2));
    }

    @Test
    void shouldBeNotAbleToDeleteNoteWhenYouDidntCreateIt() {
        var institutionUri = randomUri();
        var candidateData = createDbCandidate(institutionUri);
        var dbApprovalStatus = List.of(createDbApprovalStatus(institutionUri));
        var fullCandidate = nviCandidateRepository.create(candidateData, dbApprovalStatus);
        var dbNote = DbNote.builder().user(randomUsername()).text(randomString()).build();
        nviService.createNote(fullCandidate.identifier(), dbNote);
        dbNote = DbNote.builder().user(randomUsername()).text(randomString()).build();
        var candidateWith2Notes = nviService.createNote(fullCandidate.identifier(), dbNote);
        assertThrows(IllegalArgumentException.class, () -> nviService.deleteNote(candidateWith2Notes.identifier(),
                                                                                 candidateWith2Notes.notes()
                                                                                     .get(0)
                                                                                     .noteId(), randomString()));
        assertThat(nviService.findCandidateById(fullCandidate.identifier()).orElseThrow().notes(), hasSize(2));
    }

    @Test
    void shouldBeAbleToDeleteNoteWhenYouCreatedIt() {
        var institutionUri = randomUri();
        var candidateData = createDbCandidate(institutionUri);
        var dbApprovalStatus = List.of(createDbApprovalStatus(institutionUri));
        var fullCandidate = nviCandidateRepository.create(candidateData, dbApprovalStatus);
        var dbNote = DbNote.builder().user(randomUsername()).text(randomString()).build();
        nviService.createNote(fullCandidate.identifier(), dbNote);
        var user = randomUsername();
        dbNote = DbNote.builder().user(user).text(randomString()).build();
        var candidateWith2Notes = nviService.createNote(fullCandidate.identifier(), dbNote);
        var noteIdentifier = getNoteIdentifier(candidateWith2Notes, user);
        var candidateWith1Note = nviService.deleteNote(candidateWith2Notes.identifier(), noteIdentifier, user.value());
        assertThat(candidateWith1Note.notes(), hasSize(1));
    }

    @Test
    void shouldReturnPeriodsOnlyWhenFetchingPeriods() {
        var nviService = new NviService(localDynamo);
        nviService.upsertCandidate(randomCandidate());
        nviService.createPeriod(createPeriod("2100"));
        nviService.createPeriod(createPeriod("2101"));
        var periods = nviService.getPeriods();
        assertThat(periods, hasSize(2));
    }

    @Test
    void shouldResetApprovalsButKeepNotesWhenUpdatingExistingCandidate() {
        var candidate = nviService.upsertCandidate(randomCandidate());
        var createdNotes = nviService.createNote(candidate.orElseThrow().identifier(), randomDbNote()).notes();
        var persistedCandidate = nviService.upsertCandidate(updateCandidate(candidate.orElseThrow()));
        var actualNotes = persistedCandidate.orElseThrow().notes();
        assertThat(actualNotes, is(equalTo(createdNotes)));
    }

    @Test
    void shouldUpdateCandidateRemovingApprovalsWhenCandidateIsNoLongerApplicable() {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var notApplicableCandidate = candidate.candidate().copy().applicable(false).build();
        nviService.upsertCandidate(notApplicableCandidate);
        var persistedCandidate = nviService.findCandidateById(candidate.identifier());
        assertThat(persistedCandidate.orElseThrow().candidate().applicable(), is(false));
        assertThat(persistedCandidate.orElseThrow().approvalStatuses(), is(empty()));
    }

    @Test
    void shouldRemoveAssigneeWhenExistingApprovalHasAssignee() {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var existingApprovalStatus = getSingleApproval(candidate);
        updateAssignee(existingApprovalStatus);
        var updatedApprovalStatus = existingApprovalStatus.copy().assignee(null).build();
        var actualApprovalStatus = nviService.updateApproval(candidate.identifier(), updatedApprovalStatus);

        assertThat(actualApprovalStatus.assignee(), is(nullValue()));
    }

    @Test
    void approvalShouldFetchItself() {
        var approval = getSingleApproval(nviService.upsertCandidate(randomCandidate()).orElseThrow());
        var fetchedApproval = approval.fetch(nviService);
        assertThat(approval, is(equalTo(fetchedApproval)));
    }

    @Test
    void approvalShouldUpdateAssigneeByItself() {
        var assignee = randomUsername();
        var approval = getSingleApproval(nviService.upsertCandidate(randomCandidate()).orElseThrow());
        var fetchedApproval = approval.update(nviService, new UpdateAssigneeRequest(assignee));
        assertThat(fetchedApproval.assignee(), is(equalTo(assignee)));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("statusProvider")
    void approvalShouldUpdateStatusByItself(DbStatus oldStatus, DbStatus newStatus) {
        var existingCandidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var approval = getSingleApproval(existingCandidate);
        updateApproval(existingCandidate, oldStatus);
        var fetchedApproval = approval.update(nviService, updateRequestWithReason(newStatus));
        assertThat(fetchedApproval.status(), is(equalTo(newStatus)));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldThrowUnsupportedOperationExceptionIfRejectingWithoutReason(DbStatus oldStatus) {
        var existingCandidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var approval = getSingleApproval(existingCandidate);
        updateApproval(existingCandidate, oldStatus);
        assertThrows(UnsupportedOperationException.class,
                     () -> approval.update(nviService, updateRequestWithoutReason(DbStatus.REJECTED)));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldRemoveReasonWhenUpdatingStatusFromReject(DbStatus newStatus) {
        var existingCandidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var approval = getSingleApproval(existingCandidate);
        updateApproval(existingCandidate, REJECTED);

        var actualApproval = approval.update(nviService, updateRequestWithoutReason(newStatus));
        assertThat(actualApproval.status(), is(equalTo(newStatus)));
        assertThat(actualApproval.reason(), is(nullValue()));
    }

    @Test
    void shouldKeepAssigneeWhenFinalizingApproval() {
        var existingCandidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var approval = getSingleApproval(existingCandidate);
        var assignee = randomUsername();
        var assignedApproval = updateAssignee(approval, assignee);
        var fetchedApproval = assignedApproval.update(nviService, updateRequestWithoutReason(APPROVED));

        assertThat(fetchedApproval.assignee(), is(equalTo(assignee)));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenUnknownUpdateRequest() {
        var approval = getSingleApproval(nviService.upsertCandidate(randomCandidate()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> approval.update(nviService, null));
    }

    @Test
    void shouldThrowInvalidNviCandidateExceptionWhenNviCandidateIsMissingMandatoryFields() {
        var candidate = randomCandidate().copy().publicationDate(null).build();
        assertThrows(InvalidNviCandidateException.class, () -> nviService.upsertCandidate(candidate));
    }

    @Test
    void shouldThrowExceptionWhenCreatingCandidateWithUndefinedInstanceType() {
        var candidate = randomCandidate().copy().instanceType(InstanceType.parse("asd")).build();
        assertThrows(InvalidNviCandidateException.class, () -> nviService.upsertCandidate(candidate));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingApprovalAndWhenReportingPeriodIsClosed() {
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var approval = candidate.approvalStatuses().get(0);
        var nviService = nviServiceReturningClosedPeriod(localDynamo, YEAR);

        assertThrows(IllegalStateException.class, () -> nviService.updateApproval(candidate.identifier(), approval));
    }

    @Test
    void shouldUpdateCandidateWhenPeriodIsOpen() {
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var approval = candidate.approvalStatuses().get(0);

        assertDoesNotThrow(() -> nviService.updateApproval(candidate.identifier(), approval));
    }

    @Test
    void shouldReturnCandidateWithOpenPeriodStatusWhenPeriodIsOpen() {
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();

        assertThat(candidate.periodStatus().status(), is(equalTo(OPEN_PERIOD)));
    }

    @Test
    void shouldReturnCandidateWithClosedPeriodStatusWhenPeriodIsClosed() {
        var nviService = nviServiceReturningClosedPeriod(localDynamo, YEAR);
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();

        assertThat(candidate.periodStatus().status(), is(equalTo(CLOSED_PERIOD)));
    }

    @Test
    void shouldReturnCandidateWithNoPeriodStatusWhenPeriodIsNotPresent() {
        var nviService = new NviService(localDynamo);
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();

        assertThat(candidate.periodStatus().periodClosesAt(), is(nullValue()));
        assertThat(candidate.periodStatus().status(), is(equalTo(NO_PERIOD)));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingNoteWhenPeriodIsClosed() {
        var nviService = nviServiceReturningClosedPeriod(localDynamo, YEAR);
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var note = new DbNote(randomUUID(), randomUsername(), randomString(), Instant.now());
        assertThrows(IllegalStateException.class, () -> nviService.createNote(candidate.identifier(), note));
    }

    @Test
    void shouldThrowExceptionWhenDeletingNoteWhenPeriodIsClosed() {
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var note = new DbNote(randomUUID(), randomUsername(), randomString(), Instant.now());
        var persistedNote = nviService.createNote(candidate.identifier(), note);
        var nviService = nviServiceReturningClosedPeriod(localDynamo, YEAR);
        assertThrows(IllegalStateException.class,
                     () -> nviService.deleteNote(candidate.identifier(), persistedNote.identifier(), randomString()));
    }

    private static UpdateStatusRequest updateRequestWithReason(DbStatus newStatus) {
        return UpdateStatusRequest.builder()
                   .withApprovalStatus(newStatus)
                   .withReason(DbStatus.REJECTED.equals(newStatus) ? randomString() : null)
                   .withUsername(randomString())
                   .build();
    }

    private static UpdateStatusRequest updateRequestWithoutReason(DbStatus dbStatus) {
        return UpdateStatusRequest.builder()
                   .withApprovalStatus(dbStatus)
                   .withUsername(randomString())
                   .build();
    }

    private static DbApprovalStatus getSingleApproval(Candidate existingCandidate) {
        return existingCandidate.approvalStatuses().get(0);
    }

    private static UUID getNoteIdentifier(Candidate candidateWith2Notes, Username user) {
        return candidateWith2Notes.notes()
                   .stream()
                   .filter(n -> n.user().value().equals(user.value()))
                   .findFirst()
                   .map(DbNote::noteId)
                   .orElseThrow();
    }

    private static DbCandidate updateCandidate(Candidate candidate) {
        var creators = candidate.candidate().creators();
        var updatedCreators = new ArrayList<>(creators);
        updatedCreators.add(new DbCreator(randomUri(), List.of(randomUri())));
        return candidate.candidate().copy().creators(updatedCreators).build();
    }

    private static DbNote randomDbNote() {
        return DbNote.builder().text(randomString()).user(randomUsername()).build();
    }

    private static DbNviPeriod createPeriod(String publishingYear) {
        return DbNviPeriod.builder()
                   .reportingDate(new Date(2050, 03, 25).toInstant())
                   .publishingYear(publishingYear)
                   .createdBy(randomUsername())
                   .build();
    }

    private static DbApprovalStatus createDbApprovalStatus(URI institutionUri) {
        return DbApprovalStatus.builder()
                   .institutionId(institutionUri)
                   .status(APPROVED)
                   .finalizedBy(new Username("metoo"))
                   .finalizedDate(Instant.now())
                   .build();
    }

    private static DbCandidate createDbCandidate(URI institutionUri) {
        var publicationIdentifier = randomUUID();
        return DbCandidate.builder()
                   .publicationBucketUri(generateS3BucketUri(publicationIdentifier))
                   .publicationId(generatePublicationId(publicationIdentifier))
                   .creators(List.of(new DbCreator(randomUri(), List.of(institutionUri))))
                   .instanceType(randomInstanceType())
                   .level(randomElement(DbLevel.values()))
                   .applicable(true)
                   .publicationDate(new DbPublicationDate(randomString(), randomString(), randomString()))
                   .points(List.of(new DbInstitutionPoints(institutionUri, new BigDecimal("1.2"))))
                   .build();
    }

    private static Username randomUsername() {
        return Username.fromString(randomString());
    }

    private static List<DbInstitutionPoints> mapToInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
        return institutionPoints.entrySet()
                   .stream()
                   .map(entry -> new DbInstitutionPoints(entry.getKey(), entry.getValue()))
                   .toList();
    }

    private void updateApproval(Candidate existingCandidate, DbStatus status) {
        nviService.updateApproval(existingCandidate.identifier(),
                                  getSingleApproval(existingCandidate).copy()
                                      .status(status)
                                      .reason(DbStatus.REJECTED.equals(status) ? randomString() : null)
                                      .build());
    }

    private DbApprovalStatus updateAssignee(DbApprovalStatus approval, Username assignee) {
        return approval.update(nviService, new UpdateAssigneeRequest(assignee));
    }

    private void updateAssignee(DbApprovalStatus existingApprovalStatus) {
        nviService.updateApproval(existingApprovalStatus.candidateIdentifier(),
                                  existingApprovalStatus.copy().assignee(randomUsername()).build());
    }

    private DbCandidate createExpectedCandidate(UUID identifier, List<DbCreator> creators, InstanceType instanceType,
                                                DbLevel level, DbPublicationDate publicationDate,
                                                Map<URI, BigDecimal> institutionPoints, boolean applicable) {
        return DbCandidate.builder()
                   .publicationBucketUri(generateS3BucketUri(identifier))
                   .publicationId(generatePublicationId(identifier))
                   .creators(creators)
                   .instanceType(instanceType)
                   .level(level)
                   .applicable(applicable)
                   .publicationDate(publicationDate)
                   .points(mapToInstitutionPoints(institutionPoints))
                   .build();
    }
}