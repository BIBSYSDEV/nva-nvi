package no.sikt.nva.nvi.index.query;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.utils.SearchConstants.AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.CONTRIBUTORS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.KEYWORD;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NAME;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PART_OF;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PUBLICATION_DATE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PUBLICATION_DETAILS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ROLE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.TITLE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.TYPE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.YEAR;
import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchPhraseQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

public class CandidateQuery {

    private static final int MULTIPLE = 2;
    private static final CharSequence JSON_PATH_DELIMITER = ".";
    private static final String CREATOR_ROLE = "Creator";
    private final List<String> affiliations;
    private final boolean excludeSubUnits;
    private final QueryFilterType filter;
    private final String username;
    private final String topLevelCristinOrg;
    private final String searchTerm;
    private final String year;
    private final String category;
    private final String title;
    private final String contributor;
    private final String assignee;

    public CandidateQuery(CandidateQueryParameters params) {
        this.searchTerm = params.searchTerm;
        this.affiliations = params.affiliations.stream().map(URI::toString).toList();
        this.excludeSubUnits = params.excludeSubUnits;
        this.filter = params.filter;
        this.username = params.username;
        this.topLevelCristinOrg = params.topLevelCristinOrg;
        this.year = params.year;
        this.category = params.category;
        this.title = params.title;
        this.contributor = params.contributor;
        this.assignee = params.assignee;
    }

