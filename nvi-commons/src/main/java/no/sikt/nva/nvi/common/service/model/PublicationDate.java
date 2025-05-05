package no.sikt.nva.nvi.common.service.model;

import no.sikt.nva.nvi.common.db.model.DbPublicationDate;
import no.sikt.nva.nvi.common.dto.PublicationDateDto;

public record PublicationDate(String year, String month, String day) {

  public static PublicationDate from(PublicationDateDto dtoDate) {
    return new PublicationDate(dtoDate.year(), dtoDate.month(), dtoDate.day());
  }

  public static PublicationDate from(DbPublicationDate dbDate) {
    return new PublicationDate(dbDate.year(), dbDate.month(), dbDate.day());
  }

  public PublicationDateDto toDtoPublicationDate() {
    return new PublicationDateDto(year, month, day);
  }

  public DbPublicationDate toDbPublicationDate() {
    return DbPublicationDate.builder().year(year).month(month).day(day).build();
  }
}
