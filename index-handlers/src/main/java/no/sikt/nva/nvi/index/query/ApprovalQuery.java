package no.sikt.nva.nvi.index.query;

import static java.util.Collections.emptyList;
import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.existsQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.fieldValueQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.matchAtLeastOne;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustNotMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.nestedQuery;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static nva.commons.core.StringUtils.isBlank;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.utils.QueryFunctions;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

public record ApprovalQuery(
    String topLevelOrganization,
    String assignee,
    boolean excludeUnassigned,
    Set<ApprovalStatus> allowedStatuses) {

  public Optional<Query> toQuery() {
    var subQueries = getSubQueries();
    if (subQueries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        nestedQuery(
            APPROVALS,
            approvalBelongsTo(topLevelOrganization),
            mustMatch(subQueries.toArray(Query[]::new))));
  }

  public static Query approvalBelongsTo(String organization) {
    return fieldValueQuery(jsonPathOf(APPROVALS, INSTITUTION_ID), organization);
  }

  public static Query approvalStatusIs(ApprovalStatus approvalStatus) {
    return fieldValueQuery(jsonPathOf(APPROVALS, APPROVAL_STATUS), approvalStatus.getValue());
  }

  private List<Query> getSubQueries() {
    return Stream.of(assigneeQuery(), excludeUnassignedQuery(), statusQuery())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private Optional<Query> statusQuery() {
    return allowedStatuses.stream()
        .map(ApprovalQuery::toStatusQuery)
        .flatMap(List::stream)
        .reduce(QueryFunctions::matchAtLeastOne);
  }

  private static List<Query> toStatusQuery(ApprovalStatus status) {
    return switch (status) {
      case PENDING -> List.of(approvalStatusIs(NEW), approvalStatusIs(PENDING));
      case APPROVED -> List.of(approvalStatusIs(APPROVED));
      case REJECTED -> List.of(approvalStatusIs(REJECTED));
      default -> emptyList();
    };
  }

  private Optional<Query> assigneeQuery() {
    if (isBlank(assignee)) {
      return Optional.empty();
    }

    return Optional.of(matchAtLeastOne(approvalIsAssignedTo(assignee), approvalIsUnassigned()));
  }

  private Optional<Query> excludeUnassignedQuery() {
    if (!excludeUnassigned) {
      return Optional.empty();
    }
    return Optional.of(mustNotMatch(approvalIsUnassigned()));
  }

  private static Query approvalIsAssignedTo(String username) {
    return QueryBuilders.matchPhrase()
        .field(jsonPathOf(APPROVALS, ASSIGNEE))
        .query(username)
        .build()
        .toQuery();
  }

  private static Query approvalIsUnassigned() {
    return mustNotMatch(existsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));
  }
}
