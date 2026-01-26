package no.sikt.nva.nvi.events.batch.message;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.unit.nva.commons.json.JsonSerializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RefreshCandidateMessage.class, name = "REFRESH_CANDIDATE"),
  @JsonSubTypes.Type(value = MigrateCandidateMessage.class, name = "MIGRATE_CANDIDATE"),
  @JsonSubTypes.Type(value = RefreshPeriodMessage.class, name = "REFRESH_PERIOD")
})
public sealed interface BatchJobMessage extends JsonSerializable
    permits RefreshCandidateMessage, MigrateCandidateMessage, RefreshPeriodMessage {

  static BatchJobMessage fromJson(String json) throws JsonProcessingException {
    return dtoObjectMapper.readValue(json, BatchJobMessage.class);
  }
}
