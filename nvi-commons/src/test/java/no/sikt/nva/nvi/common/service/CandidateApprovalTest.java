package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.fromRequest;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.periodRepositoryReturningNotOpenedPeriod;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.randomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupApprovedCandidate;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.TestUtils.randomUriWithSuffix;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateOperation;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorFromOrganizationProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorProblem;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationChannel;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings("PMD.CouplingBetweenObjects")
class CandidateApprovalTest extends CandidateTestSetup {

  private static final String APPROVED = "APPROVED";
  private static final URI HARDCODED_INSTITUTION_ID =
      URI.create("https://example.org/topLevelInstitutionId");
  private static final URI HARDCODED_SUBUNIT_ID =
      URI.create("https://example.org/subUnitInstitutionId");
  private static final URI HARDCODED_CHANNEL_ID =
      URI.create("https://example.org/publication-channels-v2/journal/123/2018");
  private static final String HARDCODED_LEVEL = "LevelOne";
  private static final URI HARDCODED_CREATOR_ID = URI.create("https://example.org/someCreator");
  private static final InstanceType HARDCODED_INSTANCE_TYPE = InstanceType.ACADEMIC_ARTICLE;
  private static final BigDecimal HARDCODED_POINTS =
      BigDecimal.ONE.setScale(EXPECTED_SCALE, EXPECTED_ROUNDING_MODE);
  private URI topLevelOrganizationId;

