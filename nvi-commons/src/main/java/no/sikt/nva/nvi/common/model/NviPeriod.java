package no.sikt.nva.nvi.common.model;

import java.time.Instant;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public class NviPeriod {

    private final Instant start;
    private final Instant end;
    private final int year;

    public NviPeriod(Builder builder) {
        this.start = builder.start;
        this.end = builder.end;
        this.year = builder.year;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NviPeriod nviPeriod = (NviPeriod) o;
        return getYear() == nviPeriod.getYear()
               && Objects.equals(getStart(), nviPeriod.getStart())
               && Objects.equals(getEnd(), nviPeriod.getEnd());
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getStart(), getEnd(), getYear());
    }

    public int getYear() {
        return year;
    }

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
            return new NviPeriod(this);
        }
    }
}
