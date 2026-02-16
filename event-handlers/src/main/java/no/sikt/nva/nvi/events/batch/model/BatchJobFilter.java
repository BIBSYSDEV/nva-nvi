package no.sikt.nva.nvi.events.batch.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import no.unit.nva.commons.json.JsonSerializable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ReportingYearFilter.class, name = "ReportingYearFilter"),
})
public sealed interface BatchJobFilter extends JsonSerializable permits ReportingYearFilter {}
