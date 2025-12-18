package no.sikt.nva.nvi.events.batch.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TableScanState.class, name = "TABLE_SCAN"),
  @JsonSubTypes.Type(value = YearQueryState.class, name = "YEAR_QUERY")
})
public sealed interface PaginationState permits TableScanState, YearQueryState {

  Map<String, String> lastEvaluatedKey();

  int itemsEnqueued();

  PaginationState withNextPage(Map<String, String> newLastEvaluatedKey, int additionalItems);
}
