package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningNotOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomApplicableCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.sikt.nva.nvi.test.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import java.math.BigDecimal;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationChannel;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.UpsertRequestBuilder;
import no.unit.nva.auth.uriretriever.UriRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class CandidateApprovalTest extends CandidateTestSetup {

    private static final String APPROVED = "APPROVED";
    private static final URI HARDCODED_INSTITUTION_ID = URI.create("https://example.org/topLevelInstitutionId");
    private static final URI HARDCODED_SUBUNIT_ID = URI.create("https://example.org/subUnitInstitutionId");
    private static final URI HARDCODED_CHANNEL_ID = URI.create(
        "https://example.org/publication-channels-v2/journal/123/2018");
    private static final String HARDCODED_LEVEL = "LevelOne";
    private static final URI HARDCODED_CREATOR_ID = URI.create("https://example.org/someCreator");
    private static final InstanceType HARDCODED_INSTANCE_TYPE = InstanceType.ACADEMIC_ARTICLE;
    private static final BigDecimal HARDCODED_POINTS = BigDecimal.ONE.setScale(EXPECTED_SCALE, EXPECTED_ROUNDING_MODE);
    private OrganizationRetriever organizationRetriever;

    public static Stream<Arguments> statusProvider() {
        return Stream.of(Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.PENDING),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED));
    }

    public static Stream<Arguments> periodRepositoryProvider() {
        var year = ZonedDateTime
                       .now()
                       .getYear();
        return Stream.of(Arguments.of(Named.of("Repository returning closed period",
                                               periodRepositoryReturningClosedPeriod(year))),
                         Arguments.of(Named.of("Repository returning period not opened yet",
                                               periodRepositoryReturningNotOpenedPeriod(year))),
                         Arguments.of(Named.of("Mocked repository", mock(PeriodRepository.class))));
    }

    @BeforeEach
    void setUp() {
        var mockUriRetriever = mock(UriRetriever.class);
        organizationRetriever = new OrganizationRetriever(mockUriRetriever);
        mockOrganizationResponseForAffiliation(HARDCODED_INSTITUTION_ID, HARDCODED_SUBUNIT_ID, mockUriRetriever);
    }

    @Test
    void shouldCreatePendingApprovalsForNewCandidate() {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(upsertCandidateRequest);
        assertThat(candidate.getApprovals().size(), is(equalTo(1)));
        assertThat(candidate
                       .getApprovals()
                       .get(HARDCODED_INSTITUTION_ID)
                       .getStatus(), is(equalTo(ApprovalStatus.PENDING)));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("statusProvider")
    void shouldUpdateStatusWhenUpdateStatusRequestValid(ApprovalStatus oldStatus, ApprovalStatus newStatus) {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository);
        var existingCandidate = Candidate.fetchByPublicationId(upsertCandidateRequest::publicationId,
                                                               candidateRepository,
                                                               periodRepository);
        existingCandidate.updateApproval(createUpdateStatusRequest(oldStatus, HARDCODED_INSTITUTION_ID, randomString()),
                                         organizationRetriever);

        var updateRequest = createUpdateStatusRequest(newStatus, HARDCODED_INSTITUTION_ID, randomString());
        var updatedCandidate = existingCandidate.updateApproval(updateRequest, organizationRetriever);

        var actualNewStatus = updatedCandidate
                                  .getApprovals()
                                  .get(HARDCODED_INSTITUTION_ID)
                                  .getStatus();
        assertThat(actualNewStatus, is(equalTo(newStatus)));
    }

    @ParameterizedTest(name="Should reset approval when changing to pending from {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"REJECTED", APPROVED})
    void shouldResetApprovalWhenChangingToPending(ApprovalStatus oldStatus) {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(upsertCandidateRequest);
        var assignee = randomString();
        candidate
            .updateApproval(new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, assignee))
            .updateApproval(createUpdateStatusRequest(oldStatus, HARDCODED_INSTITUTION_ID, randomString()),
                            organizationRetriever)
            .updateApproval(createUpdateStatusRequest(ApprovalStatus.PENDING, HARDCODED_INSTITUTION_ID, randomString()),
                            organizationRetriever);
        var approvalStatus = candidate
                                 .getApprovals()
                                 .get(HARDCODED_INSTITUTION_ID);
        assertThat(approvalStatus.getStatus(), is(equalTo(ApprovalStatus.PENDING)));
        assertThat(approvalStatus.getAssigneeUsername(), is(assignee));
        assertThat(approvalStatus.getFinalizedByUserName(), is(nullValue()));
        assertThat(approvalStatus.getFinalizedDate(), is(nullValue()));
    }

    @ParameterizedTest(name="Should remove reason when updating from rejection status to {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED})
    void shouldRemoveReasonWhenUpdatingFromRejectionStatusToNewStatus(ApprovalStatus newStatus) {
        var createRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        Candidate.upsert(createRequest, candidateRepository, periodRepository);
        var rejectedCandidate = Candidate.fetchByPublicationId(createRequest::publicationId, candidateRepository,
                                                               periodRepository)
                                         .updateApproval(createUpdateStatusRequest(ApprovalStatus.REJECTED,
                                                                                   HARDCODED_INSTITUTION_ID,
                                                                                   randomString()),
                                                         organizationRetriever);

        var updatedCandidate = rejectedCandidate.updateApproval(createUpdateStatusRequest(newStatus,
                                                                                          HARDCODED_INSTITUTION_ID,
                                                                                          randomString()),
                                                                organizationRetriever);
        assertThat(updatedCandidate.getApprovals().size(), is(equalTo(1)));
        assertThat(updatedCandidate
                       .getApprovals()
                       .get(HARDCODED_INSTITUTION_ID)
                       .getStatus(), is(equalTo(newStatus)));
        assertThat(updatedCandidate
                       .getApprovals()
                       .get(HARDCODED_INSTITUTION_ID)
                       .getReason(), is(nullValue()));
    }

    @Test
    void shouldRemoveOldInstitutionsWhenUpdatingCandidate() {
        var keepInstitutionId = randomUri();
        var deleteInstitutionId = randomUri();
        var createCandidateRequest = createUpsertCandidateRequest(keepInstitutionId, deleteInstitutionId,
                                                                  randomUri()).build();
        Candidate.upsert(createCandidateRequest, candidateRepository, periodRepository);
        var updateRequest = UpsertRequestBuilder.fromRequest(createCandidateRequest)
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
        var updatedCandidate = Candidate.updateNonCandidate(updateRequest, candidateRepository)
                                   .orElseThrow();
        assertThat(updatedCandidate.getIdentifier(), is(equalTo(candidate.getIdentifier())));
        assertThat(updatedCandidate.getApprovals().size(), is(equalTo(0)));
    }

    @Test
    void shouldThrowExceptionWhenApplicableAndNonCandidate() {
        var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
        var updateRequest = randomUpsertRequestBuilder()
                                .withPublicationId(candidate.getPublicationId())
                                .withInstanceType(null)
                                .build();
        assertThrows(InvalidNviCandidateException.class,
                     () -> Candidate.upsert(updateRequest, candidateRepository, periodRepository));
    }

    @ParameterizedTest(name="shouldThrowUnsupportedOperationWhenRejectingWithoutReason {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED})
    void shouldThrowUnsupportedOperationWhenRejectingWithoutReason(ApprovalStatus oldStatus) {
        var createRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        Candidate.upsert(createRequest, candidateRepository, periodRepository);
        var candidate = Candidate.fetchByPublicationId(createRequest::publicationId, candidateRepository,
                                                       periodRepository)
                                 .updateApproval(createUpdateStatusRequest(oldStatus,
                                                                           HARDCODED_INSTITUTION_ID,
                                                                           randomString()), organizationRetriever);
        assertThrows(UnsupportedOperationException.class, () -> candidate.updateApproval(
            createRejectionRequestWithoutReason(HARDCODED_INSTITUTION_ID, randomString()),
            organizationRetriever));
    }

    @ParameterizedTest(name="shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED, "REJECTED"})
    void shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername(ApprovalStatus newStatus) {
        var createRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(createRequest);

        assertThrows(IllegalArgumentException.class,
                     () -> candidate.updateApproval(createUpdateStatusRequest(newStatus,
                                                                              HARDCODED_INSTITUTION_ID,
                                                                              null), organizationRetriever));
    }

    @Test
    void shouldPersistStatusChangeWhenRequestingAndUpdate() {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(createUpdateStatusRequest(ApprovalStatus.APPROVED,
                                                           HARDCODED_INSTITUTION_ID,
                                                           randomString()), organizationRetriever);

        var status = Candidate
                         .fetch(candidate::getIdentifier, candidateRepository, periodRepository)
                         .getApprovals()
                         .get(HARDCODED_INSTITUTION_ID)
                         .getStatus();
        assertThat(status, is(equalTo(ApprovalStatus.APPROVED)));
    }

    @Test
    void shouldChangeAssigneeWhenValidUpdateAssigneeRequest() {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(upsertCandidateRequest);
        var newUsername = randomString();
        candidate.updateApproval(new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, newUsername));

        var assignee = Candidate
                           .fetch(candidate::getIdentifier, candidateRepository, periodRepository)
                           .toDto()
                           .approvals()
                           .getFirst()
                           .assignee();

        assertThat(assignee, is(equalTo(newUsername)));
    }

    @Test
    void shouldNotAllowUpdateApprovalStatusWhenTryingToPassAnonymousImplementations() {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(upsertCandidateRequest);
        assertThrows(IllegalArgumentException.class, () -> candidate.updateApproval(() -> HARDCODED_INSTITUTION_ID));
    }

    @ParameterizedTest
    @MethodSource("periodRepositoryProvider")
    void shouldNotAllowToUpdateApprovalWhenCandidateIsNotWithinPeriod(PeriodRepository periodRepository) {
        var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
        assertThrows(IllegalStateException.class,
                     () -> candidate.updateApproval(new UpdateAssigneeRequest(randomUri(), randomString())));
    }

    @Test
    void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {
        var assignee = randomString();
        var request = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        Candidate.upsert(request, candidateRepository, periodRepository);
        var candidate = Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository)
                                 .updateApproval(new UpdateAssigneeRequest(HARDCODED_INSTITUTION_ID, assignee))
                                 .updateApproval(createUpdateStatusRequest(ApprovalStatus.APPROVED,
                                                                           HARDCODED_INSTITUTION_ID,
                                                                           randomString()), organizationRetriever)
                                 .updateApproval(createUpdateStatusRequest(ApprovalStatus.REJECTED,
                                                                           HARDCODED_INSTITUTION_ID,
                                                                           randomString()), organizationRetriever)
                            .toDto();
        assertThat(candidate.approvals().getFirst().assignee(), is(equalTo(assignee)));
        assertThat(candidate.approvals().getFirst().finalizedBy(), is(not(equalTo(assignee))));
    }

    @Test
    void shouldNotResetApprovalsWhenUpdatingCandidateFieldsNotEffectingApprovals() {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(new UpdateStatusRequest(HARDCODED_INSTITUTION_ID,
                                                         ApprovalStatus.APPROVED,
                                                         randomString(),
                                                         randomString()), organizationRetriever);
        var approval = candidate
                           .getApprovals()
                           .get(HARDCODED_INSTITUTION_ID);
        var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest);
        var updatedCandidate = upsert(newUpsertRequest);
        var updatedApproval = updatedCandidate
                                  .getApprovals()
                                  .get(HARDCODED_INSTITUTION_ID);

        assertThat(updatedApproval, is(equalTo(approval)));
    }

    // FIXME: This test compares random URIs, there is no actual check that they belong to the same organization
    // We also lack the mirror case, where the top-level affiliation changes and resets the approval (NP-48112)
    @Test
    void shouldNotResetApprovalsWhenCreatorAffiliationChangesWithinSameInstitution() {
        var upsertCandidateRequest = getUpsertCandidateRequestWithHardcodedValues();
        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(new UpdateStatusRequest(HARDCODED_INSTITUTION_ID,
                                                         ApprovalStatus.APPROVED,
                                                         randomString(),
                                                         randomString()), organizationRetriever);
        var points = List.of(new InstitutionPoints(HARDCODED_INSTITUTION_ID, HARDCODED_POINTS,
                                                   List.of(new CreatorAffiliationPoints(HARDCODED_CREATOR_ID,
                                                                                        HARDCODED_SUBUNIT_ID,
                                                                                        HARDCODED_POINTS))));

        var newUpsertRequest = UpsertRequestBuilder.fromRequest(upsertCandidateRequest)
                                   .withPoints(points)
                                   .build();
        var updatedCandidate = upsert(newUpsertRequest);
        var updatedApproval = updatedCandidate.getApprovals().get(HARDCODED_INSTITUTION_ID);

        assertThat(updatedApproval.getStatus(), is(equalTo(ApprovalStatus.APPROVED)));
    }

    @Test
    void shouldNotResetApprovalsWhenUpsertRequestContainsSameDecimalsWithAnotherScale() {
        var upsertCandidateRequest = createUpsertRequestWithDecimalScale(0, HARDCODED_INSTITUTION_ID);
        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(new UpdateStatusRequest(HARDCODED_INSTITUTION_ID,
                                                         ApprovalStatus.APPROVED,
                                                         randomString(),
                                                         randomString()), organizationRetriever);
        var approval = candidate
                           .getApprovals()
                           .get(HARDCODED_INSTITUTION_ID);
        var samePointsWithDifferentScale = upsertCandidateRequest.institutionPoints()
                                               .stream()
                                               .map(institutionPoints -> new InstitutionPoints(
                                                   institutionPoints.institutionId(),
                                                   institutionPoints.institutionPoints()
                                                       .setScale(1, EXPECTED_ROUNDING_MODE),
                                                   institutionPoints.creatorAffiliationPoints()))
                                               .toList();
        var newUpsertRequest = UpsertRequestBuilder.fromRequest(upsertCandidateRequest)
                                   .withPoints(samePointsWithDifferentScale)
                                   .build();
        var updatedCandidate = upsert(newUpsertRequest);
        var updatedApproval = updatedCandidate
                                  .getApprovals()
                                  .get(HARDCODED_INSTITUTION_ID);

        assertThat(updatedApproval, is(equalTo(approval)));
    }

    @Test
    void shouldResetApprovalsWhenNonCandidateBecomesCandidate() {
        var upsertCandidateRequest = createUpsertCandidateRequest(HARDCODED_INSTITUTION_ID).build();
        var candidate = upsert(upsertCandidateRequest);
        var nonCandidate = Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationId()),
            candidateRepository).orElseThrow();
        assertFalse(nonCandidate.isApplicable());
        var updatedCandidate = upsert(createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest));

        assertTrue(updatedCandidate.isApplicable());
        assertThat(updatedCandidate.getApprovals().size(), is(greaterThan(0)));
    }

    @ParameterizedTest(name="Should reset approvals when {0}")
    @MethodSource("candidateResetCauseProvider")
    @DisplayName("Should reset approvals when updating fields affecting approvals")
    void shouldResetApprovalsWhenUpdatingFieldsEffectingApprovals(CandidateResetCauseArgument arguments) {
        var upsertCandidateRequest = getUpsertCandidateRequestWithHardcodedValues();

        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(new UpdateStatusRequest(HARDCODED_INSTITUTION_ID,
                                                         ApprovalStatus.APPROVED,
                                                         randomString(),
                                                         randomString()), organizationRetriever);

        var creators = arguments.creators()
                           .stream()
                           .collect(Collectors.toMap(VerifiedNviCreatorDto::id, VerifiedNviCreatorDto::affiliations));

        var newUpsertRequest = UpsertRequestBuilder.fromRequest(upsertCandidateRequest)
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

    private static UpdateStatusRequest createRejectionRequestWithoutReason(URI institutionId, String username) {
        return UpdateStatusRequest.builder()
                   .withApprovalStatus(ApprovalStatus.REJECTED)
                   .withInstitutionId(institutionId)
                   .withUsername(username)
                   .build();
    }

    private static Stream<Arguments> candidateResetCauseProvider() {
        return Stream.of(
            Arguments.of(Named.of("channel changed",
                                  CandidateResetCauseArgument.defaultBuilder().withChannelId(randomUri()).build())),
            Arguments.of(Named.of("level changed",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withLevel("LevelTwo")
                                      .build())),
            Arguments.of(Named.of("type changed",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withType(InstanceType.ACADEMIC_MONOGRAPH)
                                      .build())),
            Arguments.of(Named.of("institution points changed",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withPointsForInstitution(BigDecimal.TEN)
                                      .build())),
            Arguments.of(Named.of("creator changed",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withCreators(
                                          List.of(new VerifiedNviCreatorDto(randomUri(), List.of(HARDCODED_INSTITUTION_ID))))
                                      .build())),
            Arguments.of(Named.of("creator removed",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withCreators(Collections.emptyList())
                                      .build())),
            Arguments.of(Named.of("creator added",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withCreators(List.of(CandidateResetCauseArgument.Builder.DEFAULT_CREATOR,
                                                            new VerifiedNviCreatorDto(randomUri(),
                                                                                      List.of(HARDCODED_INSTITUTION_ID))))
                                      .build())));
    }

    private UpsertCandidateRequest getUpsertCandidateRequestWithHardcodedValues() {
        var verifiedCreator = new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID, List.of(HARDCODED_SUBUNIT_ID));
        return randomUpsertRequestBuilder()
                   .withCreators(Map.of(HARDCODED_CREATOR_ID, List.of(HARDCODED_SUBUNIT_ID)))
                   .withVerifiedCreators(List.of(verifiedCreator))
                   .withInstanceType(HARDCODED_INSTANCE_TYPE)
                   .withChannelId(HARDCODED_CHANNEL_ID)
                   .withLevel(HARDCODED_LEVEL)
                   .withPoints(List.of(
                       new InstitutionPoints(HARDCODED_INSTITUTION_ID, HARDCODED_POINTS,
                                             List.of(new CreatorAffiliationPoints(HARDCODED_CREATOR_ID,
                                                                                  HARDCODED_SUBUNIT_ID,
                                                                                  HARDCODED_POINTS)))))
                   .build();
    }

    private Candidate upsert(UpsertCandidateRequest request) {
        Candidate.upsert(request, candidateRepository, periodRepository);
        return Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
    }

    // FIXME: This is duplicated and probably not needed
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

    private record CandidateResetCauseArgument(PublicationChannel channel, InstanceType type,
                                               List<InstitutionPoints> institutionPoints, List<VerifiedNviCreatorDto> creators) {

        private static CandidateResetCauseArgument.Builder defaultBuilder() {
            return new Builder();
        }

        private static final class Builder {

            private static final VerifiedNviCreatorDto DEFAULT_CREATOR = new VerifiedNviCreatorDto(HARDCODED_CREATOR_ID,
                                                                                                   List.of(HARDCODED_SUBUNIT_ID));
            private PublicationChannel channel = new PublicationChannel(ChannelType.JOURNAL,
                                                                        HARDCODED_CHANNEL_ID,
                                                                        HARDCODED_LEVEL);
            private InstanceType type = HARDCODED_INSTANCE_TYPE;
            private List<InstitutionPoints> institutionPoints = List.of(
                new InstitutionPoints(HARDCODED_INSTITUTION_ID, HARDCODED_POINTS,
                                      List.of(new CreatorAffiliationPoints(HARDCODED_CREATOR_ID,
                                                                           HARDCODED_SUBUNIT_ID,
                                                                           HARDCODED_POINTS))));
            private List<VerifiedNviCreatorDto> creators = List.of(DEFAULT_CREATOR);

            private Builder() {
            }

            private Builder withType(InstanceType type) {
                this.type = type;
                return this;
            }

            private Builder withCreators(List<VerifiedNviCreatorDto> creators) {
                this.creators = creators;
                return this;
            }

            private Builder withChannelId(URI publicationChannelId) {
                this.channel = new PublicationChannel(ChannelType.JOURNAL, publicationChannelId, HARDCODED_LEVEL);
                return this;
            }

            private Builder withLevel(String level) {
                this.channel = new PublicationChannel(ChannelType.JOURNAL, HARDCODED_CHANNEL_ID, level);
                return this;
            }

            private Builder withPointsForInstitution(BigDecimal institutionPoints) {
                this.institutionPoints = List.of(new InstitutionPoints(HARDCODED_INSTITUTION_ID, institutionPoints,
                                                                       List.of(new CreatorAffiliationPoints(
                                                                           HARDCODED_CREATOR_ID,
                                                                           HARDCODED_SUBUNIT_ID,
                                                                           HARDCODED_POINTS))));
                return this;
            }

            private CandidateResetCauseArgument build() {
                return new CandidateResetCauseArgument(channel, type, institutionPoints, creators);
            }
        }
    }
}
