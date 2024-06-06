package no.sikt.nva.nvi.index.model.search;

public record SearchResultParameters(int offset, int size, String orderBy, String sortOrder) {

    public static final int DEFAULT_SIZE = 10;
    public static final int DEFAULT_OFFSET = 0;
    public static final String DEFAULT_ORDER_BY_FIELD = "createDate";
    public static final String DEFAULT_SORT_ORDER = "desc";

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int offset = DEFAULT_OFFSET;
        private int size = DEFAULT_SIZE;
        private String orderBy = DEFAULT_ORDER_BY_FIELD;
        private String sortOrder = DEFAULT_SORT_ORDER;

        private Builder() {
        }

        public Builder withOffset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder withSize(int size) {
            this.size = size;
            return this;
        }

        public Builder withOrderBy(String orderBy) {
            this.orderBy = orderBy;
            return this;
        }

        public Builder withSortOrder(String sortOrder) {
            this.sortOrder = sortOrder;
            return this;
        }

        public SearchResultParameters build() {
            return new SearchResultParameters(offset, size, orderBy, sortOrder);
        }
    }
}
