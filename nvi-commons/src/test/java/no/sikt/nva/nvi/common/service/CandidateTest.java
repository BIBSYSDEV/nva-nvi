package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApproval;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import no.sikt.nva.nvi.common.db.CandidateDao.DbInstitutionPoints;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.DbPages;
import no.sikt.nva.nvi.common.db.model.DbPointCalculation;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbPublicationDetails;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.dto.PublicationDtoBuilder;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.PageCount;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
class CandidateTest extends CandidateTestSetup {

  @Test
  void shouldThrowNotFoundExceptionWhenCandidateDoesNotExist() {
    assertThrows(
        CandidateNotFoundException.class,
        () -> Candidate.fetch(UUID::randomUUID, candidateRepository, periodRepository));
  }

  @Test
  void shouldReturnCandidateWhenExists() {
    var upsertCandidateRequest = randomUpsertRequestBuilder().build();
    var fetchedCandidate = upsert(upsertCandidateRequest);
    assertNotNull(fetchedCandidate);
  }

  @Deprecated
  @ParameterizedTest(name = "Should persist new candidate with correct level {0}")
  @MethodSource("levelValues")
  void shouldPersistNewCandidateWithCorrectLevelBasedOnVersionTwoLevelValues(
      DbLevel expectedLevel, String versionTwoValue) {
    var channel =
        PublicationChannelDto.builder()
            .withId(randomUri())
            .withChannelType(ChannelType.JOURNAL)
            .withScientificValue(ScientificValue.parse(versionTwoValue))
            .build();
    var publicationDtoBuilder =
        PublicationDtoBuilder.randomPublicationDtoBuilder()
            .withPublicationChannels(List.of(channel));
    var request = randomUpsertRequestBuilder(publicationDtoBuilder).build();
    Candidate.upsert(request, candidateRepository, periodRepository);
    var persistedCandidate =
        candidateRepository.findByPublicationId(request.publicationId()).orElseThrow().candidate();
    assertEquals(expectedLevel, persistedCandidate.level());
  }

  @Test
  void shouldGetUnverifiedCreatorsFromDetails() {
    var unverifiedCreator = new UnverifiedNviCreatorDto(randomString(), List.of(randomUri()));
    var upsertCandidateRequest =
        randomUpsertRequestBuilder().withUnverifiedCreators(List.of(unverifiedCreator)).build();
    var expectedUnverifiedCreatorCount = upsertCandidateRequest.unverifiedCreators().size();
    var expectedVerifiedCreatorCount = upsertCandidateRequest.verifiedCreators().size();

    var fetchedCandidate = upsert(upsertCandidateRequest);

    var actualUnverifiedCreatorCount =
        fetchedCandidate.getPublicationDetails().unverifiedCreators().size();
    var actualVerifiedCreatorCount =
        fetchedCandidate.getPublicationDetails().verifiedCreators().size();
    assertEquals(expectedUnverifiedCreatorCount, actualUnverifiedCreatorCount);
    assertEquals(expectedVerifiedCreatorCount, actualVerifiedCreatorCount);
  }

