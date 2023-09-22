package no.sikt.nva.nvi.common.model;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record ListingResult<T>(boolean shouldContinueScan,
                               Map<String, AttributeValue> startMarker, int totalItem, int unprocessedPutItemsForTable,
                               int unprocessedDeleteItemsForTable) {

/*
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    public static final class Builder<T> {

        private List<T> databaseEntries;
        private Map<String, AttributeValue> startMarker;

        private Builder() {
        }

        public Builder<T> withDatabaseEntries(List<T> databaseEntries) {
            this.databaseEntries = databaseEntries;
            return this;
        }

        public Builder<T> withStartMarker(Map<String, AttributeValue> startMarker) {
            this.startMarker = startMarker;
            return this;
        }

        public ListingResult<T> build() {
            return new ListingResult<>(databaseEntries, startMarker);
        }
    }

 */
}
