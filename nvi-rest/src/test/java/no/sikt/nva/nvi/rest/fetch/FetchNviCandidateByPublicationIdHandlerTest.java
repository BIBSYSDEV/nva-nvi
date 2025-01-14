package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateByPublicationIdHandler.CANDIDATE_PUBLICATION_ID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.sikt.nva.nvi.test.TestUtils.setupReportedCandidate;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.service.dto.ApprovalStatusDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.UpsertCandidateRequest;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchNviCandidateByPublicationIdHandlerTest extends LocalDynamoTest {

    public static final URI CUSTOMER_ID = randomUri();
    private static final Context CONTEXT = mock(Context.class);
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private ByteArrayOutputStream output;
    private FetchNviCandidateByPublicationIdHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        handler = new FetchNviCandidateByPublicationIdHandler(candidateRepository, periodRepository, new FakeViewingScopeValidator(true));
    }

    @Test
    void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
        handler.handleRequest(requestWithAccessRight(randomUri()), output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnNotFoundWhenCandidateExistsButNotApplicable() throws IOException {
        var institutionId = randomUri();
        var nonApplicableCandidate = createNonApplicableCandidate(institutionId);
        var request = requestWithAccessRight(nonApplicableCandidate.getPublicationId());
        handler.handleRequest(request, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
        var institutionId = randomUri();
        var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
        var request = requestWithAccessRight(candidate.getPublicationId());

        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var expectedResponse = candidate.toDto();
        var actualResponse = response.getBodyObject(CandidateDto.class);

        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void shouldReturnCandidateWithReportStatus() throws IOException {
        var candidate = setupReportedCandidate(candidateRepository, randomYear());
        var request = requestWithAccessRight(candidate.candidate().publicationId());

        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var actualCandidate = response.getBodyObject(CandidateDto.class);

        assertEquals(ReportStatus.REPORTED.getValue(), actualCandidate.status());
    }

    @Test
    void shouldReturnCandidateDtoWithApprovalStatusNewWhenApprovalStatusIsPendingAndUnassigned() throws IOException {
        var candidate = upsert(createUpsertCandidateRequest(randomUri()).build());
        var request = requestWithAccessRight(candidate.getPublicationId());
        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var actualResponse = response.getBodyObject(CandidateDto.class);
        var actualStatus = actualResponse.approvals().get(0).status();

        assertEquals(ApprovalStatusDto.NEW, actualStatus);
    }

    private static InputStream requestWithAccessRight(URI publicationId)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType()))
                   .withCurrentCustomer(CUSTOMER_ID)
                   .withUserName(randomString())
                   .withPathParameters(Map.of(CANDIDATE_PUBLICATION_ID, publicationId.toString()))
                   .build();
    }

    private Candidate createNonApplicableCandidate(URI institutionId) {
        var candidate = upsert(createUpsertCandidateRequest(institutionId).build());
        return Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationId()),
            candidateRepository).orElseThrow();
    }

    private GatewayResponse<CandidateDto> getGatewayResponse()
        throws JsonProcessingException {
        return dtoObjectMapper.readValue(
            output.toString(),
            dtoObjectMapper.getTypeFactory()
                .constructParametricType(
                    GatewayResponse.class,
                    CandidateDto.class
                ));
    }

    private Candidate upsert(UpsertCandidateRequest request) {
        Candidate.upsert(request, candidateRepository, periodRepository);
        return Candidate.fetchByPublicationId(request::publicationId, candidateRepository, periodRepository);
    }
}