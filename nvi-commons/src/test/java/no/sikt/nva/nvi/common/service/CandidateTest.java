package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequestWithSingleAffiliation;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApproval;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.getExpectedUpdatedDbCandidate;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CAN_FINALIZE_APPROVAL;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.unverifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.dto.PointCalculationDtoBuilder.randomPointCalculationDtoBuilder;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createCuratorUserInstance;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.PeriodStatus.Status;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.PeriodStatusDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
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
        CandidateNotFoundException.class, () -> candidateService.getByIdentifier(randomUUID()));
  }

  @Test
  void shouldReturnCandidateWhenExists() {
    var upsertCandidateRequest = randomUpsertRequestBuilder().build();
    var fetchedCandidate = scenario.upsertCandidate(upsertCandidateRequest);
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
    var pointCalculation = randomPointCalculationDtoBuilder().withChannel(channel).build();
    var request = randomUpsertRequestBuilder().withPointCalculation(pointCalculation).build();
    candidateService.upsert(request);
    var persistedCandidate =
        candidateRepository.findByPublicationId(request.publicationId()).orElseThrow().candidate();
    assertEquals(expectedLevel, persistedCandidate.level());
  }

  @Test
  void shouldGetUnverifiedCreatorsFromDetails() {
    var upsertCandidateRequest =
        randomUpsertRequestBuilder()
            .withNviCreators(
                unverifiedNviCreatorDtoFrom(randomUri()), verifiedNviCreatorDtoFrom(randomUri()))
            .build();
    var expectedUnverifiedCreatorCount = upsertCandidateRequest.unverifiedCreators().size();
    var expectedVerifiedCreatorCount = upsertCandidateRequest.verifiedCreators().size();

    var fetchedCandidate = scenario.upsertCandidate(upsertCandidateRequest);

    var actualUnverifiedCreatorCount =
        fetchedCandidate.publicationDetails().unverifiedCreators().size();
    var actualVerifiedCreatorCount =
        fetchedCandidate.publicationDetails().verifiedCreators().size();
    assertEquals(expectedUnverifiedCreatorCount, actualUnverifiedCreatorCount);
    assertEquals(expectedVerifiedCreatorCount, actualVerifiedCreatorCount);
  }

  @Test
  void shouldPersistNewCandidateWithCorrectDataFromUpsertRequest() {
    var request = randomUpsertRequestBuilder().build();
    var candidate = scenario.upsertCandidate(request);
    var expectedCandidate = getExpectedUpdatedDbCandidate(candidate, request);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.identifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @ParameterizedTest(name = "Should persist new candidate with correct level {0}")
  @ValueSource(ints = {0, 1, 4})
  void shouldPersistNewCandidateWithCorrectScaleForAllDecimals(int scale) {
    var request = createUpsertRequestWithDecimalScale(scale, randomUri());
    var candidate = scenario.upsertCandidate(request);
    var expectedCandidate = getExpectedUpdatedDbCandidate(candidate, request);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.identifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @ParameterizedTest(name = "Should update candidate with correct level {0}")
  @ValueSource(ints = {0, 1, 4})
  void shouldUpdateCandidateWithCorrectScaleForAllDecimals(int scale) {
    var pointCalculation =
        randomPointCalculationDtoBuilder()
            .withCollaborationFactor(randomBigDecimal(scale))
            .withBasePoints(randomBigDecimal(scale))
            .withTotalPoints(randomBigDecimal(scale))
            .build();
    var request = randomUpsertRequestBuilder().withPointCalculation(pointCalculation).build();
    var candidate = scenario.upsertCandidate(request);
    var expectedCandidate = getExpectedUpdatedDbCandidate(candidate, request);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.identifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @Test
  void shouldUpdateCandidateWithSystemGeneratedModifiedDate() {
    var request = getUpdateRequestForExistingCandidate();
    var candidate = candidateService.getByPublicationId(request.publicationId());
    candidateService.upsert(request);
    var updatedCandidate =
        candidateRepository.findCandidateById(candidate.identifier()).orElseThrow().candidate();
    assertNotEquals(candidate.modifiedDate(), updatedCandidate.modifiedDate());
  }

  @Test
  void shouldNotUpdateCandidateCreatedDateOnUpdates() {
    var request = getUpdateRequestForExistingCandidate();
    var candidate = candidateService.getByPublicationId(request.publicationId());
    var expectedCreatedDate = candidate.createdDate();
    candidateService.upsert(request);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.identifier()).orElseThrow().candidate();
    assertEquals(expectedCreatedDate, actualPersistedCandidate.createdDate());
  }

  @Test
  void shouldNotUpdateReportedCandidate() {
    var year = randomYear();
    var candidate = setupReportedCandidate(candidateRepository, year).candidate();

    var updateRequest =
        randomUpsertRequestBuilder().withPublicationId(candidate.publicationId()).build();
    assertThrows(
        IllegalCandidateUpdateException.class, () -> candidateService.upsert(updateRequest));
  }

  @Test
  void shouldPersistUpdatedCandidateWithCorrectDataFromUpsertRequest() {
    var updateRequest = getUpdateRequestForExistingCandidate();
    var candidate = scenario.upsertCandidate(updateRequest);
    var expectedCandidate = getExpectedUpdatedDbCandidate(candidate, updateRequest);
    var actualPersistedCandidate =
        candidateRepository.findCandidateById(candidate.identifier()).orElseThrow().candidate();
    assertThatCandidatesAreEqual(actualPersistedCandidate, expectedCandidate);
  }

  @Test
  void shouldFetchCandidateByPublicationId() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var fetchedCandidate = candidateService.getByPublicationId(candidate.getPublicationId());
    assertThat(fetchedCandidate.identifier(), is(equalTo(candidate.identifier())));
  }

  @Test
  void shouldDoNothingIfCreateRequestIsForNonCandidateThatDoesNotExist() {
    var updateRequest = createUpsertNonCandidateRequest(randomUri());

    var optionalCandidate = candidateService.updateNonCandidate(updateRequest);
    assertThat(optionalCandidate, is(equalTo(Optional.empty())));
  }

  @ParameterizedTest(
      name = "Should return global approval status {0} when all approvals have status {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void shouldReturnGlobalApprovalStatus(ApprovalStatus approvalStatus) {
    var organization1 = randomTopLevelOrganization();
    var organization2 = randomTopLevelOrganization();
    var request = createUpsertCandidateRequest(organization1, organization2).build();
    var candidate = scenario.upsertCandidate(request);

    scenario.updateApprovalStatus(candidate.identifier(), approvalStatus, organization1.id());
    scenario.updateApprovalStatus(candidate.identifier(), approvalStatus, organization2.id());

    var updatedCandidate = candidateService.getByPublicationId(request.publicationId());
    assertEquals(approvalStatus.getValue(), updatedCandidate.getGlobalApprovalStatus().getValue());
  }

  @Test()
  @DisplayName("Should return global approval status pending when any approval is pending")
  void shouldReturnGlobalApprovalStatusPendingWhenAnyApprovalIsPending() {
    var createRequest = randomUpsertRequestBuilder().build();
    var candidate = scenario.upsertCandidate(createRequest);
    assertEquals(GlobalApprovalStatus.PENDING, candidate.getGlobalApprovalStatus());
  }

  // This is tested more in rest-module
  @Test
  void shouldReturnExpectedDto() {
    UpsertNviCandidateRequest request = randomUpsertRequestBuilder().build();
    var candidate = scenario.upsertCandidate(request);
    var userOrganizationId = getAnyOrganizationId(candidate);
    mockOrganizationResponseForAffiliation(userOrganizationId, null, mockUriRetriever);

    var expectedDto =
        CandidateDto.builder()
            .withApprovals(mapToApprovalDtos(candidate))
            .withAllowedOperations(CURATOR_CAN_FINALIZE_APPROVAL)
            .withProblems(emptySet())
            .withId(candidate.getId())
            .withPublicationId(candidate.getPublicationId())
            .withIdentifier(candidate.identifier())
            .withContext(CONTEXT_URI)
            .withPeriod(PeriodStatusDto.fromPeriodStatus(candidate.period()))
            .withTotalPoints(candidate.getTotalPoints())
            .withNotes(emptyList())
            .build();

    var userInstance = createCuratorUserInstance(userOrganizationId);
    var actualDto = CandidateResponseFactory.create(candidate, userInstance);
    assertEquals(expectedDto, actualDto);
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
    var candidate = scenario.upsertCandidate(createRequest);
    scenario.updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED, institution1);
    scenario.updateApprovalStatus(candidate.identifier(), ApprovalStatus.REJECTED, institution2);

    var updatedCandidate = scenario.getCandidateByIdentifier(candidate.identifier());
    assertEquals(
        ApprovalStatus.PENDING, updatedCandidate.getApprovals().get(institution3).status());
    assertEquals(GlobalApprovalStatus.DISPUTE, updatedCandidate.getGlobalApprovalStatus());
  }

  @Test
  void shouldReturnCandidateWithExpectedData() {
    var createRequest = randomUpsertRequestBuilder().build();
    var candidate = scenario.upsertCandidate(createRequest);
    var requestPoints = createRequest.pointCalculation();
    assertEquals(
        adjustScaleAndRoundingMode(requestPoints.totalPoints()), candidate.getTotalPoints());
    assertEquals(adjustScaleAndRoundingMode(requestPoints.basePoints()), candidate.getBasePoints());
    assertEquals(
        adjustScaleAndRoundingMode(requestPoints.collaborationFactor()),
        candidate.getCollaborationFactor());
    assertEquals(requestPoints.creatorShareCount(), candidate.getCreatorShareCount());
  }

  @Test
  void shouldReturnCandidateWithNoPeriodWhenNotApplicable() {
    var tempCandidate = setupRandomApplicableCandidate(scenario);
    var updateRequest = createUpsertNonCandidateRequest(tempCandidate.getPublicationId());
    var candidateBO = candidateService.updateNonCandidate(updateRequest).orElseThrow();
    var fetchedCandidate = candidateService.getByIdentifier(candidateBO.identifier());
    assertThat(fetchedCandidate.period().status(), is(equalTo(Status.NO_PERIOD)));
  }

  @Test
  void shouldSetPeriodYearWhenResettingCandidate() {
    var nonApplicableCandidate = nonApplicableCandidate();
    candidateService.upsert(
        randomUpsertRequestBuilder()
            .withPublicationId(nonApplicableCandidate.getPublicationId())
            .build());
    var updatedApplicableCandidate =
        candidateService.getByIdentifier(nonApplicableCandidate.identifier());
    assertThat(
        updatedApplicableCandidate.period().year(),
        is(equalTo(updatedApplicableCandidate.period().year())));
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
    var candidate = candidateService.getByIdentifier(dao.identifier());

    var actualStatus = candidate.reportStatus().getValue();
    var expectedStatus = ReportStatus.REPORTED.getValue();
    assertEquals(expectedStatus, actualStatus);
  }

  @Test
  void shouldReturnTrueIfReportStatusIsReported() {
    var dao =
        candidateRepository.create(
            randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
            List.of(randomApproval()));
    var candidate = candidateService.getByIdentifier(dao.identifier());
    assertTrue(candidate.isReported());
  }

  @Test
  void shouldReturnCandidateWithPeriodStatusContainingPeriodId() {
    var dao =
        candidateRepository.create(
            randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
            List.of(randomApproval()));
    var candidate = candidateService.getByIdentifier(dao.identifier());
    var id = PeriodStatusDto.fromPeriodStatus(candidate.period()).id();

    assertThat(id, is(not(nullValue())));
  }

  @Test
  void shouldReturnCandidateWithPeriodStatusContainingPeriodIdWhenFetchingByPublicationId() {
    var dao =
        candidateRepository.create(
            randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
            List.of(randomApproval()));
    var candidate = candidateService.getByPublicationId(dao.candidate().publicationId());
    var id = PeriodStatusDto.fromPeriodStatus(candidate.period()).id();

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
    var candidate = candidateService.getByIdentifier(dao.identifier());
    assertEquals(
        List.of(creator1affiliation, creator2affiliation), candidate.getNviCreatorAffiliations());
  }

  @Test
  void shouldUpdateVersion() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var dao = candidateRepository.findCandidateById(candidate.identifier()).orElseThrow();

    candidateService.update(candidate);

    var updatedDao = candidateRepository.findCandidateById(candidate.identifier()).orElseThrow();

    assertEquals(dao.identifier(), updatedDao.identifier());
    assertNotEquals(dao.version(), updatedDao.version());
  }

  @Test
  void shouldUpdateRevision() {
    var candidate = setupRandomApplicableCandidate(scenario);

    candidateService.update(candidate);

    var updatedCandidate = candidateService.getByIdentifier(candidate.identifier());

    assertEquals(candidate.identifier(), updatedCandidate.identifier());
    assertNotEquals(candidate.revision(), updatedCandidate.revision());
  }

  @Test
  void shouldReturnTrueWhenAllApprovalsArePending() {
    var request = createUpsertCandidateRequestWithSingleAffiliation(randomUri(), randomUri());
    var candidate = scenario.upsertCandidate(request);
    assertTrue(candidate.isPendingReview());
  }

  @ParameterizedTest(name = "Should return false when at least one approval is: {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void shouldReturnFalseWhenAtLeastOneApprovalIsApprovedOrRejected(ApprovalStatus approvalStatus) {
    var reviewingInstitution = randomUri();
    var request =
        createUpsertCandidateRequestWithSingleAffiliation(reviewingInstitution, randomUri());
    var candidate = scenario.upsertCandidate(request);
    candidate =
        scenario.updateApprovalStatus(candidate.identifier(), approvalStatus, reviewingInstitution);
    assertFalse(candidate.isPendingReview());
  }

  @ParameterizedTest(name = "Should return true when at least one approval is: {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"APPROVED", "REJECTED"})
  void shouldReturnTrueWhenAtLeastOneApprovalIsApprovedOrRejected(ApprovalStatus approvalStatus) {
    var reviewingInstitution = randomUri();
    var request =
        createUpsertCandidateRequestWithSingleAffiliation(reviewingInstitution, randomUri());
    var candidate = scenario.upsertCandidate(request);
    candidate =
        scenario.updateApprovalStatus(candidate.identifier(), approvalStatus, reviewingInstitution);
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
    var candidate = candidateService.getByIdentifier(dao.identifier());
    assertFalse(candidate.isNotReportedInClosedPeriod());
  }

  @Test
  @Disabled
  void shouldBeAbleToRoundTripWithNoLossOfData() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var aggregate = scenario.getAllRelatedData(candidate.identifier());

    var originalCandidateDao = aggregate.candidate();
    var roundTrippedCandidate = candidateService.getByIdentifier(candidate.identifier()).toDao();

    Assertions.assertThat(originalCandidateDao)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(roundTrippedCandidate);
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
                    .withInstitutionId(approval.institutionId())
                    .withPoints(candidate.getPointValueForInstitution(approval.institutionId()))
                    .withStatus(ApprovalStatusDto.from(approval))
                    .withFinalizedBy(approval.getFinalizedByUserName())
                    .withAssignee(approval.getAssigneeUsername())
                    .build())
        .toList();
  }

  private Candidate nonApplicableCandidate() {
    var tempCandidate = setupRandomApplicableCandidate(scenario);
    var updateRequest = createUpsertNonCandidateRequest(tempCandidate.getPublicationId());
    var candidateBO = candidateService.updateNonCandidate(updateRequest).orElseThrow();
    return candidateService.getByIdentifier(candidateBO.identifier());
  }

  private UpsertNviCandidateRequest getUpdateRequestForExistingCandidate() {
    var insertRequest = randomUpsertRequestBuilder().build();
    candidateService.upsert(insertRequest);
    return UpsertRequestBuilder.fromRequest(insertRequest).build();
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
