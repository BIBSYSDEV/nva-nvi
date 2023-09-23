package no.sikt.nva.nvi.rest.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.PeriodStatus;
import nva.commons.core.JacocoGenerated;

public record PeriodStatusDto(Status status, String periodClosesAt) {

    public static Builder builder() {
        return new Builder();
    }

    public static PeriodStatusDto fromPeriodStatus(PeriodStatus periodStatus) {
        return builder().withStatus(Status.parse(periodStatus.status().getValue()))
                   .withPeriodClosesAt(toClosesAt(periodStatus))
                   .build();
    }

    private static String toClosesAt(PeriodStatus periodStatus) {
        return Optional.of(periodStatus)
                   .map(PeriodStatus::periodClosesAt)
                   .map(Object::toString)
                   .orElse(null);
    }

    public enum Status {
        OPEN_PERIOD("OpenPeriod"), CLOSED_PERIOD("ClosedPeriod"), NO_PERIOD("NoPeriod");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public static Status parse(String value) {
            return Arrays.stream(Status.values())
                       .filter(status -> status.getValue().equalsIgnoreCase(value))
                       .findFirst()
                       .orElseThrow();
        }

        @JacocoGenerated
        @JsonValue
        public String getValue() {
            return value;
        }
    }

    public static final class Builder {

        private Status status;
        private String periodClosesAt;

        private Builder() {
        }

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withPeriodClosesAt(String periodClosesAt) {
            this.periodClosesAt = periodClosesAt;
            return this;
        }

        public PeriodStatusDto build() {
            return new PeriodStatusDto(status, periodClosesAt);
        }
    }
}