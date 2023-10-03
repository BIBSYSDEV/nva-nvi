package no.sikt.nva.nvi.rest.fetch;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class FetchNviCandidateHandlerTest extends LocalDynamoTest {

    private static final Context CONTEXT = mock(Context.class);
    private final DynamoDbClient localDynamo = initializeTestDatabase();
    private ByteArrayOutputStream output;
    private FetchNviCandidateHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    public void setUp() {
        output = new ByteArrayOutputStream();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(CURRENT_YEAR);
        handler = new FetchNviCandidateHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldReturnNotFoundWhenCandidateDoesNotExist() throws IOException {
        handler.handleRequest(createRequest(UUID.randomUUID()), output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnNotFoundWhenCandidateExistsButNotApplicable() throws IOException {
        var nonApplicableCandidate = setUpNonApplicableCandidate();
        handler.handleRequest(createRequest(nonApplicableCandidate.identifier()), output, CONTEXT);
        var gatewayResponse = getGatewayResponse();

        assertEquals(HttpStatus.SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }

    @Test
    void shouldReturnValidCandidateWhenCandidateExists() throws IOException {
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()), candidateRepository, periodRepository);
        var request = createRequest(candidate.identifier());

        handler.handleRequest(request, output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var expectedResponse = candidate.toDto();
        var actualResponse = response.getBodyObject(CandidateDto.class);

        assertEquals(expectedResponse, actualResponse);
    }

    private static InputStream createRequest(UUID publicationId) throws JsonProcessingException {
        return new HandlerRequestBuilder<InputStream>(dtoObjectMapper)
                   .withHeaders(Map.of(HttpHeader.ACCEPT.asString(), ContentType.APPLICATION_JSON.getMimeType()))
                   .withPathParameters(Map.of(CANDIDATE_IDENTIFIER, publicationId.toString()))
                   .build();
    }

    private CandidateBO setUpNonApplicableCandidate() {
        URI institutionId = randomUri();
        var candidate =
            CandidateBO.fromRequest(createUpsertCandidateRequest(institutionId), candidateRepository, periodRepository);
        return CandidateBO.fromRequest(createUpsertCandidateRequest(candidate.publicationId(),
                                                                    false, 0,
                                                                    InstanceType.NON_CANDIDATE,
                                                                    institutionId),
                                       candidateRepository, periodRepository);
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