package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;

import no.sikt.nva.nvi.common.dto.PublicationDateDto;

public class PublicationDateFixtures {
  public static final PublicationDateDto CURRENT_YEAR_AS_PUBLICATION_DATE_DTO =
      new PublicationDateDto(String.valueOf(CURRENT_YEAR), "01", "01");
}