  public static Stream<Arguments> statusProvider() {
    return Stream.of(
        Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED),
        Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
        Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING),
        Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
        Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.PENDING),
        Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED));
  }

  public static Stream<Arguments> periodRepositoryProvider() {
    var year = ZonedDateTime.now().getYear();
    return Stream.of(
        Arguments.of(
            Named.of(
                "Repository returning closed period", periodRepositoryReturningClosedPeriod(year))),
        Arguments.of(
            Named.of(
                "Repository returning period not opened yet",
                periodRepositoryReturningNotOpenedPeriod(year))),
        Arguments.of(Named.of("Mocked repository", mock(PeriodRepository.class))));
  }

  @BeforeEach
  void setUp() {
    topLevelOrganizationId = randomUriWithSuffix("topLevelOrganization");
    mockOrganizationResponseForAffiliation(topLevelOrganizationId, null, mockUriRetriever);
    mockOrganizationResponseForAffiliation(
        HARDCODED_INSTITUTION_ID, HARDCODED_SUBUNIT_ID, mockUriRetriever);
  }

  @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
  @MethodSource("statusProvider")
  void shouldUpdateStatusWhenUpdateStatusRequestValid(
      ApprovalStatus oldStatus, ApprovalStatus newStatus) {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository);
    var existingCandidate =
        Candidate.fetchByPublicationId(
            upsertCandidateRequest::publicationId, candidateRepository, periodRepository);
    existingCandidate = updateApprovalStatus(existingCandidate, oldStatus);

    var updatedCandidate = updateApprovalStatus(existingCandidate, newStatus);
    var actualNewStatus = updatedCandidate.getApprovals().get(HARDCODED_INSTITUTION_ID).getStatus();
    assertThat(actualNewStatus, is(equalTo(newStatus)));
  }

  @ParameterizedTest(name = "Should reset approval when changing to pending from {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"REJECTED", APPROVED})
  void shouldResetApprovalWhenChangingToPending(ApprovalStatus oldStatus) {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(upsertCandidateRequest);
    var assignee = randomString();
    candidate.updateApprovalAssignee(new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, assignee));
    updateApprovalStatus(candidate, oldStatus);

    updateApprovalStatus(candidate, ApprovalStatus.PENDING);
    var approvalStatus = candidate.getApprovals().get(HARDCODED_INSTITUTION_ID);
    assertThat(approvalStatus.getStatus(), is(equalTo(ApprovalStatus.PENDING)));
    assertThat(approvalStatus.getAssigneeUsername(), is(assignee));
    assertThat(approvalStatus.getFinalizedByUserName(), is(nullValue()));
    assertThat(approvalStatus.getFinalizedDate(), is(nullValue()));
  }

  @Test
  void shouldCreatePendingApprovalsForNewCandidate() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(upsertCandidateRequest);
    assertThat(candidate.getApprovals().size(), is(equalTo(1)));
    assertThat(
        candidate.getApprovals().get(HARDCODED_INSTITUTION_ID).getStatus(),
        is(equalTo(ApprovalStatus.PENDING)));
  }

  @ParameterizedTest(name = "shouldThrowUnsupportedOperationWhenRejectingWithoutReason {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"PENDING", APPROVED})
  void shouldThrowUnsupportedOperationWhenRejectingWithoutReason(ApprovalStatus oldStatus) {
    var createRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    Candidate.upsert(createRequest, candidateRepository, periodRepository);
    var candidate =
        Candidate.fetchByPublicationId(
            createRequest::publicationId, candidateRepository, periodRepository);
    var updatedCandidate = updateApprovalStatus(candidate, oldStatus);
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            updatedCandidate.updateApprovalStatus(
                createRejectionRequestWithoutReason(randomString()), mockOrganizationRetriever));
  }

  @ParameterizedTest(name = "Should remove reason when updating from rejection status to {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"PENDING", APPROVED})
  void shouldRemoveReasonWhenUpdatingFromRejectionStatusToNewStatus(ApprovalStatus newStatus) {
    var createRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    Candidate.upsert(createRequest, candidateRepository, periodRepository);
    var rejectedCandidate =
        Candidate.fetchByPublicationId(
                createRequest::publicationId, candidateRepository, periodRepository)
            .updateApprovalStatus(
                createUpdateStatusRequest(
                    ApprovalStatus.REJECTED, HARDCODED_INSTITUTION_ID, randomString()),
                mockOrganizationRetriever);

    var updatedCandidate = updateApprovalStatus(rejectedCandidate, newStatus);
    assertThat(updatedCandidate.getApprovals().size(), is(equalTo(1)));
    assertThat(
        updatedCandidate.getApprovals().get(HARDCODED_INSTITUTION_ID).getStatus(),
        is(equalTo(newStatus)));
    assertThat(
        updatedCandidate.getApprovals().get(HARDCODED_INSTITUTION_ID).getReason(), is(nullValue()));
  }

  @ParameterizedTest(
      name = "shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"PENDING", APPROVED, "REJECTED"})
  void shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername(
      ApprovalStatus newStatus) {
    var createRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(createRequest);
    var invalidRequest = createUpdateStatusRequest(newStatus, HARDCODED_INSTITUTION_ID, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> candidate.updateApprovalStatus(invalidRequest, mockOrganizationRetriever));
  }

  @Test
  void shouldRemoveOldInstitutionsWhenUpdatingCandidate() {
    var keepInstitutionId = randomUri();
    var deleteInstitutionId = randomUri();
    var createCandidateRequest =
        createUpsertCandidateRequest(keepInstitutionId, deleteInstitutionId, randomUri()).build();
    Candidate.upsert(createCandidateRequest, candidateRepository, periodRepository);
    var updateRequest =
        fromRequest(createCandidateRequest)
            .withPoints(List.of(new InstitutionPoints(keepInstitutionId, randomBigDecimal(), null)))
            .build();
    var updatedCandidate = upsert(updateRequest);
    var approvalMap = updatedCandidate.getApprovals();

    assertThat(approvalMap.containsKey(deleteInstitutionId), is(false));
    assertThat(approvalMap.containsKey(keepInstitutionId), is(true));
    assertThat(approvalMap.size(), is(1));
  }

  @Test
  void shouldRemoveApprovalsWhenBecomingNonCandidate() {
    var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
    var updateRequest = createUpsertNonCandidateRequest(candidate.getPublicationId());
    var updatedCandidate =
        Candidate.updateNonCandidate(updateRequest, candidateRepository).orElseThrow();
    assertThat(updatedCandidate.getIdentifier(), is(equalTo(candidate.getIdentifier())));
    assertThat(updatedCandidate.getApprovals().size(), is(equalTo(0)));
  }

  @Test
  void shouldThrowExceptionWhenApplicableAndNonCandidate() {
    var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
    var updateRequest =
        randomUpsertRequestBuilder()
            .withPublicationId(candidate.getPublicationId())
            .withInstanceType(null)
            .build();
    assertThrows(
        InvalidNviCandidateException.class,
        () -> Candidate.upsert(updateRequest, candidateRepository, periodRepository));
  }

  @Test
  void shouldPersistStatusChangeWhenRequestingAndUpdate() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(upsertCandidateRequest);
    updateApprovalStatus(candidate, ApprovalStatus.APPROVED);

    var status =
        Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository)
            .getApprovals()
            .get(HARDCODED_INSTITUTION_ID)
            .getStatus();
    assertThat(status, is(equalTo(ApprovalStatus.APPROVED)));
  }

  @Test
  void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {
    var assignee = randomString();
    var request = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    Candidate.upsert(request, candidateRepository, periodRepository);
    var candidate =
        Candidate.fetchByPublicationId(
                request::publicationId, candidateRepository, periodRepository)
            .updateApprovalAssignee(new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, assignee));
    candidate = updateApprovalStatus(candidate, ApprovalStatus.APPROVED);
    candidate = updateApprovalStatus(candidate, ApprovalStatus.REJECTED);
    var candidateDto = candidate.toDto(HARDCODED_INSTITUTION_ID, mockOrganizationRetriever);
    assertThat(candidateDto.approvals().getFirst().assignee(), is(equalTo(assignee)));
    assertThat(candidateDto.approvals().getFirst().finalizedBy(), is(not(equalTo(assignee))));
  }

  @Test
  void shouldChangeAssigneeWhenValidUpdateAssigneeRequest() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(upsertCandidateRequest);
    var newUsername = randomString();
    candidate.updateApprovalAssignee(
        new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, newUsername));

    var assignee =
        Candidate.fetch(candidate::getIdentifier, candidateRepository, periodRepository)
            .toDto(HARDCODED_INSTITUTION_ID, mockOrganizationRetriever)
            .approvals()
            .getFirst()
            .assignee();

    assertThat(assignee, is(equalTo(newUsername)));
  }

  /*
  This is deprecated because the method being tested is deprecated, remove both.
  */
  @Deprecated(since = "2025-01-31", forRemoval = true)
  @Test
  void shouldNotAllowUpdateApprovalStatusWhenTryingToPassAnonymousImplementations() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(upsertCandidateRequest);
    assertThrows(
        IllegalArgumentException.class,
        () -> candidate.updateApproval(() -> HARDCODED_INSTITUTION_ID));
  }

  @ParameterizedTest
  @MethodSource("periodRepositoryProvider")
  void shouldNotAllowToUpdateApprovalWhenCandidateIsNotWithinPeriod(
      PeriodRepository periodRepository) {
    var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
    assertThrows(
        IllegalStateException.class,
        () ->
            candidate.updateApprovalAssignee(
                new UpdateAssigneeRequest(randomUri(), randomString())));
  }

  @Test
  void shouldNotResetApprovalsWhenUpdatingCandidateFieldsNotEffectingApprovals() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(upsertCandidateRequest);
    candidate = updateApprovalStatus(candidate, ApprovalStatus.APPROVED);
    var approval = candidate.getApprovals().get(HARDCODED_INSTITUTION_ID);
    var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest);
    var updatedCandidate = upsert(newUpsertRequest);
    var updatedApproval = updatedCandidate.getApprovals().get(HARDCODED_INSTITUTION_ID);

    assertThat(updatedApproval, is(equalTo(approval)));
  }

  @Test
  void shouldNotResetApprovalsWhenCreatorAffiliationChangesWithinSameInstitution() {
    var organization = scenario.getDefaultOrganization();
    var creator = createVerifiedCreator(organization);
    Map<URI, Collection<NviCreatorDto>> creatorMap = Map.of(organization.id(), List.of(creator));
    var requestBuilder = setupApprovedCandidate(scenario, organization.id(), creatorMap);

    var otherSubUnitId = organization.hasPart().get(1).id();
    var updatedCreator = new VerifiedNviCreatorDto(creator.id(), List.of(otherSubUnitId));
    creatorMap = Map.of(organization.id(), List.of(updatedCreator));
    var updatedRequest = requestBuilder.withCreatorsAndPoints(creatorMap).build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    var updatedApprovals = updatedCandidate.getApprovals();
    var updatedApproval = updatedApprovals.get(organization.id());
    assertThat(creator.affiliations(), is(not(equalTo(updatedCreator.affiliations()))));
    assertThat(ApprovalStatus.APPROVED, is(equalTo(updatedApproval.getStatus())));
    assertThat(1, is(equalTo(updatedApprovals.size())));
  }

  @Test
  void shouldNotResetApprovalWhenOtherCreatorBecomesVerified() {
    var organization = scenario.getDefaultOrganization();
    var creator = createVerifiedCreator(organization);
    var otherOrganization = scenario.setupTopLevelOrganizationWithSubUnits();
    var otherCreator = createUnverifiedCreator(otherOrganization);

    Map<URI, Collection<NviCreatorDto>> creatorMap =
        Map.of(organization.id(), List.of(creator), otherOrganization.id(), List.of(otherCreator));
    var requestBuilder = setupApprovedCandidate(scenario, organization.id(), creatorMap);

    var updatedRequest = requestBuilder.withCreatorsAndPoints(creatorMap).build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    var updatedApprovals = updatedCandidate.getApprovals();
    var updatedApproval = updatedApprovals.get(organization.id());
    assertThat(ApprovalStatus.APPROVED, is(equalTo(updatedApproval.getStatus())));
    assertThat(2, is(equalTo(updatedApprovals.size())));
  }

  @Test
  void shouldResetApprovalWhenCreatorBecomesUnverified() {
    var organization = scenario.getDefaultOrganization();
    var creator = createVerifiedCreator(organization);
    Map<URI, Collection<NviCreatorDto>> creatorMap = Map.of(organization.id(), List.of(creator));
    var requestBuilder = setupApprovedCandidate(scenario, organization.id(), creatorMap);

    var updatedCreator = new UnverifiedNviCreatorDto(randomString(), creator.affiliations());
    var updatedRequest =
        requestBuilder
            .withCreatorsAndPoints(Map.of(organization.id(), List.of(updatedCreator)))
            .build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    var updatedApprovals = updatedCandidate.getApprovals();
    var updatedApproval = updatedApprovals.get(organization.id());
    assertThat(ApprovalStatus.PENDING, is(equalTo(updatedApproval.getStatus())));
    assertThat(1, is(equalTo(updatedApprovals.size())));
  }

  @Test
  void shouldResetApprovalWhenTopLevelAffiliationChanges() {
    var organization = scenario.getDefaultOrganization();
    var creator = createVerifiedCreator(organization);
    Map<URI, Collection<NviCreatorDto>> creatorMap = Map.of(organization.id(), List.of(creator));
    var requestBuilder = setupApprovedCandidate(scenario, organization.id(), creatorMap);

    var otherOrganization = scenario.setupTopLevelOrganizationWithSubUnits();
    var otherSubUnitId = otherOrganization.hasPart().getFirst().id();
    var updatedCreator = new VerifiedNviCreatorDto(creator.id(), List.of(otherSubUnitId));
    var updatedRequest =
        requestBuilder
            .withCreatorsAndPoints(Map.of(otherOrganization.id(), List.of(updatedCreator)))
            .build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    var updatedApprovals = updatedCandidate.getApprovals();
    var updatedApproval = updatedApprovals.get(otherOrganization.id());
    assertThat(ApprovalStatus.PENDING, is(equalTo(updatedApproval.getStatus())));
    assertThat(1, is(equalTo(updatedApprovals.size())));
  }

  private VerifiedNviCreatorDto createVerifiedCreator(Organization topLevelOrg) {
    var subUnitId = topLevelOrg.hasPart().getFirst().id();
    var creatorId = randomUriWithSuffix("creatorId");
    return new VerifiedNviCreatorDto(creatorId, List.of(subUnitId));
  }

  private UnverifiedNviCreatorDto createUnverifiedCreator(Organization topLevelOrg) {
    var subUnitId = topLevelOrg.hasPart().getFirst().id();
    return new UnverifiedNviCreatorDto(randomString(), List.of(subUnitId));
  }

  @Test
  void shouldNotResetApprovalsWhenUpsertRequestContainsSameDecimalsWithAnotherScale() {
    var upsertCandidateRequest = createUpsertRequestWithDecimalScale(0, HARDCODED_INSTITUTION_ID);
    var candidate = upsert(upsertCandidateRequest);
    candidate.updateApprovalStatus(
        new UpdateStatusRequest(
            HARDCODED_INSTITUTION_ID, ApprovalStatus.APPROVED, randomString(), randomString()),
        mockOrganizationRetriever);
    var approval = candidate.getApprovals().get(HARDCODED_INSTITUTION_ID);
    var samePointsWithDifferentScale =
        upsertCandidateRequest.institutionPoints().stream()
            .map(
                institutionPoints ->
                    new InstitutionPoints(
                        institutionPoints.institutionId(),
                        institutionPoints.institutionPoints().setScale(1, EXPECTED_ROUNDING_MODE),
                        institutionPoints.creatorAffiliationPoints()))
            .toList();
    var newUpsertRequest =
        fromRequest(upsertCandidateRequest).withPoints(samePointsWithDifferentScale).build();
    var updatedCandidate = upsert(newUpsertRequest);
    var updatedApproval = updatedCandidate.getApprovals().get(HARDCODED_INSTITUTION_ID);

    assertThat(updatedApproval, is(equalTo(approval)));
  }

  @Test
  void shouldResetApprovalsWhenNonCandidateBecomesCandidate() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = upsert(upsertCandidateRequest);
    var nonCandidate =
        Candidate.updateNonCandidate(
                createUpsertNonCandidateRequest(candidate.getPublicationId()), candidateRepository)
            .orElseThrow();
    assertFalse(nonCandidate.isApplicable());
    var updatedCandidate =
        upsert(createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest));

    assertTrue(updatedCandidate.isApplicable());
    assertThat(updatedCandidate.getApprovals().size(), is(greaterThan(0)));
  }

  @ParameterizedTest(name = "Should reset approvals when {0}")
  @MethodSource("candidateResetCauseProvider")
  @DisplayName("Should reset approvals when updating fields affecting approvals")
  void shouldResetApprovalsWhenUpdatingFieldsEffectingApprovals(
      CandidateResetCauseArgument arguments) {
    var upsertCandidateRequest = getUpsertCandidateRequestWithHardcodedValues();

    var candidate = upsert(upsertCandidateRequest);
    updateApprovalStatus(candidate, ApprovalStatus.APPROVED);

    var creators =
        arguments.creators().stream()
            .collect(
                Collectors.toMap(VerifiedNviCreatorDto::id, VerifiedNviCreatorDto::affiliations));

    var newUpsertRequest =
        fromRequest(upsertCandidateRequest)
            .withCreators(creators)
            .withVerifiedCreators(arguments.creators())
            .withInstanceType(arguments.type())
            .withChannelId(arguments.channel().id())
            .withLevel(arguments.channel().level())
            .withPoints(arguments.institutionPoints())
            .build();

    var updatedCandidate = upsert(newUpsertRequest);
    var updatedApproval = updatedCandidate.getApprovals().get(HARDCODED_INSTITUTION_ID);
    assertThat(updatedApproval.getStatus(), is(equalTo(ApprovalStatus.PENDING)));
  }

  @Test
  void shouldAllowFinalizingNewValidCandidate() {
    var request = createUpsertCandidateRequest(topLevelOrganizationId).build();
    var candidate = upsert(request);

    var candidateDto = candidate.toDto(topLevelOrganizationId, mockOrganizationRetriever);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations =
        List.of(CandidateOperation.APPROVAL_APPROVE, CandidateOperation.APPROVAL_REJECT);
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }

  @Test
  void shouldNotAllowFinalizingNewCandidateWithUnverifiedCreator() {
    var unverifiedCreator =
        new UnverifiedNviCreatorDto(randomString(), List.of(topLevelOrganizationId));
    var request =
        createUpsertCandidateRequest(topLevelOrganizationId)
            .withUnverifiedCreators(List.of(unverifiedCreator))
            .build();
    var candidate = upsert(request);

    var candidateDto = candidate.toDto(topLevelOrganizationId, mockOrganizationRetriever);

    var actualAllowedOperations = candidateDto.allowedOperations();
    var expectedAllowedOperations = emptyList();
    assertThat(actualAllowedOperations, containsInAnyOrder(expectedAllowedOperations.toArray()));
  }

  @Test
  void shouldHaveNoProblemsWhenCandidateIsValid() {
    var request = createUpsertCandidateRequest(topLevelOrganizationId).build();
    var candidate = upsert(request);

    var candidateDto = candidate.toDto(topLevelOrganizationId, mockOrganizationRetriever);

    var actualProblems = candidateDto.problems();
    var expectedProblems = emptySet();
    assertEquals(actualProblems, expectedProblems);
  }

  @Test
  void shouldIncludeProblemsWhenCandidateHasUnverifiedCreator() {
    var unverifiedCreator =
        new UnverifiedNviCreatorDto(randomString(), List.of(topLevelOrganizationId));
    var request =
        createUpsertCandidateRequest(topLevelOrganizationId)
            .withUnverifiedCreators(List.of(unverifiedCreator))
            .build();
    var candidate = upsert(request);

    var candidateDto = candidate.toDto(topLevelOrganizationId, mockOrganizationRetriever);

    var expectedProblems =
        Set.of(
            new UnverifiedCreatorProblem(),
            new UnverifiedCreatorFromOrganizationProblem(List.of(unverifiedCreator.name())));
    var actualProblems = candidateDto.problems();
    assertEquals(actualProblems, expectedProblems);
  }

  private static UpdateStatusRequest createRejectionRequestWithoutReason(String username) {
    return UpdateStatusRequest.builder()
        .withApprovalStatus(ApprovalStatus.REJECTED)
        .withInstitutionId(HARDCODED_INSTITUTION_ID)
        .withUsername(username)
        .build();
  }

  private static Stream<Arguments> candidateResetCauseProvider() {
    return Stream.of(
        Arguments.of(
            Named.of(
                "channel changed",
                CandidateResetCauseArgument.defaultBuilder().withChannelId(randomUri()).build())),
        Arguments.of(
            Named.of(
                "level changed",
                CandidateResetCauseArgument.defaultBuilder().withLevel("LevelTwo").build())),
        Arguments.of(
            Named.of(
                "type changed",
                CandidateResetCauseArgument.defaultBuilder()
                    .withType(InstanceType.ACADEMIC_MONOGRAPH)
                    .build())),
        Arguments.of(
            Named.of(
                "institution points changed",
                CandidateResetCauseArgument.defaultBuilder()
                    .withPointsForInstitution(BigDecimal.TEN)
                    .build())),
        Arguments.of(
            Named.of(
                "creator changed",
                CandidateResetCauseArgument.defaultBuilder()
                    .withCreators(
                        List.of(
                            new VerifiedNviCreatorDto(
                                randomUri(), List.of(HARDCODED_INSTITUTION_ID))))
                    .build())),
        Arguments.of(
            Named.of(
                "creator removed",
                CandidateResetCauseArgument.defaultBuilder().withCreators(emptyList()).build())),
        Arguments.of(
            Named.of(
                "creator added",
                CandidateResetCauseArgument.defaultBuilder()
                    .withCreators(
                        List.of(
                            CandidateResetCauseArgument.Builder.DEFAULT_CREATOR,
                            new VerifiedNviCreatorDto(
                                randomUri(), List.of(HARDCODED_INSTITUTION_ID))))
                    .build())));
  }

  private Candidate updateApprovalStatus(Candidate candidate, ApprovalStatus status) {
    var updateRequest = createUpdateStatusRequest(status, HARDCODED_INSTITUTION_ID, randomString());
    return candidate.updateApprovalStatus(updateRequest, mockOrganizationRetriever);
  }

  private UpsertCandidateRequest getUpsertCandidateRequestWithHardcodedValues() {
    var verifiedCreator =
        new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID, List.of(HARDCODED_SUBUNIT_ID));
    return randomUpsertRequestBuilder()
        .withCreators(Map.of(HARDCODED_CREATOR_ID, List.of(HARDCODED_SUBUNIT_ID)))
        .withVerifiedCreators(List.of(verifiedCreator))
        .withInstanceType(HARDCODED_INSTANCE_TYPE)
        .withChannelId(HARDCODED_CHANNEL_ID)
        .withLevel(HARDCODED_LEVEL)
        .withPoints(
            List.of(
                new InstitutionPoints(
                    HARDCODED_INSTITUTION_ID,
                    HARDCODED_POINTS,
                    List.of(
                        new CreatorAffiliationPoints(
                            HARDCODED_CREATOR_ID, HARDCODED_SUBUNIT_ID, HARDCODED_POINTS)))))
        .build();
  }

  private Candidate upsert(UpsertCandidateRequest request) {
    Candidate.upsert(request, candidateRepository, periodRepository);
    return Candidate.fetchByPublicationId(
        request::publicationId, candidateRepository, periodRepository);
  }

  // FIXME: This is duplicated and probably not needed
  private UpsertCandidateRequest createNewUpsertRequestNotAffectingApprovals(
      UpsertCandidateRequest request) {
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
      public List<VerifiedNviCreatorDto> verifiedCreators() {
        return request.verifiedCreators();
      }

      @Override
      public List<UnverifiedNviCreatorDto> unverifiedCreators() {
        return List.of();
      }

      @Override
      public String channelType() {
        return null;
      }

      @Override
      public URI publicationChannelId() {
        return request.publicationChannelId();
      }

      @Override
      public String level() {
        return request.level();
      }

      @Override
      public InstanceType instanceType() {
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
      public List<InstitutionPoints> institutionPoints() {
        return request.institutionPoints();
      }

      @Override
      public BigDecimal totalPoints() {
        return request.totalPoints();
      }
    };
  }

  private record CandidateResetCauseArgument(
      PublicationChannel channel,
      InstanceType type,
      List<InstitutionPoints> institutionPoints,
      List<VerifiedNviCreatorDto> creators) {

    private static CandidateResetCauseArgument.Builder defaultBuilder() {
      return new Builder();
    }

    private static final class Builder {

      private static final VerifiedNviCreatorDto DEFAULT_CREATOR =
          new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID, List.of(HARDCODED_SUBUNIT_ID));
      private PublicationChannel channel =
          new PublicationChannel(ChannelType.JOURNAL, HARDCODED_CHANNEL_ID, HARDCODED_LEVEL);
      private InstanceType type = HARDCODED_INSTANCE_TYPE;
      private List<InstitutionPoints> institutionPoints =
          List.of(
              new InstitutionPoints(
                  HARDCODED_INSTITUTION_ID,
                  HARDCODED_POINTS,
                  List.of(
                      new CreatorAffiliationPoints(
                          HARDCODED_CREATOR_ID, HARDCODED_SUBUNIT_ID, HARDCODED_POINTS))));
      private List<VerifiedNviCreatorDto> creators = List.of(DEFAULT_CREATOR);

      private Builder() {}

      private Builder withType(InstanceType type) {
        this.type = type;
        return this;
      }

      private Builder withCreators(List<VerifiedNviCreatorDto> creators) {
        this.creators = creators;
        return this;
      }

      private Builder withChannelId(URI publicationChannelId) {
        this.channel =
            new PublicationChannel(ChannelType.JOURNAL, publicationChannelId, HARDCODED_LEVEL);
        return this;
      }

      private Builder withLevel(String level) {
        this.channel = new PublicationChannel(ChannelType.JOURNAL, HARDCODED_CHANNEL_ID, level);
        return this;
      }

      private Builder withPointsForInstitution(BigDecimal institutionPoints) {
        this.institutionPoints =
            List.of(
                new InstitutionPoints(
                    HARDCODED_INSTITUTION_ID,
                    institutionPoints,
                    List.of(
                        new CreatorAffiliationPoints(
                            HARDCODED_CREATOR_ID, HARDCODED_SUBUNIT_ID, HARDCODED_POINTS))));
        return this;
      }

      private CandidateResetCauseArgument build() {
        return new CandidateResetCauseArgument(channel, type, institutionPoints, creators);
      }
    }
  }
}
