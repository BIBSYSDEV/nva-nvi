package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createNoteRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.paths.UriWrapper.HTTPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
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
import no.sikt.nva.nvi.common.service.dto.ApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.NviApprovalStatus;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.requests.PublicationDate;
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

class CandidateBOTest extends LocalDynamoTest {

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

    @BeforeEach
    void setup() {
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(ZonedDateTime.now().getYear());
    }

    @Test
    void shouldThrowNotFoundExceptionWhenCandidateDoesNotExist() {
        assertThrows(CandidateNotFoundException.class,
                     () -> CandidateBO.fromRequest(UUID::randomUUID, candidateRepository, periodRepository));
    }

    @Test
    void shouldReturnCandidateWhenExists() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        var fetchedCandidate = CandidateBO.fromRequest(candidate::identifier, candidateRepository, periodRepository);
        assertThat(fetchedCandidate.identifier(), is(equalTo(candidate.identifier())));
    }

    @Test
    void shouldPersistNewCandidateWithCorrectDataFromUpsertRequest() {
        var request = createUpsertCandidateRequest(randomUri());
        var candidateIdentifier = CandidateBO.fromRequest(request, candidateRepository, periodRepository)
                                      .orElseThrow()
                                      .identifier();
        var expectedCandidate = generateExpectedCandidate(candidateIdentifier, request);
        var actualPersistedCandidate = candidateRepository.findCandidateDaoById(candidateIdentifier)
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @Test
    void shouldPersistUpdatedCandidateWithCorrectDataFromUpsertRequest() {
        var institutionId = randomUri();
        var insertRequest = createUpsertCandidateRequest(institutionId);
        CandidateBO.fromRequest(insertRequest, candidateRepository, periodRepository);
        var updateRequest = createUpsertCandidateRequest(insertRequest.publicationId(),
                                                         insertRequest.publicationBucketUri(), true,
                                                         InstanceType.parse(insertRequest.instanceType()),
                                                         insertRequest.creators().size(),
                                                         institutionId);
        var candidateIdentifier = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository)
                                      .orElseThrow()
                                      .identifier();
        var expectedCandidate = generateExpectedCandidate(candidateIdentifier, updateRequest);
        var actualPersistedCandidate = candidateRepository.findCandidateDaoById(candidateIdentifier)
                                           .orElseThrow()
                                           .candidate();
        assertEquals(expectedCandidate, actualPersistedCandidate);
    }

    @Test
    void shouldFetchCandidateByPublicationId() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        var fetchedCandidate = CandidateBO.fromRequest(candidate::publicationId, candidateRepository, periodRepository);
        assertThat(fetchedCandidate.identifier(), is(equalTo(candidate.identifier())));
    }

    @Test
    void shouldDoNothingIfCreateRequestIsForNonCandidateThatDoesNotExist() {
        var updateRequest = createUpsertCandidateRequest(randomUri(), randomUri(), false, InstanceType.NON_CANDIDATE, 2,
                                                         randomUri());

        var optionalCandidate = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository);
        assertThat(optionalCandidate, is(equalTo(Optional.empty())));
    }

    @Test
    void dontMindMeJustTestingToDto() {
        var institutionToReject = randomUri();
        var institutionToApprove = randomUri();
        var createRequest = createUpsertCandidateRequest(randomUri(), randomUri(), true,
                                                         InstanceType.ACADEMIC_MONOGRAPH, 4,
                                                         institutionToApprove, randomUri(), institutionToReject);
        var candidateBO = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository).orElseThrow();
        candidateBO.createNote(createNoteRequest(randomString(), randomString()))
            .createNote(createNoteRequest(randomString(), randomString()))
            .updateApproval(createUpdateStatusRequest(DbStatus.APPROVED, institutionToApprove, randomString()))
            .updateApproval(createUpdateStatusRequest(DbStatus.REJECTED, institutionToReject, randomString()));
        var dto = candidateBO.toDto();
        var approvalMap = dto.approvalStatuses()
                              .stream()
                              .collect(Collectors.toMap(ApprovalStatus::institutionId, Function.identity()));

        assertAll(() -> {
            assertThat(dto.publicationId(), is(equalTo(createRequest.publicationId())));
            assertThat(dto.approvalStatuses().size(), is(equalTo(createRequest.institutionPoints().size())));
            assertThat(dto.notes().size(), is(2));
            var note = dto.notes().get(0);
            assertThat(note.text(), is(notNullValue()));
            assertThat(note.user(), is(notNullValue()));
            assertThat(note.createdDate(), is(notNullValue()));
            assertThat(dto.id(), is(equalTo(constructId(candidateBO.identifier()))));
            assertThat(dto.identifier(), is(equalTo(candidateBO.identifier())));
            var periodStatus = getDefaultPeriodStatus();
            assertThat(dto.periodStatus().status(), is(equalTo(periodStatus.status())));
            assertThat(approvalMap.get(institutionToApprove).status(), is(equalTo(NviApprovalStatus.APPROVED)));
            var rejectedAP = approvalMap.get(institutionToReject);
            assertThat(rejectedAP.status(), is(equalTo(NviApprovalStatus.REJECTED)));
            assertThat(rejectedAP.reason(), is(notNullValue()));
            assertThat(rejectedAP.points(), is(createRequest.institutionPoints().get(rejectedAP.institutionId())));
        });
    }

    @Test
    void toDtoShouldThrowCandidateNotFoundException() {
        var institutionToReject = randomUri();
        var institutionToApprove = randomUri();
        var createRequest = createUpsertCandidateRequest(randomUri(), randomUri(), true,
                                                         InstanceType.ACADEMIC_MONOGRAPH, 4,
                                                         institutionToApprove, randomUri(), institutionToReject);
        var tempCandidateBO = CandidateBO.fromRequest(createRequest, candidateRepository, periodRepository)
                                  .orElseThrow();
        var updateRequest = createUpsertCandidateRequest(tempCandidateBO.publicationId(), randomUri(), false,
                                                         InstanceType.ACADEMIC_MONOGRAPH, 4,
                                                         institutionToApprove,
                                                         randomUri(), institutionToReject);
        var candidateBO = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository).orElseThrow();

        assertThrows(CandidateNotFoundException.class, candidateBO::toDto);
    }

    @Test
    void shouldReturnCandidateWithNoPeriodWhenNotApplicable() {
        var upsertCandidateRequest = createUpsertCandidateRequest(randomUri());
        var tempCandidateBO = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                                  .orElseThrow();
        var updateRequest = createUpsertCandidateRequest(tempCandidateBO.publicationId(), randomUri(), false,
                                                         InstanceType.ACADEMIC_MONOGRAPH, 4,
                                                         randomUri(), randomUri(),
                                                         randomUri());
        var candidateBO = CandidateBO.fromRequest(updateRequest, candidateRepository, periodRepository).orElseThrow();
        var fetchedCandidate = CandidateBO.fromRequest(candidateBO::identifier, candidateRepository, periodRepository);
        assertThat(fetchedCandidate.periodStatus().status(), is(equalTo(Status.NO_PERIOD)));
    }

    @Test
    void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {
        var institutionId = randomUri();
        var assignee = randomString();
        var createCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(createCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow()
                            .updateApproval(new UpdateAssigneeRequest(institutionId, assignee))
                            .updateApproval(createUpdateStatusRequest(DbStatus.APPROVED, institutionId, randomString()))
                            .updateApproval(createUpdateStatusRequest(DbStatus.REJECTED, institutionId, randomString()))
                            .toDto();
        assertThat(candidate.approvalStatuses().get(0).assignee(), is(equalTo(assignee)));
        assertThat(candidate.approvalStatuses().get(0).finalizedBy(), is(not(equalTo(assignee))));
    }

    @Test
    void shouldNotResetApprovalsWhenUpdatingFieldsNotEffectingApprovals() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId);
        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();
        candidate.updateApproval(
            new UpdateStatusRequest(institutionId, DbStatus.APPROVED, randomString(), randomString()));
        var approval = candidate.toDto().approvalStatuses().get(0);
        var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest);
        var updatedCandidate = CandidateBO.fromRequest(newUpsertRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        var updatedApproval = updatedCandidate.toDto().approvalStatuses().get(0);

        assertThat(updatedApproval, is(equalTo(approval)));
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
                                                                      String.valueOf(TestUtils.CURRENT_YEAR), null,
                                                                      null), getCreators(institutionIdsOriginal),
                                                                  originalType,
                                                                  randomString(), randomUri(), originalLevel,
                                                                  getPointsOriginal(institutionIdsOriginal),
                                                                  randomInteger(), false,
                                                                  TestUtils.randomBigDecimal(), null, null);

        var candidate = CandidateBO.fromRequest(upsertCandidateRequest, candidateRepository, periodRepository)
                            .orElseThrow();

        candidate.updateApproval(
            new UpdateStatusRequest(Arrays.stream(arguments.institutionIds()).findFirst().orElseThrow(),
                                    DbStatus.APPROVED, randomString(), randomString()));

        var newUpsertRequest = createUpsertCandidateRequest(candidate.publicationId(), randomUri(), true,
                                                            new PublicationDate(String.valueOf(TestUtils.CURRENT_YEAR),
                                                                                null, null),
                                                            getCreators(arguments.institutionIds()), arguments.type(),
                                                            randomString(), randomUri(), arguments.level(),
                                                            getPointsOriginal(arguments.institutionIds()),
                                                            randomInteger(), false,
                                                            TestUtils.randomBigDecimal(), null, null);

        var updatedCandidate = CandidateBO.fromRequest(newUpsertRequest, candidateRepository, periodRepository)
                                   .orElseThrow();
        var updatedApproval = updatedCandidate.toDto().approvalStatuses().get(0);

        assertThat(updatedApproval.status(), is(equalTo(NviApprovalStatus.PENDING)));
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

    private DbCandidate generateExpectedCandidate(UUID identifier, UpsertCandidateRequest request) {
        return new CandidateDao(identifier,
                                DbCandidate.builder()
                                    .publicationId(request.publicationId())
                                    .publicationBucketUri(request.publicationBucketUri())
                                    .publicationDate(mapToDbPublicationDate(request.publicationDate()))
                                    .applicable(request.isApplicable())
                                    .instanceType(InstanceType.parse(request.instanceType()))
                                    .channelType(ChannelType.parse(request.channelType()))
                                    .channelId(request.channelId())
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
            public URI channelId() {
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
            public int creatorCount() {
                return 1;
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
                return null;
            }
        };
    }

    //TODO; add xDto to DTO classes

    private record CandidateResetCauseArgument(DbLevel level, InstanceType type, URI... institutionIds) {

    }
}