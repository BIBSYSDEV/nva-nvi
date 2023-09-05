package no.sikt.nva.nvi.common.model.business;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbImmutable(builder = PublicationDate.Builder.class)
public record PublicationDate(String year, String month, String day) {

    private PublicationDate(Builder b) {
        this(b.year, b.month, b.day);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String year;
        private String month;
        private String day;

        private Builder() {
        }

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

        public PublicationDate build() {
            return new PublicationDate(year, month, day);
        }
    }
}
