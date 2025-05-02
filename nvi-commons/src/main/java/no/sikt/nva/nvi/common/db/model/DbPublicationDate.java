package no.sikt.nva.nvi.common.db.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublicationDate.Builder.class)
public record DbPublicationDate(String year, String month, String day) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String builderYear;
    private String builderMonth;
    private String builderDay;

    private Builder() {}

    public Builder year(String year) {
      this.builderYear = year;
      return this;
    }

    public Builder month(String month) {
      this.builderMonth = month;
      return this;
    }

    public Builder day(String day) {
      this.builderDay = day;
      return this;
    }

    public DbPublicationDate build() {
      return new DbPublicationDate(builderYear, builderMonth, builderDay);
    }
  }
}
