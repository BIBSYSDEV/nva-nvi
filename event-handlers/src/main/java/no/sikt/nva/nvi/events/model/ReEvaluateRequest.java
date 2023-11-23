package no.sikt.nva.nvi.events.model;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ReEvaluateRequest(@JsonProperty(PAGE_SIZE_FIELD) Integer pageSize,
                                @JsonProperty(START_MARKER_FIELD) Map<String, String> startMarker,
                                @JsonProperty(YEAR_FIELD) String year) {

    public static final String PAGE_SIZE_FIELD = "pageSize";
    public static final String START_MARKER_FIELD = "startMarker";
    public static final String YEAR_FIELD = "year";
    public static final int DEFAULT_PAGE_SIZE = 500;
    public static final int MAX_PAGE_SIZE = 1000;

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Integer pageSize() {
        return pageSizeWithinLimits(pageSize)
                   ? pageSize
                   : DEFAULT_PAGE_SIZE;
    }

    private boolean pageSizeWithinLimits(Integer pageSize) {
        return nonNull(pageSize) && pageSize > 0 && pageSize <= MAX_PAGE_SIZE;
    }

    public static final class Builder {

        private Integer pageSize;
        private Map<String, String> startMarker;
        private String year;

        private Builder() {
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
