package no.sikt.nva.nvi.test;

import static java.util.Objects.nonNull;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record ExpandedPublicationDate(String year, String month, String day) {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(ExpandedPublicationDate other) {
        return new Builder(other);
    }

    public ObjectNode asObjectNode() {
        var publicationDateNode = objectMapper.createObjectNode();
        publicationDateNode.put("type", "PublicationDate");
        publicationDateNode.put("year", year);
        if (nonNull(month)) {
            publicationDateNode.put("month", month);
        }
        if (nonNull(day)) {
            publicationDateNode.put("day", day);
        }
        return publicationDateNode;
    }

    public static final class Builder {
        private String year;
        private String month;
        private String day;

        private Builder() {}

        private Builder(ExpandedPublicationDate other) {
            this.year = other.year();
            this.month = other.month();
            this.day = other.day();
        }

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

        public ExpandedPublicationDate build() {
            return new ExpandedPublicationDate(year, month, day);
        }
    }
}
