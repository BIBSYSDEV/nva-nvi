package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.db.dto.PeriodDb.Builder;

public class PeriodDb implements WithCopy<Builder> {

    public static final String CLOSED_FIELD = "closed";
    public static final String YEAR_FIELD = "year";
    @JsonProperty(CLOSED_FIELD)
    private Instant closed;
    @JsonProperty(YEAR_FIELD)
    private int year;

    public PeriodDb(Instant closed, int year) {
        this.closed = closed;
        this.year = year;
    }

    @Override
    public Builder copy() {
        return new Builder().withClosed(closed).withYear(year);
    }

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

        public PeriodDb build() {
            return new PeriodDb(closed, year);
        }
    }
}