  @Test
  void shouldPersistNewCandidateWithCorrectDataFromUpsertRequest() {
    var request = randomUpsertRequestBuilder().build();
    var candidate = upsert(request);
    var expectedCandidate = generateExpectedCandidate(candidate, request);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @ParameterizedTest(name = "Should persist new candidate with correct level {0}")
  @ValueSource(ints = {0, 1, 4})
  void shouldPersistNewCandidateWithCorrectScaleForAllDecimals(int scale) {
    var request = createUpsertRequestWithDecimalScale(scale, randomUri());
    var candidate = upsert(request);
    var expectedCandidate = generateExpectedCandidate(candidate, request);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @ParameterizedTest(name = "Should update candidate with correct level {0}")
  @ValueSource(ints = {0, 1, 4})
  void shouldUpdateCandidateWithCorrectScaleForAllDecimals(int scale) {
    var request =
        randomUpsertRequestBuilder()
            .withCollaborationFactor(randomBigDecimal(scale))
            .withBasePoints(randomBigDecimal(scale))
            .withTotalPoints(randomBigDecimal(scale))
            .build();
    var candidate = upsert(request);
    var expectedCandidate = generateExpectedCandidate(candidate, request);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @Test
  void shouldUpdateCandidateWithSystemGeneratedModifiedDate() {
    var request = getUpdateRequestForExistingCandidate();
    var candidate =
        Candidate.fetchByPublicationId(
            request::publicationId, candidateRepository, periodRepository);
    Candidate.upsert(request, candidateRepository, periodRepository);
    var updatedCandidate =
        candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow().candidate();
    assertNotEquals(candidate.getModifiedDate(), updatedCandidate.modifiedDate());
  }

  @Test
  void shouldNotUpdateCandidateCreatedDateOnUpdates() {
    var request = getUpdateRequestForExistingCandidate();
    var candidate =
        Candidate.fetchByPublicationId(
            request::publicationId, candidateRepository, periodRepository);
    var expectedCreatedDate = candidate.getCreatedDate();
    Candidate.upsert(request, candidateRepository, periodRepository);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow().candidate();
    assertEquals(expectedCreatedDate, actualPersistedCandidate.createdDate());
  }

  @Test
  void shouldNotUpdateReportedCandidate() {
    var year = randomYear();
    var candidate = setupReportedCandidate(candidateRepository, year).candidate();

    var updateRequest =
        randomUpsertRequestBuilder().withPublicationId(candidate.publicationId()).build();
    assertThrows(
        IllegalCandidateUpdateException.class,
        () -> Candidate.upsert(updateRequest, candidateRepository, periodRepository));
  }

  @Test
  void shouldPersistUpdatedCandidateWithCorrectDataFromUpsertRequest() {
    var updateRequest = getUpdateRequestForExistingCandidate();
    var candidate = upsert(updateRequest);
    var expectedCandidate = generateExpectedCandidate(candidate, updateRequest);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @Test
  void shouldFetchCandidateByPublicationId() {
    var candidate = setupRandomApplicableCandidate(candidateRepository, periodRepository);
    var fetchedCandidate =
        Candidate.fetchByPublicationId(
            candidate::getPublicationId, candidateRepository, periodRepository);
    assertThat(fetchedCandidate.getIdentifier(), is(equalTo(candidate.getIdentifier())));
  }

  @Test
  void shouldDoNothingIfCreateRequestIsForNonCandidateThatDoesNotExist() {
    var updateRequest = createUpsertNonCandidateRequest(randomUri());

    var optionalCandidate = Candidate.updateNonCandidate(updateRequest, candidateRepository);
    assertThat(optionalCandidate, is(equalTo(Optional.empty())));
  }

  @ParameterizedTest(
      name = "Should return global approval status {0} when all approvals have status {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void shouldReturnGlobalApprovalStatus(ApprovalStatus approvalStatus) {
    var institution1 = randomUri();
    var institution2 = randomUri();
    var createRequest = createUpsertCandidateRequest(institution1, institution2);
    var candidate = upsert(createRequest);
    candidate.updateApproval(
        createUpdateStatusRequest(approvalStatus, institution1, randomString()));
    candidate.updateApproval(
        createUpdateStatusRequest(approvalStatus, institution2, randomString()));
    assertEquals(approvalStatus.getValue(), candidate.getGlobalApprovalStatus().getValue());
  }

  @Test()
  @DisplayName("Should return global approval status pending when any approval is pending")
  void shouldReturnGlobalApprovalStatusPendingWhenAnyApprovalIsPending() {
    var createRequest = randomUpsertRequestBuilder().build();
    var candidate = upsert(createRequest);
    assertEquals(GlobalApprovalStatus.PENDING, candidate.getGlobalApprovalStatus());
  }

  // This is tested more in rest-module
  @Test
  void shouldReturnExpectedDto() {
    var candidate = upsert(randomUpsertRequestBuilder().build());
    var userOrganizationId = getAnyOrganizationId(candidate);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var expectedDto =
        CandidateDto.builder()
            .withApprovals(mapToApprovalDtos(candidate))
            .withAllowedOperations(
                Set.of(CandidateOperation.APPROVAL_APPROVE, CandidateOperation.APPROVAL_REJECT))
            .withProblems(emptySet())
            .withId(candidate.getId())
            .withPublicationId(candidate.getPublicationId())
            .withIdentifier(candidate.getIdentifier())
            .withContext(CONTEXT_URI)
            .withPeriod(PeriodStatusDto.fromPeriodStatus(candidate.getPeriod()))
            .withTotalPoints(candidate.getTotalPoints())
            .withNotes(Collections.emptyList())
            .build();
    assertEquals(expectedDto, candidate.toDto(userOrganizationId, mockOrganizationRetriever));
  }

  @Test()
  @DisplayName(
      "Should return global approval status dispute when a candidate has at least one Rejected and"
          + " one Approved approval")
  void shouldReturnGlobalApprovalStatusDisputeWhenConflictsExist() {
    var institution1 = randomUri();
    var institution2 = randomUri();
    var institution3 = randomUri();
    var createRequest =
        createUpsertCandidateRequest(institution1, institution2, institution3).build();
    var candidate = upsert(createRequest);
    candidate.updateApproval(
        createUpdateStatusRequest(ApprovalStatus.APPROVED, institution1, randomString()));
    candidate.updateApproval(
        createUpdateStatusRequest(ApprovalStatus.REJECTED, institution2, randomString()));
    assertEquals(ApprovalStatus.PENDING, candidate.getApprovals().get(institution3).getStatus());
    assertEquals(GlobalApprovalStatus.DISPUTE, candidate.getGlobalApprovalStatus());
  }

  @Test
  void shouldReturnCandidateWithExpectedData() {
    var createRequest = randomUpsertRequestBuilder().build();
    var candidate = upsert(createRequest);
    assertEquals(
        adjustScaleAndRoundingMode(createRequest.totalPoints()), candidate.getTotalPoints());
    assertEquals(adjustScaleAndRoundingMode(createRequest.basePoints()), candidate.getBasePoints());
    assertEquals(
        adjustScaleAndRoundingMode(createRequest.collaborationFactor()),
        candidate.getCollaborationFactor());
    assertEquals(createRequest.creatorShareCount(), candidate.getCreatorShareCount());
  }

  @Test
  void shouldReturnCandidateWithNoPeriodWhenNotApplicable() {
    var tempCandidate = setupRandomApplicableCandidate(candidateRepository, periodRepository);
    var updateRequest = createUpsertNonCandidateRequest(tempCandidate.getPublicationId());
    var candidateBO =
        Candidate.updateNonCandidate(updateRequest, candidateRepository).orElseThrow();
    var fetchedCandidate =
        Candidate.fetch(candidateBO::getIdentifier, candidateRepository, periodRepository);
    assertThat(fetchedCandidate.getPeriod().status(), is(equalTo(Status.NO_PERIOD)));
  }

  @Test
  void shouldSetPeriodYearWhenResettingCandidate() {
    var nonApplicableCandidate = nonApplicableCandidate();
    Candidate.upsert(
        randomUpsertRequestBuilder()
            .withPublicationId(nonApplicableCandidate.getPublicationId())
            .build(),
        candidateRepository,
        periodRepository);
    var updatedApplicableCandidate =
        Candidate.fetch(
            nonApplicableCandidate::getIdentifier, candidateRepository, periodRepository);
    assertThat(
        updatedApplicableCandidate.getPeriod().year(),
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
    var currentUserOrganizationId = getAnyOrganizationId(candidate);

    var actualStatus =
        candidate.toDto(currentUserOrganizationId, mockOrganizationRetriever).status();
    var expectedStatus = ReportStatus.REPORTED.getValue();
    assertEquals(expectedStatus, actualStatus);
  }

  @Test
  void shouldReturnTrueIfReportStatusIsReported() {
    var dao =
        candidateRepository.create(
            randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
            List.of(randomApproval()));
    var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
    assertTrue(candidate.isReported());
  }

  @Test
  void shouldReturnCandidateWithPeriodStatusContainingPeriodId() {
    var dao =
        candidateRepository.create(
            randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
            List.of(randomApproval()));
    var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
    var id = PeriodStatusDto.fromPeriodStatus(candidate.getPeriod()).id();

    assertThat(id, is(not(nullValue())));
  }

  @Test
  void shouldReturnCandidateWithPeriodStatusContainingPeriodIdWhenFetchingByPublicationId() {
    var dao =
        candidateRepository.create(
            randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
            List.of(randomApproval()));
    var candidate =
        Candidate.fetchByPublicationId(
            () -> dao.candidate().publicationId(), candidateRepository, periodRepository);
    var id = PeriodStatusDto.fromPeriodStatus(candidate.getPeriod()).id();

    assertThat(id, is(not(nullValue())));
  }

  @Test
  void shouldReturnCreatorAffiliations() {
    var creator1affiliation = randomUri();
    var creator2affiliation = randomUri();
    var creator1 =
        DbCreator.builder()
            .creatorId(randomUri())
            .affiliations(List.of(creator1affiliation))
            .build();
    var creator2 =
        DbCreator.builder()
            .creatorId(randomUri())
            .affiliations(List.of(creator2affiliation))
            .build();
    var dao =
        candidateRepository.create(
            randomCandidate().copy().creators(List.of(creator1, creator2)).build(),
            List.of(randomApproval()));
    var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
    assertEquals(
        List.of(creator1affiliation, creator2affiliation), candidate.getNviCreatorAffiliations());
  }

  @Test
  void shouldUpdateVersion() {
    var candidate = setupRandomApplicableCandidate(candidateRepository, periodRepository);
    var dao = candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow();

    candidate.updateVersion(candidateRepository);

    var updatedCandidate =
        Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository);
    var updatedDao = candidateRepository.findCandidateById(candidate.getIdentifier()).orElseThrow();

    assertEquals(candidate, updatedCandidate);
    assertNotEquals(dao.version(), updatedDao.version());
  }

  @Test
  void shouldReturnTrueWhenAllApprovalsArePending() {
    var candidate = upsert(createUpsertCandidateRequest(randomUri(), randomUri()));
    assertTrue(candidate.isPendingReview());
  }

  @ParameterizedTest(name = "Should return false when at least one approval is: {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void shouldReturnFalseWhenAtLeastOneApprovalIsApprovedOrRejected(ApprovalStatus approvalStatus) {
    var reviewingInstitution = randomUri();
    var candidate = upsert(createUpsertCandidateRequest(reviewingInstitution, randomUri()));
    candidate.updateApproval(
        createUpdateStatusRequest(approvalStatus, reviewingInstitution, randomString()));
    assertFalse(candidate.isPendingReview());
  }

  @ParameterizedTest(name = "Should return true when at least one approval is: {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void shouldReturnTrueWhenAtLeastOneApprovalIsApprovedOrRejected(ApprovalStatus approvalStatus) {
    var reviewingInstitution = randomUri();
    var candidate = upsert(createUpsertCandidateRequest(reviewingInstitution, randomUri()));
    candidate.updateApproval(
        createUpdateStatusRequest(approvalStatus, reviewingInstitution, randomString()));
    assertTrue(candidate.isUnderReview());
  }

  @Test
  void shouldReturnTrueWhenCandidateIsNotReportedInClosedPeriod() {
    setupClosedPeriod(scenario, CURRENT_YEAR);
    var candidate = setupRandomApplicableCandidate(scenario, CURRENT_YEAR);
    assertTrue(candidate.isNotReportedInClosedPeriod());
  }

  @Test
  void shouldReturnFalseWhenCandidateIsReportedInClosedPeriod() {
    var dao = setupReportedCandidate(candidateRepository, String.valueOf(CURRENT_YEAR));
    setupClosedPeriod(scenario, CURRENT_YEAR);
    var candidate = Candidate.fetch(dao::identifier, candidateRepository, periodRepository);
    assertFalse(candidate.isNotReportedInClosedPeriod());
  }

  @Deprecated
  private static Stream<Arguments> levelValues() {
    return Stream.of(
        Arguments.of(DbLevel.LEVEL_ONE, "LevelOne"), Arguments.of(DbLevel.LEVEL_TWO, "LevelTwo"));
  }

  private static BigDecimal adjustScaleAndRoundingMode(BigDecimal bigDecimal) {
    return bigDecimal.setScale(EXPECTED_SCALE, EXPECTED_ROUNDING_MODE);
  }

  private List<ApprovalDto> mapToApprovalDtos(Candidate candidate) {
    return candidate.getApprovals().values().stream()
        .map(
            approval ->
                ApprovalDto.builder()
                    .withInstitutionId(approval.getInstitutionId())
                    .withPoints(candidate.getPointValueForInstitution(approval.getInstitutionId()))
                    .withStatus(ApprovalStatusDto.from(approval))
                    .withFinalizedBy(approval.getFinalizedByUserName())
                    .withAssignee(approval.getAssigneeUsername())
                    .build())
        .toList();
  }

  private Candidate upsert(UpsertNviCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  private Candidate nonApplicableCandidate() {
    var tempCandidate = setupRandomApplicableCandidate(candidateRepository, periodRepository);
    var updateRequest = createUpsertNonCandidateRequest(tempCandidate.getPublicationId());
    var candidateBO =
        Candidate.updateNonCandidate(updateRequest, candidateRepository).orElseThrow();
    return Candidate.fetch(candidateBO::getIdentifier, candidateRepository, periodRepository);
  }

  private UpsertNviCandidateRequest getUpdateRequestForExistingCandidate() {
    var insertRequest = randomUpsertRequestBuilder().build();
    Candidate.upsert(insertRequest, candidateRepository, periodRepository);
    return UpsertRequestBuilder.fromRequest(insertRequest).build();
  }

  private static List<DbCreatorType> mapToDbCreators(
      List<VerifiedNviCreatorDto> verifiedNviCreators,
      List<UnverifiedNviCreatorDto> unverifiedNviCreators) {
    Stream<DbCreatorType> verifiedCreators =
        verifiedNviCreators.stream().map(VerifiedNviCreatorDto::toDao);
    Stream<DbCreatorType> unverifiedCreators =
        unverifiedNviCreators.stream().map(UnverifiedNviCreatorDto::toDao);
    return Stream.concat(verifiedCreators, unverifiedCreators).toList();
  }

  private DbPublicationDate mapToDbPublicationDate(PublicationDateDto publicationDate) {
    return new DbPublicationDate(
        publicationDate.year(), publicationDate.month(), publicationDate.day());
  }

  private List<DbInstitutionPoints> mapToDbInstitutionPoints(List<InstitutionPoints> points) {
    return points.stream().map(DbInstitutionPoints::from).toList();
  }

  // TODO: Replace this with a mapping to Candidate, instead of checking what is in the DB
  private DbCandidate generateExpectedCandidate(
      Candidate candidate, UpsertNviCandidateRequest request) {
    var dtoChannel = request.publicationChannelForLevel();
    var dtoPublicationDetails = request.publicationDetails();
    var dbCreators = mapToDbCreators(request.verifiedCreators(), request.unverifiedCreators());
    var dbPublicationChannel =
        DbPublicationChannel.builder()
            .id(dtoChannel.id())
            .channelType(dtoChannel.channelType().getValue())
            .scientificValue(dtoChannel.scientificValue().getValue())
            .build();
    var dbOrganizations =
        dtoPublicationDetails.topLevelOrganizations().stream()
            .map(Organization::toDbOrganization)
            .toList();

    var dbPointCalculation =
        DbPointCalculation.builder()
            .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
            .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
            .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
            .publicationChannel(dbPublicationChannel)
            .institutionPoints(mapToDbInstitutionPoints(request.institutionPoints()))
            .internationalCollaboration(request.isInternationalCollaboration())
            .creatorShareCount(request.creatorShareCount())
            .instanceType(dtoPublicationDetails.publicationType().getValue())
            .build();

    var dbPublicationDetails =
        DbPublicationDetails.builder()
            .id(request.publicationId())
            .identifier(dtoPublicationDetails.identifier())
            .publicationBucketUri(request.publicationBucketUri())
            .title(dtoPublicationDetails.title())
            .status(dtoPublicationDetails.status())
            .publicationDate(mapToDbPublicationDate(dtoPublicationDetails.publicationDate()))
            .modifiedDate(dtoPublicationDetails.modifiedDate())
            .creators(dbCreators)
            .contributorCount(dtoPublicationDetails.contributors().size())
            .abstractText(dtoPublicationDetails.abstractText())
            .pages(getDbPagesFromRequest(request))
            .topLevelOrganizations(dbOrganizations)
            .build();
    var dbCandidate =
        DbCandidate.builder()
            .publicationId(request.publicationId())
            .publicationIdentifier(dtoPublicationDetails.identifier())
            .publicationBucketUri(request.publicationBucketUri())
            .pointCalculation(dbPointCalculation)
            .publicationDetails(dbPublicationDetails)
            .publicationDate(mapToDbPublicationDate(dtoPublicationDetails.publicationDate()))
            .applicable(dtoPublicationDetails.isApplicable())
            .instanceType(dtoPublicationDetails.publicationType().getValue())
            .channelType(dtoChannel.channelType().getValue())
            .channelId(dtoChannel.id())
            .level(DbLevel.parse(dtoChannel.scientificValue().getValue()))
            .basePoints(adjustScaleAndRoundingMode(request.basePoints()))
            .internationalCollaboration(request.isInternationalCollaboration())
            .collaborationFactor(adjustScaleAndRoundingMode(request.collaborationFactor()))
            .creators(dbCreators)
            .creatorShareCount(request.creatorShareCount())
            .points(mapToDbInstitutionPoints(request.institutionPoints()))
            .totalPoints(adjustScaleAndRoundingMode(request.totalPoints()))
            .createdDate(candidate.getCreatedDate())
            .build();
    return new CandidateDao(candidate.getIdentifier(), dbCandidate, randomString(), randomString())
        .candidate();
  }

  private static DbPages getDbPagesFromRequest(UpsertNviCandidateRequest request) {
    var publicationDetails = request.publicationDetails();
    if (isNull(publicationDetails) || isNull(publicationDetails.pageCount())) {
      return null;
    }
    return PageCount.from(publicationDetails.pageCount()).toDbPages();
  }

  private static void assertThatCandidatesAreEqual(
      DbCandidate actualPersistedCandidate, DbCandidate expectedCandidate) {
    Assertions.assertThat(actualPersistedCandidate)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("modifiedDate")
        .isEqualTo(expectedCandidate);
  }
}
