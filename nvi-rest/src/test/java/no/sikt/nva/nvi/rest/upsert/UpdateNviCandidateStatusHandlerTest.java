package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.mockOrganizationResponseForAffiliation;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningNotOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class UpdateNviCandidateStatusHandlerTest extends LocalDynamoTest {

    private static final String ERROR_MISSING_REJECTION_REASON = "Cannot reject approval status without reason";
    private static final String CANDIDATE_IDENTIFIER_PATH = "candidateIdentifier";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_REJECTED = "REJECTED";
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private UpdateNviCandidateStatusHandler handler;
    private Context context;
    private ByteArrayOutputStream output;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private FakeViewingScopeValidator viewingScopeValidator;
    private final URI defaultTopLevelInstitutionId = URI.create("https://www.example.com/toplevelOrganization");
    private final URI defaultSubUnitInstitutionId = URI.create("https://www.example.com/subOrganization");
    private OrganizationRetriever mockOrganizationRetriever;

    public static Stream<Arguments> approvalStatusProvider() {
        return Stream.of(Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
                         Arguments.of(ApprovalStatus.PENDING, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.PENDING),
                         Arguments.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.APPROVED),
                         Arguments.of(ApprovalStatus.REJECTED, ApprovalStatus.PENDING));
    }

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        viewingScopeValidator = new FakeViewingScopeValidator(true);
        var mockUriRetriever = mock(UriRetriever.class);
        mockOrganizationRetriever = new OrganizationRetriever(mockUriRetriever);
        handler = new UpdateNviCandidateStatusHandler(candidateRepository,
                                                      periodRepository,
                                                      viewingScopeValidator,
                                                      mockOrganizationRetriever);
        mockOrganizationResponseForAffiliation(defaultTopLevelInstitutionId,
                                               defaultSubUnitInstitutionId,
                                               mockUriRetriever);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(randomStatusRequest()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        var request = createRequest(candidate.getIdentifier(), defaultTopLevelInstitutionId, ApprovalStatus.APPROVED);
        viewingScopeValidator = new FakeViewingScopeValidator(false);
        handler = new UpdateNviCandidateStatusHandler(candidateRepository,
                                                      periodRepository,
                                                      viewingScopeValidator,
                                                      mockOrganizationRetriever);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldBeForbiddenToChangeStatusOfOtherInstitution() throws IOException {
        var otherInstitutionId = randomUri();
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        var requestBody = new NviStatusRequest(candidate.getIdentifier(),
                                               defaultTopLevelInstitutionId,
                                               ApprovalStatus.PENDING,
                                               null);
        var request = createRequest(candidate.getIdentifier(), otherInstitutionId, requestBody);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_FORBIDDEN)));
    }

    @Test
    void shouldReturnConflictWhenUpdatingStatusAndReportingPeriodIsClosed() throws IOException {
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningClosedPeriod(CURRENT_YEAR);
        handler = new UpdateNviCandidateStatusHandler(candidateRepository,
                                                      periodRepository,
                                                      viewingScopeValidator,
                                                      mockOrganizationRetriever);
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        var request = createRequest(candidate.getIdentifier(), defaultTopLevelInstitutionId, ApprovalStatus.APPROVED);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @ParameterizedTest(name = "Should not allow status {0} for institution with unverified creators")
    @EnumSource(value = ApprovalStatus.class, names = {STATUS_APPROVED, STATUS_REJECTED})
    void shouldReturnConflictWhenUpdatingStatusAndInstitutionHasUnverifiedCreators(ApprovalStatus newStatus)
        throws IOException {
        var request = createRequestForInstitutionWithUnverifiedCreator(defaultTopLevelInstitutionId,
                                                                       defaultTopLevelInstitutionId,
                                                                       newStatus);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @ParameterizedTest(name = "Should not allow status {0} for institution with unverified creators")
    @EnumSource(value = ApprovalStatus.class, names = {STATUS_APPROVED, STATUS_REJECTED})
    void shouldReturnConflictWhenUpdatingStatusAndSubInstitutionHasUnverifiedCreators(ApprovalStatus newStatus)
        throws IOException {
        var request = createRequestForInstitutionWithUnverifiedCreator(defaultTopLevelInstitutionId,
                                                                       defaultSubUnitInstitutionId,
                                                                       newStatus);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldReturnConflictWhenUpdatingStatusAndNotOpenedPeriod() throws IOException {
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningNotOpenedPeriod(CURRENT_YEAR);
        handler = new UpdateNviCandidateStatusHandler(candidateRepository,
                                                      periodRepository,
                                                      viewingScopeValidator,
                                                      mockOrganizationRetriever);
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        var request = createRequest(candidate.getIdentifier(), defaultTopLevelInstitutionId, ApprovalStatus.APPROVED);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldReturnBadRequestIfRejectionDoesNotContainReason() throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        var request = createRequestWithoutReason(candidate.getIdentifier(), defaultTopLevelInstitutionId);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertEquals(response.getStatusCode(), HttpURLConnection.HTTP_BAD_REQUEST);
        assertThat(response.getBody(), containsString(ERROR_MISSING_REJECTION_REASON));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("approvalStatusProvider")
    void shouldUpdateApprovalStatus(ApprovalStatus oldStatus, ApprovalStatus newStatus) throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        candidate.updateApproval(createStatusRequest(oldStatus));
        var request = createRequest(candidate.getIdentifier(), defaultTopLevelInstitutionId, newStatus);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var candidateResponse = response.getBodyObject(CandidateDto.class);
        var actualApproval = candidateResponse.approvals().get(0);
        var expectedStatus = getExpectedApprovalStatus(newStatus);
        assertThat(actualApproval.status(), is(equalTo(expectedStatus)));
    }

    @ParameterizedTest(name="shouldResetFinalizedValuesWhenUpdatingStatusToPending from old status {0}")
    @EnumSource(value = ApprovalStatus.class, names = {STATUS_REJECTED, STATUS_APPROVED})
    void shouldResetFinalizedValuesWhenUpdatingStatusToPending(ApprovalStatus oldStatus) throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        candidate.updateApproval(createStatusRequest(oldStatus));
        var newStatus = ApprovalStatus.PENDING;
        var request = createRequest(candidate.getIdentifier(), defaultTopLevelInstitutionId, newStatus);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var candidateResponse = response.getBodyObject(CandidateDto.class);
        var actualApproval = candidateResponse.approvals().getFirst();
        assertThat(actualApproval.finalizedBy(), is(nullValue()));
        assertThat(actualApproval.finalizedDate(), is(nullValue()));
        assertThat(actualApproval.status(), is(equalTo(ApprovalStatusDto.NEW)));
    }

    @ParameterizedTest(name="shouldUpdateApprovalStatusToRejectedWithReason from old status {0}")
    @EnumSource(value = ApprovalStatus.class, names = {STATUS_PENDING, STATUS_APPROVED})
    void shouldUpdateApprovalStatusToRejectedWithReason(ApprovalStatus oldStatus) throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        candidate.updateApproval(createStatusRequest(oldStatus));
        var rejectionReason = randomString();
        var requestBody = new NviStatusRequest(candidate.getIdentifier(),
                                               defaultTopLevelInstitutionId,
                                               ApprovalStatus.REJECTED,
                                               rejectionReason);
        var request = createRequest(candidate.getIdentifier(),
                                    defaultTopLevelInstitutionId,
                                    requestBody,
                                    randomString());
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var candidateResponse = response.getBodyObject(CandidateDto.class);
        var actualApprovalStatus = candidateResponse.approvals().getFirst();

        assertThat(actualApprovalStatus.status(), is(equalTo(ApprovalStatusDto.REJECTED)));
        assertThat(actualApprovalStatus.reason(), is(equalTo(rejectionReason)));
    }

    @ParameterizedTest(name="shouldRemoveReasonWhenUpdatingStatusFromRejected to {0}")
    @EnumSource(value = ApprovalStatus.class, names = {STATUS_PENDING, STATUS_APPROVED})
    void shouldRemoveReasonWhenUpdatingStatusFromRejected(ApprovalStatus newStatus) throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        candidate.updateApproval(createStatusRequest(ApprovalStatus.REJECTED));
        var request = createRequest(candidate.getIdentifier(), defaultTopLevelInstitutionId,
                                    ApprovalStatus.parse(newStatus.getValue()));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var candidateResponse = response.getBodyObject(CandidateDto.class);
        var actualApproval = candidateResponse.approvals().getFirst();
        var expectedStatus = getExpectedApprovalStatus(newStatus);
        assertThat(actualApproval.status(), is(equalTo(expectedStatus)));
        assertThat(actualApproval.reason(), is(nullValue()));
    }

    @Test
    void shouldUpdateAssigneeWhenFinalizingApprovalWithoutAssignee() throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(defaultTopLevelInstitutionId).build());
        var assignee = randomString();
        var requestBody = new NviStatusRequest(candidate.getIdentifier(),
                                               defaultTopLevelInstitutionId,
                                               ApprovalStatus.APPROVED,
                                               null);
        var request = createRequest(candidate.getIdentifier(), defaultTopLevelInstitutionId, requestBody, assignee);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var candidateResponse = response.getBodyObject(CandidateDto.class);

        assertThat(candidateResponse.approvals().get(0).assignee(), is(equalTo(assignee)));
    }

    private static UpdateStatusRequest createStatusRequest(ApprovalStatus status) {
        return UpdateStatusRequest.builder()
                   .withApprovalStatus(status)
                   .withUsername(randomString())
                   .withReason(ApprovalStatus.REJECTED.equals(status) ? randomString() : null)
                   .build();
    }

    private static InputStream createRequest(UUID candidateIdentifier, URI institutionId, NviStatusRequest requestBody)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper).withPathParameters(
                Map.of(CANDIDATE_IDENTIFIER_PATH, candidateIdentifier.toString()))
                   .withBody(requestBody)
                   .withCurrentCustomer(institutionId)
                   .withTopLevelCristinOrgId(institutionId)
                   .withAccessRights(institutionId, AccessRight.MANAGE_NVI_CANDIDATES)
                   .withUserName(randomString())
                   .build();
    }

    private static InputStream createRequest(UUID candidateIdentifier, URI institutionId, NviStatusRequest requestBody,
                                             String username) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper).withPathParameters(
                Map.of(CANDIDATE_IDENTIFIER_PATH, candidateIdentifier.toString()))
                   .withBody(requestBody)
                   .withCurrentCustomer(institutionId)
                   .withTopLevelCristinOrgId(institutionId)
                   .withAccessRights(institutionId, AccessRight.MANAGE_NVI_CANDIDATES)
                   .withUserName(username)
                   .build();
    }

    private Candidate upsert(UpsertCandidateRequest request) {
        Candidate.upsert(request, candidateRepository, periodRepository);
        return Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
    }

    private ApprovalStatusDto getExpectedApprovalStatus(ApprovalStatus status) {
        return switch (status) {
            case PENDING -> ApprovalStatusDto.NEW;
            case APPROVED -> ApprovalStatusDto.APPROVED;
            case REJECTED -> ApprovalStatusDto.REJECTED;
        };
    }

    private InputStream createRequest(UUID candidateIdentifier, URI institutionId, ApprovalStatus status)
        throws JsonProcessingException {
        var requestBody =
            new NviStatusRequest(candidateIdentifier, institutionId, status, ApprovalStatus.REJECTED.equals(status)
                                                                                 ? randomString() : null);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }

    private InputStream createRequestWithoutReason(UUID candidateIdentifier, URI institutionId)
        throws JsonProcessingException {
        var requestBody = new NviStatusRequest(candidateIdentifier, institutionId, ApprovalStatus.REJECTED, null);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }

    private InputStream createRequestWithoutAccessRights(NviStatusRequest body) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper).withBody(body).build();
    }

    private NviStatusRequest randomStatusRequest() {
        return new NviStatusRequest(randomUUID(), randomUri(), ApprovalStatus.APPROVED, null);
    }

    private InputStream createRequestForInstitutionWithUnverifiedCreator(URI topLevelInstitution,
                                                                         URI subInstitution,
                                                                         ApprovalStatus newStatus)
        throws JsonProcessingException {
        var unverifiedCreator = UnverifiedNviCreatorDto
                                    .builder()
                                    .withAffiliations(List.of(subInstitution))
                                    .withName(randomString())
                                    .build();
        var upsertRequest = createUpsertCandidateRequest(topLevelInstitution)
                                .withUnverifiedCreators(List.of(unverifiedCreator))
                                .build();
        var candidate = upsert(upsertRequest);
        return createRequest(candidate.getIdentifier(), topLevelInstitution, newStatus);
    }
}
