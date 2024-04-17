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
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchNviCandidatesHandler
    extends ApiGatewayHandler<Void, PaginatedSearchResult<NviCandidateIndexDocument>> {

    private final Logger logger = LoggerFactory.getLogger(SearchNviCandidatesHandler.class);
    private static final String USER_IS_NOT_ALLOWED_TO_SEARCH_FOR_AFFILIATIONS_S
        = "User is not allowed to search for affiliations: %s";
    private static final String COMMA_AND_SPACE = ", ";
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;
    private final AuthorizedBackendUriRetriever uriRetriever;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        super(Void.class);
        this.openSearchClient = defaultOpenSearchClient();
        this.uriRetriever = defaultUriRetriever();
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
        logger.info("Aggregation type {} requested for topLevelCristinOrg {}", candidateSearchParameters.aggregationType(),
                    candidateSearchParameters.topLevelCristinOrg());
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
        if (!userIsNviAdmin(requestInfo)) {
            return Optional.ofNullable(extractQueryParamAffiliations(requestInfo))
                       .orElse(List.of(topLevelOrg));
        } else {
            return Optional.ofNullable(extractQueryParamAffiliations(requestInfo))
                       .orElse(List.of());
        }
    }

    private static List<URI> extractQueryParamAffiliations(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_AFFILIATIONS)
                   .map(s -> Arrays.stream(s.split(",")).map(URI::create).collect(Collectors.toList()))
                   .orElse(null);
    }

    @JacocoGenerated
    private static AuthorizedBackendUriRetriever defaultUriRetriever() {
        return new AuthorizedBackendUriRetriever(new Environment().readEnv("BACKEND_CLIENT_AUTH_URL"),
                                                 new Environment().readEnv("BACKEND_CLIENT_SECRET_NAME"));
    }

    private CandidateSearchParameters getCandidateSearchParameters(RequestInfo requestInfo)
        throws UnauthorizedException {
        var isAdmin = userIsNviAdmin(requestInfo);
        var topLevelOrg = !isAdmin ? requestInfo.getTopLevelOrgCristinId().orElseThrow() : null;
        var affiliations = getAffiliations(requestInfo, topLevelOrg);
        if (!isAdmin) {
            assertUserIsAllowedToSearchAffiliations(affiliations, topLevelOrg);
        }
        return CandidateSearchParameters.fromRequestInfo(requestInfo)
                   .withAffiliations(affiliations)
                   .withTopLevelCristinOrg(topLevelOrg)
                   .build();
    }

    private void assertUserIsAllowedToSearchAffiliations(List<URI> affiliations, URI topLevelOrg)
        throws UnauthorizedException {
        var allowed = attempt(() -> this.uriRetriever.getRawContent(topLevelOrg, APPLICATION_JSON))
                          .map(Optional::orElseThrow)
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
}

