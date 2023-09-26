package no.sikt.nva.nvi.common.db;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;
import java.util.Objects;
import no.sikt.nva.nvi.common.db.model.DbNviPeriod;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.core.JacocoGenerated;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record PeriodStatus(Instant periodClosesAt, Status status) implements JsonSerializable {

    public static Builder builder() {
        return new Builder();
    }

    public static PeriodStatus fromPeriod(DbNviPeriod period) {
        Objects.requireNonNull(period);
        return period.reportingDate().isAfter(Instant.now())
                   ? toOpenPeriodStatus(period)
                   : toClosedPeriodStatus(period);
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return toJsonString();
    }

    private static PeriodStatus toOpenPeriodStatus(DbNviPeriod period) {
        return PeriodStatus.builder().withPeriodClosesAt(period.reportingDate()).withStatus(Status.OPEN_PERIOD).build();
    }

    private static PeriodStatus toClosedPeriodStatus(DbNviPeriod period) {
        return PeriodStatus.builder()
                   .withPeriodClosesAt(period.reportingDate())
                   .withStatus(Status.CLOSED_PERIOD)
                   .build();
    }

    public enum Status {
        OPEN_PERIOD("OpenPeriod"), CLOSED_PERIOD("ClosedPeriod"), NO_PERIOD("NoPeriod");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        @JacocoGenerated
        @JsonValue
        public String getValue() {
            return value;
        }
    }

    public static final class Builder {

        private Instant periodClosesAt;
        private Status status;

        private Builder() {
        }

        public Builder withPeriodClosesAt(Instant periodClosesAt) {
            this.periodClosesAt = periodClosesAt;
            return this;
        }

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public PeriodStatus build() {
            return new PeriodStatus(periodClosesAt, status);
        }
    }
}