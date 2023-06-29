package no.sikt.nva.nvi.common;

import java.time.Instant;

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

    public int getYear() {
        return year;
    }

    public static class Builder {

        private Instant start;
        private Instant end;
        private int year;

        public Builder() {
        }

        public Builder setStart(Instant start) {
            this.start = start;
            return this;
        }

        public Builder setEnd(Instant end) {
            this.end = end;
            return this;
        }

        public Builder setYear(int year) {
            this.year = year;
            return this;
        }

        public NviPeriod build() {
            return new NviPeriod(this);
        }
    }
}
