package no.sikt.nva.nvi.common.db.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPages.Builder.class)
public record DbPages(String firstPage, String lastPage, String numberOfPages) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String builderFirstPage;
    private String builderLastPage;
    private String builderNumberOfPages;

    private Builder() {}

    public Builder firstPage(String firstPage) {
      this.builderFirstPage = firstPage;
      return this;
    }

    public Builder lastPage(String lastPage) {
      this.builderLastPage = lastPage;
      return this;
    }

    public Builder numberOfPages(String numberOfPages) {
      this.builderNumberOfPages = numberOfPages;
      return this;
    }

    public DbPages build() {
      return new DbPages(builderFirstPage, builderLastPage, builderNumberOfPages);
    }
  }
}
