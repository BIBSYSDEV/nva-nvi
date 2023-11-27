package no.sikt.nva.nvi.events.model;

import static java.util.Objects.nonNull;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public record ReEvaluateRequest(@JsonProperty(PAGE_SIZE_FIELD) Integer pageSize,
                                @JsonProperty(START_MARKER_FIELD) Map<String, String> startMarker,
                                @JsonProperty(YEAR_FIELD) String year,
                                @JsonProperty(TOPIC_FIELD) String topic) {

    public static final String PAGE_SIZE_FIELD = "pageSize";
    public static final String START_MARKER_FIELD = "startMarker";
    public static final String YEAR_FIELD = "year";
    public static final String TOPIC_FIELD = "topic";
    public static final int DEFAULT_PAGE_SIZE = 500;
    public static final int MAX_PAGE_SIZE = 1000;

    public static Builder builder() {
        return new Builder();
    }

    public static ReEvaluateRequest fromJson(String detail) {
        return attempt(() -> dtoObjectMapper.readValue(detail, ReEvaluateRequest.class)).orElseThrow();
    }

    @Override
    public Integer pageSize() {
        return pageSizeWithinLimits(pageSize)
                   ? pageSize
                   : DEFAULT_PAGE_SIZE;
    }

    public ReEvaluateRequest newReEvaluateRequest(Map<String, String> stringStringMap, String outputTopic) {
        return new ReEvaluateRequest(this.pageSize(), stringStringMap, year, outputTopic);
    }

    public PutEventsRequestEntry createNewEventEntry(
        String eventBusName,
        String invokedFunctionArn
    ) {
        return PutEventsRequestEntry
                   .builder()
                   .eventBusName(eventBusName)
                   .detail(this.toJsonString())
                   .resources(invokedFunctionArn)
                   .time(Instant.now())
                   .source(invokedFunctionArn)
                   .build();
    }

    private String toJsonString() {
        return attempt(() -> dtoObjectMapper.writeValueAsString(this)).orElseThrow();
    }

    private boolean pageSizeWithinLimits(Integer pageSize) {
        return nonNull(pageSize) && pageSize > 0 && pageSize <= MAX_PAGE_SIZE;
    }

    public static final class Builder {

        private Integer pageSize;
        private Map<String, String> startMarker;
        private String year;
        private String topic;

        private Builder() {
        }

        public Builder withYear(String year) {
            this.year = year;
            return this;
        }

        public Builder withPageSize(Integer pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public ReEvaluateRequest build() {
            return new ReEvaluateRequest(pageSize, startMarker, year, topic);
        }
    }
}
