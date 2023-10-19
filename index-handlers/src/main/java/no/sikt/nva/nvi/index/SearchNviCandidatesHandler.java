package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.utils.GraphUtils.APPLICATION_JSON;
import static no.sikt.nva.nvi.common.utils.GraphUtils.HAS_PART_PROPERTY;
import static no.sikt.nva.nvi.common.utils.GraphUtils.createModel;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.utils.PaginatedResultConverter.toPaginatedResult;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.google.common.collect.Sets;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import org.apache.jena.rdf.model.RDFNode;

public class SearchNviCandidatesHandler
    extends ApiGatewayHandler<Void, PaginatedSearchResult<NviCandidateIndexDocument>> {

    public static final String QUERY_PARAM_AFFILIATIONS = "affiliations";
    public static final String QUERY_PARAM_EXCLUDE_SUB_UNITS = "excludeSubUnits";
    public static final String QUERY_PARAM_FILTER = "filter";
    private static final String DEFAULT_STRING = StringUtils.EMPTY_STRING;
    private static final String QUERY_SIZE_PARAM = "size";
    private static final String QUERY_OFFSET_PARAM = "offset";
    private static final int DEFAULT_QUERY_SIZE = 10;
    private static final int DEFAULT_OFFSET_SIZE = 0;
    public static final String QUERY_PARAM_YEAR = "year";
    public static final String QUERY_PARAMETER_SEARCH_TERM = "query";
    public static final String USER_IS_NOT_ALLOWED_TO_SEARCH_FOR_AFFILIATIONS_S
        = "User is not allowed to search for affiliations: %s";
    public static final String COMMA_AND_SPACE = ", ";
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final AuthorizedBackendUriRetriever uriRetriever;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        super(Void.class);
        this.openSearchClient = defaultOpenSearchClient();
        this.uriRetriever = defaultUriRetriver();
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
        throws UnauthorizedException {
        var candidateSearchParameters = getCandidateSearchParameters(requestInfo);
        assertUserIsAllowedToSearchAffiliations(candidateSearchParameters.affiliations(),
                                                candidateSearchParameters.customer());

        return attempt(() -> openSearchClient.search(candidateSearchParameters))
                   .map(searchResponse -> toPaginatedResult(searchResponse, candidateSearchParameters))
                   .orElseThrow();
    }

    private CandidateSearchParameters getCandidateSearchParameters(RequestInfo requestInfo)
        throws UnauthorizedException {
        var offset = extractQueryParamOffsetOrDefault(requestInfo);
        var size = extractQueryParamSizeOrDefault(requestInfo);
        var filter = extractQueryParamFilterOrDefault(requestInfo);
        var excludeSubUnits = extractQueryParamExcludeSubUnitsOrDefault(requestInfo);
        var topLevelOrg = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        var affiliations = Optional.ofNullable(extractQueryParamAffiliations(requestInfo))
                               .orElse(List.of(topLevelOrg));
        var username = requestInfo.getUserName();
        var year = extractQueryParamPublicationDateOrDefault(requestInfo);
        var searchTerm = extractQueryParamSearchTermOrDefault(requestInfo);

        assertUserIsAllowedToSearchAffiliations(affiliations, topLevelOrg);

        var candidateSearchParameters = new CandidateSearchParameters(affiliations, excludeSubUnits, filter, username,
                                                                      year, searchTerm, topLevelOrg, offset, size);
        return candidateSearchParameters;
    }

    private void assertUserIsAllowedToSearchAffiliations(List<URI> affiliations, URI topLevelOrg)
        throws UnauthorizedException {
        var allowed = attempt(() -> this.uriRetriever.getRawContent(topLevelOrg, APPLICATION_JSON)).map(
                Optional::orElseThrow)
                          .map(str -> createModel(dtoObjectMapper.readTree(str)))
                          .map(model -> model.listObjectsOfProperty(model.createProperty(HAS_PART_PROPERTY)))
                          .map(node -> node.toList().stream().map(RDFNode::toString))
                          .map(s -> Stream.concat(s, Stream.of(topLevelOrg.toString())))
                          .orElseThrow()
                          .collect(Collectors.toSet());

        var illegal = Sets.difference(new HashSet<>(affiliations.stream().map(URI::toString)
                                                               .collect(Collectors.toSet())), allowed);

        if (!illegal.isEmpty()) {
            throw new UnauthorizedException(
                String.format(USER_IS_NOT_ALLOWED_TO_SEARCH_FOR_AFFILIATIONS_S,
                              String.join(COMMA_AND_SPACE, illegal))
            );
        }
    }

    private String extractQueryParamPublicationDateOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(QUERY_PARAM_YEAR, String.valueOf(ZonedDateTime.now().getYear()));
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PaginatedSearchResult<NviCandidateIndexDocument> output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static Integer extractQueryParamSizeOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_SIZE_PARAM).map(Integer::parseInt).orElse(DEFAULT_QUERY_SIZE);
    }

    private static Integer extractQueryParamOffsetOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_OFFSET_PARAM)
                   .map(Integer::parseInt)
                   .orElse(DEFAULT_OFFSET_SIZE);
    }

    private static List<URI> extractQueryParamAffiliations(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_AFFILIATIONS)
                   .map(SearchNviCandidatesHandler::splitStringToUris)
                   .orElse(null);
    }

    private static List<URI> splitStringToUris(String s) {
        return Arrays.stream(s.split(COMMA)).map(URI::create).collect(Collectors.toList());
    }

    private static boolean extractQueryParamExcludeSubUnitsOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_EXCLUDE_SUB_UNITS)
                    .map(Boolean::parseBoolean).orElse(false);
    }

    private static String extractQueryParamFilterOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(QUERY_PARAM_FILTER, DEFAULT_STRING);
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultUriRetriver() {
        return new AuthorizedBackendUriRetriever(new Environment().readEnv("BACKEND_CLIENT_AUTH_URL"),
                                                 new Environment().readEnv("BACKEND_CLIENT_SECRET_NAME"));
    }

    private static String extractQueryParamSearchTermOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
            .getOrDefault(QUERY_PARAMETER_SEARCH_TERM, DEFAULT_STRING);
    }
}