    public static Query matchAtLeastOne(Query... queries) {
        return new Query.Builder()
                   .bool(new BoolQuery.Builder().should(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public Query toQuery() {
        return mustMatch(specificMatch().toArray(Query[]::new));
    }

    private static Query mustMatch(Query... queries) {
        return new Query.Builder()
                   .bool(new BoolQuery.Builder().must(Arrays.stream(queries).toList()).build())
                   .build();
    }

    private static Query multipleApprovalsQuery() {
        return mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE));
    }

    private static Query nestedQuery(String path, Query... queries) {
        return new NestedQuery.Builder()
                   .path(path)
                   .query(mustMatch(queries))
                   .build()._toQuery();
    }

    private static Query statusQuery(String customer, ApprovalStatus status) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)),
                           termQuery(status.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));
    }

    private static Query disputeQuery() {
        return termQuery(GlobalApprovalStatus.DISPUTE.getValue(), jsonPathOf(GLOBAL_APPROVAL_STATUS));
    }

    private static Query termQuery(String value, String field) {
        return nonNull(value)
                   ? new TermQuery.Builder()
                         .value(new FieldValue.Builder().stringValue(value).build())
                         .field(field)
                         .build()._toQuery()
                   : new MatchAllQuery.Builder().build()._toQuery();
    }

    private static String jsonPathOf(String... args) {
        return String.join(JSON_PATH_DELIMITER, args);
    }

    private static Query rangeFromQuery(String field, int greaterThanOrEqualTo) {
        return new RangeQuery.Builder().field(field).gte(JsonData.of(greaterThanOrEqualTo)).build()._toQuery();
    }

    private static Query containsPendingStatusQuery() {
        return nestedQuery(APPROVALS, termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));
    }

    private static Query assignmentsQuery(String username, String customer) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)),
                           termQuery(username, jsonPathOf(APPROVALS, ASSIGNEE)));
    }

    private static Query termsQuery(List<String> values, String field) {
        var termsFields = values.stream().map(FieldValue::of).toList();
        return new TermsQuery.Builder()
                   .field(field)
                   .terms(new TermsQueryField.Builder().value(termsFields).build())
                   .build()._toQuery();
    }

    private static Query matchQuery(String value, String field) {
        return new MatchQuery.Builder().field(field)
                   .query(new FieldValue.Builder().stringValue(value).build())
                   .build()._toQuery();
    }

    private static Query contributorQueryIncludingSubUnits(List<String> organizations) {
        return nestedQuery(jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS),
                           QueryBuilders.bool().must(
                               matchAtLeastOne(
                                   termsQuery(organizations,
                                              jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, AFFILIATIONS, ID)),
                                   termsQuery(organizations,
                                              jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, AFFILIATIONS, PART_OF))
                               ),
                               matchQuery(CREATOR_ROLE, jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, ROLE))
                           ).build()._toQuery()
        );
    }

    private static Query contributorQueryExcludingSubUnits(List<String> organizations) {
        return nestedQuery(jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS),
                           QueryBuilders.bool().must(
                               termsQuery(organizations,
                                          jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, AFFILIATIONS, ID)),
                               matchQuery(CREATOR_ROLE, jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, ROLE))
                           ).build()._toQuery()
        );
    }

    private static Query yearQuery(String year) {
        return termQuery(nonNull(year) ? year : String.valueOf(ZonedDateTime.now().getYear()),
                         jsonPathOf(PUBLICATION_DETAILS, PUBLICATION_DATE, YEAR, KEYWORD));
    }

    private static Query categoryQuery(String category) {
        return Optional.ofNullable(category)
                   .map(c -> termQuery(c, jsonPathOf(PUBLICATION_DETAILS, TYPE, KEYWORD)))
                   .orElse(null);
    }

    private List<Query> specificMatch() {
        var institutionQuery = affiliations.isEmpty() ? Optional.<Query>empty() : createInstitutionQuery();
        var filterQuery = constructQueryWithFilter();
        var searchTermQuery = createSearchTermQuery(searchTerm);
        var yearQuery = createYearQuery(year);
        var categoryQuery = createCategoryQuery(category);
        var titleQuery = createTitleQuery(title);
        var contributorQuery = createContributorQuery(contributor);
        var assigneeQuery = createAssigneeQuery(assignee);

        return Stream.of(searchTermQuery, institutionQuery, filterQuery, yearQuery, categoryQuery, titleQuery,
                         contributorQuery,
                         assigneeQuery)
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .toList();
    }

    private Optional<Query> constructQueryWithFilter() {

        var aggregation = switch (filter) {
            case EMPTY_FILTER -> null;
            case NEW_AGG -> statusQuery(topLevelCristinOrg, NEW);

            case NEW_COLLABORATION_AGG -> mustMatch(statusQuery(topLevelCristinOrg, NEW), multipleApprovalsQuery());

            case PENDING_AGG -> mustMatch(statusQuery(topLevelCristinOrg, PENDING));

            case PENDING_COLLABORATION_AGG ->
                mustMatch(statusQuery(topLevelCristinOrg, PENDING), multipleApprovalsQuery());

            case APPROVED_AGG -> mustMatch(statusQuery(topLevelCristinOrg, APPROVED));

            case APPROVED_COLLABORATION_AGG -> mustMatch(statusQuery(topLevelCristinOrg, APPROVED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case REJECTED_AGG -> mustMatch(statusQuery(topLevelCristinOrg, REJECTED));

            case REJECTED_COLLABORATION_AGG -> mustMatch(statusQuery(topLevelCristinOrg, REJECTED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case DISPUTED_AGG -> mustMatch(disputeQuery(), institutionQuery(topLevelCristinOrg));
            case ASSIGNMENTS_AGG -> mustMatch(assignmentsQuery(username, topLevelCristinOrg));
        };

        return Optional.ofNullable(aggregation);
    }

    private Query institutionQuery(String topLevelCristinOrg) {
        return nestedQuery(APPROVALS, termQuery(topLevelCristinOrg, jsonPathOf(APPROVALS, INSTITUTION_ID)));
    }

    private Optional<Query> createInstitutionQuery() {
        return excludeSubUnits
                   ? Optional.of(contributorQueryExcludingSubUnits(affiliations))
                   : Optional.of(contributorQueryIncludingSubUnits(affiliations));
    }

    private Optional<Query> createSearchTermQuery(String searchTerm) {
        return nonNull(searchTerm) ? Optional.of(new MultiMatchQuery.Builder().query(searchTerm).build()._toQuery())
                   : Optional.empty();
    }

    private Optional<Query> createYearQuery(String year) {
        return nonNull(year) ? Optional.of(yearQuery(year)) : Optional.empty();
    }

    private Optional<Query> createCategoryQuery(String category) {
        return nonNull(category) ? Optional.of(categoryQuery(category)) : Optional.empty();
    }

    private Optional<Query> createTitleQuery(String title) {
        return Optional.ofNullable(title)
                   .map(t -> new MatchPhraseQuery.Builder()
                                 .field(jsonPathOf(PUBLICATION_DETAILS, TITLE))
                                 .query(t)
                                 .build()
                                 ._toQuery());
    }

    private Optional<Query> createContributorQuery(String contributor) {
        return Optional.ofNullable(contributor)
                   .map(c -> new MatchPhraseQuery.Builder()
                                 .field(jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, NAME))
                                 .query(c)
                                 .build()
                                 ._toQuery());
    }

    private Optional<Query> createAssigneeQuery(String assignee) {
        return Optional.ofNullable(assignee)
                   .map(a -> new MatchPhraseQuery.Builder()
                                 .field(jsonPathOf(APPROVALS, ASSIGNEE))
                                 .query(a)
                                 .build()
                                 ._toQuery());
    }

    public enum QueryFilterType {
        NEW_AGG("pending"),
        NEW_COLLABORATION_AGG("pendingCollaboration"),
        PENDING_AGG("assigned"),
        PENDING_COLLABORATION_AGG("assignedCollaboration"),
        APPROVED_AGG("approved"),
        APPROVED_COLLABORATION_AGG("approvedCollaboration"),
        REJECTED_AGG("rejected"),
        REJECTED_COLLABORATION_AGG("rejectedCollaboration"),
        DISPUTED_AGG("dispute"),
        ASSIGNMENTS_AGG("assignments"),
        EMPTY_FILTER("");

        private final String filter;

        QueryFilterType(String filter) {
            this.filter = filter;
        }

        public static Optional<QueryFilterType> parse(String candidate) {
            var testValue = isNull(candidate) ? "" : candidate;
            return Arrays.stream(values())
                       .filter(item -> item.getFilter().equalsIgnoreCase(testValue))
                       .findAny();
        }

        @JsonValue
        public String getFilter() {
            return filter;
        }
    }

    public static class Builder {

        private List<URI> institutions;
        private boolean excludeSubUnits;
        private QueryFilterType filter;
        private String username;
        private String topLevelCristinOrg;
        private String searchTerm;
        private String year;
        private String category;
        private String title;
        private String contributor;
        private String assignee;

        public Builder() {
            // No-args constructor.
        }

        public Builder withInstitutions(List<URI> institutions) {
            this.institutions = institutions;
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

        public Builder withContributor(String contributor) {
            this.contributor = contributor;
            return this;
        }

        public Builder withAssignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        public CandidateQuery build() {
            CandidateQueryParameters params = new CandidateQueryParameters();

            params.searchTerm = this.searchTerm;
            params.affiliations = this.institutions;
            params.excludeSubUnits = this.excludeSubUnits;
            params.filter = this.filter;
            params.username = this.username;
            params.topLevelCristinOrg = this.topLevelCristinOrg;
            params.year = this.year;
            params.category = this.category;
            params.title = this.title;
            params.contributor = this.contributor;
            params.assignee = this.assignee;

            return new CandidateQuery(params);
        }
    }

    public static class CandidateQueryParameters {

        public String searchTerm;
        public List<URI> affiliations;
        public boolean excludeSubUnits;
        public QueryFilterType filter;
        public String username;
        public String topLevelCristinOrg;
        public String year;
        public String category;
        public String title;
        public String contributor;
        public String assignee;
    }
}