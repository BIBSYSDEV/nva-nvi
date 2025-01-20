package no.sikt.nva.nvi.rest.upsert;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.sikt.nva.nvi.common.db.DynamoRepository.defaultDynamoClient;
import static no.sikt.nva.nvi.rest.fetch.FetchNviCandidateHandler.CANDIDATE_IDENTIFIER;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.UUID;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import no.unit.nva.auth.uriretriever.UriRetriever;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ForbiddenException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class UpdateNviCandidateStatusHandler extends ApiGatewayHandler<NviStatusRequest, CandidateDto>
    implements ViewingScopeHandler {

    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final ViewingScopeValidator viewingScopeValidator;
    private final OrganizationRetriever organizationRetriever;

    @JacocoGenerated
    public UpdateNviCandidateStatusHandler() {
        this(new CandidateRepository(defaultDynamoClient()),
             new PeriodRepository(defaultDynamoClient()),
             ViewingScopeHandler.defaultViewingScopeValidator(),
             new OrganizationRetriever(new UriRetriever()));
    }

    public UpdateNviCandidateStatusHandler(CandidateRepository candidateRepository, PeriodRepository periodRepository,
                                           ViewingScopeValidator viewingScopeValidator,
                                           OrganizationRetriever organizationRetriever) {
        super(NviStatusRequest.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.viewingScopeValidator = viewingScopeValidator;
        this.organizationRetriever = organizationRetriever;
    }

    @Override
    protected void validateRequest(NviStatusRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        validateCustomerAndAccessRight(input, requestInfo);
    }

    @Override
    protected CandidateDto processInput(NviStatusRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
        var username = RequestUtil.getUsername(requestInfo);
        var updateRequest = input.toUpdateRequest(username.value());

        return attempt(() -> Candidate.fetch(() -> candidateIdentifier, candidateRepository, periodRepository))
                   .map(candidate -> validateViewingScope(viewingScopeValidator, username, candidate))
                   .map(candidate -> candidate.updateApprovalStatus(updateRequest, organizationRetriever))
                   .map(Candidate::toDto)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(NviStatusRequest input, CandidateDto output) {
        return HTTP_OK;
    }

    private static void validateCustomerAndAccessRight(NviStatusRequest input, RequestInfo requestInfo)
        throws UnauthorizedException, ForbiddenException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
        hasSameCustomer(requestInfo, input);
    }

    private static void hasSameCustomer(RequestInfo requestInfo, NviStatusRequest input) throws ForbiddenException {
        if (!input.institutionId().equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new ForbiddenException();
        }
    }
}
