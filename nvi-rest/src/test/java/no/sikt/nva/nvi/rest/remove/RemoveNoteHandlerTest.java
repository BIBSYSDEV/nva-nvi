package no.sikt.nva.nvi.rest.remove;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static no.sikt.nva.nvi.rest.remove.RemoveNoteHandler.PARAM_NOTE_IDENTIFIER;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningOpenedPeriod;
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
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.db.model.Username;
import no.sikt.nva.nvi.common.model.CreateNoteRequest;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.rest.create.NviNoteRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class RemoveNoteHandlerTest extends LocalDynamoTest {

    public static final int YEAR = Calendar.getInstance().getWeeksInWeekYear();
    private Context context;
    private ByteArrayOutputStream output;
    private RemoveNoteHandler handler;
    private PeriodRepository periodRepository;
    private CandidateRepository candidateRepository;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = periodRepositoryReturningOpenedPeriod(YEAR);
        handler = new RemoveNoteHandler(candidateRepository, periodRepository);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(
            createRequestWithoutAccessRights(randomUri(), randomString(), randomString(), randomString()).build(),
            output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenNotTheUserThatCreatedTheNote() throws IOException {
        var candidate = createCandidate();
        var user = randomUsername();
        var candidateWithNote = createNote(candidate, user);
        var noteId = candidateWithNote.toDto().notes().get(0).identifier();
        var request = createRequest(candidate.getIdentifier(), noteId, randomString()).build();
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldBeAbleToRemoveNoteWhenTheUserThatCreatedIt() throws IOException {
        var candidate = createCandidate();
        var user = randomUsername();
        var candidateWithNote = createNote(candidate, user);
        var noteId = candidateWithNote.toDto().notes().get(0).identifier();
        var request = createRequest(candidate.getIdentifier(), noteId, user.value()).build();
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateDto.class);
        var body = gatewayResponse.getBodyObject(CandidateDto.class);
        assertThat(body.notes(), hasSize(0));
    }

    @Test
    void shouldReturnNotFoundWhenNoteDoesntExist() throws IOException {
        var request = createRequest(UUID.randomUUID(), UUID.randomUUID(), randomString()).build();
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_NOT_FOUND)));
    }

    @Test
    void shouldReturnConflictWhenRemovingNoteAndReportingPeriodIsClosed() throws IOException {
        var candidate = createCandidate();
        var user = randomString();
        candidate.createNote(new CreateNoteRequest(randomString(), user));
        var noteId = candidate.toDto().notes().get(0).identifier();
        var request = createRequest(candidate.getIdentifier(), noteId, user).build();
        handler = new RemoveNoteHandler(candidateRepository, periodRepositoryReturningClosedPeriod(YEAR));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    private static HandlerRequestBuilder<NviNoteRequest> createRequestWithoutAccessRights(URI customerId,
                                                                                          String candidateId,
                                                                                          String noteId,
                                                                                          String userName) {
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dynamoObjectMapper).withPathParameters(
                Map.of(CANDIDATE_IDENTIFIER, candidateId, PARAM_NOTE_IDENTIFIER, noteId))
                   .withCurrentCustomer(customerId)
                   .withUserName(userName);
    }

    private Candidate createNote(Candidate candidate, Username user) {
        return candidate.createNote(new CreateNoteRequest(randomString(), user.value()));
    }

    private Candidate createCandidate() {
        return Candidate.fromRequest(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                     periodRepository).orElseThrow();
    }

    private HandlerRequestBuilder<NviNoteRequest> createRequest(UUID candidateIdentifier, UUID noteIdentifier,
                                                                String userName) {
        var customerId = randomUri();
        return createRequestWithoutAccessRights(customerId, candidateIdentifier.toString(), noteIdentifier.toString(),
                                                userName).withAccessRights(customerId,
                                                                           AccessRight.MANAGE_NVI_CANDIDATE.name());
    }
}