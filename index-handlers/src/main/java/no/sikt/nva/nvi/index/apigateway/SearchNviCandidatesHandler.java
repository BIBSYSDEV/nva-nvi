package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.utils.RequestUtil.isNviAdmin;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.PaginatedResultConverter.toPaginatedResult;
import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.client.OrganizationRetriever;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidator;
import no.sikt.nva.nvi.common.validator.ViewingScopeValidatorImpl;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchNviCandidatesHandler
    extends ApiGatewayHandler<Void, PaginatedSearchResult<NviCandidateIndexDocument>> {

  private static final String CRISTIN_PATH = "cristin";
  private static final String ORGANIZATION_PATH = "organization";
  private final Logger logger = LoggerFactory.getLogger(SearchNviCandidatesHandler.class);
  private final SearchClient<NviCandidateIndexDocument> openSearchClient;
  private final ViewingScopeValidator viewingScopeValidator;
  private final IdentityServiceClient identityServiceClient;
  private final String apiHost;

  @JacocoGenerated
  public SearchNviCandidatesHandler() {
    this(
        defaultOpenSearchClient(),
        defaultViewingScopeValidator(),
        IdentityServiceClient.prepare(),
        new Environment());
  }

  public SearchNviCandidatesHandler(
      SearchClient<NviCandidateIndexDocument> openSearchClient,
      ViewingScopeValidator viewingScopeValidator,
      IdentityServiceClient identityServiceClient,
      Environment environment) {
    super(Void.class, environment);
    this.openSearchClient = openSearchClient;
    this.viewingScopeValidator = viewingScopeValidator;
    this.identityServiceClient = identityServiceClient;
    this.apiHost = environment.readEnv("API_HOST");
  }

  @Override
  protected void validateRequest(Void unused, RequestInfo requestInfo, Context context)
      throws ApiGatewayException {
    validateAccessRights(requestInfo);
  }

  @Override
  protected PaginatedSearchResult<NviCandidateIndexDocument> processInput(
      Void input, RequestInfo requestInfo, Context context)
      throws UnauthorizedException, BadRequestException {
    var affiliations = getQueryParamAffiliationsOrViewingScope(requestInfo);
    var candidateSearchParameters =
        CandidateSearchParameters.fromRequestInfo(requestInfo, affiliations);
    logAggregationType(candidateSearchParameters);
    return attempt(() -> openSearchClient.search(candidateSearchParameters))
        .map(searchResponse -> toPaginatedResult(searchResponse, candidateSearchParameters))
        .orElseThrow();
  }

  @Override
  protected Integer getSuccessStatusCode(
      Void input, PaginatedSearchResult<NviCandidateIndexDocument> output) {
    return HttpURLConnection.HTTP_OK;
  }

  @JacocoGenerated
  private static ViewingScopeValidatorImpl defaultViewingScopeValidator() {
    return new ViewingScopeValidatorImpl(
        IdentityServiceClient.prepare(), new OrganizationRetriever(new UriRetriever()));
  }

  private static Optional<List<String>> extractQueryParamAffiliations(RequestInfo requestInfo) {
    return requestInfo
        .getQueryParameterOpt(QUERY_PARAM_AFFILIATIONS)
        .map(SearchNviCandidatesHandler::toListOfIdentifiers);
  }

  // TODO: Use this
  private static List<String> toListOfIdentifiers(String identifierListAsString) {
    return Arrays.stream(identifierListAsString.split(COMMA)).collect(Collectors.toList());
  }

  private static boolean userIsNotNviAdmin(RequestInfo requestInfo) {
    return !isNviAdmin(requestInfo);
  }

  private List<String> getQueryParamAffiliationsOrViewingScope(RequestInfo requestInfo)
      throws UnauthorizedException {
    return extractQueryParamAffiliations(requestInfo).orElse(getDefaultAffiliations(requestInfo));
  }

  private List<String> getDefaultAffiliations(RequestInfo requestInfo)
      throws UnauthorizedException {
    return isNviAdmin(requestInfo) ? List.of() : getAffiliationsFromViewingScope(requestInfo);
  }

  private List<String> getAffiliationsFromViewingScope(RequestInfo requestInfo)
      throws UnauthorizedException {
    return fetchViewingScope(requestInfo.getUserName()).stream()
        .map(UriWrapper::fromUri)
        .map(UriWrapper::getLastPathElement)
        .collect(Collectors.toList());
  }

  private Set<URI> fetchViewingScope(String userName) {
    var user = attempt(() -> identityServiceClient.getUser(userName)).orElseThrow();
    return new HashSet<>(user.viewingScope().includedUnits());
  }

  private URI toCristinOrgUri(String identifier) {
    return UriWrapper.fromHost(apiHost)
        .addChild(CRISTIN_PATH, ORGANIZATION_PATH)
        .addChild(identifier)
        .getUri();
  }

  private void logAggregationType(CandidateSearchParameters candidateSearchParameters) {
    logger.info(
        "Aggregation type {} requested for topLevelCristinOrg {}",
        candidateSearchParameters.aggregationType(),
        candidateSearchParameters.topLevelCristinOrg());
  }

  private void validateAccessRights(RequestInfo requestInfo) throws UnauthorizedException {
    if (userIsNotNviAdmin(requestInfo)) {
      var requestedOrganizations =
          extractQueryParamAffiliations(requestInfo)
              .map(this::toOrganizationUris)
              .orElse(List.of());
      if (userIsNotAllowedToView(requestInfo, requestedOrganizations)) {
        throw new UnauthorizedException("User is not allowed to view requested organizations");
      }
    }
  }

  private boolean userIsNotAllowedToView(RequestInfo requestInfo, List<URI> requestedOrganizations)
      throws UnauthorizedException {
    return !viewingScopeValidator.userIsAllowedToAccessAll(
        requestInfo.getUserName(), requestedOrganizations);
  }

  private List<URI> toOrganizationUris(List<String> affiliationIdentifiers) {
    return affiliationIdentifiers.stream().map(this::toCristinOrgUri).toList();
  }
}
