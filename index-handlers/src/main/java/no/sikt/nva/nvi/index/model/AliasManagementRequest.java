package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AliasManagementRequest(
    @JsonProperty("aliasName") String aliasName, @JsonProperty("targetIndex") String targetIndex) {

  @JsonCreator
  public AliasManagementRequest {}
}
