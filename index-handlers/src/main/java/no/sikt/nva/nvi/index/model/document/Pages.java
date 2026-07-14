package no.sikt.nva.nvi.index.model.document;

import static java.util.Objects.isNull;

import no.sikt.nva.nvi.common.service.model.PageCount;

public record Pages(String begin, String end, String numberOfPages) {

  public static Pages from(PageCount pageCount) {
    if (isNull(pageCount)) {
      return null;
    }
    return builder()
        .withBegin(pageCount.first())
        .withEnd(pageCount.last())
        .withNumberOfPages(pageCount.total())
        .build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String begin;
    private String end;
    private String numberOfPages;

    private Builder() {}

    public Builder withBegin(String begin) {
      this.begin = begin;
      return this;
    }

    public Builder withEnd(String end) {
      this.end = end;
      return this;
    }

    public Builder withNumberOfPages(String numberOfPages) {
      this.numberOfPages = numberOfPages;
      return this;
    }

    public Pages build() {
      return new Pages(begin, end, numberOfPages);
    }
  }
}
