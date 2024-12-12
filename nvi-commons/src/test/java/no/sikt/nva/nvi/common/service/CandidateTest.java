package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequestWithLevel;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomApplicableCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomApproval;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceType;
import static no.sikt.nva.nvi.test.TestUtils.randomLevelExcluding;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.sikt.nva.nvi.test.TestUtils.setupReportedCandidate;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.UpsertRequestBuilder;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
class CandidateTest extends LocalDynamoTest {

    protected static final int EXPECTED_SCALE = 4;
    protected static final RoundingMode EXPECTED_ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    public static final URI CONTEXT_URI = UriWrapper.fromHost(API_DOMAIN).addChild(BASE_PATH, "context").getUri();
    protected CandidateRepository candidateRepository;
    protected PeriodRepository periodRepository;

    protected static UpsertCandidateRequest createUpsertRequestWithDecimalScale(int scale, URI institutionId) {
        var creatorId = randomUri();
        var creators = Map.of(creatorId, List.of(institutionId));
        var points = List.of(createInstitutionPoints(institutionId, randomBigDecimal(scale), creatorId));

        return new UpsertRequestBuilder()
                   .withPoints(points)
                   .withCreators(creators)
                   .withCollaborationFactor(randomBigDecimal(scale))
                   .withBasePoints(randomBigDecimal(scale))
                   .withTotalPoints(randomBigDecimal(scale))
                   .build();
    }

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(ZonedDateTime.now().getYear());
    }

    @Test
    void shouldThrowNotFoundExceptionWhenCandidateDoesNotExist() {
        assertThrows(CandidateNotFoundException.class,
                     () -> Candidate.fetch(UUID::randomUUID, candidateRepository, periodRepository));
    }

    @Test
    void shouldReturnCandidateWhenExists() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var fetchedCandidate = upsert(upsertCandidateRequest);
        assertNotNull(fetchedCandidate);
    }

    @Deprecated
    @ParameterizedTest
    @MethodSource("levelValues")
    void shouldPersistNewCandidateWithCorrectLevelBasedOnVersionTwoLevelValues(DbLevel expectedLevel,
                                                                               String versionTwoValue) {
        var request = createUpsertCandidateRequestWithLevel(versionTwoValue, randomUri());
        Candidate.upsert(request, candidateRepository, periodRepository);
        var persistedCandidate = candidateRepository.findByPublicationId(request.publicationId())
                                     .orElseThrow()
                                     .candidate();
        assertEquals(expectedLevel, persistedCandidate.level());
    }

    @Test
    void shouldPersistNewCandidateWithCorrectDataFromUpsertRequest() {
        var request = createUpsertCandidateRequest(randomUri());
        var candidate = upsert(request);
        var expectedCandidate = generateExpectedCandidate(candidate, request);
        var actualPersistedCandidate = candidateRepository.findCandidateById(candidate.getIdentifier())
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4})
    void shouldPersistNewCandidateWithCorrectScaleForAllDecimals(int scale) {
        var request = createUpsertRequestWithDecimalScale(scale, randomUri());
        var candidate = upsert(request);
        var expectedCandidate = generateExpectedCandidate(candidate, request);
        var actualPersistedCandidate = candidateRepository.findCandidateById(candidate.getIdentifier())
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4})
    void shouldUpdateCandidateWithCorrectScaleForAllDecimals(int scale) {
        var request = new UpsertRequestBuilder()
                          .withCollaborationFactor(randomBigDecimal(scale))
                          .withBasePoints(randomBigDecimal(scale))
                          .withTotalPoints(randomBigDecimal(scale))
                          .build();
        var candidate = upsert(request);
        var expectedCandidate = generateExpectedCandidate(candidate, request);
        var actualPersistedCandidate = candidateRepository.findCandidateById(candidate.getIdentifier())
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @Test
    void shouldUpdateCandidateWithSystemGeneratedModifiedDate() {
        var request = getUpdateRequestForExistingCandidate();
        var candidate = Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
        Candidate.upsert(request, candidateRepository, periodRepository);
        var updatedCandidate = candidateRepository.findCandidateById(candidate.getIdentifier())
                                   .orElseThrow()
                                   .candidate();
        assertNotEquals(candidate.getModifiedDate(), updatedCandidate.modifiedDate());
    }

    @Test
    void shouldNotUpdateCandidateCreatedDateOnUpdates() {
        var request = getUpdateRequestForExistingCandidate();
        var candidate = Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
        var expectedCreatedDate = candidate.getCreatedDate();
        Candidate.upsert(request, candidateRepository, periodRepository);
        var actualPersistedCandidate = candidateRepository.findCandidateById(candidate.getIdentifier())
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCreatedDate, actualPersistedCandidate.createdDate());
    }

    @Test
    void shouldNotUpdateReportedCandidate() {
        var year = randomYear();
        var candidate = setupReportedCandidate(candidateRepository, year).candidate();
        var updateRequest = createUpsertCandidateRequest(candidate.publicationId(),
                                                         randomUri(),
                                                         true,
                                                         InstanceType.parse(candidate.instanceType()),
                                                         candidate.creatorCount(),
                                                         candidate.totalPoints(),
                                                         candidate.level().getValue(),
                                                         Integer.parseInt(year),
                                                         randomUri());
        assertThrows(IllegalCandidateUpdateException.class,
                     () -> Candidate.upsert(updateRequest, candidateRepository, periodRepository));
    }

    @Test
    void shouldPersistUpdatedCandidateWithCorrectDataFromUpsertRequest() {
        var updateRequest = getUpdateRequestForExistingCandidate();
        var candidate = upsert(updateRequest);
        var expectedCandidate = generateExpectedCandidate(candidate, updateRequest);
        var actualPersistedCandidate = candidateRepository.findCandidateById(candidate.getIdentifier())
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @Test
    void shouldFetchCandidateByPublicationId() {
        var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
        var fetchedCandidate = Candidate.fetchByPublicationId(candidate::getPublicationId,
                                                              candidateRepository,
                                                              periodRepository);
        assertThat(fetchedCandidate.getIdentifier(), is(equalTo(candidate.getIdentifier())));
    }

    @Test
    void shouldDoNothingIfCreateRequestIsForNonCandidateThatDoesNotExist() {
        var updateRequest = createUpsertNonCandidateRequest(randomUri());

        var optionalCandidate = Candidate.updateNonCandidate(updateRequest, candidateRepository);
        assertThat(optionalCandidate, is(equalTo(Optional.empty())));
    }

    @ParameterizedTest(name = "Should return global approval status {0} when all approvals have status {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"APPROVED", "REJECTED"})
    void shouldReturnGlobalApprovalStatus(ApprovalStatus approvalStatus) {
        var institution1 = randomUri();
        var institution2 = randomUri();
        var createRequest = createUpsertCandidateRequest(institution1, institution2);
        var candidate = upsert(createRequest);
        candidate.updateApproval(createUpdateStatusRequest(approvalStatus, institution1, randomString()));
        candidate.updateApproval(createUpdateStatusRequest(approvalStatus, institution2, randomString()));
        assertEquals(approvalStatus.getValue(), candidate.getGlobalApprovalStatus().getValue());
    }

    @Test()
    @DisplayName("Should return global approval status pending when any approval is pending")
    void shouldReturnGlobalApprovalStatusPendingWhenAnyApprovalIsPending() {
        var createRequest = createUpsertCandidateRequest(randomUri());
        var candidate = upsert(createRequest);
        assertEquals(GlobalApprovalStatus.PENDING, candidate.getGlobalApprovalStatus());
    }

    @Test()
    @DisplayName("Should return global approval status dispute when a candidate has at least one Rejected and one "
                 + "Approved approval")
    void shouldReturnGlobalApprovalStatusDisputeWhenConflictsExist() {
        var institution1 = randomUri();
        var institution2 = randomUri();
        var institution3 = randomUri();
        var createRequest = createUpsertCandidateRequest(institution1, institution2, institution3);
        var candidate = upsert(createRequest);
        candidate.updateApproval(createUpdateStatusRequest(ApprovalStatus.APPROVED, institution1, randomString()));
        candidate.updateApproval(createUpdateStatusRequest(ApprovalStatus.REJECTED, institution2, randomString()));
        assertEquals(ApprovalStatus.PENDING, candidate.getApprovals().get(institution3).getStatus());
        assertEquals(GlobalApprovalStatus.DISPUTE, candidate.getGlobalApprovalStatus());
    }

    @Test
    void dontMindMeJustTestingToDto() {
        var institutionToReject = randomUri();
        var institutionToApprove = randomUri();
        var totalPoints = randomBigDecimal();
        var createRequest = createUpsertCandidateRequest(randomUri(), randomUri(), true,
                                                         InstanceType.ACADEMIC_MONOGRAPH, 4, totalPoints,
                                                         randomLevelExcluding(DbLevel.NON_CANDIDATE)
                                                             .getValue(), CURRENT_YEAR,
                                                         institutionToApprove, randomUri(), institutionToReject);
        var candidateBO = upsert(createRequest);
        candidateBO.createNote(createNoteRequest(randomString(), randomString()), candidateRepository)
            .createNote(createNoteRequest(randomString(), randomString()), candidateRepository)
            .updateApproval(createUpdateStatusRequest(ApprovalStatus.APPROVED, institutionToApprove, randomString()))
            .updateApproval(createUpdateStatusRequest(ApprovalStatus.REJECTED, institutionToReject, randomString()));
        var dto = candidateBO.toDto();
        var approvalMap = dto.approvals()
                              .stream()
                              .collect(Collectors.toMap(ApprovalDto::institutionId, Function.identity()));

        assertAll(() -> {
            assertThat(dto.context(), is(equalTo(CONTEXT_URI)));
            assertThat(dto.publicationId(), is(equalTo(createRequest.publicationId())));
            assertThat(dto.approvals().size(), is(equalTo(createRequest.institutionPoints().size())));
            assertThat(dto.notes().size(), is(2));
            assertThat(dto.totalPoints(), is(equalTo(adjustScaleAndRoundingMode(totalPoints))));
            var note = dto.notes().getFirst();
            assertThat(note.text(), is(notNullValue()));
            assertThat(note.user(), is(notNullValue()));
            assertThat(note.createdDate(), is(notNullValue()));
            assertThat(dto.id(), is(equalTo(constructId(candidateBO.getIdentifier()))));
            assertThat(dto.identifier(), is(equalTo(candidateBO.getIdentifier())));
            var periodStatus = getDefaultPeriodStatus();
            assertThat(dto.period().status(), is(equalTo(periodStatus.status())));
            var approvalDto = approvalMap.get(institutionToApprove);
            assertThat(approvalDto.status().getValue(), is(equalTo(ApprovalStatusDto.APPROVED.getValue())));
            assertNotNull(approvalDto.finalizedDate());
            var rejectedAP = approvalMap.get(institutionToReject);
            assertThat(rejectedAP.status(), is(equalTo(ApprovalStatusDto.REJECTED)));
            assertThat(rejectedAP.reason(), is(notNullValue()));
            assertThat(rejectedAP.points(),
                       is(adjustScaleAndRoundingMode(
                           createRequest.getPointsForInstitution(rejectedAP.institutionId()))));
        });
    }

    @Test
    void shouldReturnCandidateWithExpectedData() {
        var publicationId = randomUri();
        var publicationBucketUri = randomUri();
        var isApplicable = true;
        var institutionId = randomUri();
        var creatorId = randomUri();
        var creators = Map.of(creatorId, List.of(institutionId));
        var institutionPoints = adjustScaleAndRoundingMode(randomBigDecimal());
        var points = List.of(createInstitutionPoints(institutionId, institutionPoints, creatorId));
        var totalPoints = randomBigDecimal();
        var publicationDate = new PublicationDate(String.valueOf(CURRENT_YEAR), null, null);
        var instanceType = randomInstanceType();

        var createRequest = new UpsertRequestBuilder()
                                .withPublicationId(publicationId)
                                .withPublicationBucketUri(publicationBucketUri)
                                .withIsApplicable(isApplicable)
                                .withPublicationDate(publicationDate)
                                .withCreators(creators)
                                .withInstanceType(instanceType)
                                .withChannelType(randomElement(ChannelType.values()).getValue())
                                .withLevel(DbLevel.LEVEL_TWO.getValue())
                                .withPoints(points)
                                .withTotalPoints(totalPoints)
                                .build();
        var candidate = upsert(createRequest);
        assertEquals(candidate.getPublicationDetails().publicationBucketUri(), publicationBucketUri);
        assertEquals(candidate.isApplicable(), isApplicable);
        assertEquals(candidate.getPublicationId(), publicationId);
        assertEquals(candidate.getTotalPoints(), adjustScaleAndRoundingMode(totalPoints));
        assertEquals(adjustScaleAndRoundingMode(candidate.getPointValueForInstitution(institutionId)),
                     institutionPoints);
        assertEquals(candidate.getInstitutionPointsMap().get(institutionId), institutionPoints);
        assertEquals(candidate.getInstitutionPoints(), points);
        assertEquals(candidate.getPublicationDetails().publicationDate(), publicationDate);
        assertCorrectCreatorData(creators, candidate);
        assertEquals(candidate.getPublicationDetails().type(), instanceType.getValue());
        assertEquals(candidate.getPublicationChannelType().getValue(),
                     createRequest.channelType());
        assertEquals(candidate.getPublicationChannelId(), createRequest.publicationChannelId());
        assertEquals(candidate.getScientificLevel(), createRequest.level());
        assertEquals(candidate.getBasePoints(), adjustScaleAndRoundingMode(createRequest.basePoints()));
        assertEquals(candidate.isInternationalCollaboration(),
                     createRequest.isInternationalCollaboration());
        assertEquals(candidate.getCollaborationFactor(),
                     adjustScaleAndRoundingMode(createRequest.collaborationFactor()));
        assertEquals(candidate.getCreatorShareCount(), createRequest.creatorShareCount());
    }

    @Test
    void shouldReturnCandidateWithNoPeriodWhenNotApplicable() {
        var tempCandidate = randomApplicableCandidate(candidateRepository, periodRepository);
        var updateRequest = createUpsertNonCandidateRequest(tempCandidate.getPublicationId());
        var candidateBO = Candidate.updateNonCandidate(updateRequest, candidateRepository).orElseThrow();
        var fetchedCandidate = Candidate.fetch(candidateBO::getIdentifier, candidateRepository,
                                               periodRepository);
        assertThat(fetchedCandidate.getPeriod().status(), is(equalTo(Status.NO_PERIOD)));
    }

    @Test
    void shouldSetPeriodYearWhenResettingCandidate() {
        var nonApplicableCandidate = nonApplicableCandidate();
        Candidate.upsert(
            createUpsertCandidateRequest(nonApplicableCandidate.getPublicationId()),
            candidateRepository, periodRepository
        );
        var updatedApplicableCandidate = Candidate.fetch(nonApplicableCandidate::getIdentifier, candidateRepository,
                                                         periodRepository);
        assertThat(updatedApplicableCandidate.getPeriod().year(),
                   is(equalTo(updatedApplicableCandidate.getPeriod().year())));
    }

    @Test
    void shouldReturnNviCandidateContextAsString() {
        var expectedContext = stringFromResources(Path.of("nviCandidateContext.json"));
        assertThat(Candidate.getJsonLdContext(), is(equalTo(expectedContext)));
    }

    @Test
    void shouldReturnContextUri() {
        var contextUri = Candidate.getContextUri();
        assertThat(contextUri, is(equalTo(CONTEXT_URI)));
    }

    @Test
    void shouldReturnCandidateWithReportStatus() {
        var dao = setupReportedCandidate(candidateRepository, randomYear());
        var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
        assertThat(candidate.toDto().status(), is(equalTo(ReportStatus.REPORTED.getValue())));
        assertThat(candidate.toDto().status(), is(equalTo(ReportStatus.REPORTED.getValue())));
    }

    @Test
    void shouldReturnTrueIfReportStatusIsReported() {
        var dao = candidateRepository.create(randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
                                             List.of(randomApproval()));
        var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
        assertTrue(candidate.isReported());
    }

    @Test
    void shouldReturnCandidateWithPeriodStatusContainingPeriodId() {
        var dao = candidateRepository.create(randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
                                             List.of(randomApproval()));
        var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
        var id = PeriodStatusDto.fromPeriodStatus(candidate.getPeriod()).id();

        assertThat(id, is(not(nullValue())));
    }

    @Test
    void shouldReturnCandidateWithPeriodStatusContainingPeriodIdWhenFetchingByPublicationId() {
        var dao = candidateRepository.create(randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
                                             List.of(randomApproval()));
        var candidate = Candidate.fetchByPublicationId(() -> dao.candidate().publicationId(), candidateRepository,
                                                       periodRepository);
        var id = PeriodStatusDto.fromPeriodStatus(candidate.getPeriod()).id();

        assertThat(id, is(not(nullValue())));
    }

    @Test
    void shouldReturnCreatorAffiliations() {
        var creator1affiliation = randomUri();
        var creator2affiliation = randomUri();
        var creator1 = DbCreator.builder().creatorId(randomUri()).affiliations(List.of(creator1affiliation)).build();
        var creator2 = DbCreator.builder().creatorId(randomUri()).affiliations(List.of(creator2affiliation)).build();
        var dao = candidateRepository.create(randomCandidate().copy().creators(List.of(creator1, creator2)).build(),
                                             List.of(randomApproval()));
        var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
        assertEquals(List.of(creator1affiliation, creator2affiliation), candidate.getNviCreatorAffiliations());
    }

    @Test
    void shouldUpdateVersion() {
        var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
        var dao = candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow();

        candidate.updateVersion(candidateRepository);

        var updatedCandidate = Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository);
        var updatedDao = candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow();

        assertEquals(candidate, updatedCandidate);
        assertNotEquals(dao.version(), updatedDao.version());
    }

    @Deprecated
    private static Stream<Arguments> levelValues() {
        return Stream.of(Arguments.of(DbLevel.LEVEL_ONE, "LevelOne"), Arguments.of(DbLevel.LEVEL_TWO, "LevelTwo"));
    }

    private static InstitutionPoints createInstitutionPoints(URI institutionId, BigDecimal institutionPoints,
                                                             URI creatorId) {
        return new InstitutionPoints(institutionId, institutionPoints,
                                     List.of(new CreatorAffiliationPoints(creatorId, institutionId,
                                                                          institutionPoints)));
    }

    private static void assertCorrectCreatorData(Map<URI, List<URI>> creators, Candidate candidate) {
        creators.forEach((key, value) -> {
            var actualCreator =
                candidate.getPublicationDetails()
                    .creators()
                    .stream()
                    .filter(creator -> creator.id().equals(key))
                    .findFirst()
                    .orElse(null);
            assert actualCreator != null;
            assertEquals(value, actualCreator.affiliations());
        });
    }

    private static PeriodStatusDto getDefaultPeriodStatus() {
        return PeriodStatusDto.fromPeriodStatus(PeriodStatus.builder().withStatus(Status.OPEN_PERIOD).build());
    }

    private static URI constructId(UUID identifier) {
        return new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, "candidate", identifier.toString()).getUri();
    }

    private static BigDecimal adjustScaleAndRoundingMode(BigDecimal bigDecimal) {
        return bigDecimal.setScale(EXPECTED_SCALE, EXPECTED_ROUNDING_MODE);
    }

    private Candidate upsert(UpsertCandidateRequest request) {
        Candidate.upsert(request, candidateRepository, periodRepository);
        return Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
    }

    private List<InstitutionPoints> createPoints(Map<URI, List<URI>> creators) {
        return creators.entrySet()
                   .stream()
                   .map(entry -> entry.getValue()
                                     .stream()
                                     .map(affiliation -> {
                                         var institutionPoints = randomBigDecimal();
                                         return createInstitutionPoints(affiliation, institutionPoints, entry.getKey());
                                     })
                                     .toList())
                   .flatMap(List::stream)
                   .toList();
    }

    private Candidate nonApplicableCandidate() {
        var tempCandidate = randomApplicableCandidate(candidateRepository, periodRepository);
        var updateRequest = createUpsertNonCandidateRequest(tempCandidate.getPublicationId());
        var candidateBO = Candidate.updateNonCandidate(updateRequest, candidateRepository).orElseThrow();
        return Candidate.fetch(candidateBO::getIdentifier, candidateRepository, periodRepository);
    }

    private UpsertCandidateRequest getUpdateRequestForExistingCandidate() {
        var institutionId = randomUri();
        var insertRequest = createUpsertCandidateRequest(institutionId);
        Candidate.upsert(insertRequest, candidateRepository, periodRepository);
        return UpsertRequestBuilder.fromRequest(insertRequest).build();
    }

    private DbCandidate generateExpectedCandidate(Candidate candidate, UpsertCandidateRequest request) {
        return new CandidateDao(candidate.getIdentifier(),
                                DbCandidate.builder()
                                    .publicationId(request.publicationId())
                                    .publicationBucketUri(request.publicationBucketUri())
                                    .publicationDate(mapToDbPublicationDate(request.publicationDate()))
                                    .applicable(request.isApplicable())
                                    .instanceType(request.instanceType().getValue())
                                    .channelType(ChannelType.parse(request.channelType()))
                                    .channelId(request.publicationChannelId())
                                    .level(DbLevel.parse(request.level()))
                                    .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
                                    .internationalCollaboration(request.isInternationalCollaboration())
                                    .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
                                    .creators(mapToDbCreators(request.creators()))
                                    .creatorShareCount(request.creatorShareCount())
                                    .points(mapToDbInstitutionPoints(request.institutionPoints()))
                                    .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
                                    .createdDate(candidate.getCreatedDate())
                                    .build(), randomString(), randomString()).candidate();
    }

    private DbPublicationDate mapToDbPublicationDate(PublicationDate publicationDate) {
        return new DbPublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private List<DbInstitutionPoints> mapToDbInstitutionPoints(List<InstitutionPoints> points) {
        return points.stream()
                   .map(DbInstitutionPoints::from)
                   .toList();
    }

    private List<DbCreator> mapToDbCreators(Map<URI, List<URI>> creators) {
        return creators.entrySet().stream().map(entry -> new DbCreator(entry.getKey(), entry.getValue())).toList();
    }
}