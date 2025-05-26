package no.sikt.nva.nvi.common.service.model;

import static java.util.Objects.isNull;

import no.sikt.nva.nvi.common.db.model.DbPageCount;
import no.sikt.nva.nvi.common.dto.PageCountDto;

public record PageCount(String first, String last, String total) {

  public static PageCount from(PageCountDto pageCount) {
    if (isNull(pageCount)) {
      return new PageCount(null, null, null);
    }
    return new PageCount(pageCount.first(), pageCount.last(), pageCount.total());
  }

  public static PageCount from(DbPageCount pageCount) {
    if (isNull(pageCount)) {
      return new PageCount(null, null, null);
    }
    return new PageCount(pageCount.first(), pageCount.last(), pageCount.total());
  }

  public DbPageCount toDbPageCount() {
    return DbPageCount.builder().first(first).last(last).total(total).build();
  }
}
