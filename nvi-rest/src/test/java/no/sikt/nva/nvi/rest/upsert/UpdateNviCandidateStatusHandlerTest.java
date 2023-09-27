package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.PENDING;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.test.TestUtils.nviServiceReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidateWithPublicationYear;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
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
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreator;
import no.sikt.nva.nvi.common.db.CandidateDao.DbLevel;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.Candidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.CandidateResponse;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
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

public class UpdateNviCandidateStatusHandlerTest extends LocalDynamoTest {

    private static final int YEAR = Calendar.getInstance().getWeekYear();
    private static final String ERROR_MISSING_REJECTION_REASON = "Cannot reject approval status without reason";
    private UpdateNviCandidateStatusHandler handler;
    private NviService nviService;
    private Context context;
    private ByteArrayOutputStream output;

    public static Stream<Arguments> approvalStatusProvider() {
        return Stream.of(Arguments.of(DbStatus.PENDING, DbStatus.APPROVED),
                         Arguments.of(DbStatus.APPROVED, DbStatus.PENDING),
                         Arguments.of(DbStatus.REJECTED, DbStatus.PENDING),
                         Arguments.of(DbStatus.APPROVED, DbStatus.APPROVED));
    }

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = TestUtils.nviServiceReturningOpenPeriod(initializeTestDatabase(), YEAR);
        handler = new UpdateNviCandidateStatusHandler(nviService);
    }

    @Test
    void shouldUpdateAssigneeWhenFinalizingApprovalWithoutAssignee() throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var assignee = randomString();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        var requestBody = new NviStatusRequest(candidate.identifier(), institutionId, APPROVED, null);
        var request = createRequest(candidate.identifier(), institutionId, requestBody, assignee);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);

        assertThat(candidateResponse.approvalStatuses().get(0).assignee(), is(equalTo(assignee)));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("approvalStatusProvider")
    void shouldUpdateApprovalStatus(DbStatus oldStatus, DbStatus newStatus) throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        candidate.approvalStatuses().get(0).update(nviService, UpdateStatusRequest.builder()
                                                                   .withApprovalStatus(oldStatus)
                                                                   .withUsername(randomString())
                                                                   .withReason(randomString())
                                                                   .build());
        var request = createRequest(candidate.identifier(), institutionId,
                                    NviApprovalStatus.parse(newStatus.getValue()));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);

        assertThat(candidateResponse.approvalStatuses().get(0).status().getValue(), is(equalTo(newStatus.getValue())));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"REJECTED", "APPROVED"})
    void shouldResetFinalizedValuesWhenUpdatingStatusToPending(DbStatus oldStatus) throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        candidate.approvalStatuses().get(0).update(nviService, UpdateStatusRequest.builder()
                                                                   .withApprovalStatus(oldStatus)
                                                                   .withUsername(randomString())
                                                                   .withReason(randomString())
                                                                   .build());
        var request = createRequest(candidate.identifier(), institutionId, PENDING);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);
        assertThat(candidateResponse.approvalStatuses().get(0).finalizedBy(), is(nullValue()));

        assertThat(candidateResponse.approvalStatuses().get(0).finalizedDate(), is(nullValue()));
        assertThat(candidateResponse.approvalStatuses().get(0).status(), is(equalTo(PENDING)));
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
        var candidate = createExistingCandidateWithApprovalStatus(institutionId);
        var request = createUnauthorizedRequest(candidate.identifier(), institutionId);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_FORBIDDEN)));
    }

    @Test
    void shouldReturnBadRequestWhenUpdatingStatusAndReportingPeriodIsClosed() throws IOException {
        var nviService = nviServiceReturningClosedPeriod(initializeTestDatabase(), YEAR);
        var candidate = nviService.upsertCandidate(randomCandidateWithPublicationYear(YEAR)).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        var handler = new UpdateNviCandidateStatusHandler(nviService);
        var request = createRequest(candidate.identifier(), institutionId, PENDING);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @Test
    void shouldReturnBadRequestIfRejectionDoesNotContainReason() throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        var requestBody = new NviStatusRequest(candidate.identifier(), institutionId, REJECTED, null);
        var request = createRequest(candidate.identifier(), institutionId, requestBody, randomString());
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
        assertThat(response.getBody(), containsString(ERROR_MISSING_REJECTION_REASON));
    }

    @ParameterizedTest
    @EnumSource(value = DbStatus.class, names = {"PENDING", "APPROVED"})
    void shouldUpdateApprovalStatusToRejectedWithReason(DbStatus oldStatus) throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        candidate.approvalStatuses().get(0).update(nviService, new UpdateStatusRequest(oldStatus, randomString(),
                                                                                       null));
        var newStatus = REJECTED;
        var rejectionReason = randomString();
        var request = createRejectionRequest(candidate.identifier(), institutionId, newStatus, rejectionReason);
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
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        candidate.approvalStatuses()
            .get(0)
            .update(nviService, new UpdateStatusRequest(DbStatus.REJECTED, randomString(),
                                                        randomString()));
        var request = createRequest(candidate.identifier(), institutionId,
                                    NviApprovalStatus.parse(newStatus.getValue()));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);

        var actualApprovalStatus = candidateResponse.approvalStatuses().get(0);
        assertThat(actualApprovalStatus.status().getValue(), is(equalTo(newStatus.getValue())));
        assertThat(actualApprovalStatus.reason(), is(nullValue()));
    }

    private static DbCandidate createCandidate(URI institutionId) {
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .level(DbLevel.LEVEL_ONE)
                   .internationalCollaboration(false)
                   .publicationBucketUri(randomUri())
                   .publicationDate(new DbPublicationDate("2023", "01", "01"))
                   .creators(List.of(new DbCreator(randomUri(), List.of(institutionId))))
                   .creatorCount(1)
                   .instanceType(randomInstanceTypeExcluding(InstanceType.NON_CANDIDATE))
                   .applicable(true)
                   .points(List.of())
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
                Map.of("candidateIdentifier", candidateIdentifier.toString()))
                   .withBody(requestBody)
                   .withCurrentCustomer(institutionId)
                   .withTopLevelCristinOrgId(institutionId)
                   .withAccessRights(institutionId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(username)
                   .build();
    }

    private InputStream createRequest(UUID candidateIdentifier, URI institutionId, NviApprovalStatus status)
        throws JsonProcessingException {
        var requestBody = new NviStatusRequest(candidateIdentifier, institutionId, status, null);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }

    private InputStream createRejectionRequest(UUID candidateIdentifier, URI institutionId, NviApprovalStatus status,
                                               String rejectionReason)
        throws JsonProcessingException {
        var requestBody = new NviStatusRequest(candidateIdentifier, institutionId, status, rejectionReason);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }

    private Candidate createExistingCandidateWithApprovalStatus(URI institutionId) {
        return nviService.upsertCandidate(createCandidate(institutionId)).orElseThrow();
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
