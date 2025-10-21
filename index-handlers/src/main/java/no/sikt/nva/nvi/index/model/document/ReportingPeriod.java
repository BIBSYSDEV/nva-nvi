package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.NviPeriod;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record ReportingPeriod(String year) {

  @JsonIgnore
  public static ReportingPeriod fromCandidate(Candidate candidate) {
    return candidate
        .getPeriod()
        .map(NviPeriod::publishingYear)
        .map(String::valueOf)
        .map(ReportingPeriod::new)
        .orElseThrow();
  }
}
