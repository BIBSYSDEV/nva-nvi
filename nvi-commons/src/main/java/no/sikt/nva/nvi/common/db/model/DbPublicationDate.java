package no.sikt.nva.nvi.common.db.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPublicationDate.Builder.class)
public record DbPublicationDate(String year, String month, String day) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String year;
    private String month;
    private String day;

    private Builder() {}

    public Builder year(String year) {
      this.year = year;
      return this;
    }

    public Builder month(String month) {
      this.month = month;
      return this;
    }

    public Builder day(String day) {
      this.day = day;
      return this;
    }

    public DbPublicationDate build() {
      return new DbPublicationDate(year, month, day);
    }
  }
}
