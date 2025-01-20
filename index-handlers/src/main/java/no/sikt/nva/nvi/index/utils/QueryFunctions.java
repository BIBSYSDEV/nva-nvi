package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;

import java.util.Arrays;
import java.util.List;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

public final class QueryFunctions {

  private static final CharSequence JSON_PATH_DELIMITER = ".";
  private static final int MULTIPLE = 2;

  private QueryFunctions() {}

  public static Query nestedQuery(String path, Query query) {
    return new NestedQuery.Builder().path(path).query(query).build()._toQuery();
  }

  public static Query nestedQuery(String path, Query... queries) {
    return new NestedQuery.Builder().path(path).query(mustMatch(queries)).build()._toQuery();
  }

  public static Query fieldValueQuery(String field, String value) {
    return nonNull(value)
        ? new TermQuery.Builder().field(field).value(getFieldValue(value)).build()._toQuery()
        : matchAllQuery();
  }

  public static Query termsQuery(List<String> values, String field) {
    var termsFields = values.stream().map(FieldValue::of).toList();
    return new TermsQuery.Builder()
        .field(field)
        .terms(new TermsQueryField.Builder().value(termsFields).build())
        .build()
        ._toQuery();
  }

  public static Query rangeFromQuery(String field, int greaterThanOrEqualTo) {
    return new RangeQuery.Builder()
        .field(field)
        .gte(JsonData.of(greaterThanOrEqualTo))
        .build()
        ._toQuery();
  }

  public static Query mustNotMatch(String value, String field) {
    return mustNotMatch(matchQuery(value, field));
  }

  public static Query mustNotMatch(Query query) {
    return new Builder().mustNot(query).build()._toQuery();
  }

  public static Query matchAtLeastOne(Query... queries) {
    return new Query.Builder()
        .bool(new BoolQuery.Builder().should(Arrays.stream(queries).toList()).build())
        .build();
  }

  public static Query mustMatch(Query... queries) {
    return new Query.Builder()
        .bool(new Builder().must(Arrays.stream(queries).toList()).build())
        .build();
  }

  public static Query matchQuery(String value, String field) {
    return new MatchQuery.Builder().field(field).query(getFieldValue(value)).build()._toQuery();
  }

  public static Query containsNonFinalizedStatusQuery() {
    return matchAtLeastOne(containsStatusPendingQuery(), containsStatusNewQuery());
  }

  public static Query statusQuery(String customer, ApprovalStatus status) {
    return mustMatch(
        nestedQuery(
            APPROVALS,
            fieldValueQuery(jsonPathOf(APPROVALS, INSTITUTION_ID), customer),
            fieldValueQuery(jsonPathOf(APPROVALS, APPROVAL_STATUS), status.getValue())),
        notDisputeQuery());
  }

  public static Query notDisputeQuery() {
    return mustNotMatch(disputeQuery());
  }

  public static Query disputeQuery() {
    return fieldValueQuery(
        jsonPathOf(GLOBAL_APPROVAL_STATUS), GlobalApprovalStatus.DISPUTE.getValue());
  }

  public static Query multipleApprovalsQuery() {
    return mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE));
  }

  public static Query assignmentsQuery(String username, String customer) {
    return nestedQuery(
        APPROVALS,
        fieldValueQuery(jsonPathOf(APPROVALS, INSTITUTION_ID), customer),
        fieldValueQuery(jsonPathOf(APPROVALS, ASSIGNEE), username));
  }

  private static Query containsStatusPendingQuery() {
    return nestedQuery(
        APPROVALS, fieldValueQuery(jsonPathOf(APPROVALS, APPROVAL_STATUS), PENDING.getValue()));
  }

  private static Query containsStatusNewQuery() {
    return nestedQuery(
        APPROVALS, fieldValueQuery(jsonPathOf(APPROVALS, APPROVAL_STATUS), NEW.getValue()));
  }

  private static Query matchAllQuery() {
    return new MatchAllQuery.Builder().build()._toQuery();
  }

  private static FieldValue getFieldValue(String value) {
    return new FieldValue.Builder().stringValue(value).build();
  }

  private static String jsonPathOf(String... args) {
    return String.join(JSON_PATH_DELIMITER, args);
  }
}
