package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.test.TestUtils.createUpdateStatusRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
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
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.ChannelType;
import no.sikt.nva.nvi.common.model.InvalidNviCandidateException;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstanceType;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.common.service.model.PublicationChannel;
import no.sikt.nva.nvi.common.service.model.VerifiedNviCreator;
import no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate;
import no.sikt.nva.nvi.common.service.model.UnverifiedNviCreator;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.UpsertRequestBuilder;
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

    public static Stream<Arguments> statusProvider() {
        return Stream.of(Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.PENDING),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED));
    }

    public static Stream<PeriodRepository> periodRepositoryProvider() {
        var year = ZonedDateTime.now().getYear();
        return Stream.of(periodRepositoryReturningClosedPeriod(year), periodRepositoryReturningNotOpenedPeriod(year),
                         mock(PeriodRepository.class));
    }

    @Test
    void shouldCreatePendingApprovalsForNewCandidate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
        var candidate = upsert(upsertCandidateRequest);
        assertThat(candidate.getApprovals().size(), is(equalTo(1)));
        assertThat(candidate.getApprovals().get(institutionId).getStatus(), is(equalTo(ApprovalStatus.PENDING)));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("statusProvider")
    void shouldUpdateStatusWhenUpdateStatusRequestValid(ApprovalStatus oldStatus, ApprovalStatus newStatus) {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
        Candidate.upsert(upsertCandidateRequest, candidateRepository, periodRepository);
        var existingCandidate = Candidate.fetchByPublicationId(upsertCandidateRequest::publicationId,
                                                               candidateRepository,
                                                               periodRepository)
                                    .updateApproval(
                                        createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        var updatedCandidate = existingCandidate.updateApproval(
            createUpdateStatusRequest(newStatus, institutionId, randomString()));

        var actualNewStatus = updatedCandidate.getApprovals().get(institutionId).getStatus();
        assertThat(actualNewStatus, is(equalTo(newStatus)));
    }

    @ParameterizedTest(name="Should reset approval when changing to pending from {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"REJECTED", APPROVED})
    void shouldResetApprovalWhenChangingToPending(ApprovalStatus oldStatus) {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
        var candidateBO = upsert(upsertCandidateRequest);
        var assignee = randomString();
        candidateBO.updateApproval(new UpdateAssigneeRequest(institutionId, assignee))
            .updateApproval(createUpdateStatusRequest(oldStatus, institutionId, randomString()))
            .updateApproval(createUpdateStatusRequest(ApprovalStatus.PENDING, institutionId, randomString()));
        var approvalStatus = candidateBO.getApprovals().get(institutionId);
        assertThat(approvalStatus.getStatus(), is(equalTo(ApprovalStatus.PENDING)));
        assertThat(approvalStatus.getAssigneeUsername(), is(assignee));
        assertThat(approvalStatus.getFinalizedByUserName(), is(nullValue()));
        assertThat(approvalStatus.getFinalizedDate(), is(nullValue()));
    }

    @ParameterizedTest(name="Should remove reason when updating from rejection status to {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED})
    void shouldRemoveReasonWhenUpdatingFromRejectionStatusToNewStatus(ApprovalStatus newStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId).build();
        Candidate.upsert(createRequest, candidateRepository, periodRepository);
        var rejectedCandidate = Candidate.fetchByPublicationId(createRequest::publicationId, candidateRepository,
                                                               periodRepository)
                                    .updateApproval(
                                        createUpdateStatusRequest(ApprovalStatus.REJECTED, institutionId,
                                                                  randomString()));

        var updatedCandidate = rejectedCandidate.updateApproval(
            createUpdateStatusRequest(newStatus, institutionId, randomString()));
        assertThat(updatedCandidate.getApprovals().size(), is(equalTo(1)));
        assertThat(updatedCandidate.getApprovals().get(institutionId).getStatus(), is(equalTo(newStatus)));
        assertThat(updatedCandidate.getApprovals().get(institutionId).getReason(), is(nullValue()));
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
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId).build();
        Candidate.upsert(createRequest, candidateRepository, periodRepository);
        var candidate = Candidate.fetchByPublicationId(createRequest::publicationId, candidateRepository,
                                                       periodRepository)
                            .updateApproval(createUpdateStatusRequest(oldStatus, institutionId, randomString()));
        assertThrows(UnsupportedOperationException.class, () -> candidate.updateApproval(
            createRejectionRequestWithoutReason(institutionId, randomString())));
    }

    @ParameterizedTest(name="shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername {0}")
    @EnumSource(value = ApprovalStatus.class, names = {"PENDING", APPROVED, "REJECTED"})
    void shouldThrowIllegalArgumentExceptionWhenUpdateStatusWithoutUsername(ApprovalStatus newStatus) {
        var institutionId = randomUri();
        var createRequest = createUpsertCandidateRequest(institutionId).build();
        var candidate = upsert(createRequest);

        assertThrows(IllegalArgumentException.class,
                     () -> candidate.updateApproval(createUpdateStatusRequest(newStatus, institutionId, null)));
    }

    @Test
    void shouldPersistStatusChangeWhenRequestingAndUpdate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
        var candidateBO = upsert(upsertCandidateRequest);
        candidateBO.updateApproval(createUpdateStatusRequest(ApprovalStatus.APPROVED, institutionId, randomString()));

        var status = Candidate.fetch(candidateBO::getIdentifier, candidateRepository, periodRepository)
                         .getApprovals().get(institutionId).getStatus();
        assertThat(status, is(equalTo(ApprovalStatus.APPROVED)));
    }

    @Test
    void shouldChangeAssigneeWhenValidUpdateAssigneeRequest() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
        var candidateBO = upsert(upsertCandidateRequest);
        var newUsername = randomString();
        candidateBO.updateApproval(new UpdateAssigneeRequest(institutionId, newUsername));

        var assignee = Candidate.fetch(candidateBO::getIdentifier, candidateRepository, periodRepository)
                           .toDto()
                           .approvals()
                           .getFirst()
                           .assignee();

        assertThat(assignee, is(equalTo(newUsername)));
    }

    @Test
    void shouldNotAllowUpdateApprovalStatusWhenTryingToPassAnonymousImplementations() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
        var candidateBO = upsert(upsertCandidateRequest);
        assertThrows(IllegalArgumentException.class, () -> candidateBO.updateApproval(() -> institutionId));
    }

    @ParameterizedTest(name="Should not allow to update approval when candidate is not within period {0}")
    @MethodSource("periodRepositoryProvider")
    void shouldNotAllowToUpdateApprovalWhenCandidateIsNotWithinPeriod(PeriodRepository periodRepository) {
        var candidate = randomApplicableCandidate(candidateRepository, periodRepository);
        assertThrows(IllegalStateException.class,
                     () -> candidate.updateApproval(new UpdateAssigneeRequest(randomUri(), randomString())));
    }

    @Test
    void shouldNotOverrideAssigneeWhenAssigneeAlreadyIsSet() {
        var institutionId = randomUri();
        var assignee = randomString();
        var request = createUpsertCandidateRequest(institutionId).build();
        Candidate.upsert(request, candidateRepository, periodRepository);
        var candidate = Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository)
                            .updateApproval(new UpdateAssigneeRequest(institutionId, assignee))
                            .updateApproval(
                                createUpdateStatusRequest(ApprovalStatus.APPROVED, institutionId, randomString()))
                            .updateApproval(
                                createUpdateStatusRequest(ApprovalStatus.REJECTED, institutionId, randomString()))
                            .toDto();
        assertThat(candidate.approvals().getFirst().assignee(), is(equalTo(assignee)));
        assertThat(candidate.approvals().getFirst().finalizedBy(), is(not(equalTo(assignee))));
    }

    @Test
    void shouldNotResetApprovalsWhenUpdatingCandidateFieldsNotEffectingApprovals() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(
            new UpdateStatusRequest(institutionId, ApprovalStatus.APPROVED, randomString(), randomString()));
        var approval = candidate.getApprovals().get(institutionId);
        var newUpsertRequest = createNewUpsertRequestNotAffectingApprovals(upsertCandidateRequest);
        var updatedCandidate = upsert(newUpsertRequest);
        var updatedApproval = updatedCandidate.getApprovals().get(institutionId);

        assertThat(updatedApproval, is(equalTo(approval)));
    }

    @Test
    void shouldNotResetApprovalsWhenCreatorAffiliationChangesWithinSameInstitution() {
        var upsertCandidateRequest = getUpsertCandidateRequestWithHardcodedValues();
        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(
            new UpdateStatusRequest(HARDCODED_INSTITUTION_ID, ApprovalStatus.APPROVED, randomString(), randomString()));
        var newSubUnitInSameOrganization = randomUri();
        var points = List.of(new InstitutionPoints(HARDCODED_INSTITUTION_ID, HARDCODED_POINTS,
                                                   List.of(new CreatorAffiliationPoints(HARDCODED_CREATOR_ID,
                                                                                        newSubUnitInSameOrganization,
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
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertRequestWithDecimalScale(0, institutionId);
        var candidate = upsert(upsertCandidateRequest);
        candidate.updateApproval(
            new UpdateStatusRequest(institutionId, ApprovalStatus.APPROVED, randomString(), randomString()));
        var approval = candidate.getApprovals().get(institutionId);
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
        var updatedApproval = updatedCandidate.getApprovals().get(institutionId);

        assertThat(updatedApproval, is(equalTo(approval)));
    }

    @Test
    void shouldResetApprovalsWhenNonCandidateBecomesCandidate() {
        var institutionId = randomUri();
        var upsertCandidateRequest = createUpsertCandidateRequest(institutionId).build();
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
        candidate.updateApproval(
            new UpdateStatusRequest(HARDCODED_INSTITUTION_ID, ApprovalStatus.APPROVED, randomString(), randomString()));

        var creators = arguments.creators()
                           .stream()
                           .collect(Collectors.toMap(VerifiedNviCreator::id, VerifiedNviCreator::affiliations));

        var newUpsertRequest = UpsertRequestBuilder.fromRequest(upsertCandidateRequest)
                                   .withCreators(creators)
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
                                          List.of(new VerifiedNviCreator(randomUri(), List.of(HARDCODED_INSTITUTION_ID))))
                                      .build())),
            Arguments.of(Named.of("creator removed",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withCreators(Collections.emptyList())
                                      .build())),
            Arguments.of(Named.of("creator added",
                                  CandidateResetCauseArgument.defaultBuilder()
                                      .withCreators(List.of(CandidateResetCauseArgument.Builder.DEFAULT_CREATOR,
                                                            new VerifiedNviCreator(randomUri(),
                                                                                   List.of(HARDCODED_INSTITUTION_ID))))
                                      .build())));
    }

    private UpsertCandidateRequest getUpsertCandidateRequestWithHardcodedValues() {
        return randomUpsertRequestBuilder()
                   .withCreators(Map.of(HARDCODED_CREATOR_ID, List.of(HARDCODED_SUBUNIT_ID)))
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
            public List<UnverifiedNviCreator> unverifiedCreators() {
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
                                               List<InstitutionPoints> institutionPoints, List<VerifiedNviCreator> creators) {

        private static CandidateResetCauseArgument.Builder defaultBuilder() {
            return new Builder();
        }

        private static final class Builder {

            private static final VerifiedNviCreator DEFAULT_CREATOR = new VerifiedNviCreator(HARDCODED_CREATOR_ID,
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
            private List<VerifiedNviCreator> creators = List.of(DEFAULT_CREATOR);

            private Builder() {
            }

            private Builder withType(InstanceType type) {
                this.type = type;
                return this;
            }

            private Builder withCreators(List<VerifiedNviCreator> creators) {
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
