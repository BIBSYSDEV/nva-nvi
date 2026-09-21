package no.sikt.nva.nvi.events.batch.message;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.unit.nva.commons.json.JsonSerializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CandidateJobMessage.class, name = "CANDIDATE_JOB"),
  @JsonSubTypes.Type(value = RefreshPeriodMessage.class, name = "REFRESH_PERIOD")
})
public sealed interface BatchJobMessage extends JsonSerializable
    permits CandidateJobMessage, RefreshPeriodMessage {

  static BatchJobMessage fromJson(String json) throws JsonProcessingException {
    return dtoObjectMapper.readValue(json, BatchJobMessage.class);
  }
}
