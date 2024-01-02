package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequestWithLevel;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
import static no.unit.nva.testutils.RandomDataGenerator.randomBoolean;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
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
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CandidateTest extends LocalDynamoTest {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String API_DOMAIN = ENVIRONMENT.readEnv("API_HOST");
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    public static Stream<Arguments> candidateResetCauseProvider() {
        return Stream.of(Arguments.of(Named.of("change level",
                                               new CandidateResetCauseArgument(DbLevel.LEVEL_ONE,
                                                                               InstanceType.ACADEMIC_MONOGRAPH,
                                                                               URI.create("uri")))),
                         Arguments.of(Named.of("change type",
                                               new CandidateResetCauseArgument(DbLevel.LEVEL_TWO,
                                                                               InstanceType.ACADEMIC_LITERATURE_REVIEW,
                                                                               URI.create("uri")))),
                         Arguments.of(Named.of("points changed",
                                               new CandidateResetCauseArgument(DbLevel.LEVEL_TWO,
                                                                               InstanceType.ACADEMIC_MONOGRAPH,
                                                                               randomUri(),
                                                                               randomUri()))));
    }

    @Deprecated
    public static Stream<Arguments> levelValues() {
        return Stream.of(Arguments.of(DbLevel.LEVEL_ONE, "LevelOne"), Arguments.of(DbLevel.LEVEL_TWO, "LevelTwo"));
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
        var candidate = Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow().toDto();
        var fetchedCandidate = Candidate.fetch(candidate::identifier, candidateRepository, periodRepository)
                                   .toDto();
        assertThat(fetchedCandidate, is(equalTo(candidate)));
    }

    @Deprecated
    @ParameterizedTest
    @MethodSource("levelValues")
    void shouldPersistNewCandidateWithCorrectLevelBasedOnVersionTwoLevelValues(DbLevel expectedLevel,
                                                                               String versionTwoValue) {
        var request = createUpsertCandidateRequestWithLevel(versionTwoValue, randomUri());
        var candidateIdentifier = Candidate.upsert(request, candidateRepository, periodRepository)
                                      .orElseThrow()
                                      .getIdentifier();
        var persistedCandidate = candidateRepository.findCandidateById(candidateIdentifier)
                                     .orElseThrow()
                                     .candidate();
        assertEquals(expectedLevel, persistedCandidate.level());
    }

    @Test
    void shouldPersistNewCandidateWithCorrectDataFromUpsertRequest() {
        var request = createUpsertCandidateRequest(randomUri());
        var candidateIdentifier = Candidate.upsert(request, candidateRepository, periodRepository)
                                      .orElseThrow()
                                      .getIdentifier();
        var expectedCandidate = generateExpectedCandidate(candidateIdentifier, request);
        var actualPersistedCandidate = candidateRepository.findCandidateById(candidateIdentifier)
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @Test
    void shouldPersistUpdatedCandidateWithCorrectDataFromUpsertRequest() {
        var updateRequest = getUpdateRequestForExistingCandidate();
        var candidateIdentifier = Candidate.upsert(updateRequest, candidateRepository, periodRepository)
                                      .orElseThrow()
                                      .getIdentifier();
        var expectedCandidate = generateExpectedCandidate(candidateIdentifier, updateRequest);
        var actualPersistedCandidate = candidateRepository.findCandidateById(candidateIdentifier)
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @Test
    void shouldFetchCandidateByPublicationId() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        var fetchedCandidate = Candidate.fetchByPublicationId(() -> candidate.getPublicationDetails().publicationId(),
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

    @Test
    void dontMindMeJustTestingToDto() {
        var institutionToReject = randomUri();
        var institutionToApprove = randomUri();
        var totalPoints = randomBigDecimal();
        var createRequest = createUpsertCandidateRequest(randomUri(), randomUri(), true,
                                                         InstanceType.ACADEMIC_MONOGRAPH, 4, totalPoints,
                                                         TestUtils.randomLevelExcluding(DbLevel.NON_CANDIDATE)
                                                             .getVersionOneValue(), CURRENT_YEAR,
                                                         institutionToApprove, randomUri(), institutionToReject);
        var candidateBO = Candidate.upsert(createRequest, candidateRepository, periodRepository).orElseThrow();
        candidateBO.createNote(createNoteRequest(randomString(), randomString()))
            .createNote(createNoteRequest(randomString(), randomString()))
            .updateApproval(createUpdateStatusRequest(ApprovalStatus.APPROVED, institutionToApprove, randomString()))
            .updateApproval(createUpdateStatusRequest(ApprovalStatus.REJECTED, institutionToReject, randomString()));
        var dto = candidateBO.toDto();
        var approvalMap = dto.approvals()
                              .stream()
                              .collect(Collectors.toMap(ApprovalDto::institutionId, Function.identity()));

        assertAll(() -> {
            assertThat(dto.publicationId(), is(equalTo(createRequest.publicationId())));
            assertThat(dto.approvals().size(), is(equalTo(createRequest.institutionPoints().size())));
            assertThat(dto.notes().size(), is(2));
            assertThat(dto.totalPoints(), is(equalTo(totalPoints)));
            var note = dto.notes().get(0);
            assertThat(note.text(), is(notNullValue()));
            assertThat(note.user(), is(notNullValue()));
            assertThat(note.createdDate(), is(notNullValue()));
            assertThat(dto.id(), is(equalTo(constructId(candidateBO.getIdentifier()))));
            assertThat(dto.identifier(), is(equalTo(candidateBO.getIdentifier())));
            var periodStatus = getDefaultPeriodStatus();
            assertThat(dto.periodStatus().status(), is(equalTo(periodStatus.status())));
            assertThat(approvalMap.get(institutionToApprove).status(), is(equalTo(ApprovalStatus.APPROVED)));
            var rejectedAP = approvalMap.get(institutionToReject);
            assertThat(rejectedAP.status(), is(equalTo(ApprovalStatus.REJECTED)));
            assertThat(rejectedAP.reason(), is(notNullValue()));
            assertThat(rejectedAP.points(), is(createRequest.institutionPoints().get(rejectedAP.institutionId())));
        });
    }

    @Test
    void shouldReturnCandidateWithExpectedData() {
        var publicationId = randomUri();
        var publicationBucketUri = randomUri();
        var isApplicable = true;
        var institution = randomUri();
        var creators = Map.of(randomUri(), List.of(institution));
        var points = Map.of(institution, randomBigDecimal());
        var totalPoints = randomBigDecimal();
        var publicationDate = new PublicationDate(String.valueOf(CURRENT_YEAR), null, null);
        var instanceType = randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE);
        var createRequest = createUpsertCandidateRequest(publicationId, publicationBucketUri,
                                                         isApplicable,
                                                         publicationDate,
                                                         creators,
                                                         instanceType,
                                                         randomElement(ChannelType.values()).getValue(), randomUri(),
                                                         DbLevel.LEVEL_TWO.getVersionOneValue(), points,
                                                         randomInteger(10),
                                                         randomBoolean(),
                                                         randomBigDecimal(), randomBigDecimal(), totalPoints);
        var candidate = Candidate.upsert(createRequest, candidateRepository, periodRepository).orElseThrow();
        assertEquals(candidate.getPublicationDetails().publicationBucketUri(), publicationBucketUri);
        assertEquals(candidate.isApplicable(), isApplicable);
        assertEquals(candidate.getPublicationDetails().publicationId(), publicationId);
        assertEquals(candidate.getTotalPoints(), totalPoints);
        assertEquals(candidate.getInstitutionPoints(), points);
        assertEquals(candidate.getPublicationDetails().publicationDate(), publicationDate);
        assertCorrectCreatorData(creators, candidate);
        assertEquals(candidate.getPublicationDetails().type(), instanceType.getValue());
        assertEquals(candidate.getPublicationDetails().channelType().getValue(), createRequest.channelType());
        assertEquals(candidate.getPublicationDetails().publicationChannelId(), createRequest.publicationChannelId());
        assertEquals(candidate.getPublicationDetails().level(), createRequest.level());
        assertEquals(candidate.getBasePoints(), createRequest.basePoints());
        assertEquals(candidate.isInternationalCollaboration(),
                     createRequest.isInternationalCollaboration());
        assertEquals(candidate.getCollaborationFactor(), createRequest.collaborationFactor());
        assertEquals(candidate.getCreatorShareCount(), createRequest.creatorShareCount());
    }

    @Test
    void shouldReturnCandidateWithNoPeriodWhenNotApplicable() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var tempCandidate = Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository)
                                .orElseThrow();
        var updateRequest = createUpsertCandidateRequest(tempCandidate.getPublicationDetails().publicationId(),
                                                         randomUri(), false,
                                                         InstanceType.ACADEMIC_MONOGRAPH, 4, randomBigDecimal(),
                                                         TestUtils.randomLevelExcluding(DbLevel.NON_CANDIDATE)
                                                             .getVersionOneValue(), CURRENT_YEAR,
                                                         randomUri(), randomUri(),
                                                         randomUri());
        var candidateBO = Candidate.upsert(updateRequest, candidateRepository, periodRepository).orElseThrow();
        var fetchedCandidate = Candidate.fetch(candidateBO::getIdentifier, candidateRepository,
                                               periodRepository);
        assertThat(fetchedCandidate.getPeriodStatus().status(), is(equalTo(Status.NO_PERIOD)));
    }

    @Test
    void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {
        var institutionId = randomUri();
        var assignee = randomString();
        var createCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = Candidate.upsert(createCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow()
                            .updateApproval(new UpdateAssigneeRequest(institutionId, assignee))
                            .updateApproval(
                                createUpdateStatusRequest(ApprovalStatus.APPROVED, institutionId, randomString()))
                            .updateApproval(
                                createUpdateStatusRequest(ApprovalStatus.REJECTED, institutionId, randomString()))
                            .toDto();
        assertThat(candidate.approvals().get(0).assignee(), is(equalTo(assignee)));
        assertThat(candidate.approvals().get(0).finalizedBy(), is(not(equalTo(assignee))));
    }

    @Test
    void shouldNotResetApprovalsWhenUpdatingCandidateFieldsNotEffectingApprovals() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        candidate.updateApproval(
            new UpdateStatusRequest(institutionId, ApprovalStatus.APPROVED, randomString(), randomString()));
        var approval = candidate.toDto().approvals().get(0);
        var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest);
        var updatedCandidate = Candidate.upsert(newUpsertRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        var updatedApproval = updatedCandidate.toDto().approvals().get(0);

        assertThat(updatedApproval, is(equalTo(approval)));
    }

    @Test
    void shouldResetApprovalsWhenNonCandidateBecomesCandidate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        var nonCandidate = Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationDetails()
                                                .publicationId()),
            candidateRepository).orElseThrow();
        assertFalse(nonCandidate.isApplicable());

        var updatedCandidate = Candidate.upsert(
            createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest), candidateRepository,
            periodRepository).orElseThrow();

        assertTrue(updatedCandidate.isApplicable());
        assertThat(updatedCandidate.getApprovals().size(), is(greaterThan(0)));
    }

    @ParameterizedTest
    @MethodSource("candidateResetCauseProvider")
    @DisplayName("Should reset approvals when updating fields effecting approvals")
    void shouldResetApprovalsWhenUpdatingFieldsEffectingApprovals(CandidateResetCauseArgument arguments) {
        URI[] institutionIdsOriginal = new URI[]{URI.create("uri")};
        DbLevel originalLevel = DbLevel.LEVEL_TWO;
        InstanceType originalType = InstanceType.ACADEMIC_MONOGRAPH;

        var upsertCandidateRequest = createUpsertCandidateRequest(URI.create("publicationId"), randomUri(), true,
                                                                  new PublicationDate(
                                                                      String.valueOf(CURRENT_YEAR), null,
                                                                      null), getCreators(institutionIdsOriginal),
                                                                  originalType,
                                                                  randomString(), randomUri(),
                                                                  originalLevel.getVersionOneValue(),
                                                                  getPointsOriginal(institutionIdsOriginal),
                                                                  randomInteger(), false,
                                                                  randomBigDecimal(), null, randomBigDecimal());

        var candidate = Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();

        candidate.updateApproval(
            new UpdateStatusRequest(Arrays.stream(arguments.institutionIds()).findFirst().orElseThrow(),
                                    ApprovalStatus.APPROVED, randomString(), randomString()));

        var newUpsertRequest = createUpsertCandidateRequest(candidate.getPublicationDetails().publicationId(),
                                                            randomUri(), true,
                                                            new PublicationDate(String.valueOf(CURRENT_YEAR),
                                                                                null, null),
                                                            getCreators(arguments.institutionIds()), arguments.type(),
                                                            randomString(), randomUri(),
                                                            arguments.level().getVersionOneValue(),
                                                            getPointsOriginal(arguments.institutionIds()),
                                                            randomInteger(), false,
                                                            TestUtils.randomBigDecimal(), null, randomBigDecimal());

        var updatedCandidate = Candidate.upsert(newUpsertRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        var updatedApproval = updatedCandidate.toDto().approvals().get(0);

        assertThat(updatedApproval.status(), is(equalTo(ApprovalStatus.PENDING)));
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

    private static Map<URI, BigDecimal> getPointsOriginal(URI[] institutionIdsOriginal) {
        return Arrays.stream(institutionIdsOriginal)
                   .collect(Collectors.toMap(Function.identity(), e -> new BigDecimal(1)));
    }

    private static Map<URI, List<URI>> getCreators(URI[] institutionIdsOriginal) {
        return Map.of(URI.create("uri"), List.of(institutionIdsOriginal));
    }

    private static PeriodStatusDto getDefaultPeriodStatus() {
        return PeriodStatusDto.fromPeriodStatus(PeriodStatus.builder().withStatus(Status.OPEN_PERIOD).build());
    }

    private static URI constructId(UUID identifier) {
        return new UriWrapper(HTTPS, API_DOMAIN).addChild(BASE_PATH, "candidate", identifier.toString()).getUri();
    }

    private UpsertCandidateRequest getUpdateRequestForExistingCandidate() {
        var institutionId = randomUri();
        var insertRequest = createUpsertCandidateRequest(institutionId);
        Candidate.upsert(insertRequest, candidateRepository, periodRepository);
        return createUpsertCandidateRequest(insertRequest.publicationId(),
                                            insertRequest.publicationBucketUri(), true,
                                            InstanceType.parse(insertRequest.instanceType()),
                                            insertRequest.creators().size(), randomBigDecimal(),
                                            TestUtils.randomLevelExcluding(DbLevel.NON_CANDIDATE).getVersionOneValue(),
                                            CURRENT_YEAR,
                                            institutionId);
    }

    private DbCandidate generateExpectedCandidate(UUID identifier, UpsertCandidateRequest request) {
        return new CandidateDao(identifier,
                                DbCandidate.builder()
                                    .publicationId(request.publicationId())
                                    .publicationBucketUri(request.publicationBucketUri())
                                    .publicationDate(mapToDbPublicationDate(request.publicationDate()))
                                    .applicable(request.isApplicable())
                                    .instanceType(InstanceType.parse(request.instanceType()))
                                    .channelType(ChannelType.parse(request.channelType()))
                                    .channelId(request.publicationChannelId())
                                    .level(DbLevel.parse(request.level()))
                                    .basePoints(request.basePoints())
                                    .internationalCollaboration(request.isInternationalCollaboration())
                                    .collaborationFactor(request.collaborationFactor())
                                    .creators(mapToDbCreators(request.creators()))
                                    .creatorShareCount(request.creatorShareCount())
                                    .points(mapToDbInstitutionPoints(request.institutionPoints()))
                                    .totalPoints(request.totalPoints())
                                    .build(), randomString()).candidate();
    }

    private DbPublicationDate mapToDbPublicationDate(PublicationDate publicationDate) {
        return new DbPublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private List<DbInstitutionPoints> mapToDbInstitutionPoints(Map<URI, BigDecimal> points) {
        return points.entrySet()
                   .stream()
                   .map(entry -> new DbInstitutionPoints(entry.getKey(), entry.getValue()))
                   .toList();
    }

    private List<DbCreator> mapToDbCreators(Map<URI, List<URI>> creators) {
        return creators.entrySet().stream().map(entry -> new DbCreator(entry.getKey(), entry.getValue())).toList();
    }

    private UpsertCandidateRequest createNewUpsertRequestNotAffectingApprovals(UpsertCandidateRequest request) {
        return new UpsertCandidateRequest() {
            @Override
            public URI publicationBucketUri() {
                return request.publicationBucketUri();
            }

            @Override
            public URI publicationId() {
                return request.publicationId();
            }

            @Override
            public boolean isApplicable() {
                return true;
            }

            @Override
            public boolean isInternationalCollaboration() {
                return false;
            }

            @Override
            public Map<URI, List<URI>> creators() {
                return request.creators();
            }

            @Override
            public String channelType() {
                return null;
            }

            @Override
            public URI publicationChannelId() {
                return null;
            }

            @Override
            public String level() {
                return request.level();
            }

            @Override
            public String instanceType() {
                return request.instanceType();
            }

            @Override
            public PublicationDate publicationDate() {
                return request.publicationDate();
            }

            @Override
            public int creatorShareCount() {
                return 0;
            }

            @Override
            public BigDecimal collaborationFactor() {
                return null;
            }

            @Override
            public BigDecimal basePoints() {
                return null;
            }

            @Override
            public Map<URI, BigDecimal> institutionPoints() {
                return request.institutionPoints();
            }

            @Override
            public BigDecimal totalPoints() {
                return request.totalPoints();
            }
        };
    }

    //TODO; add xDto to DTO classes

    private record CandidateResetCauseArgument(DbLevel level, InstanceType type, URI... institutionIds) {

    }
}