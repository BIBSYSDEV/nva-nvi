package no.sikt.nva.nvi.common.model;

import no.sikt.nva.nvi.common.dto.PageCountDto;

public class PageCountFixtures {
  public static final PageCountDto PAGE_RANGE_AS_DTO = new PageCountDto("10", "20", null);

  public static final PageCountDto PAGE_NUMBER_AS_DTO = new PageCountDto(null, null, "42");
}
