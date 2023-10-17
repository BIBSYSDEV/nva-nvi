package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.Year;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.CandidateBO;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class CreateNoteHandlerTest extends LocalDynamoTest {

    public static final int YEAR = Year.now().getValue();
    private Context context;
    private ByteArrayOutputStream output;
    private CreateNoteHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = TestUtils.periodRepositoryReturningOpenedPeriod(YEAR);
        handler = new CreateNoteHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(randomNote()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnBadRequestIfCreateNoteRequestIsInvalid() throws IOException {
        var invalidRequestBody = JsonUtils.dtoObjectMapper.writeValueAsString(
            Map.of("someInvalidInputField", randomString()));
        var request = createRequest(UUID.randomUUID(), invalidRequestBody, randomString());

        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @Test
    void shouldAddNoteToCandidateWhenNoteIsValid() throws IOException {
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                                periodRepository).orElseThrow();
        var theNote = "The note";
        var userName = randomString();

        var request = createRequest(candidate.getIdentifier(), new NviNoteRequest(theNote), userName);
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var actualNote = gatewayResponse.getBodyObject(CandidateDto.class).notes().get(0);

        assertThat(actualNote.text(), is(equalTo(theNote)));
        assertThat(actualNote.user(), is(equalTo(userName)));
    }

    @Test
    void shouldReturnConflictWhenCreatingNoteAndReportingPeriodIsClosed() throws IOException {
        var candidate = CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                                periodRepository).orElseThrow();
        var request = createRequest(candidate.getIdentifier(), new NviNoteRequest(randomString()), randomString());
        var handler = new CreateNoteHandler(candidateRepository, periodRepositoryReturningClosedPeriod(YEAR));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldReturn405NotAllowedWhenAddingNoteToNonCandidate() throws IOException {
        var candidateBO = CandidateBO.fromRequest(createUpsertCandidateRequest(randomUri()),
                                                  candidateRepository, periodRepository).orElseThrow();
        var nonCandidate = CandidateBO.fromRequest(
            createUpsertNonCandidateRequest(candidateBO.getPublicationId()), candidateRepository).orElseThrow();
        var request = createRequest(nonCandidate.getIdentifier(), randomNote(), randomString());
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_METHOD)));
    }

    private InputStream createRequest(UUID identifier, NviNoteRequest body, String userName)
        throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper).withBody(body)
                   .withCurrentCustomer(customerId)
                   .withPathParameters(Map.of("candidateIdentifier", identifier.toString()))
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(userName)
                   .build();
    }

    private InputStream createRequest(UUID identifier, String body, String userName) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<String>(JsonUtils.dtoObjectMapper).withBody(body)
                   .withCurrentCustomer(customerId)
                   .withPathParameters(Map.of("candidateIdentifier", identifier.toString()))
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(userName)
                   .build();
    }

    private NviNoteRequest randomNote() {
        return new NviNoteRequest(randomString());
    }

    private InputStream createRequestWithoutAccessRights(NviNoteRequest request) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper).withBody(request).build();
    }
}
