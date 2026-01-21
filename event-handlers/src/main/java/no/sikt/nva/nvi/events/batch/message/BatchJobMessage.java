package no.sikt.nva.nvi.events.batch.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import no.unit.nva.commons.json.JsonSerializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = RefreshCandidateMessage.class, name = "REFRESH_CANDIDATE"),
  @JsonSubTypes.Type(value = MigrateCandidateMessage.class, name = "MIGRATE_CANDIDATE"),
  @JsonSubTypes.Type(value = RefreshPeriodMessage.class, name = "REFRESH_PERIOD")
})
public sealed interface BatchJobMessage extends JsonSerializable
    permits RefreshCandidateMessage, MigrateCandidateMessage, RefreshPeriodMessage {}
