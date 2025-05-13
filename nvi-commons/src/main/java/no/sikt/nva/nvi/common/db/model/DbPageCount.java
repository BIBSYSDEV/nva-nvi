package no.sikt.nva.nvi.common.db.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPageCount.Builder.class)
public record DbPageCount(String first, String last, String total) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String first;
    private String last;
    private String total;

    private Builder() {}

    public Builder first(String first) {
      this.first = first;
      return this;
    }

    public Builder last(String last) {
      this.last = last;
      return this;
    }

    public Builder total(String total) {
      this.total = total;
      return this;
    }

    public DbPageCount build() {
      return new DbPageCount(first, last, total);
    }
  }
}
