package no.sikt.nva.nvi.rest.upsert;

import static no.sikt.nva.nvi.common.service.NviService.defaultNviService;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.CandidateResponse;
import no.sikt.nva.nvi.common.db.Candidate;
import no.sikt.nva.nvi.common.db.model.DbApprovalStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.rest.model.ApprovalDto;
import no.sikt.nva.nvi.rest.model.User;
import no.sikt.nva.nvi.rest.model.User.Role;
import no.sikt.nva.nvi.rest.utils.RequestUtil;
import no.sikt.nva.nvi.utils.ExceptionMapper;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.RawContentRetriever;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public class UpsertAssigneeHandler extends ApiGatewayHandler<ApprovalDto, CandidateResponse> {

    public static final String CANDIDATE_IDENTIFIER = "candidateIdentifier";
    public static final Environment ENVIRONMENT = new Environment();
    public static final String BACKEND_CLIENT_AUTH_URL = ENVIRONMENT.readEnv("BACKEND_CLIENT_AUTH_URL");
    public static final String BACKEND_CLIENT_SECRET_NAME = ENVIRONMENT.readEnv("BACKEND_CLIENT_SECRET_NAME");
    public static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
    public static final String USERS_ROLES_PATH_PARAM = "users-roles";
    public static final String USERS_PATH_PARAM = "users";
    public static final String CONTENT_TYPE = "application/json";
    public static final String CANDIDATE_NOT_FOUND_MESSAGE = "Candidate to update does not exist";
    private final RawContentRetriever uriRetriever;
    private final NviService nviService;

    @JacocoGenerated
    public UpsertAssigneeHandler() {
        super(ApprovalDto.class);
        this.nviService = defaultNviService();
        this.uriRetriever = new AuthorizedBackendUriRetriever(BACKEND_CLIENT_AUTH_URL, BACKEND_CLIENT_SECRET_NAME);
    }

    public UpsertAssigneeHandler(NviService nviService, RawContentRetriever uriRetriever) {
        super(ApprovalDto.class);
        this.nviService = nviService;
        this.uriRetriever = uriRetriever;
    }

    @Override
    protected CandidateResponse processInput(ApprovalDto input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        validateRequest(input, requestInfo);
        return attempt(() -> requestInfo.getPathParameter(CANDIDATE_IDENTIFIER))
                   .map(UUID::fromString)
                   .map(nviService::findById)
                   .map(optional -> optional.orElseThrow(() -> new NotFoundException(CANDIDATE_NOT_FOUND_MESSAGE)))
                   .map(candidate -> updateApprovalStatus(input, candidate))
                   .map(CandidateResponse::fromCandidate)
                   .orElseThrow(ExceptionMapper::map);
    }

    private Candidate updateApprovalStatus(ApprovalDto input, Candidate candidate) {
        return nviService.updateApprovalStatus(candidate.identifier(), injectAssignee(candidate, input));
    }

    private DbApprovalStatus injectAssignee(Candidate candidate, ApprovalDto input) {
        return getApprovalStatus(candidate, input).copy()
                   .assignee(new DbUsername(input.assignee()))
                   .build();

    }

    private static DbApprovalStatus getApprovalStatus(Candidate candidate, ApprovalDto input) {
        return candidate.approvalStatuses().stream()
                   .filter(approval -> approval.institutionId().equals(input.institutionId()))
                   .findFirst()
                   .orElseThrow();
    }

    private void validateRequest(ApprovalDto input, RequestInfo requestInfo) throws UnauthorizedException {
        RequestUtil.hasAccessRight(requestInfo, AccessRight.MANAGE_NVI_CANDIDATE);
        hasSameCustomer(input, requestInfo);
        assigneeHasAccessRight(input.assignee());
    }

    private void assigneeHasAccessRight(String assignee) throws UnauthorizedException {
        var user = fetchUser(assignee);
        if (!hasAccessRight(user)) {
            throw new UnauthorizedException();
        }
    }

    private static void hasSameCustomer(ApprovalDto input, RequestInfo requestInfo) throws UnauthorizedException {
        if (!input.institutionId().equals(requestInfo.getTopLevelOrgCristinId().orElseThrow())) {
            throw new UnauthorizedException();
        }
    }

    private boolean hasAccessRight(User user) {
        return user.roles().stream()
                   .map(Role::accessRights)
                   .flatMap(List::stream)
                   .anyMatch(accessRights -> accessRights.name().equals(AccessRight.MANAGE_NVI_CANDIDATE.name()));
    }

    private User fetchUser(String assignee) {
        return attempt(() -> constructFetchUserUri(assignee))
                   .map(uri -> uriRetriever.getRawContent(uri, CONTENT_TYPE))
                   .map(Optional::orElseThrow)
                   .map(UpsertAssigneeHandler::toUser)
                   .orElseThrow();
    }

    private static User toUser(String body) {
        return attempt(() -> JsonUtils.dtoObjectMapper.readValue(body, User.class)).orElseThrow();
    }

    private URI constructFetchUserUri(String assignee) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(USERS_ROLES_PATH_PARAM)
                   .addChild(USERS_PATH_PARAM)
                   .addChild(assignee)
                   .getUri();
    }

    @Override
    protected Integer getSuccessStatusCode(ApprovalDto input, CandidateResponse output) {
        return HttpURLConnection.HTTP_OK;
    }
}
