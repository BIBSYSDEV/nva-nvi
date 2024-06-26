package no.sikt.nva.nvi.rest.create;

import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.periodRepositoryReturningClosedPeriod;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Year;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.service.dto.ApprovalDto;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
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
    private ViewingScopeValidator viewingScopeValidatorReturningFalse;

    @BeforeEach
    void setUp() {
        output = new ByteArrayOutputStream();
        context = mock(Context.class);
        localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = TestUtils.periodRepositoryReturningOpenedPeriod(YEAR);
        viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(true);
        handler = new CreateNoteHandler(candidateRepository, periodRepository, viewingScopeValidatorReturningFalse);
    }

    @Test
    void shouldReturnUnauthorizedWhenMissingAccessRights() throws IOException {
        handler.handleRequest(requestWithoutAccessRights(UUID.randomUUID(), randomNote()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    }

    @Test
    void shouldReturnUnauthorizedWhenCandidateIsNotInUsersViewingScope() throws IOException {
        var candidate = Candidate.upsert(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                         periodRepository).orElseThrow();
        var request = createRequest(candidate.getIdentifier(), new NviNoteRequest("The note"), randomString());
        viewingScopeValidatorReturningFalse = new FakeViewingScopeValidator(false);
        handler = new CreateNoteHandler(candidateRepository, periodRepository, viewingScopeValidatorReturningFalse);
        handler.handleRequest(request, output, context);
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
        var candidate = Candidate.upsert(createUpsertCandidateRequest(randomUri()), candidateRepository,
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
        var candidate = Candidate.upsert(createUpsertCandidateRequest(randomUri()), candidateRepository,
                                         periodRepository).orElseThrow();
        var request = createRequest(candidate.getIdentifier(), new NviNoteRequest(randomString()), randomString());
        var handler = new CreateNoteHandler(candidateRepository, periodRepositoryReturningClosedPeriod(YEAR),
                                            viewingScopeValidatorReturningFalse);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_CONFLICT)));
    }

    @Test
    void shouldReturn405NotAllowedWhenAddingNoteToNonCandidate() throws IOException {
        var candidateBO = Candidate.upsert(createUpsertCandidateRequest(randomUri()),
                                           candidateRepository, periodRepository).orElseThrow();
        var nonCandidate = Candidate.updateNonCandidate(
            createUpsertNonCandidateRequest(candidateBO.getPublicationId()),
            candidateRepository).orElseThrow();
        var request = createRequest(nonCandidate.getIdentifier(), randomNote().toJsonString(), randomString());
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(response.getStatusCode(), is(Matchers.equalTo(HttpURLConnection.HTTP_BAD_METHOD)));
    }

    @Test
    void shouldSetUserAsAssigneeWhenUsersInstitutionApprovalIsUnassigned() throws IOException {
        var institutionId = randomUri();
        var candidate = Candidate.upsert(createUpsertCandidateRequest(institutionId), candidateRepository,
                                         periodRepository).orElseThrow();
        assertNull(candidate.getApprovals().get(institutionId).getAssignee());
        var userName = randomString();
        var request = createRequest(candidate.getIdentifier(), randomNote(), userName, institutionId);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class).getBodyObject(CandidateDto.class);
        var actualAssignee = getActualAssignee(response, institutionId);
        assertEquals(userName, actualAssignee);
    }

    @Test
    void shouldNotSetUserAsAssigneeWhenUsersInstitutionApprovalHasAssignee() throws IOException {
        var institutionId = randomUri();
        var candidate = Candidate.upsert(createUpsertCandidateRequest(institutionId), candidateRepository,
                                         periodRepository).orElseThrow();
        var existingApprovalAssignee = randomString();
        candidate.updateApproval(new UpdateAssigneeRequest(institutionId, existingApprovalAssignee));
        assertNotNull(candidate.getApprovals().get(institutionId).getAssignee());
        var request = createRequest(candidate.getIdentifier(), randomNote(), randomString(), institutionId);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, CandidateDto.class).getBodyObject(CandidateDto.class);
        var actualAssignee = getActualAssignee(response, institutionId);
        assertEquals(existingApprovalAssignee, actualAssignee);
    }

    private static String getActualAssignee(CandidateDto response, URI institutionId) {
        return response.approvals().stream()
                   .filter(approval -> isInstitutionId(institutionId, approval))
                   .findFirst()
                   .orElseThrow()
                   .assignee();
    }

    private static boolean isInstitutionId(URI institutionId, ApprovalDto approval) {
        return approval.institutionId().equals(institutionId);
    }

    private InputStream createRequest(UUID identifier, NviNoteRequest body, String userName)
        throws JsonProcessingException {
        return createRequest(identifier, body, userName, randomUri());
    }

    private InputStream createRequest(UUID identifier, NviNoteRequest body, String userName, URI institutionId)
        throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(institutionId)
                   .withPathParameters(Map.of("candidateIdentifier", identifier.toString()))
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATES)
                   .withUserName(userName)
                   .build();
    }

    private InputStream createRequest(UUID identifier, String body, String userName) throws JsonProcessingException {
        var customerId = randomUri();
        return new HandlerRequestBuilder<String>(JsonUtils.dtoObjectMapper)
                   .withBody(body)
                   .withCurrentCustomer(customerId)
                   .withTopLevelCristinOrgId(randomUri())
                   .withPathParameters(Map.of("candidateIdentifier", identifier.toString()))
                   .withAccessRights(customerId, AccessRight.MANAGE_NVI_CANDIDATES)
                   .withUserName(userName)
                   .build();
    }

    private NviNoteRequest randomNote() {
        return new NviNoteRequest(randomString());
    }

    private InputStream requestWithoutAccessRights(UUID candidateIdentifier, NviNoteRequest request)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<NviNoteRequest>(JsonUtils.dtoObjectMapper)
                   .withPathParameters(Map.of("candidateIdentifier", candidateIdentifier.toString()))
                   .withBody(request).build();
    }
}
