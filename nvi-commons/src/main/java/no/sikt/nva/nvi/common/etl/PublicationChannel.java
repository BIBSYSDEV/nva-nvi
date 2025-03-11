package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record PublicationChannel(
    URI id,
    String channelType,
    UUID identifier,
    String name,
    String scientificLevel,
    String onlineIssn,
    String printIssn) {

  public PublicationChannel {
    requireNonNull(id, "Required field 'id' is null");
    requireNonNull(channelType, "Required field 'channelType' is null");
    requireNonNull(scientificLevel, "Required field 'scientificLevel' is null");
  }
}
