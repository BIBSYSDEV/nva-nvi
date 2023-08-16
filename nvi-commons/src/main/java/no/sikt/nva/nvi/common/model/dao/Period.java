package no.sikt.nva.nvi.common.model.dao;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Period(Instant closed,
                     int year) {

    public static final class Builder {

        private Instant closed;
        private int year;

        public Builder() {
        }

        public Builder withClosed(Instant closed) {
            this.closed = closed;
            return this;
        }

        public Builder withYear(int year) {
            this.year = year;
            return this;
        }

        public Period build() {
            return new Period(closed, year);
        }
    }
}
