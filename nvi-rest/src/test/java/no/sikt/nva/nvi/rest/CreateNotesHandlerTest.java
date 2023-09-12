package no.sikt.nva.nvi.rest;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbCandidate;
import no.sikt.nva.nvi.common.db.model.DbNote;
import no.sikt.nva.nvi.common.service.NviService;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

public class CreateNotesHandlerTest {

    private Context context;
    private ByteArrayOutputStream output;
    private CreateNotesHandler handler;
    private NviService service;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        service = mock(NviService.class);
        handler = new CreateNotesHandler(service);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(createRequestWithoutAccessRights(randomNote()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldAddNoteToCandidate() throws IOException {
        var identifier = UUID.randomUUID();
        var theNote = "The note";
        var candidate = new Candidate(
            identifier,
            DbCandidate.builder()
                .points(List.of())
                .build(),
            List.of(DbApprovalStatus.builder().build()),
            List.of(DbNote.builder().text(theNote).build()));
        when(service.createNote(any(), any())).thenReturn(candidate);

        handler.handleRequest(createRequest(identifier, new NviNotesRequest(theNote)), output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, CandidateResponse.class);
        var body = gatewayResponse.getBodyObject(CandidateResponse.class);
        assertThat(body.notes().get(0).text(), is(equalTo(theNote)));
    }

    private InputStream createRequest(UUID identifier, NviNotesRequest body) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviNotesRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   .withPathParameters(Map.of("candidateIdentifier", identifier.toString()))
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATE.name())
                   .withUserName(randomString())
                   .build();
    }

    private NviNotesRequest randomNote() {
        return new NviNotesRequest(randomString());
    }

    private InputStream createRequestWithoutAccessRights(NviNotesRequest request) throws JsonProcessingException {
        return new HandlerRequestBuilder<NviNotesRequest>(JsonUtils.dtoObjectMapper).withBody(request).build();
    }
}
