package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.PENDING;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.test.TestUtils.OPEN_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.candidateRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.candidateRepositoryReturningNotOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.candidateRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.dto.CandidateDTO;
import no.sikt.nva.nvi.rest.model.CandidateResponse;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class UpdateNviCandidateStatusHandlerTest extends LocalDynamoTest {

    private static final String ERROR_MISSING_REJECTION_REASON = "Cannot reject approval status without reason";
    private static final String CANDIDATE_IDENTIFIER_QUERY_PARAM = "candidateIdentifier";
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private UpdateNviCandidateStatusHandler handler;
    private Context context;
    private ByteArrayOutputStream output;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    public static Stream<Arguments> approvalStatusProvider() {
        return Stream.of(Arguments.of(DbStatus.PENDING, DbStatus.APPROVED),
                         Arguments.of(DbStatus.PENDING, DbStatus.REJECTED),
                         Arguments.of(DbStatus.APPROVED, DbStatus.PENDING),
                         Arguments.of(DbStatus.APPROVED, DbStatus.REJECTED),
                         Arguments.of(DbStatus.REJECTED, DbStatus.APPROVED),
                         Arguments.of(DbStatus.REJECTED, DbStatus.PENDING));
    }

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = candidateRepositoryReturningOpenedPeriod(Integer.parseInt(OPEN_YEAR));
        handler = new UpdateNviCandidateStatusHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(randomStatusRequest()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(CoreMatchers.equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldBeForbiddenToChangeStatusOfOtherInstitution() throws IOException {
        var institutionId = randomUri();
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository,
                                                periodRepository);
        var request = createUnauthorizedRequest(candidate.identifier(), institutionId);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_FORBIDDEN)));
    }

    @Test
    void shouldReturnConflictWhenUpdatingStatusAndReportingPeriodIsClosed() throws IOException {
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = candidateRepositoryReturningClosedPeriod(Integer.parseInt(OPEN_YEAR));
        handler = new UpdateNviCandidateStatusHandler(candidateRepository, periodRepository);
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        var request = createRequest(candidate.identifier(), institutionId, APPROVED);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldReturnConflictWhenUpdatingStatusAndNotOpenedPeriod() throws IOException {
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = candidateRepositoryReturningNotOpenedPeriod(Integer.parseInt(OPEN_YEAR));
        handler = new UpdateNviCandidateStatusHandler(candidateRepository, periodRepository);
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        var request = createRequest(candidate.identifier(), institutionId, APPROVED);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldReturnBadRequestIfRejectionDoesNotContainReason() throws IOException {
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        var request = createRequestWithoutReason(candidate.identifier(), institutionId, REJECTED);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
        assertThat(response.getBody(), containsString(ERROR_MISSING_REJECTION_REASON));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("approvalStatusProvider")
    void shouldUpdateApprovalStatus(DbStatus oldStatus, DbStatus newStatus) throws IOException {
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        candidate.updateStatus(createStatusRequest(oldStatus));
        var request = createRequest(candidate.identifier(), institutionId,
                                    NviApprovalStatus.parse(newStatus.getValue()));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDTO.class);
        var candidateResponse = response.getBodyObject(CandidateDTO.class);

        assertThat(candidateResponse.approvalStatuses().get(0).status().getValue(), is(equalTo(newStatus.getValue())));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"REJECTED", "APPROVED"})
    void shouldResetFinalizedValuesWhenUpdatingStatusToPending(DbStatus oldStatus) throws IOException {
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        candidate.updateStatus(createStatusRequest(oldStatus));
        var newStatus = PENDING;
        var request = createRequest(candidate.identifier(), institutionId, newStatus);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDTO.class);
        var candidateResponse = response.getBodyObject(CandidateDTO.class);

        assertThat(candidateResponse.approvalStatuses().get(0).finalizedBy(), is(nullValue()));
        assertThat(candidateResponse.approvalStatuses().get(0).finalizedDate(), is(nullValue()));
        assertThat(candidateResponse.approvalStatuses().get(0).status().getValue(), is(equalTo(newStatus.getValue())));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldUpdateApprovalStatusToRejectedWithReason(DbStatus oldStatus) throws IOException {
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        candidate.updateStatus(createStatusRequest(oldStatus));
        var rejectionReason = randomString();
        var newStatus = REJECTED;
        var requestBody = new NviStatusRequest(candidate.identifier(), institutionId, newStatus, rejectionReason);
        var request = createRequest(candidate.identifier(), institutionId, requestBody, randomString());
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);

        var actualApprovalStatus = candidateResponse.approvalStatuses().get(0);
        assertThat(actualApprovalStatus.status().getValue(), is(equalTo(newStatus.getValue())));
        assertThat(actualApprovalStatus.reason(), is(equalTo(rejectionReason)));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldRemoveReasonWhenUpdatingStatusFromRejected(DbStatus newStatus) throws IOException {
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        candidate.updateStatus(createStatusRequest(DbStatus.REJECTED));
        var request = createRequest(candidate.identifier(), institutionId,
                                    NviApprovalStatus.parse(newStatus.getValue()));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);

        var actualApprovalStatus = candidateResponse.approvalStatuses().get(0);
        assertThat(actualApprovalStatus.status().getValue(), is(equalTo(newStatus.getValue())));
        assertThat(actualApprovalStatus.reason(), is(nullValue()));
    }

    @Test
    void shouldUpdateAssigneeWhenFinalizingApprovalWithoutAssignee() throws IOException {
        var institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        var assignee = randomString();
        var requestBody = new NviStatusRequest(candidate.identifier(), institutionId, APPROVED, null);
        var request = createRequest(candidate.identifier(), institutionId, requestBody, assignee);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDTO.class);
        var candidateResponse = response.getBodyObject(CandidateDTO.class);

        assertThat(candidateResponse.approvalStatuses().get(0).assignee(), is(equalTo(assignee)));
    }

    private static UpdateStatusRequest createStatusRequest(DbStatus status) {
        return UpdateStatusRequest.builder()
                   .withApprovalStatus(status)
                   .withUsername(randomString())
                   .withReason(DbStatus.REJECTED.equals(status) ? randomString() : null)
                   .build();
    }

    private static InputStream createRequest(UUID candidateIdentifier, URI institutionId, NviStatusRequest requestBody)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper).withPathParameters(
                Map.of("candidateIdentifier", candidateIdentifier.toString()))
                   .withBody(requestBody)
                   .withCurrentCustomer(institutionId)
                   .withTopLevelCristinOrgId(institutionId)
                   .withAccessRights(institutionId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }

    private static InputStream createRequest(UUID candidateIdentifier, URI institutionId, NviStatusRequest requestBody,
                                             String username) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper).withPathParameters(
                Map.of(CANDIDATE_IDENTIFIER_QUERY_PARAM, candidateIdentifier.toString()))
                   .withBody(requestBody)
                   .withCurrentCustomer(institutionId)
                   .withTopLevelCristinOrgId(institutionId)
                   .withAccessRights(institutionId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(username)
                   .build();
    }

    private InputStream createRequest(UUID candidateIdentifier, URI institutionId, NviApprovalStatus status)
        throws JsonProcessingException {
        var requestBody = new NviStatusRequest(candidateIdentifier, institutionId, status, REJECTED.equals(status) ?
                                                                                               randomString() : null);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }

    private InputStream createRequestWithoutReason(UUID candidateIdentifier, URI institutionId,
                                                   NviApprovalStatus status)
        throws JsonProcessingException {
        var requestBody = new NviStatusRequest(candidateIdentifier, institutionId, status, null);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }

    private InputStream createRequestWithoutAccessRights(NviStatusRequest body) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper).withBody(body).build();
    }

    private NviStatusRequest randomStatusRequest() {
        return new NviStatusRequest(randomUUID(), randomUri(), NviApprovalStatus.APPROVED, null);
    }

    private InputStream createUnauthorizedRequest(UUID candidateIdentifier, URI institutionId)
        throws JsonProcessingException {
        var requestBody = new NviStatusRequest(candidateIdentifier, randomUri(), NviApprovalStatus.PENDING, null);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }
}
