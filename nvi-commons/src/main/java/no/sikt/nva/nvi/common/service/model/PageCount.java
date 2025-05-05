package no.sikt.nva.nvi.common.service.model;

import no.sikt.nva.nvi.common.db.model.DbPages;
import no.sikt.nva.nvi.common.dto.PageCountDto;

public record PageCount(String firstPage, String lastPage, String numberOfPages) {

  public static PageCount from(PageCountDto dtoPages) {
    return new PageCount(dtoPages.firstPage(), dtoPages.lastPage(), dtoPages.numberOfPages());
  }

  public static PageCount from(DbPages dbPages) {
    return new PageCount(dbPages.firstPage(), dbPages.lastPage(), dbPages.numberOfPages());
  }

  public PageCountDto toDto() {
    return new PageCountDto(firstPage, lastPage, numberOfPages);
  }

  public DbPages toDbPages() {
    return DbPages.builder()
        .firstPage(firstPage)
        .lastPage(lastPage)
        .numberOfPages(numberOfPages)
        .build();
  }
}
