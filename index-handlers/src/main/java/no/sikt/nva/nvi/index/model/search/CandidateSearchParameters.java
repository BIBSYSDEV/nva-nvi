package no.sikt.nva.nvi.index.model.search;

import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_AGGREGATION_TYPE;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_OFFSET_PARAM;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_AFFILIATIONS;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_ASSIGNEE;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_CATEGORY;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_CONTRIBUTOR;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_EXCLUDE_SUB_UNITS;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_FILTER;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_ORDER_BY;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_SEARCH_TERM;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_TITLE;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_PARAM_YEAR;
import static no.sikt.nva.nvi.index.model.SearchQueryParameters.QUERY_SIZE_PARAM;
import static no.sikt.nva.nvi.index.model.search.SearchResultParameters.DEFAULT_ORDER_BY_FIELD;
import static nva.commons.apigateway.RestRequestHandler.COMMA;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.StringUtils;

public record CandidateSearchParameters(String searchTerm,
                                        List<URI> affiliations,
                                        boolean excludeSubUnits,
                                        String filter,
                                        String username,
                                        String year,
                                        String category,
                                        String title,
                                        String contributor,
                                        String assignee,
                                        URI topLevelCristinOrg,
                                        String aggregationType,
                                        SearchResultParameters searchResultParameters) implements JsonSerializable {

    public static final String DEFAULT_AGGREGATION_TYPE = "all";
    private static final String DEFAULT_STRING = StringUtils.EMPTY_STRING;
    private static final int DEFAULT_QUERY_SIZE = 10;
    private static final int DEFAULT_OFFSET_SIZE = 0;

    public static Builder builder() {
        return new Builder();
    }

    public static CandidateSearchParameters fromRequestInfo(RequestInfo requestInfo)
        throws UnauthorizedException {
        var aggregationType = extractQueryParamAggregationType(requestInfo);
        return CandidateSearchParameters.builder()
                   .withSearchTerm(extractQueryParamSearchTermOrDefault(requestInfo))
                   .withAffiliations(extractQueryParamAffiliations(requestInfo))
                   .withExcludeSubUnits(extractQueryParamExcludeSubUnitsOrDefault(requestInfo))
                   .withFilter(extractQueryParamFilterOrDefault(requestInfo))
                   .withUsername(requestInfo.getUserName())
                   .withYear(extractQueryParamPublicationDateOrDefault(requestInfo))
                   .withCategory(extractQueryParamCategoryOrDefault(requestInfo))
                   .withTitle(extractQueryParamTitle(requestInfo))
                   .withContributor(extractQueryParamContributor(requestInfo))
                   .withAssignee(extractQueryParamAssignee(requestInfo))
                   .withAggregationType(aggregationType)
                   .withSearchResultParameters(getResultParameters(requestInfo))
                   .withTopLevelCristinOrg(requestInfo.getTopLevelOrgCristinId().orElse(null))
                   .build();
    }

    public String topLevelOrgUriAsString() {
        return Optional.ofNullable(topLevelCristinOrg).map(URI::toString).orElse(null);
    }

    private static SearchResultParameters getResultParameters(RequestInfo requestInfo) {
        return SearchResultParameters.builder()
                   .withOffset(extractQueryParamOffsetOrDefault(requestInfo))
                   .withSize(extractQueryParamSizeOrDefault(requestInfo))
                   .withOrderBy(extractQueryParamOrderByOrDefault(requestInfo))
                   .build();
    }

    private static String extractQueryParamOrderByOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_ORDER_BY).orElse(DEFAULT_ORDER_BY_FIELD);
    }

    private static String extractQueryParamAggregationType(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_AGGREGATION_TYPE).orElse(DEFAULT_AGGREGATION_TYPE);
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
                   .map(CandidateSearchParameters::splitStringToUris)
                   .orElse(requestInfo.getTopLevelOrgCristinId().map(List::of).orElse(List.of()));
    }

    private static List<URI> splitStringToUris(String uriListAsString) {
        return Arrays.stream(uriListAsString.split(COMMA)).map(URI::create).collect(Collectors.toList());
    }

    private static boolean extractQueryParamExcludeSubUnitsOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_EXCLUDE_SUB_UNITS)
                   .map(Boolean::parseBoolean).orElse(false);
    }

    private static String extractQueryParamFilterOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters().getOrDefault(QUERY_PARAM_FILTER, DEFAULT_STRING);
    }

    private static String extractQueryParamSearchTermOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters().get(QUERY_PARAM_SEARCH_TERM);
    }

    private static String extractQueryParamCategoryOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters().get(QUERY_PARAM_CATEGORY);
    }

    private static String extractQueryParamTitle(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters().get(QUERY_PARAM_TITLE);
    }

    private static String extractQueryParamContributor(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters().get(QUERY_PARAM_CONTRIBUTOR);
    }

    private static String extractQueryParamAssignee(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters().get(QUERY_PARAM_ASSIGNEE);
    }

    private static String extractQueryParamPublicationDateOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_PARAM_YEAR)
                   .orElse(String.valueOf(ZonedDateTime.now().getYear()));
    }

    public static final class Builder {

        private String searchTerm;
        private List<URI> affiliations;
        private boolean excludeSubUnits;
        private String filter;
        private String username;
        private String year;
        private String category;
        private String title;
        private String contributor;
        private String assignee;
        private URI topLevelCristinOrg;
        private String aggregationType;
        private SearchResultParameters searchResultParameters = SearchResultParameters.builder().build();

        private Builder() {
        }

        public Builder withSearchTerm(String searchTerm) {
            this.searchTerm = searchTerm;
            return this;
        }

        public Builder withAffiliations(List<URI> affiliations) {
            this.affiliations = affiliations;
            return this;
        }

        public Builder withExcludeSubUnits(boolean excludeSubUnits) {
            this.excludeSubUnits = excludeSubUnits;
            return this;
        }

        public Builder withFilter(String filter) {
            this.filter = filter;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withYear(String year) {
            this.year = year;
            return this;
        }

        public Builder withCategory(String category) {
            this.category = category;
            return this;
        }

        public Builder withTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder withContributor(String contributor) {
            this.contributor = contributor;
            return this;
        }

        public Builder withAssignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        public Builder withTopLevelCristinOrg(URI topLevelCristinOrg) {
            this.topLevelCristinOrg = topLevelCristinOrg;
            return this;
        }

        public Builder withAggregationType(String aggregationType) {
            this.aggregationType = aggregationType;
            return this;
        }

        public Builder withSearchResultParameters(SearchResultParameters searchResultParameters) {
            this.searchResultParameters = searchResultParameters;
            return this;
        }

        public CandidateSearchParameters build() {
            return new CandidateSearchParameters(searchTerm, affiliations, excludeSubUnits, filter, username, year,
                                                 category, title, contributor, assignee, topLevelCristinOrg,
                                                 aggregationType, searchResultParameters);
        }
    }
}
