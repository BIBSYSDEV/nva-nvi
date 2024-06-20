package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.PaginatedResultConverter.toPaginatedResult;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.client.UserRetriever;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchNviCandidatesHandler
    extends ApiGatewayHandler<Void, PaginatedSearchResult<NviCandidateIndexDocument>> {

    public static final String ENV_IDENTITY_SERVICE_URL = "IDENTITY_SERVICE_URL";
    public static final String CRISTIN_PATH = "cristin";
    public static final String ORGANIZATION_PATH = "organization";
    private final Logger logger = LoggerFactory.getLogger(SearchNviCandidatesHandler.class);
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final ViewingScopeValidator viewingScopeValidator;
    private final String apiHost;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        this(defaultOpenSearchClient(), defaultViewingScopeValidator(new Environment()), new Environment());
    }

    public SearchNviCandidatesHandler(SearchClient<NviCandidateIndexDocument> openSearchClient,
                                      ViewingScopeValidator viewingScopeValidator,
                                      Environment environment) {
        super(Void.class);
        this.openSearchClient = openSearchClient;
        this.viewingScopeValidator = viewingScopeValidator;
        this.apiHost = environment.readEnv("API_HOST");
    }

    @Override
    protected PaginatedSearchResult<NviCandidateIndexDocument> processInput(Void input, RequestInfo requestInfo,
                                                                            Context context)
        throws UnauthorizedException, BadRequestException {
        validateAccessRights(requestInfo);
        var candidateSearchParameters = CandidateSearchParameters.fromRequestInfo(requestInfo);
        logAggregationType(candidateSearchParameters);
        return attempt(() -> openSearchClient.search(candidateSearchParameters))
                   .map(searchResponse -> toPaginatedResult(searchResponse, candidateSearchParameters))
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PaginatedSearchResult<NviCandidateIndexDocument> output) {
        return HttpURLConnection.HTTP_OK;
    }

    @JacocoGenerated
    private static ViewingScopeValidator defaultViewingScopeValidator(Environment environment) {
        return new ViewingScopeValidator(
            new UserRetriever(defaultAuthorizedUriRetriever(environment),
                              environment.readEnv(ENV_IDENTITY_SERVICE_URL)),
            new OrganizationRetriever(new UriRetriever()));
    }

    private static boolean userIsNviAdmin(RequestInfo requestInfo) {
        return requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI);
    }

    private static List<String> extractAffiliations(RequestInfo requestInfo) {
        return Optional.ofNullable(extractQueryParamAffiliations(requestInfo))
                   .orElse(List.of(getTopLevelOrg(requestInfo).getLastPathElement()));
    }

    private static UriWrapper getTopLevelOrg(RequestInfo requestInfo) {
        return UriWrapper.fromUri(requestInfo.getTopLevelOrgCristinId().orElseThrow());
    }

    private static List<String> extractQueryParamAffiliations(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_AFFILIATIONS)
                   .map(SearchNviCandidatesHandler::toListOfIdentifiers)
                   .orElse(null);
    }

    private static List<String> toListOfIdentifiers(String identifierListAsString) {
        return Arrays.stream(identifierListAsString.split(COMMA)).collect(Collectors.toList());
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultAuthorizedUriRetriever(Environment environment) {
        return new AuthorizedBackendUriRetriever(environment.readEnv("BACKEND_CLIENT_AUTH_URL"),
                                                 environment.readEnv("BACKEND_CLIENT_SECRET_NAME"));
    }

    private static boolean userIsNotNviAdmin(RequestInfo requestInfo) {
        return !userIsNviAdmin(requestInfo);
    }

    private URI toCristinOrgUri(String identifier) {
        return UriWrapper.fromHost(apiHost)
                   .addChild(CRISTIN_PATH, ORGANIZATION_PATH)
                   .addChild(identifier)
                   .getUri();
    }

    private void logAggregationType(CandidateSearchParameters candidateSearchParameters) {
        logger.info("Aggregation type {} requested for topLevelCristinOrg {}",
                    candidateSearchParameters.aggregationType(),
                    candidateSearchParameters.topLevelCristinOrg());
    }

    private void validateAccessRights(RequestInfo requestInfo) throws UnauthorizedException {
        if (userIsNotNviAdmin(requestInfo)) {
            var requestedOrganizations = toOrganizationUris(extractAffiliations(requestInfo));
            if (userIsNotAllowedToView(requestInfo, requestedOrganizations)) {
                throw new UnauthorizedException("User is not allowed to view requested organizations");
            }
        }
    }

    private boolean userIsNotAllowedToView(RequestInfo requestInfo, List<URI> requestedOrganizations)
        throws UnauthorizedException {
        return !viewingScopeValidator.userIsAllowedToAccess(requestInfo, requestedOrganizations);
    }

    private List<URI> toOrganizationUris(List<String> affiliationIdentifiers) {
        return affiliationIdentifiers.stream().map(this::toCristinOrgUri).toList();
    }
}

