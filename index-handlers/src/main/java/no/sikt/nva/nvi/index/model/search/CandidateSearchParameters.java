package no.sikt.nva.nvi.index.model.search;

import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_AGGREGATION_TYPE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_ASSIGNEE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_CATEGORY;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_EXCLUDE_SUB_UNITS;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_FILTER;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_OFFSET;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_ORDER_BY;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SEARCH_TERM;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SIZE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SORT_ORDER;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_TITLE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_YEAR;
import static no.sikt.nva.nvi.index.model.search.SearchResultParameters.DEFAULT_SORT_ORDER;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.StringUtils;

public record CandidateSearchParameters(
    String searchTerm,
    List<String> affiliationIdentifiers,
    boolean excludeSubUnits,
    String filter,
    String username,
    String year,
    String category,
    String title,
    String assignee,
    URI topLevelCristinOrg,
    String aggregationType,
    List<String> excludeFields,
    SearchResultParameters searchResultParameters)
    implements JsonSerializable {

  public static final String DEFAULT_AGGREGATION_TYPE = "all";
  private static final String DEFAULT_STRING = StringUtils.EMPTY_STRING;
  private static final int DEFAULT_QUERY_SIZE = 10;
  private static final int DEFAULT_OFFSET_SIZE = 0;
  private static final String CONTRIBUTORS_EXCLUDED_TO_REDUCE_RESPONSE_SIZE =
      "publicationDetails.contributors";

  public static Builder builder() {
    return new Builder();
  }

  public static CandidateSearchParameters fromRequestInfo(
      RequestInfo requestInfo, List<String> affiliationIdentifiers)
      throws UnauthorizedException, BadRequestException {
    var aggregationType = extractQueryParamAggregationType(requestInfo);
    return builder()
        .withSearchTerm(extractQueryParamSearchTermOrDefault(requestInfo))
        .withAffiliations(affiliationIdentifiers)
        .withExcludeSubUnits(extractQueryParamExcludeSubUnitsOrDefault(requestInfo))
        .withFilter(extractQueryParamFilterOrDefault(requestInfo))
        .withUsername(requestInfo.getUserName())
        .withYear(extractQueryParamPublicationDateOrDefault(requestInfo))
        .withCategory(extractQueryParamCategoryOrDefault(requestInfo))
        .withTitle(extractQueryParamTitle(requestInfo))
        .withAssignee(extractQueryParamAssignee(requestInfo))
        .withAggregationType(aggregationType)
        .withSearchResultParameters(getResultParameters(requestInfo))
        .withTopLevelCristinOrg(requestInfo.getTopLevelOrgCristinId().orElse(null))
        .withExcludeFields(List.of(CONTRIBUTORS_EXCLUDED_TO_REDUCE_RESPONSE_SIZE))
        .build();
  }

  public String topLevelOrgUriAsString() {
    return Optional.ofNullable(topLevelCristinOrg).map(URI::toString).orElse(null);
  }

  private static SearchResultParameters getResultParameters(RequestInfo requestInfo)
      throws BadRequestException {
    return SearchResultParameters.builder()
        .withOffset(extractQueryParamOffsetOrDefault(requestInfo))
        .withSize(extractQueryParamSizeOrDefault(requestInfo))
        .withOrderBy(extractQueryParamOrderByOrDefault(requestInfo))
        .withSortOrder(extractQueryParamSortOrderOrDefault(requestInfo))
        .build();
  }

  private static String extractQueryParamSortOrderOrDefault(RequestInfo requestInfo) {
    return requestInfo.getQueryParameterOpt(QUERY_PARAM_SORT_ORDER).orElse(DEFAULT_SORT_ORDER);
  }

  private static String extractQueryParamOrderByOrDefault(RequestInfo requestInfo)
      throws BadRequestException {
    try {
      return requestInfo
          .getQueryParameterOpt(QUERY_PARAM_ORDER_BY)
          .map(OrderByFields::parse)
          .map(OrderByFields::getValue)
          .orElse(OrderByFields.CREATED_DATE.getValue());
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException(exception.getMessage());
    }
  }

  private static String extractQueryParamAggregationType(RequestInfo requestInfo) {
    return requestInfo
        .getQueryParameterOpt(QUERY_AGGREGATION_TYPE)
        .orElse(DEFAULT_AGGREGATION_TYPE);
  }

  private static Integer extractQueryParamSizeOrDefault(RequestInfo requestInfo) {
    return requestInfo
        .getQueryParameterOpt(QUERY_PARAM_SIZE)
        .map(Integer::parseInt)
        .orElse(DEFAULT_QUERY_SIZE);
  }

  private static Integer extractQueryParamOffsetOrDefault(RequestInfo requestInfo) {
    return requestInfo
        .getQueryParameterOpt(QUERY_PARAM_OFFSET)
        .map(Integer::parseInt)
        .orElse(DEFAULT_OFFSET_SIZE);
  }

  private static boolean extractQueryParamExcludeSubUnitsOrDefault(RequestInfo requestInfo) {
    return requestInfo
        .getQueryParameterOpt(QUERY_PARAM_EXCLUDE_SUB_UNITS)
        .map(Boolean::parseBoolean)
        .orElse(false);
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

  private static String extractQueryParamAssignee(RequestInfo requestInfo) {
    return requestInfo.getQueryParameters().get(QUERY_PARAM_ASSIGNEE);
  }

  private static String extractQueryParamPublicationDateOrDefault(RequestInfo requestInfo) {
    return requestInfo
        .getQueryParameterOpt(QUERY_PARAM_YEAR)
        .orElse(String.valueOf(ZonedDateTime.now().getYear()));
  }

  public static final class Builder {

    private String searchTerm;
    private List<String> affiliationIdentifiers;
    private boolean excludeSubUnits;
    private String filter;
    private String username;
    private String year;
    private String category;
    private String title;
    private String assignee;
    private URI topLevelCristinOrg;
    private String aggregationType;
    private List<String> excludeFields = new ArrayList<>();
    private SearchResultParameters searchResultParameters =
        SearchResultParameters.builder().build();

    private Builder() {}

    public Builder withSearchTerm(String searchTerm) {
      this.searchTerm = searchTerm;
      return this;
    }

    public Builder withAffiliations(List<String> affiliationIdentifiers) {
      this.affiliationIdentifiers = affiliationIdentifiers;
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

    public Builder withExcludeFields(List<String> excludeFields) {
      this.excludeFields = excludeFields;
      return this;
    }

    public Builder withSearchResultParameters(SearchResultParameters searchResultParameters) {
      this.searchResultParameters = searchResultParameters;
      return this;
    }

    public CandidateSearchParameters build() {
      return new CandidateSearchParameters(
          searchTerm,
          affiliationIdentifiers,
          excludeSubUnits,
          filter,
          username,
          year,
          category,
          title,
          assignee,
          topLevelCristinOrg,
          aggregationType,
          excludeFields,
          searchResultParameters);
    }
  }
}
