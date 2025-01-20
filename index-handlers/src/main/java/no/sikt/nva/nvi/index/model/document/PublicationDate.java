package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record PublicationDate(String year, String month, String day) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String year;
    private String month;
    private String day;

    private Builder() {}

    public Builder withYear(String year) {
      this.year = year;
      return this;
    }

    public Builder withMonth(String month) {
      this.month = month;
      return this;
    }

    public Builder withDay(String day) {
      this.day = day;
      return this;
    }

    public PublicationDate build() {
      return new PublicationDate(year, month, day);
    }
  }
}
