package no.sikt.nva.nvi.common.service;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.RequestFixtures.randomAssigneeRequest;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.fromRequest;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpdateStatusRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupFuturePeriod;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CANNOT_UPDATE_APPROVAL;
import static no.sikt.nva.nvi.common.dto.AllowedOperationFixtures.CURATOR_CAN_FINALIZE_APPROVAL;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.unverifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoCopiedFrom;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.randomApplicableCandidateRequestBuilder;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.createOrganizationWithSubUnit;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.common.model.UserInstanceFixtures.createCuratorUserInstance;
import static no.sikt.nva.nvi.common.model.UsernameFixtures.randomUsername;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PointCalculationDtoBuilder;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.UpsertNviCandidateRequest;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.InstanceType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.PublicationChannel;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.model.UserInstance;
import no.sikt.nva.nvi.common.service.dto.NviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorFromOrganizationProblem;
import no.sikt.nva.nvi.common.service.dto.problem.UnverifiedCreatorProblem;
import no.sikt.nva.nvi.common.service.exception.IllegalCandidateUpdateException;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
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
  private static final ScientificValue HARDCODED_LEVEL = ScientificValue.LEVEL_ONE;
  private static final URI HARDCODED_CREATOR_ID = URI.create("https://example.org/someCreator");
  private static final InstanceType HARDCODED_INSTANCE_TYPE = InstanceType.ACADEMIC_ARTICLE;
  private static final BigDecimal HARDCODED_POINTS =
      BigDecimal.ONE.setScale(EXPECTED_SCALE, EXPECTED_ROUNDING_MODE);
  private static final UserInstance CURATOR_USER =
      createCuratorUserInstance(HARDCODED_INSTITUTION_ID);
  private Organization topLevelOrganization;
  private URI topLevelOrganizationId;
  private Candidate initialCandidate;
  private UUID candidateIdentifier;

  private static Stream<Arguments> statusProvider() {
    return Stream.of(
        Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED),
        Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
        Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING),
        Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
        Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.PENDING),
        Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED));
  }

  @BeforeEach
  void setUp() {
    topLevelOrganization = scenario.setupTopLevelOrganizationWithSubUnits();
    topLevelOrganizationId = topLevelOrganization.id();
    mockOrganizationResponseForAffiliation(
        HARDCODED_INSTITUTION_ID, HARDCODED_SUBUNIT_ID, mockUriRetriever);

    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    initialCandidate = scenario.upsertCandidate(upsertCandidateRequest);
    candidateIdentifier = initialCandidate.identifier();
  }

  @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
  @MethodSource("statusProvider")
  void shouldUpdateStatusWhenUpdateStatusRequestValid(
      ApprovalStatus oldStatus, ApprovalStatus newStatus) {
    updateApprovalStatus(candidateIdentifier, oldStatus);
    var updatedCandidate = updateApprovalStatus(candidateIdentifier, newStatus);
    var actualNewStatus = updatedCandidate.approvals().get(HARDCODED_INSTITUTION_ID).status();
    assertThat(actualNewStatus).isEqualTo(newStatus);
  }

  @ParameterizedTest(name = "Should reset approval when changing to pending from {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"REJECTED", APPROVED})
  void shouldResetApprovalWhenChangingToPending(ApprovalStatus oldStatus) {
    var assignee = randomString();
    var request = new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, assignee);
    approvalService.updateApproval(initialCandidate, request, CURATOR_USER);
    updateApprovalStatus(candidateIdentifier, oldStatus);

    var updatedCandidate = updateApprovalStatus(candidateIdentifier, ApprovalStatus.PENDING);
    var approvalStatus = updatedCandidate.approvals().get(HARDCODED_INSTITUTION_ID);

    assertThat(approvalStatus)
        .extracting(
            Approval::status,
            Approval::getAssigneeUsername,
            Approval::getFinalizedByUserName,
            Approval::finalizedDate)
        .containsExactly(ApprovalStatus.PENDING, assignee, null, null);
  }

  @Test
  void shouldCreatePendingApprovalsForNewCandidate() {
    var candidate = scenario.getCandidateByIdentifier(candidateIdentifier);
    assertThat(candidate.approvals())
        .hasSize(1)
        .extractingByKey(HARDCODED_INSTITUTION_ID)
        .extracting(Approval::status)
        .isEqualTo(ApprovalStatus.PENDING);
  }

  @ParameterizedTest(name = "shouldThrowIllegalArgumentExceptionWhenRejectingWithoutReason {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"PENDING", APPROVED})
  void shouldThrowUnsupportedOperationWhenRejectingWithoutReason(ApprovalStatus oldStatus) {
    var updatedCandidate = updateApprovalStatus(candidateIdentifier, oldStatus);
    var updateRequest = createRejectionRequestWithoutReason(randomString());
    assertThrows(
        IllegalArgumentException.class,
        () -> approvalService.updateApproval(updatedCandidate, updateRequest, CURATOR_USER));
  }

  @ParameterizedTest(name = "Should remove reason when updating from rejection status to {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {"PENDING", APPROVED})
  void shouldRemoveReasonWhenUpdatingFromRejectionStatusToNewStatus(ApprovalStatus newStatus) {
    var rejection =
        createUpdateStatusRequest(
            ApprovalStatus.REJECTED, HARDCODED_INSTITUTION_ID, randomString());
    scenario.updateApprovalStatus(candidateIdentifier, rejection, CURATOR_USER);

    var updatedCandidate = updateApprovalStatus(candidateIdentifier, newStatus);
    assertThat(updatedCandidate.approvals())
        .hasSize(1)
        .extractingByKey(HARDCODED_INSTITUTION_ID)
        .extracting(Approval::status, Approval::reason)
        .containsExactly(newStatus, null);
  }

  @ParameterizedTest(
      name = "shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername {0}")
  @EnumSource(
      value = ApprovalStatus.class,
      names = {APPROVED, "REJECTED"})
  void shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername(
      ApprovalStatus newStatus) {
    var invalidRequest = createUpdateStatusRequest(newStatus, HARDCODED_INSTITUTION_ID, null);
    assertThrows(
        IllegalArgumentException.class,
        () -> approvalService.updateApproval(initialCandidate, invalidRequest, CURATOR_USER));
  }

  @Test
  void shouldRemoveOldInstitutionsWhenUpdatingCandidate() {
    var keepInstitutionId = randomUri();
    var deleteInstitutionId = randomUri();
    var createCandidateRequest =
        createUpsertCandidateRequest(keepInstitutionId, deleteInstitutionId, randomUri()).build();
    scenario.upsertCandidate(createCandidateRequest);

    var updateRequest =
        fromRequest(createCandidateRequest)
            .withPoints(List.of(new InstitutionPoints(keepInstitutionId, randomBigDecimal(), null)))
            .build();
    var updatedCandidate = scenario.upsertCandidate(updateRequest);

    assertThat(updatedCandidate.approvals()).hasSize(1).containsOnlyKeys(keepInstitutionId);
  }

  @Test
  void shouldRemoveApprovalsWhenBecomingNonCandidate() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var updateRequest = createUpsertNonCandidateRequest(candidate.getPublicationId());
    candidateService.updateCandidate(updateRequest);
    var updatedCandidate = candidateService.getByPublicationId(candidate.getPublicationId());

    assertThat(updatedCandidate.identifier()).isEqualTo(candidate.identifier());
    assertThat(updatedCandidate.approvals()).isEmpty();
  }

  @Test
  void shouldThrowExceptionWhenApplicableAndNonCandidate() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var updateRequest =
        randomUpsertRequestBuilder()
            .withPublicationId(candidate.getPublicationId())
            .withInstanceType(null)
            .build();
    assertThrows(
        InvalidNviCandidateException.class, () -> candidateService.upsertCandidate(updateRequest));
  }

  @Test
  void shouldPersistStatusChangeWhenRequestingAndUpdate() {
    var updatedCandidate = updateApprovalStatus(candidateIdentifier, ApprovalStatus.APPROVED);

    assertThat(updatedCandidate.approvals())
        .extractingByKey(HARDCODED_INSTITUTION_ID)
        .extracting(Approval::status)
        .isEqualTo(ApprovalStatus.APPROVED);
  }

  @Test
  void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {
    var request =
        new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, CURATOR_USER.userName().toString());
    approvalService.updateApproval(initialCandidate, request, CURATOR_USER);

    var user1 = createCuratorUserInstance(HARDCODED_INSTITUTION_ID);
    updateApprovalStatus(candidateIdentifier, ApprovalStatus.APPROVED, user1);

    var user2 = createCuratorUserInstance(HARDCODED_INSTITUTION_ID);
    updateApprovalStatus(candidateIdentifier, ApprovalStatus.REJECTED, user2);

    var updatedCandidate = scenario.getCandidateByIdentifier(candidateIdentifier);

    assertThat(updatedCandidate.approvals())
        .extractingByKey(HARDCODED_INSTITUTION_ID)
        .extracting(Approval::getAssigneeUsername, Approval::getFinalizedByUserName)
        .containsExactly(CURATOR_USER.userName().toString(), user2.userName().toString());
  }

  @Test
  void shouldChangeAssigneeWhenValidUpdateAssigneeRequest() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = scenario.upsertCandidate(upsertCandidateRequest);
    var newUsername = randomUsername().toString();
    var request = new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, newUsername);
    approvalService.updateApproval(candidate, request, CURATOR_USER);

    var updatedCandidate = candidateService.getByIdentifier(candidate.identifier());
    var updatedApproval = updatedCandidate.approvals().get(HARDCODED_INSTITUTION_ID);
    assertThat(updatedApproval.getAssigneeUsername()).isEqualTo(newUsername);
  }

  @Test
  void shouldNotAllowUpdatingApprovalAssigneeWhenCandidateIsInClosedPeriod() {
    var candidate = setupRandomApplicableCandidate(scenario);
    setupClosedPeriod(scenario, CURRENT_YEAR);
    var request = randomAssigneeRequest();
    assertThrows(
        IllegalCandidateUpdateException.class,
        () -> approvalService.updateApproval(candidate, request, CURATOR_USER));
  }

  @Test
  void shouldNotAllowUpdatingApprovalAssigneeWhenCandidateIsInPendingPeriod() {
    var candidate = setupRandomApplicableCandidate(scenario);
    setupFuturePeriod(scenario, CURRENT_YEAR);
    var request = randomAssigneeRequest();
    assertThrows(
        IllegalCandidateUpdateException.class,
        () -> approvalService.updateApproval(candidate, request, CURATOR_USER));
  }

  @Test
  void shouldNotResetApprovalsWhenUpdatingCandidateFieldsNotEffectingApprovals() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = scenario.upsertCandidate(upsertCandidateRequest);
    candidate = updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED);
    var approval = candidate.approvals().get(HARDCODED_INSTITUTION_ID);
    var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest);
    var updatedCandidate = scenario.upsertCandidate(newUpsertRequest);
    var updatedApproval = updatedCandidate.approvals().get(HARDCODED_INSTITUTION_ID);

    assertThat(updatedApproval).isEqualTo(approval);
  }

  @Test
  void shouldNotResetApprovalsWhenCreatorAffiliationChangesWithinSameInstitution() {
    var organization = scenario.getDefaultOrganization();
    var creator = createVerifiedCreator(organization);
    Map<Organization, Collection<NviCreatorDto>> creatorMap =
        Map.of(organization, List.of(creator));
    var requestBuilder =
        setupApprovedCandidateAndReturnRequestBuilder(organization.id(), creatorMap);

    var otherSubUnitId = organization.hasPart().get(1).id();
    var updatedCreator = new VerifiedNviCreatorDto(creator.id(), null, List.of(otherSubUnitId));
    creatorMap = Map.of(organization, List.of(updatedCreator));
    var updatedRequest = requestBuilder.withCreatorsAndPoints(creatorMap).build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    var updatedApprovals = updatedCandidate.approvals();

    assertThat(updatedApprovals)
        .hasSize(1)
        .extractingByKey(organization.id())
        .extracting(Approval::status)
        .isEqualTo(ApprovalStatus.APPROVED);

    var actualAffiliations = updatedCandidate.getNviCreatorAffiliations();
    assertThat(actualAffiliations)
        .contains(otherSubUnitId)
        .doesNotContain(creator.affiliations().getFirst());
  }

  @Test
  void shouldNotResetApprovalWhenOtherCreatorBecomesVerified() {
    var organization = scenario.setupTopLevelOrganizationWithSubUnits();
    var creator = verifiedNviCreatorDtoFrom(organization);
    var otherOrganization = scenario.setupTopLevelOrganizationWithSubUnits();
    var otherCreator = unverifiedNviCreatorDtoFrom(otherOrganization);

    Map<Organization, Collection<NviCreatorDto>> creatorMap =
        Map.of(organization, List.of(creator), otherOrganization, List.of(otherCreator));
    var requestBuilder =
        setupApprovedCandidateAndReturnRequestBuilder(organization.id(), creatorMap);

    var updatedRequest = requestBuilder.withCreatorsAndPoints(creatorMap).build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    assertThat(updatedCandidate.approvals())
        .hasSize(2)
        .extractingByKey(organization.id())
        .extracting(Approval::status)
        .isEqualTo(ApprovalStatus.APPROVED);
  }

  @Test
  void shouldResetApprovalWhenCreatorBecomesUnverified() {
    var organization = scenario.getDefaultOrganization();
    var creator = createVerifiedCreator(organization);
    var requestBuilder =
        setupApprovedCandidateAndReturnRequestBuilder(
            organization.id(), Map.of(organization, List.of(creator)));

    var updatedCreator = new UnverifiedNviCreatorDto(randomString(), creator.affiliations());
    var updatedRequest =
        requestBuilder.withCreatorsAndPoints(Map.of(organization, List.of(updatedCreator))).build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    assertThat(updatedCandidate.approvals())
        .hasSize(1)
        .extractingByKey(organization.id())
        .extracting(Approval::status)
        .isEqualTo(ApprovalStatus.PENDING);
  }

  @Test
  void shouldResetApprovalWhenTopLevelAffiliationChanges() {
    var organization = scenario.getDefaultOrganization();
    var creator = createVerifiedCreator(organization);
    Map<Organization, Collection<NviCreatorDto>> creatorMap =
        Map.of(organization, List.of(creator));
    var requestBuilder =
        setupApprovedCandidateAndReturnRequestBuilder(organization.id(), creatorMap);

    var otherOrganization = scenario.setupTopLevelOrganizationWithSubUnits();
    var updatedCreator =
        verifiedNviCreatorDtoCopiedFrom(creator, otherOrganization.hasPart().getFirst());
    var updatedRequest =
        requestBuilder
            .withCreatorsAndPoints(Map.of(otherOrganization, List.of(updatedCreator)))
            .build();
    var updatedCandidate = scenario.upsertCandidate(updatedRequest);

    assertThat(updatedCandidate.approvals())
        .hasSize(1)
        .extractingByKey(otherOrganization.id())
        .extracting(Approval::status)
        .isEqualTo(ApprovalStatus.PENDING);
  }

  private VerifiedNviCreatorDto createVerifiedCreator(Organization topLevelOrg) {
    return verifiedNviCreatorDtoFrom(topLevelOrg.hasPart().getFirst());
  }

  private UpsertRequestBuilder setupApprovedCandidateAndReturnRequestBuilder(
      URI approvedByOrg, Map<Organization, Collection<NviCreatorDto>> creatorsPerOrganization) {
    var requestBuilder = randomApplicableCandidateRequestBuilder(creatorsPerOrganization);
    var candidate = scenario.upsertCandidate(requestBuilder.build());
    scenario.updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED, approvedByOrg);
    return requestBuilder;
  }

  @Test
  void shouldNotResetApprovalsWhenUpsertRequestContainsSameDecimalsWithAnotherScale() {
    var upsertCandidateRequest = createUpsertRequestWithDecimalScale(0, HARDCODED_INSTITUTION_ID);
    var candidate = scenario.upsertCandidate(upsertCandidateRequest);
    candidateIdentifier = candidate.identifier();

    candidate = updateApprovalStatus(candidateIdentifier, ApprovalStatus.APPROVED);
    var approval = candidate.approvals().get(HARDCODED_INSTITUTION_ID);
    var samePointsWithDifferentScale =
        upsertCandidateRequest.pointCalculation().institutionPoints().stream()
            .map(
                institutionPoints ->
                    new InstitutionPoints(
                        institutionPoints.institutionId(),
                        institutionPoints.institutionPoints().setScale(1, EXPECTED_ROUNDING_MODE),
                        institutionPoints.creatorAffiliationPoints()))
            .toList();

    var updatedPointCalculation =
        new PointCalculationDtoBuilder(upsertCandidateRequest.pointCalculation())
            .withInstitutionPoints(samePointsWithDifferentScale)
            .build();
    var newUpsertRequest =
        fromRequest(upsertCandidateRequest).withPointCalculation(updatedPointCalculation).build();
    var updatedCandidate = scenario.upsertCandidate(newUpsertRequest);
    var updatedApproval = updatedCandidate.approvals().get(HARDCODED_INSTITUTION_ID);

    assertEquals(approval, updatedApproval);
  }

  @Test
  void shouldResetApprovalsWhenNonCandidateBecomesCandidate() {
    var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
    var candidate = scenario.upsertCandidate(upsertCandidateRequest);
    candidateService.updateCandidate(createUpsertNonCandidateRequest(candidate.getPublicationId()));

    var updatedCandidate =
        scenario.upsertCandidate(
            createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest));

    assertTrue(updatedCandidate.isApplicable());
    assertThat(updatedCandidate.approvals().values())
        .isNotEmpty()
        .extracting(Approval::status)
        .containsOnly(ApprovalStatus.PENDING);
  }

  @ParameterizedTest(name = "Should reset approvals when {0}")
  @MethodSource("candidateResetCauseProvider")
  @DisplayName("Should reset approvals when updating fields affecting approvals")
  void shouldResetApprovalsWhenUpdatingFieldsEffectingApprovals(
      CandidateResetCauseArgument arguments) {
    var upsertCandidateRequest = getUpsertCandidateRequestWithHardcodedValues();

    var candidate = scenario.upsertCandidate(upsertCandidateRequest);
    updateApprovalStatus(candidate.identifier(), ApprovalStatus.APPROVED);
    var channel =
        PublicationChannelDto.builder()
            .withId(arguments.channel().id())
            .withChannelType(arguments.channel().channelType())
            .withScientificValue(arguments.channel().scientificValue())
            .build();
    var creators = arguments.creators().stream().map(NviCreatorDto.class::cast).toList();
    var newUpsertRequest =
        fromRequest(upsertCandidateRequest)
            .withNviCreators(creators)
            .withInstanceType(arguments.type())
            .withPublicationChannel(channel)
            .withPoints(arguments.institutionPoints())
            .build();

    var updatedCandidate = scenario.upsertCandidate(newUpsertRequest);
    var updatedApproval = updatedCandidate.approvals().get(HARDCODED_INSTITUTION_ID);
    assertThat(updatedApproval.status()).isEqualTo(ApprovalStatus.PENDING);
  }

  @Test
  void shouldAllowFinalizingNewValidCandidate() {
    var request = createUpsertCandidateRequest(topLevelOrganizationId).build();
    var candidate = scenario.upsertCandidate(request);

    var userInstance = createCuratorUserInstance(topLevelOrganizationId);
    var candidateDto = CandidateResponseFactory.create(candidate, userInstance);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(actualAllowedOperations)
        .containsExactlyInAnyOrderElementsOf(CURATOR_CAN_FINALIZE_APPROVAL);
  }

  @Test
  void shouldNotAllowFinalizingNewCandidateWithUnverifiedCreator() {
    var unverifiedCreator = unverifiedNviCreatorDtoFrom(topLevelOrganization);
    var request =
        createUpsertCandidateRequest(topLevelOrganizationId)
            .withCreatorsAndPoints(Map.of(topLevelOrganization, List.of(unverifiedCreator)))
            .build();
    var candidate = scenario.upsertCandidate(request);

    var userInstance = createCuratorUserInstance(topLevelOrganizationId);
    var candidateDto = CandidateResponseFactory.create(candidate, userInstance);

    var actualAllowedOperations = candidateDto.allowedOperations();
    assertThat(actualAllowedOperations)
        .containsExactlyInAnyOrderElementsOf(CURATOR_CANNOT_UPDATE_APPROVAL);
  }

  @Test
  void shouldHaveNoProblemsWhenCandidateIsValid() {
    var request = createUpsertCandidateRequest(topLevelOrganizationId).build();
    var candidate = scenario.upsertCandidate(request);

    var userInstance = createCuratorUserInstance(topLevelOrganizationId);
    var candidateDto = CandidateResponseFactory.create(candidate, userInstance);

    assertThat(candidateDto.problems()).isEmpty();
  }

  @Test
  void shouldIncludeProblemsWhenCandidateHasUnverifiedCreator() {
    var unverifiedCreator = unverifiedNviCreatorDtoFrom(topLevelOrganization);
    var request =
        createUpsertCandidateRequest(topLevelOrganizationId)
            .withCreatorsAndPoints(Map.of(topLevelOrganization, List.of(unverifiedCreator)))
            .build();
    var candidate = scenario.upsertCandidate(request);

    var userInstance = createCuratorUserInstance(topLevelOrganizationId);
    var candidateDto = CandidateResponseFactory.create(candidate, userInstance);

    var expectedProblems =
        Set.of(
            new UnverifiedCreatorProblem(),
            new UnverifiedCreatorFromOrganizationProblem(List.of(unverifiedCreator.name())));

    assertThat(candidateDto.problems()).containsExactlyInAnyOrderElementsOf(expectedProblems);
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
                    .withCreators(List.of(verifiedNviCreatorDtoFrom(HARDCODED_INSTITUTION_ID)))
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
                            verifiedNviCreatorDtoFrom(HARDCODED_INSTITUTION_ID)))
                    .build())));
  }

  private Candidate updateApprovalStatus(UUID candidateIdentifier, ApprovalStatus status) {
    return updateApprovalStatus(candidateIdentifier, status, CURATOR_USER);
  }

  private Candidate updateApprovalStatus(
      UUID candidateIdentifier, ApprovalStatus status, UserInstance user) {
    var username = user.userName().toString();
    var updateRequest = createUpdateStatusRequest(status, HARDCODED_INSTITUTION_ID, username);
    return scenario.updateApprovalStatus(candidateIdentifier, updateRequest, user);
  }

  private UpsertNviCandidateRequest getUpsertCandidateRequestWithHardcodedValues() {
    var verifiedCreator =
        new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID, null, List.of(HARDCODED_SUBUNIT_ID));
    var channel =
        PublicationChannelDto.builder()
            .withId(HARDCODED_CHANNEL_ID)
            .withScientificValue(HARDCODED_LEVEL)
            .withChannelType(ChannelType.JOURNAL)
            .build();
    var topLevelNviOrganization =
        createOrganizationWithSubUnit(HARDCODED_INSTITUTION_ID, HARDCODED_SUBUNIT_ID);
    return randomUpsertRequestBuilder()
        .withNviCreators(verifiedCreator)
        .withTopLevelOrganizations(List.of(topLevelNviOrganization))
        .withInstanceType(HARDCODED_INSTANCE_TYPE)
        .withPublicationChannel(channel)
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

  private UpsertNviCandidateRequest createNewUpsertRequestNotAffectingApprovals(
      UpsertNviCandidateRequest request) {
    var pointCalculation =
        new PointCalculationDtoBuilder(request.pointCalculation())
            .withIsInternationalCollaboration(false)
            .build();
    return fromRequest(request).withPointCalculation(pointCalculation).build();
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
          new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID, null, List.of(HARDCODED_SUBUNIT_ID));
      private PublicationChannel channel =
          new PublicationChannel(HARDCODED_CHANNEL_ID, ChannelType.JOURNAL, HARDCODED_LEVEL);
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
            new PublicationChannel(publicationChannelId, ChannelType.JOURNAL, HARDCODED_LEVEL);
        return this;
      }

      private Builder withLevel(String level) {
        this.channel =
            new PublicationChannel(
                HARDCODED_CHANNEL_ID, ChannelType.JOURNAL, ScientificValue.parse(level));
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
