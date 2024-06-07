package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.utils.GraphUtils.APPLICATION_JSON;
import static no.sikt.nva.nvi.common.utils.GraphUtils.HAS_PART_PROPERTY;
import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.PaginatedResultConverter.toPaginatedResult;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.collect.Sets;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchNviCandidatesHandler
    extends ApiGatewayHandler<Void, PaginatedSearchResult<NviCandidateIndexDocument>> {

    private static final String USER_IS_NOT_ALLOWED_TO_SEARCH_FOR_AFFILIATIONS_S
        = "User is not allowed to search for affiliations: %s";
    private static final String COMMA_AND_SPACE = ", ";
    private final Logger logger = LoggerFactory.getLogger(SearchNviCandidatesHandler.class);
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final AuthorizedBackendUriRetriever uriRetriever;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        super(Void.class);
        this.openSearchClient = defaultOpenSearchClient();
        this.uriRetriever = defaultUriRetriever(new Environment());
    }

    public SearchNviCandidatesHandler(SearchClient<NviCandidateIndexDocument> openSearchClient,
                                      AuthorizedBackendUriRetriever uriRetriever) {
        super(Void.class);
        this.openSearchClient = openSearchClient;
        this.uriRetriever = uriRetriever;
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

    private static boolean userIsNviAdmin(RequestInfo requestInfo) {
        return requestInfo.userIsAuthorized(AccessRight.MANAGE_NVI);
    }

    private static List<URI> getAffiliations(RequestInfo requestInfo, URI topLevelOrg) {
        return Optional.ofNullable(extractQueryParamAffiliations(requestInfo))
                   .orElse(List.of(topLevelOrg));
    }

    private static List<URI> extractQueryParamAffiliations(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_AFFILIATIONS)
                   .map(SearchNviCandidatesHandler::toListOfUris)
                   .orElse(null);
    }

    private static List<URI> toListOfUris(String uriListAsString) {
        return Arrays.stream(uriListAsString.split(COMMA))
                   .map(URI::create)
                   .collect(Collectors.toList());
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultUriRetriever(Environment environment) {
        return new AuthorizedBackendUriRetriever(environment.readEnv("BACKEND_CLIENT_AUTH_URL"),
                                                 environment.readEnv("BACKEND_CLIENT_SECRET_NAME"));
    }

    private static boolean userIsNotNviAdmin(RequestInfo requestInfo) {
        return !userIsNviAdmin(requestInfo);
    }

    private static Stream<URI> toUris(Stream<String> stream) {
        return stream.map(URI::create);
    }

    private static Stream<String> concat(URI topLevelOrg, Stream<String> stringStream) {
        return Stream.concat(stringStream, Stream.of(topLevelOrg.toString()));
    }

    private static Stream<String> toStreamOfRfdNodes(NodeIterator nodeIterator) {
        return nodeIterator.toList().stream().map(RDFNode::toString);
    }

    private static NodeIterator getObjectsOfPropertyPartOf(Model model) {
        return model.listObjectsOfProperty(model.createProperty(HAS_PART_PROPERTY));
    }

    private void logAggregationType(CandidateSearchParameters candidateSearchParameters) {
        logger.info("Aggregation type {} requested for topLevelCristinOrg {}",
                    candidateSearchParameters.aggregationType(),
                    candidateSearchParameters.topLevelCristinOrg());
    }

    private void validateAccessRights(RequestInfo requestInfo) throws UnauthorizedException {
        if (userIsNotNviAdmin(requestInfo)) {
            var topLevelOrg = requestInfo.getTopLevelOrgCristinId().orElseThrow();
            var affiliations = getAffiliations(requestInfo, topLevelOrg);
            assertUserIsAllowedToSearchAffiliations(affiliations, topLevelOrg);
        }
    }

    private void assertUserIsAllowedToSearchAffiliations(List<URI> affiliations, URI topLevelOrg)
        throws UnauthorizedException {
        var allowed = attempt(() -> this.uriRetriever.getRawContent(topLevelOrg, APPLICATION_JSON)).map(
                Optional::orElseThrow)
                          .map(str -> createModel(dtoObjectMapper.readTree(str)))
                          .map(SearchNviCandidatesHandler::getObjectsOfPropertyPartOf)
                          .map(SearchNviCandidatesHandler::toStreamOfRfdNodes)
                          .map(node -> concat(topLevelOrg, node))
                          .map(SearchNviCandidatesHandler::toUris)
                          .orElseThrow()
                          .collect(Collectors.toSet());

        var illegal = Sets.difference(new HashSet<>(affiliations), allowed);

        if (!illegal.isEmpty()) {
            throw new UnauthorizedException(
                String.format(USER_IS_NOT_ALLOWED_TO_SEARCH_FOR_AFFILIATIONS_S,
                              String.join(COMMA_AND_SPACE, illegal.toString()))
            );
        }
    }
}

