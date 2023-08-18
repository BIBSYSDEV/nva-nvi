package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Period(int year, Instant start, Instant end) {

    public static final class Builder {

        private int year;
        private Instant start;
        private Instant end;

        public Builder() {
        }

        public Builder withYear(int year) {
            this.year = year;
            return this;
        }

        public Builder withStart(Instant start) {
            this.start = start;
            return this;
        }

        public Builder withEnd(Instant end) {
            this.end = end;
            return this;
        }

        public Period build() {
            return new Period(year, start, end);
        }
    }
}
