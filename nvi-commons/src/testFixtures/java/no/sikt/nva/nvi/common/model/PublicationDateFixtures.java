package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomIntBetween;

import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.service.model.PublicationDate;

public class PublicationDateFixtures {

  public static final PublicationDate getRandomDateInCurrentYear() {
    var month = String.valueOf(randomIntBetween(1, 12));
    var day = String.valueOf(randomIntBetween(1, 28));
    return new PublicationDate(String.valueOf(CURRENT_YEAR), month, day);
  }

  public static final PublicationDateDto getRandomDateInCurrentYearAsDto() {
    return getRandomDateInCurrentYear().toDtoPublicationDate();
  }

  public static final DbPublicationDate getRandomDateInCurrentYearAsDbDate() {
    return getRandomDateInCurrentYear().toDbPublicationDate();
  }
}
