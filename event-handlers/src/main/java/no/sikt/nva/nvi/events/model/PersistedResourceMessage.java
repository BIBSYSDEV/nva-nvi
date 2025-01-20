package no.sikt.nva.nvi.events.model;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import java.net.URI;

public record PersistedResourceMessage(URI resourceFileUri) {

  public static PersistedResourceMessage fromJson(String body) {
    return attempt(() -> dtoObjectMapper.readValue(body, PersistedResourceMessage.class))
        .orElseThrow();
  }
}
