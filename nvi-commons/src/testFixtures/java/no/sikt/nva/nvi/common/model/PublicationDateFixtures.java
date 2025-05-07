package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;

import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;
import no.sikt.nva.nvi.common.service.model.PublicationDate;

public class PublicationDateFixtures {

  public static PublicationDate randomPublicationDateInCurrentYear() {
    var randomDate = randomLocalDate();
    return new PublicationDate(
        String.valueOf(CURRENT_YEAR),
        String.valueOf(randomDate.getMonthValue()),
        String.valueOf(randomDate.getDayOfMonth()));
  }

  public static PublicationDate randomPublicationDate() {
    var randomDate = randomLocalDate();
    return new PublicationDate(
        String.valueOf(randomDate.getYear()),
        String.valueOf(randomDate.getMonthValue()),
        String.valueOf(randomDate.getDayOfMonth()));
  }

  public static PublicationDateDto getRandomDateInCurrentYearAsDto() {
    return randomPublicationDateInCurrentYear().toDtoPublicationDate();
  }

  public static DbPublicationDate mapToDbPublicationDate(PublicationDateDto publicationDate) {
    return new DbPublicationDate(
        publicationDate.year(), publicationDate.month(), publicationDate.day());
  }
}
