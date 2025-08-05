package no.sikt.nva.nvi.index.query;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.query.ApprovalQuery.approvalBelongsTo;
import static no.sikt.nva.nvi.index.query.ApprovalQuery.approvalStatusIs;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.assignmentsQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.containsNonFinalizedStatusQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.disputeQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.fieldValueQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.matchAtLeastOne;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.multipleApprovalsQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustNotMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.nestedQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.statusQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.termsQuery;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ABSTRACT;
import static no.sikt.nva.nvi.index.utils.SearchConstants.AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.CONTRIBUTORS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.IDENTIFIER;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.KEYWORD;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NAME;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CONTRIBUTORS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PART_OF_IDENTIFIERS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PUBLICATION_DETAILS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.REPORTING_PERIOD;
import static no.sikt.nva.nvi.index.utils.SearchConstants.TITLE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.TYPE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.YEAR;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.utils.QueryFunctions;
import org.opensearch.client.opensearch._types.query_dsl.MatchPhraseQuery;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;

public record CandidateQuery(
    ApprovalQuery approvalQuery,
    List<String> affiliations,
    boolean excludeSubUnits,
    QueryFilterType filter,
    String username,
    String topLevelCristinOrg,
    String searchTerm,
    String year,
    String category,
    String title,
    Set<GlobalApprovalStatus> globalStatuses) {

  public Query toQuery() {
    return mustMatch(specificMatch().toArray(Query[]::new));
  }

  private static Query contributorQueryIncludingSubUnits(List<String> organizations) {
    return nestedQuery(
        jsonPathOf(PUBLICATION_DETAILS, NVI_CONTRIBUTORS),
        QueryBuilders.bool()
            .must(
                matchAtLeastOne(
                    termsQuery(
                        organizations,
                        jsonPathOf(
                            PUBLICATION_DETAILS, NVI_CONTRIBUTORS, AFFILIATIONS, IDENTIFIER)),
                    termsQuery(
                        organizations,
                        jsonPathOf(
                            PUBLICATION_DETAILS,
                            NVI_CONTRIBUTORS,
                            AFFILIATIONS,
                            PART_OF_IDENTIFIERS))))
            .build()
            .toQuery());
  }

  private static Query contributorQueryExcludingSubUnits(List<String> organizations) {
    return nestedQuery(
        jsonPathOf(PUBLICATION_DETAILS, NVI_CONTRIBUTORS),
        QueryBuilders.bool()
            .must(
                termsQuery(
                    organizations,
                    jsonPathOf(PUBLICATION_DETAILS, NVI_CONTRIBUTORS, AFFILIATIONS, IDENTIFIER)))
            .build()
            .toQuery());
  }

  private static Query yearQuery(String year) {
    return fieldValueQuery(
        jsonPathOf(REPORTING_PERIOD, YEAR, KEYWORD),
        nonNull(year) ? year : String.valueOf(ZonedDateTime.now().getYear()));
  }

  private static Query categoryQuery(String optionalCategory) {
    return Optional.ofNullable(optionalCategory)
        .map(category -> fieldValueQuery(jsonPathOf(PUBLICATION_DETAILS, TYPE, KEYWORD), category))
        .orElse(null);
  }

  private static Query buildSearchTermQuery(String searchTerm) {
    return new MultiMatchQuery.Builder()
        .query(searchTerm)
        .fields(
            IDENTIFIER,
            jsonPathOf(PUBLICATION_DETAILS, IDENTIFIER),
            jsonPathOf(PUBLICATION_DETAILS, TITLE),
            jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, NAME),
            jsonPathOf(PUBLICATION_DETAILS, NVI_CONTRIBUTORS, NAME),
            jsonPathOf(PUBLICATION_DETAILS, ABSTRACT))
        .operator(Operator.And)
        .type(TextQueryType.CrossFields)
        .build()
        .toQuery();
  }

  private List<Query> specificMatch() {
    var institutionQuery =
        affiliations.isEmpty() ? Optional.<Query>empty() : createInstitutionQuery();
    var filterQuery = constructQueryWithFilter();
    var searchTermQuery = createSearchTermQuery(searchTerm);
    var yearQuery = createYearQuery(year);
    var categoryQuery = createCategoryQuery(category);
    var titleQuery = createTitleQuery(title);
    var globalStatusQuery = createGlobalStatusQuery();

    return Stream.of(
            approvalQuery.toQuery(),
            searchTermQuery,
            institutionQuery,
            filterQuery,
            yearQuery,
            categoryQuery,
            titleQuery,
            globalStatusQuery)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private Optional<Query> constructQueryWithFilter() {

    var aggregation =
        switch (filter) {
          case EMPTY_FILTER -> null;
          case COLLABORATION -> multipleApprovalsQuery();
          case OTHERS_APPROVE -> otherOrganizationStatusQuery(topLevelCristinOrg, APPROVED);
          case OTHERS_REJECT -> otherOrganizationStatusQuery(topLevelCristinOrg, REJECTED);
          case NEW_AGG -> statusQuery(topLevelCristinOrg, NEW);

          case NEW_COLLABORATION_AGG ->
              mustMatch(statusQuery(topLevelCristinOrg, NEW), multipleApprovalsQuery());

          case PENDING_AGG -> statusQuery(topLevelCristinOrg, PENDING);

          case PENDING_COLLABORATION_AGG ->
              mustMatch(statusQuery(topLevelCristinOrg, PENDING), multipleApprovalsQuery());

          case APPROVED_AGG -> statusQuery(topLevelCristinOrg, APPROVED);

          case APPROVED_COLLABORATION_AGG ->
              mustMatch(
                  statusQuery(topLevelCristinOrg, APPROVED),
                  containsNonFinalizedStatusQuery(),
                  multipleApprovalsQuery());

          case REJECTED_AGG -> statusQuery(topLevelCristinOrg, REJECTED);

          case REJECTED_COLLABORATION_AGG ->
              mustMatch(
                  statusQuery(topLevelCristinOrg, REJECTED),
                  containsNonFinalizedStatusQuery(),
                  multipleApprovalsQuery());

          case DISPUTED_AGG -> mustMatch(disputeQuery(), institutionQuery(topLevelCristinOrg));
          case ASSIGNMENTS_AGG -> assignmentsQuery(username, topLevelCristinOrg);
        };

    return Optional.ofNullable(aggregation);
  }

  private static Query otherOrganizationStatusQuery(
      String userOrganization, ApprovalStatus status) {
    return nestedQuery(
        APPROVALS, mustNotMatch(approvalBelongsTo(userOrganization)), approvalStatusIs(status));
  }

  private Query institutionQuery(String topLevelCristinOrg) {
    return nestedQuery(
        APPROVALS, fieldValueQuery(jsonPathOf(APPROVALS, INSTITUTION_ID), topLevelCristinOrg));
  }

  private Optional<Query> createInstitutionQuery() {
    return excludeSubUnits
        ? Optional.of(contributorQueryExcludingSubUnits(affiliations))
        : Optional.of(contributorQueryIncludingSubUnits(affiliations));
  }

  private Optional<Query> createSearchTermQuery(String searchTerm) {
    return nonNull(searchTerm) ? Optional.of(buildSearchTermQuery(searchTerm)) : Optional.empty();
  }

  private Optional<Query> createYearQuery(String year) {
    return nonNull(year) ? Optional.of(yearQuery(year)) : Optional.empty();
  }

  private Optional<Query> createCategoryQuery(String category) {
    return nonNull(category) ? Optional.of(categoryQuery(category)) : Optional.empty();
  }

  private Optional<Query> createTitleQuery(String title) {
    return Optional.ofNullable(title)
        .map(
            t ->
                new MatchPhraseQuery.Builder()
                    .field(jsonPathOf(PUBLICATION_DETAILS, TITLE))
                    .query(t)
                    .build()
                    .toQuery());
  }

  private Optional<Query> createGlobalStatusQuery() {
    return globalStatuses.stream()
        .map(QueryFunctions::globalStatusQuery)
        .reduce(QueryFunctions::matchAtLeastOne);
  }

  public static class Builder {

    private List<String> affiliationIdentifiers = emptyList();
    private boolean excludeSubUnits;
    private QueryFilterType filter;
    private String username;
    private String topLevelCristinOrg;
    private String searchTerm;
    private String year;
    private String category;
    private String title;
    private String assignee;
    private boolean excludeUnassigned;
    private Set<ApprovalStatus> statuses = emptySet();
    private Set<GlobalApprovalStatus> globalStatuses = emptySet();

    public Builder() {
      // No-args constructor.
    }

    public Builder withAffiliationIdentifiers(List<String> affiliationIdentifiers) {
      this.affiliationIdentifiers = affiliationIdentifiers;
      return this;
    }

    public Builder withExcludeSubUnits(boolean excludeSubUnits) {
      this.excludeSubUnits = excludeSubUnits;
      return this;
    }

    public Builder withFilter(QueryFilterType filter) {
      this.filter = filter;
      return this;
    }

    public Builder withUsername(String username) {
      this.username = username;
      return this;
    }

    public Builder withTopLevelCristinOrg(String topLevelCristinOrg) {
      this.topLevelCristinOrg = topLevelCristinOrg;
      return this;
    }

    public Builder withSearchTerm(String searchTerm) {
      this.searchTerm = searchTerm;
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

    public Builder withExcludeUnassigned(boolean excludeUnassigned) {
      this.excludeUnassigned = excludeUnassigned;
      return this;
    }

    public Builder withStatuses(List<String> statusValues) {
      this.statuses = statusValues.stream().map(ApprovalStatus::parse).collect(Collectors.toSet());
      return this;
    }

    public Builder withGlobalStatuses(List<String> statusValues) {
      this.globalStatuses =
          statusValues.stream().map(GlobalApprovalStatus::parse).collect(Collectors.toSet());
      return this;
    }

    public CandidateQuery build() {

      return new CandidateQuery(
          new ApprovalQuery(topLevelCristinOrg, assignee, excludeUnassigned, statuses),
          affiliationIdentifiers,
          excludeSubUnits,
          filter,
          username,
          topLevelCristinOrg,
          searchTerm,
          year,
          category,
          title,
          globalStatuses);
    }
  }
}
