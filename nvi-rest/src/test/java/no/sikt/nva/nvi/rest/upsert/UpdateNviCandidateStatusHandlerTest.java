package no.sikt.nva.nvi.rest.upsert;

import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.rest.upsert.NviApprovalStatus.PENDING;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomInstanceTypeExcluding;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbCreator;
import no.sikt.nva.nvi.common.db.model.DbLevel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.model.UpdateStatusRequest;
import no.sikt.nva.nvi.common.service.NviService;
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

public class UpdateNviCandidateStatusHandlerTest extends LocalDynamoTest {

    private UpdateNviCandidateStatusHandler handler;
    private NviService nviService;
    private Context context;
    private ByteArrayOutputStream output;

    public static Stream<Arguments> approvalStatusProvider() {
        return Stream.of(Arguments.of(DbStatus.PENDING, DbStatus.REJECTED),
                         Arguments.of(DbStatus.PENDING, DbStatus.APPROVED),
                         Arguments.of(DbStatus.APPROVED, DbStatus.PENDING),
                         Arguments.of(DbStatus.APPROVED, DbStatus.REJECTED),
                         Arguments.of(DbStatus.REJECTED, DbStatus.PENDING),
                         Arguments.of(DbStatus.APPROVED, DbStatus.APPROVED));
    }

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        nviService = new NviService(initializeTestDatabase());
        handler = new UpdateNviCandidateStatusHandler(nviService);
    }

    @Test
    void shouldUpdateAssigneeWhenFinalizingApprovalWithoutAssignee() throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var assignee = randomString();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        var requestBody = new NviStatusRequest(candidate.identifier(), institutionId, APPROVED);
        var request = createRequest(candidate.identifier(), institutionId, requestBody, assignee);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var candidateResponse = response.getBodyObject(CandidateResponse.class);

        assertThat(candidateResponse.approvalStatuses().get(0).assignee().getValue(), is(equalTo(assignee)));
    }

    @ParameterizedTest(name = "Should update from old status {0} to new status {1}")
    @MethodSource("approvalStatusProvider")
    void shouldUpdateApprovalStatus(DbStatus oldStatus, DbStatus newStatus) throws IOException {
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        candidate.approvalStatuses().get(0).update(nviService, new UpdateStatusRequest(oldStatus, randomString()));
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
        var candidate = nviService.upsertCandidate(randomCandidate()).orElseThrow();
        var institutionId = candidate.approvalStatuses().get(0).institutionId();
        candidate.approvalStatuses().get(0).update(nviService, new UpdateStatusRequest(oldStatus, randomString()));
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
        var requestBody = new NviStatusRequest(candidateIdentifier, institutionId, status);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }

    private Candidate createExistingCandidateWithApprovalStatus(URI institutionId) {
        return nviService.upsertCandidate(createCandidate(institutionId)).orElseThrow();
    }

    private InputStream createRequestWithoutAccessRights(NviStatusRequest body) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper).withBody(body).build();
    }

    private NviStatusRequest randomStatusRequest() {
        return new NviStatusRequest(randomUUID(), randomUri(), NviApprovalStatus.APPROVED);
    }

    private InputStream createUnauthorizedRequest(UUID candidateIdentifier, URI institutionId)
        throws JsonProcessingException {
        var requestBody = new NviStatusRequest(candidateIdentifier, randomUri(), NviApprovalStatus.PENDING);
        return createRequest(candidateIdentifier, institutionId, requestBody);
    }
}
