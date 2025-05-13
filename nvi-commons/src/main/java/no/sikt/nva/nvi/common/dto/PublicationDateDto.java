package no.sikt.nva.nvi.common.dto;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record PublicationDateDto(String year, String month, String day) {

  public PublicationDateDto {
    requireNonNull(year, "Required field 'year' is null");
  }
}
