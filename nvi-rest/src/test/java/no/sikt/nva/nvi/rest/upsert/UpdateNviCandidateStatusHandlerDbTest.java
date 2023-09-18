package no.sikt.nva.nvi.rest.upsert;

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
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbCreator;
import no.sikt.nva.nvi.common.db.model.DbLevel;
import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.zalando.problem.Problem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class UpdateNviCandidateStatusHandlerDbTest extends LocalDynamoTest {

    private UpdateNviCandidateStatusHandler handler;
    private NviService nviService;
    private NviCandidateRepository nviCandidateRepository;
    private DynamoDbClient localDynamo;
    private Context context;
    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
        nviService = new NviService(localDynamo);
        handler = new UpdateNviCandidateStatusHandler(nviService);
    }

    @ParameterizedTest
    @EnumSource(NviApprovalStatus.class)
    void shouldUpdateApprovalStatus(NviApprovalStatus status) throws IOException {
        var institutionId = randomUri();
        var dbCandidate = createCandidate(institutionId);
        var candidate = nviCandidateRepository.create(dbCandidate,
                                                      List.of(DbApprovalStatus.builder()
                                                                  .institutionId(institutionId)
                                                                  .status(DbStatus.PENDING)
                                                                  .build()));

        var req = new NviStatusRequest(candidate.identifier(), institutionId, status, randomString());
        var request = createRequest(req, institutionId);
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(bodyAsInstance.approvalStatuses().get(0).status().getValue(), is(equalTo(status.getValue())));
    }

    @Test
    void shouldRequireMessageWhenRejecting() throws IOException {
        var institutionId = randomUri();
        var dbCandidate = createCandidate(institutionId);
        var candidate = nviCandidateRepository.create(dbCandidate,
                                                      List.of(DbApprovalStatus.builder()
                                                                  .institutionId(institutionId)
                                                                  .status(DbStatus.PENDING)
                                                                  .build()));

        var req = new NviStatusRequest(candidate.identifier(), institutionId, NviApprovalStatus.REJECTED, "Denied!");
        var request = createRequest(req, institutionId);
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(bodyAsInstance.approvalStatuses().get(0).status(), is(equalTo(DbStatus.REJECTED)));
        assertThat(bodyAsInstance.approvalStatuses().get(0).reason(), is(equalTo("Denied!")));
    }

    @Test
    void shouldFailWhenRequireMessageIsMissing() throws IOException {
        var institutionId = randomUri();
        var dbCandidate = createCandidate(institutionId);
        var candidate = nviCandidateRepository.create(dbCandidate,
                                                      List.of(DbApprovalStatus.builder()
                                                                  .institutionId(institutionId)
                                                                  .status(DbStatus.PENDING)
                                                                  .build()));

        var req = new NviStatusRequest(candidate.identifier(), institutionId, NviApprovalStatus.REJECTED, null);
        var request = createRequest(req, institutionId);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @Test
    void shouldRemoveReasonWhenChangingAwayFromRejectedStatus() throws IOException {
        var institutionId = randomUri();
        var dbCandidate = createCandidate(institutionId);
        var candidate = nviCandidateRepository.create(dbCandidate,
                                                      List.of(DbApprovalStatus.builder()
                                                                  .institutionId(institutionId)
                                                                  .status(DbStatus.REJECTED)
                                                                  .reason("I made a mistake")
                                                                  .build()));

        var req = new NviStatusRequest(candidate.identifier(),
                                       institutionId,
                                       NviApprovalStatus.APPROVED,
                                       "This will be ignored");
        var request = createRequest(req, institutionId);
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var bodyAsInstance = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(bodyAsInstance.approvalStatuses().get(0).status(), is(equalTo(DbStatus.APPROVED)));
        assertThat(bodyAsInstance.approvalStatuses().get(0).reason(), is(nullValue()));
    }

    private static DbCandidate createCandidate(URI institutionId) {
        return DbCandidate.builder()
                   .publicationId(randomUri())
                   .level(DbLevel.LEVEL_ONE)
                   .internationalCollaboration(false)
                   .publicationBucketUri(randomUri())
                   .publicationDate(
                       new DbPublicationDate("2023", "01", "01"))
                   .creators(List.of(new DbCreator(randomUri(), List.of(institutionId))))
                   .creatorCount(1)
                   .instanceType("AcademicArticle")
                   .applicable(true)
                   .points(List.of())
                   .build();
    }

    private InputStream createRequest(NviStatusRequest body, URI customerId) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviStatusRequest>(JsonUtils.dtoObjectMapper)
                   .withPathParameters(Map.of("candidateIdentifier", body.candidateId().toString()))
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(customerId)
                   //TODO CHANGE TO CORRECT ACCESS RIGHT
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }
}
