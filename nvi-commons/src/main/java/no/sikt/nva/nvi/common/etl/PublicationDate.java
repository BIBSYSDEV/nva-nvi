package no.sikt.nva.nvi.common.etl;

import static java.util.Objects.requireNonNull;

public record PublicationDate(String year, String month, String day) {

  public PublicationDate {
    requireNonNull(year, "Required field 'year' is null");
  }
}
