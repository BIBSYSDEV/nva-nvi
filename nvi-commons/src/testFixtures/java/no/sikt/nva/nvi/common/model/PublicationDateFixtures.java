package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;

import no.sikt.nva.nvi.common.dto.PublicationDateDto;

public class PublicationDateFixtures {

  public static final PublicationDateDto getRandomDateInCurrentYearAsDto() {
    var month = String.valueOf(randomIntBetween(1, 12));
    var day = String.valueOf(randomIntBetween(1, 28));
    return new PublicationDateDto(String.valueOf(CURRENT_YEAR), month, day);
  }
}
