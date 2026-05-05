package no.sikt.nva.nvi.index.model;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record IndexManagementRequest(@JsonProperty("indexName") String indexName) {

  @JsonCreator
  public IndexManagementRequest {}

  public String resolvedIndexName() {
    return isNull(indexName) || indexName.isBlank() ? NVI_CANDIDATES_INDEX : indexName;
  }
}
