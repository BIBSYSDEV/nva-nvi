package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record NviPeriod(Instant start,
                        Instant end,
                        int year) {

    public static class Builder {

        private Instant start;
        private Instant end;
        private int year;

        public Builder() {
        }

        public Builder withStart(Instant start) {
            this.start = start;
            return this;
        }

        public Builder withEnd(Instant end) {
            this.end = end;
            return this;
        }

        public Builder withYear(int year) {
            this.year = year;
            return this;
        }

        public NviPeriod build() {
            return new NviPeriod(start, end, year);
        }
    }
}
