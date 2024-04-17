package no.sikt.nva.nvi.index.model.search;

public record SearchResultParameters(int offset, int size) {

    public static final int DEFAULT_SIZE = 10;
    public static final int DEFAULT_OFFSET = 0;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private int offset = DEFAULT_OFFSET;
        private int size = DEFAULT_SIZE;

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

        public SearchResultParameters build() {
            return new SearchResultParameters(offset, size);
        }
    }
}
