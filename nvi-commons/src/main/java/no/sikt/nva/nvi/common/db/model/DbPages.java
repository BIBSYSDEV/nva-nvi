package no.sikt.nva.nvi.common.db.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = DbPages.Builder.class)
public record DbPages(String firstPage, String lastPage, String pageCount) {
  // FIXME: Rename pagecount to numberOfPages

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String builderFirstPage;
    private String builderLastPage;
    private String builderPageCount;

    private Builder() {}

    public Builder firstPage(String firstPage) {
      this.builderFirstPage = firstPage;
      return this;
    }

    public Builder lastPage(String lastPage) {
      this.builderLastPage = lastPage;
      return this;
    }

    public Builder pageCount(String pageCount) {
      this.builderPageCount = pageCount;
      return this;
    }

    public DbPages build() {
      return new DbPages(builderFirstPage, builderLastPage, builderPageCount);
    }
  }
}
