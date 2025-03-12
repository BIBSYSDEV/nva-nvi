package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record PublicationChannelTemp(
    URI id,
    String channelType,
    String identifier,
    String name,
    String year,
    String scientificValue,
    String onlineIssn,
    String printIssn,
    String sameAs) {

  public PublicationChannelTemp {
    requireNonNull(id, "Required field 'id' is null");
        requireNonNull(channelType, "Required field 'channelType' is null");
        requireNonNull(scientificValue, "Required field 'scientificLevel' is null");
  }

  public void validate() {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(channelType, "Required field 'channelType' is null");
    requireNonNull(scientificValue, "Required field 'scientificLevel' is null");
  }
}
