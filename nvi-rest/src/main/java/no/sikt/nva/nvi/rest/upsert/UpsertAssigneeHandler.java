package no.sikt.nva.nvi.rest.upsert;

import static java.util.Objects.nonNull;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.DynamoRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.model.UpdateAssigneeRequest;
import no.sikt.nva.nvi.common.service.dto.CandidateDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.ExceptionMapper;
import no.sikt.nva.nvi.common.utils.RequestUtil;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.rest.ViewingScopeHandler;
import no.sikt.nva.nvi.rest.model.UpsertAssigneeRequest;
import no.sikt.nva.nvi.rest.model.User;
import no.sikt.nva.nvi.rest.model.User.Role;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.RawContentRetriever;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public class UpsertAssigneeHandler extends ApiGatewayHandler<UpsertAssigneeRequest, CandidateDto>
    implements ViewingScopeHandler {

    public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
    public static final Environment ENVIRONMENT = new Environment();
    public static final String BACKEND_CLIENT_AUTH_URL = ENVIRONMENT.readEnv("BACKEND_CLIENT_AUTH_URL");
    public static final String BACKEND_CLIENT_SECRET_NAME = ENVIRONMENT.readEnv("BACKEND_CLIENT_SECRET_NAME");
    public static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
    public static final String USERS_ROLES_PATH_PARAM = "users-roles";
    public static final String USERS_PATH_PARAM = "users";
    public static final String CONTENT_TYPE = "application/json";
    private final RawContentRetriever uriRetriever;
    private final CandidateRepository candidateRepository;
    private final PeriodRepository periodRepository;
    private final ViewingScopeValidator viewingScopeValidator;

    @JacocoGenerated
    public UpsertAssigneeHandler() {
        this(new CandidateRepository(DynamoRepository.defaultDynamoClient()),
             new PeriodRepository(DynamoRepository.defaultDynamoClient()),
             new AuthorizedBackendUriRetriever(BACKEND_CLIENT_AUTH_URL, BACKEND_CLIENT_SECRET_NAME),
             ViewingScopeHandler.defaultViewingScopeValidator());
    }

    public UpsertAssigneeHandler(CandidateRepository candidateRepository,
                                 PeriodRepository periodRepository, RawContentRetriever uriRetriever,
                                 ViewingScopeValidator viewingScopeValidator) {
        super(UpsertAssigneeRequest.class);
        this.candidateRepository = candidateRepository;
        this.periodRepository = periodRepository;
        this.uriRetriever = uriRetriever;
        this.viewingScopeValidator = viewingScopeValidator;
    }

    @Override
    protected void validateRequest(UpsertAssigneeRequest input, RequestInfo requestInfo,
                                   Context context) throws ApiGatewayException {
        validateCustomerAndAccessRight(input, requestInfo);
    }

    @Override
    protected CandidateDto processInput(UpsertAssigneeRequest input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        var candidateIdentifier = UUID.fromString(requestInfo.getPathParameter(CANDIDATE_IDENTIFIER));
        var institutionId = input.institutionId();
        var assignee = input.assignee();
        return attempt(() -> Candidate.fetch(() -> candidateIdentifier, candidateRepository, periodRepository))
                   .map(candidate -> validateViewingScope(viewingScopeValidator, RequestUtil.getUsername(requestInfo),
                                                          candidate))
                   .map(candidate -> candidate.updateApproval(new UpdateAssigneeRequest(institutionId, assignee)))
                   .map(Candidate::toDto)
                   .orElseThrow(ExceptionMapper::map);
    }

    @Override
    protected Integer getSuccessStatusCode(UpsertAssigneeRequest input, CandidateDto output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static void hasSameCustomer(UpsertAssigneeRequest input, RequestInfo requestInfo)
        throws UnauthorizedException {
        if (!input.institutionId().equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new UnauthorizedException();
        }
    }

    private static User toUser(String body) {
        return attempt(() -> JsonUtils.dtoObjectMapper.readValue(body, User.class)).orElseThrow();
    }

    private void validateCustomerAndAccessRight(UpsertAssigneeRequest input, RequestInfo requestInfo)
        throws UnauthorizedException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATES);
        hasSameCustomer(input, requestInfo);
        if (nonNull(input.assignee())) {
            assigneeHasAccessRight(input.assignee());
        }
    }

    private void assigneeHasAccessRight(String assignee) throws UnauthorizedException {
        var user = fetchUser(assignee);
        if (!hasManageNviCandidateAccessRight(user)) {
            throw new UnauthorizedException();
        }
    }

    private boolean hasManageNviCandidateAccessRight(User user) {
        return user.roles()
                   .stream()
                   .map(Role::accessRights)
                   .flatMap(List::stream)
                   .anyMatch(accessRights -> accessRights.name().equals(AccessRight.MANAGE_NVI_CANDIDATES.name()));
    }

    private User fetchUser(String assignee) {
        return attempt(() -> constructFetchUserUri(assignee))
                   .map(uri -> uriRetriever.getRawContent(uri, CONTENT_TYPE))
                   .map(Optional::orElseThrow)
                   .map(UpsertAssigneeHandler::toUser)
                   .orElseThrow();
    }

    private URI constructFetchUserUri(String assignee) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(USERS_ROLES_PATH_PARAM)
                   .addChild(USERS_PATH_PARAM)
                   .addChild(assignee)
                   .getUri();
    }
}
