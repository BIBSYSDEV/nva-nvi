package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateByPublicationIdHandler.CANDIDATE_PUBLICATION_ID;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.randomApproval;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
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
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchNviCandidateByPublicationIdHandlerTest extends LocalDynamoTest {

    private static final Context CONTEXT = mock(Context.class);
    public static final URI CUSTOMER_ID = randomUri();
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
        handler = new FetchNviCandidateByPublicationIdHandler(candidateRepository, periodRepository);
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
        var nonApplicableCandidate = setUpNonApplicableCandidate(institutionId);
        var request = requestWithAccessRight(nonApplicableCandidate.getPublicationDetails().publicationId());
        handler.handleRequest(request, output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
        var institutionId = randomUri();
        var candidate =
            Candidate.upsert(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository)
                .orElseThrow();
        var request = requestWithAccessRight(candidate.getPublicationDetails().publicationId());

        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var expectedResponse = candidate.toDto();
        var actualResponse = response.getBodyObject(CandidateDto.class);

        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void shouldReturnCandidateWithReportStatus() throws IOException {
        var candidate = candidateRepository.create(randomCandidate().copy().reportStatus(ReportStatus.REPORTED).build(),
                                                   List.of(randomApproval()));
        var request = requestWithAccessRight(candidate.candidate().publicationId());

        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var actualCandidate = response.getBodyObject(CandidateDto.class);

        assertEquals(ReportStatus.REPORTED.getValue(), actualCandidate.status());
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

    private Candidate setUpNonApplicableCandidate(URI institutionId) {
        var candidate =
            Candidate.upsert(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository)
                .orElseThrow();
        return Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidate.getPublicationDetails().publicationId()),
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
}