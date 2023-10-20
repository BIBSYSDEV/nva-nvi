package no.sikt.nva.nvi.index.aws;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.utils.SearchConstants.AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.CONTRIBUTORS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.KEYWORD;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PART_OF;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PUBLICATION_DATE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PUBLICATION_DETAILS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ROLE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.TYPE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.YEAR;
import static no.sikt.nva.nvi.index.utils.SearchConstants.TITLE;
import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchPhraseQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
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
    private final String customer;
    private final String year;
    private final String category;
    private final String title;

    public CandidateQuery(List<URI> affiliations,
                          boolean excludeSubUnits,
                          QueryFilterType filter,
                          String username,
                          String customer,
                          String year,
                          String category,
                          String title) {
        this.affiliations = affiliations.stream().map(URI::toString).toList();
        this.excludeSubUnits = excludeSubUnits;
        this.filter = filter;
        this.username = username;
        this.customer = customer;
        this.year = year;
        this.category = category;
        this.title = title;
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
                           termQuery(customer, jsonPathOf(APPROVALS, ID)),
                           termQuery(status.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));
    }

    private static Query termQuery(String value, String field) {
        return new TermQuery.Builder()
            .value(new FieldValue.Builder().stringValue(value).build())
            .field(field)
            .build()._toQuery();
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
                           termQuery(customer, jsonPathOf(APPROVALS, ID)),
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

    private static Query contributorQueryIncludingSubUnits(List<String> institutions) {
        return nestedQuery(jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS),
                           QueryBuilders.bool().must(
                               matchAtLeastOne(
                                   termsQuery(institutions,
                                              jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, AFFILIATIONS, ID)),
                                   termsQuery(institutions,
                                              jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, AFFILIATIONS, PART_OF))
                               ),
                               matchQuery(CREATOR_ROLE, jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, ROLE))
                           ).build()._toQuery()
        );
    }

    private static Query contributorQueryExcludingSubUnits(List<String> institutions) {
        return nestedQuery(jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS),
                           QueryBuilders.bool().must(
                               termsQuery(institutions,
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

    private static Query statusQueryWithAssignee(String customer, ApprovalStatus status, boolean hasAssignee) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, ID)),
                           termQuery(status.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                           hasAssignee
                               ? existsQuery(jsonPathOf(APPROVALS, ASSIGNEE))
                               : notExistsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));
    }

    private static Query notExistsQuery(String field) {
        return new BoolQuery.Builder()
            .mustNot(new ExistsQuery.Builder().field(field).build()._toQuery())
            .build()._toQuery();
    }

    private static Query existsQuery(String field) {
        return new BoolQuery.Builder()
            .must(new ExistsQuery.Builder().field(field).build()._toQuery())
            .build()._toQuery();
    }

    public static Query matchAtLeastOne(Query... queries) {
        return new Query.Builder()
            .bool(new BoolQuery.Builder().should(Arrays.stream(queries).toList()).build())
            .build();
    }

    private List<Query> specificMatch() {
        var institutionQuery = createInstitutionQuery();
        var filterQuery = constructQueryWithFilter();
        var yearQuery = createYearQuery(year);
        var categoryQuery = createCategoryQuery(category);
        var titleQuery = createTitleQuery(title);

        return Stream.of(institutionQuery, filterQuery, yearQuery, categoryQuery, titleQuery)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    private Optional<Query> constructQueryWithFilter() {

        var aggregation = switch (filter) {
            case EMPTY_FILTER -> null;
            case PENDING_AGG -> statusQueryWithAssignee(customer, PENDING, false);

            case PENDING_COLLABORATION_AGG -> mustMatch(statusQueryWithAssignee(customer, PENDING, false),
                                                        multipleApprovalsQuery());

            case ASSIGNED_AGG -> mustMatch(statusQueryWithAssignee(customer, PENDING, true));

            case ASSIGNED_COLLABORATION_AGG -> mustMatch(statusQueryWithAssignee(customer, PENDING, true),
                                                         multipleApprovalsQuery());

            case APPROVED_AGG -> mustMatch(statusQuery(customer, APPROVED));

            case APPROVED_COLLABORATION_AGG -> mustMatch(statusQuery(customer, APPROVED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case REJECTED_AGG -> mustMatch(statusQuery(customer, REJECTED));

            case REJECTED_COLLABORATION_AGG -> mustMatch(statusQuery(customer, REJECTED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case ASSIGNMENTS_AGG -> mustMatch(assignmentsQuery(username, customer));
        };

        return Optional.ofNullable(aggregation);
    }

    private Optional<Query> createInstitutionQuery() {
        return excludeSubUnits
                   ? Optional.of(contributorQueryExcludingSubUnits(affiliations))
                   : Optional.of(contributorQueryIncludingSubUnits(affiliations));
    }

    private Optional<Query> createYearQuery(String year) {
        return nonNull(year) ? Optional.of(yearQuery(year)) : Optional.empty();

    }

    private Optional<Query> createCategoryQuery(String category) {
        return nonNull(category) ? Optional.of(categoryQuery(category)) : Optional.empty();

    }

    private Optional<Query> createTitleQuery(String title) {
        return nonNull(title) ? Optional.of(new MatchPhraseQuery.Builder().field(jsonPathOf(PUBLICATION_DETAILS,
                                                                                            TITLE))
                                                .query(title)
                                                .build()
                                                ._toQuery()) : Optional.empty();
    }

    public enum QueryFilterType {
        PENDING_AGG("pending"),
        PENDING_COLLABORATION_AGG("pendingCollaboration"),
        ASSIGNED_AGG("assigned"),
        ASSIGNED_COLLABORATION_AGG("assignedCollaboration"),
        APPROVED_AGG("approved"),
        APPROVED_COLLABORATION_AGG("approvedCollaboration"),
        REJECTED_AGG("rejected"),
        REJECTED_COLLABORATION_AGG("rejectedCollaboration"),
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
        private String customer;
        private String year;
        private String category;
        private String title;

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

        public Builder withCustomer(String customer) {
            this.customer = customer;
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

        public CandidateQuery build() {
            return new CandidateQuery(institutions, excludeSubUnits, filter, username, customer, year, category, title);
        }
    }
}