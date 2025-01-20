package no.sikt.nva.nvi.events.evaluator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;

public record AffiliationDto(URI id, String countryCode) {

  public record ExpandedAffiliationDto(
      @JsonProperty("id") URI id, @JsonProperty("countryCode") String countryCode) {

    public AffiliationDto toDto() {
      return new AffiliationDto(id, countryCode);
    }
  }
}
