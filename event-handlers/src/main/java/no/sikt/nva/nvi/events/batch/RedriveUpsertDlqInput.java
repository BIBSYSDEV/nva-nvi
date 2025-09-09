package no.sikt.nva.nvi.events.batch;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RedriveUpsertDlqInput(@JsonProperty("count") Integer count) {

  private static final int DEFAULT_COUNT = 10;

  public Integer count() {
    return isNull(count) ? DEFAULT_COUNT : count;
  }
}
