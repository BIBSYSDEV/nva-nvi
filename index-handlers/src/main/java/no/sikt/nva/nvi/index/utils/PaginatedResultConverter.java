package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_AGGREGATION_TYPE;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_AFFILIATIONS;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_ASSIGNEE;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_CATEGORY;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_CONTRIBUTOR;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_EXCLUDE_SUB_UNITS;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_FILTER;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_SEARCH_TERM;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_TITLE;
import static nva.commons.apigateway.RestRequestHandler.COMMA;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.exceptions.UnprocessableContentException;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaginatedResultConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaginatedResultConverter.class);
    private static final Environment ENVIRONMENT = new Environment();
    private static final String HOST = ENVIRONMENT.readEnv("API_HOST");
    private static final String CUSTOM_DOMAIN_BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String CANDIDATE_PATH = "candidate";

    private PaginatedResultConverter() {

    }

    public static PaginatedSearchResult<NviCandidateIndexDocument> toPaginatedResult(
        SearchResponse<NviCandidateIndexDocument> searchResponse, CandidateSearchParameters candidateSearchParameters)
        throws UnprocessableContentException {
        var searchResultParameters = candidateSearchParameters.searchResultParameters();
        var paginatedSearchResult = PaginatedSearchResult.create(
            constructBaseUri(),
            searchResultParameters.offset(),
            searchResultParameters.size(),
            extractTotalNumberOfHits(searchResponse),
            extractsHits(searchResponse),
            getQueryParameters(candidateSearchParameters),
            AggregationFormatter.format(searchResponse.aggregations()));

        LOGGER.info("Returning paginatedSearchResult with id: {}", paginatedSearchResult.getId().toString());
        return paginatedSearchResult;
    }

    private static Map<String, String> getQueryParameters(CandidateSearchParameters parameters) {
        var queryParams = new HashMap<String, String>();
        putIfValueNotNull(queryParams, QUERY_PARAM_SEARCH_TERM, parameters.searchTerm());
        putAffiliationsIfNotNullOrEmpty(queryParams, QUERY_PARAM_AFFILIATIONS, parameters.affiliations());
        putIfTrue(queryParams, QUERY_PARAM_EXCLUDE_SUB_UNITS, parameters.excludeSubUnits());
        putIfValueNotEmpty(queryParams, QUERY_PARAM_FILTER, parameters.filter());
        putIfValueNotNull(queryParams, QUERY_PARAM_CATEGORY, parameters.category());
        putIfValueNotNull(queryParams, QUERY_PARAM_TITLE, parameters.title());
        putIfValueNotNull(queryParams, QUERY_PARAM_CONTRIBUTOR, parameters.contributor());
        putIfValueNotNull(queryParams, QUERY_PARAM_ASSIGNEE, parameters.assignee());
        putIfValueNotNull(queryParams, QUERY_AGGREGATION_TYPE, parameters.aggregationType());
        return queryParams;
    }

    private static void putIfValueNotNull(Map<String, String> map, String key, String value) {
        if (nonNull(value)) {
            map.put(key, value);
        }
    }

    private static void putAffiliationsIfNotNullOrEmpty(Map<String, String> map, String key, List<URI> affiliations) {
        if (nonNull(affiliations) && !affiliations.isEmpty()) {
            map.put(key, affiliations.stream().map(URI::toString).collect(Collectors.joining(COMMA)));
        }
    }

    private static void putIfTrue(Map<String, String> map, String key, boolean condition) {
        if (condition) {
            map.put(key, String.valueOf(true));
        }
    }

    private static void putIfValueNotEmpty(Map<String, String> map, String key, String value) {
        if (isNotEmpty(value)) {
            map.put(key, value);
        }
    }

    private static boolean isNotEmpty(String filter) {
        return !filter.isEmpty();
    }

    private static int extractTotalNumberOfHits(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return (int) searchResponse.hits().total().value();
    }

    private static List<NviCandidateIndexDocument> extractsHits(
        SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().stream()
                   .map(Hit::source)
                   .toList();
    }

    private static URI constructBaseUri() {
        return UriWrapper.fromHost(HOST).addChild(CUSTOM_DOMAIN_BASE_PATH).addChild(CANDIDATE_PATH).getUri();
    }
}
