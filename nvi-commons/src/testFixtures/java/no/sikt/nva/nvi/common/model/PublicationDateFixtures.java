package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;

import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;

public class PublicationDateFixtures {
  public static PublicationDate randomPublicationDateInYear(int year) {
    return randomPublicationDateInYear(String.valueOf(year));
  }

  public static PublicationDate randomPublicationDateInYear(String year) {
    var randomDate = randomLocalDate();
    return new PublicationDate(
        String.valueOf(year),
        String.valueOf(randomDate.getMonthValue()),
        String.valueOf(randomDate.getDayOfMonth()));
  }

  public static PublicationDate randomPublicationDateInCurrentYear() {
    return randomPublicationDateInYear(CURRENT_YEAR);
  }

  public static PublicationDate randomPublicationDate() {
    var randomDate = randomLocalDate();
    return new PublicationDate(
        String.valueOf(randomDate.getYear()),
        String.valueOf(randomDate.getMonthValue()),
        String.valueOf(randomDate.getDayOfMonth()));
  }

  public static PublicationDateDto randomPublicationDateDtoInYear(int year) {
    return randomPublicationDateInYear(String.valueOf(year)).toDtoPublicationDate();
  }

  public static PublicationDateDto randomPublicationDateDtoInYear(String year) {
    return randomPublicationDateInYear(year).toDtoPublicationDate();
  }

  public static PublicationDateDto getRandomDateInCurrentYearAsDto() {
    return randomPublicationDateInCurrentYear().toDtoPublicationDate();
  }

  public static DbPublicationDate mapToDbPublicationDate(PublicationDateDto publicationDate) {
    return new DbPublicationDate(
        publicationDate.year(), publicationDate.month(), publicationDate.day());
  }
}
