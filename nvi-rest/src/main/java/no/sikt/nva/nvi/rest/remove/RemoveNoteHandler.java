package no.sikt.nva.nvi.rest.remove;

import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.util.UUID;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.DynamoRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.requests.DeleteNoteRequest;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidatorImpl;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.clients.IdentityServiceClient;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;

public class RemoveNoteHandler extends ApiGatewayHandler<Void, CandidateDto> implements ViewingScopeHandler {

    public static final String PARAM_NOTE_IDENTIFIER = "noteIdentifier";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final ViewingScopeValidator viewingScopeValidator;

    @JacocoGenerated
    public RemoveNoteHandler() {
        this(new CandidateRepository(DynamoRepository.defaultDynamoClient()),
             new PeriodRepository(DynamoRepository.defaultDynamoClient()), defaultViewingScopeValidator());
    }

    public RemoveNoteHandler(CandidateRepository candidateRepository, PeriodRepository periodRepository,
                             ViewingScopeValidator viewingScopeValidator) {
        super(Void.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.viewingScopeValidator = viewingScopeValidator;
    }

    @Override
    protected void validateRequest(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
    }

    @Override
    protected CandidateDto processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        var username = RequestUtil.getUsername(requestInfo);
        var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
        var noteIdentifier = UUID.fromString(requestInfo.getPathParameter(PARAM_NOTE_IDENTIFIER));
        return attempt(() -> Candidate.fetch(() -> candidateIdentifier, candidateRepository, periodRepository))
                   .map(candidate -> validateViewingScope(viewingScopeValidator, username, candidate))
                   .map(candidate -> candidate.deleteNote(new DeleteNoteRequest(noteIdentifier, username.value())))
                   .map(Candidate::toDto)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CandidateDto output) {
        return HttpURLConnection.HTTP_OK;
    }

    @JacocoGenerated
    private static ViewingScopeValidatorImpl defaultViewingScopeValidator() {
        return new ViewingScopeValidatorImpl(IdentityServiceClient.prepare(),
                                             new OrganizationRetriever(new UriRetriever()));
    }
}
