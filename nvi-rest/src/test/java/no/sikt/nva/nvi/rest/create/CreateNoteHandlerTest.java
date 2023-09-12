package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.test.TestUtils.randomApprovalStatus;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class CreateNoteHandlerTest extends LocalDynamoTest {

    private Context context;
    private ByteArrayOutputStream output;
    private CreateNoteHandler handler;
    private NviService service;
    private NviCandidateRepository nviCandidateRepository;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
        service = new NviService(localDynamo);
        handler = new CreateNoteHandler(service);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(randomNote()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldAddNoteToCandidateWhenNoteIsValid() throws IOException {
        var theNote = "The note";
        DbCandidate dbCandidate = randomCandidate();
        Candidate candidate = nviCandidateRepository.create(dbCandidate, List.of(randomApprovalStatus()));
        handler.handleRequest(createRequest(candidate.identifier(), new NviNoteRequest(theNote)), output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var body = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(body.notes().get(0).text(), is(equalTo(theNote)));
    }

    private InputStream createRequest(UUID identifier, NviNoteRequest body) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   .withPathParameters(Map.of("candidateIdentifier", identifier.toString()))
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }

    private NviNoteRequest randomNote() {
        return new NviNoteRequest(randomString());
    }

    private InputStream createRequestWithoutAccessRights(NviNoteRequest request) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper).withBody(request).build();
    }
}
