package no.sikt.nva.nvi.rest.remove;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.PARAM_CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.rest.remove.RemoveNoteHandler.PARAM_NOTE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.sikt.nva.nvi.test.TestUtils.randomUsername;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.create.NviNoteRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class RemoveNoteHandlerTest extends LocalDynamoTest {

    private Context context;
    private ByteArrayOutputStream output;
    private RemoveNoteHandler handler;
    private NviService service;
    private NviCandidateRepository nviCandidateRepository;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        localDynamo = initializeTestDatabase();
        nviCandidateRepository = new NviCandidateRepository(localDynamo);
        service = new NviService(localDynamo);
        handler = new RemoveNoteHandler(service);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(randomUri(), randomString(), randomString(),
                                                               randomString()).build(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenNotTheUserThatCreatedTheNote() throws IOException {
        var dbCandidate = randomCandidate();
        var user = randomUsername();
        var candidate = nviCandidateRepository.create(dbCandidate, List.of());
        var candidateWithNote = service.createNote(candidate.identifier(), DbNote.builder()
                                                                               .user(user)
                                                                               .text("Not My Note")
                                                                               .build());
        var req = createRequest(candidate.identifier(), candidateWithNote.notes().get(0).noteId(),
                                randomString()).build();
        handler.handleRequest(req, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldBeAbleToRemoveNoteWhenTheUserThatCreatedIt() throws IOException {
        var dbCandidate = randomCandidate();
        var user = randomUsername();
        var candidate = nviCandidateRepository.create(dbCandidate, List.of());
        var candidateWithNote = service.createNote(candidate.identifier(),
                                                   DbNote.builder()
                                                       .user(user)
                                                       .text("Not My Note")
                                                       .build());
        var req = createRequest(candidate.identifier(),
                                candidateWithNote.notes().get(0).noteId(),
                                user.value()).build();
        handler.handleRequest(req, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var body = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(body.notes(), hasSize(0));
    }

    @Test
    void shouldReturnNotFoundWhenNoteDoesntExist() throws IOException {
        var dbCandidate = randomCandidate();
        var user = randomUsername();
        var candidate = nviCandidateRepository.create(dbCandidate, List.of());
        var req = createRequest(candidate.identifier(),
                                UUID.randomUUID(),
                                randomString())
                      .build();
        handler.handleRequest(req, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    private static HandlerRequestBuilder<NviNoteRequest> createRequestWithoutAccessRights(URI customerId,
                                                                                          String candidateId,
                                                                                          String noteId,
                                                                                          String userName) {
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dynamoObjectMapper)
                   .withPathParameters(Map.of(PARAM_CANDIDATE_IDENTIFIER, candidateId,
                                              PARAM_NOTE_IDENTIFIER, noteId))
                   .withCurrentCustomer(customerId)
                   .withUserName(userName);
    }

    private HandlerRequestBuilder<NviNoteRequest> createRequest(UUID candidateIdentifier, UUID noteIdentifier,
                                                                String userName) {
        var customerId = randomUri();
        return createRequestWithoutAccessRights(customerId, candidateIdentifier.toString(), noteIdentifier.toString(),
                                                userName)
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name());
    }
}