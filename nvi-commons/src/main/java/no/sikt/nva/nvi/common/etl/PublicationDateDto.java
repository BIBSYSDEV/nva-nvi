package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("PublicationDate")
public record PublicationDateDto(String year, String month, String day) {

  public PublicationDateDto {
    requireNonNull(year, "Required field 'year' is null");
  }
}
