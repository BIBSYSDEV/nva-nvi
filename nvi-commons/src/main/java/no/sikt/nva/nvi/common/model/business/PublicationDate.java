package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import nva.commons.core.JacocoGenerated;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JacocoGenerated //Getters not in use yet
public record PublicationDate(String year, String month, String day) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String year;
        private String month;
        private String day;

        private Builder() {
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

        public PublicationDate build() {
            return new PublicationDate(year, month, day);
        }
    }
}
