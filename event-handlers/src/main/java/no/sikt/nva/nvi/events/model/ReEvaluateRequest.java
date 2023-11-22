package no.sikt.nva.nvi.events.model;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ReEvaluateRequest(@JsonProperty(PAGE_SIZE_FIELD) int pageSize,
                                @JsonProperty(START_MARKER_FIELD) Map<String, String> startMarker,
                                @JsonProperty(YEAR_FIELD) String year) {

    public static final String PAGE_SIZE_FIELD = "pageSize";
    public static final String START_MARKER_FIELD = "startMarker";
    public static final String YEAR_FIELD = "year";

    public static Builder builder() {
        return new Builder();
    }

    public String toJsonString() {
        return attempt(() -> dtoObjectMapper.writeValueAsString(this)).orElseThrow();
    }

    public static final class Builder {

        private int pageSize;
        private Map<String, String> startMarker;

        private String year;

        private Builder() {
        }

        public Builder withPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder withStartMarker(Map<String, String> startMarker) {
            this.startMarker = startMarker;
            return this;
        }

        public Builder withYear(String year) {
            this.year = year;
            return this;
        }

        public ReEvaluateRequest build() {
            return new ReEvaluateRequest(pageSize, startMarker, year);
        }
    }
}
