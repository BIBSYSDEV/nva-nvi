package no.sikt.nva.nvi.rest.fetch;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.UUID;
import no.sikt.nva.nvi.common.ViewingScopeHandler;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.exception.CandidateNotFoundException;
import no.sikt.nva.nvi.common.service.model.Candidate;
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
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class FetchNviCandidateHandler extends ApiGatewayHandler<Void, CandidateDto> implements ViewingScopeHandler {

    public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final ViewingScopeValidator viewingScopeValidator;

    @JacocoGenerated
    public FetchNviCandidateHandler() {
        this(new CandidateRepository(defaultDynamoClient()), new PeriodRepository(defaultDynamoClient()),
             defaultViewingScopeValidator());
    }

    public FetchNviCandidateHandler(CandidateRepository candidateRepository, PeriodRepository periodRepository,
                                    ViewingScopeValidator viewingScopeValidator) {
        super(Void.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.viewingScopeValidator = viewingScopeValidator;
    }

    @Override
    protected void validateRequest(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        validateAccessRight(requestInfo);
    }

    @Override
    protected CandidateDto processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        return attempt(() -> requestInfo.getPathParameter(CANDIDATE_IDENTIFIER)).map(UUID::fromString)
                   .map(identifier -> Candidate.fetch(() -> identifier, candidateRepository, periodRepository))
                   .map(this::checkIfApplicable)
                   .map(candidate -> validateViewingScope(viewingScopeValidator, RequestUtil.getUsername(requestInfo),
                                                          candidate))
                   .map(Candidate::toDto)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CandidateDto output) {
        return HTTP_OK;
    }

    private static void validateAccessRight(RequestInfo requestInfo) throws UnauthorizedException {
        if (isNviCurator(requestInfo) || isNviAdmin(requestInfo)) {
            return;
        }
        throw new UnauthorizedException();
    }

    private static boolean isNviAdmin(RequestInfo requestInfo) {
        return requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI);
    }

    private static boolean isNviCurator(RequestInfo requestInfo) {
        return requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI_CANDIDATES);
    }

    @JacocoGenerated
    private static ViewingScopeValidatorImpl defaultViewingScopeValidator() {
        return new ViewingScopeValidatorImpl(IdentityServiceClient.prepare(),
                                             new OrganizationRetriever(new UriRetriever()));
    }

    private Candidate checkIfApplicable(Candidate candidate) {
        if (candidate.isApplicable()) {
            return candidate;
        }
        throw new CandidateNotFoundException();
    }
}
