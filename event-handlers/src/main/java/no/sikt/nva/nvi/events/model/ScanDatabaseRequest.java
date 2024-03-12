package no.sikt.nva.nvi.events.model;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.streamToString;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.model.KeyField;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.commons.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public record ScanDatabaseRequest(@JsonProperty(PAGE_SIZE_FIELD) int pageSize,
                                  @JsonProperty(START_MARKER_FIELD) Map<String, String> startMarker,
                                  @JsonProperty(TYPES) List<KeyField> types,
                                  @JsonProperty(TOPIC_FIELD) String topic) implements JsonSerializable {

    private static final Logger logger = LoggerFactory.getLogger(ScanDatabaseRequest.class);
    private static final String LOG_INITIALIZATION_MESSAGE_TEMPLATE =
        "Starting scanning with pageSize equal to: {}. Set 'pageSize' between [1,1000] "
        + "if you want a different pageSize value.";
    private static final String START_MARKER_FIELD = "startMarker";
    private static final String PAGE_SIZE_FIELD = "pageSize";
    private static final int DEFAULT_PAGE_SIZE = 700; // Choosing for safety 3/4 of max page size.
    private static final int MAX_PAGE_SIZE = 1000;
    private static final String TOPIC_FIELD = "topic";
    private static final String TYPES = "types";

    public static ScanDatabaseRequest fromJson(String detail) throws JsonProcessingException {
        return JsonUtils.dtoObjectMapper.readValue(detail, ScanDatabaseRequest.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int pageSize() {
        return pageSizeWithinLimits(pageSize)
                   ? pageSize
                   : DEFAULT_PAGE_SIZE;
    }

    @Override
    public List<KeyField> types() {
        return nonNull(types) ? types : emptyList();
    }

    public ScanDatabaseRequest newScanDatabaseRequest(Map<String, String> newStartMarker) {
        return new ScanDatabaseRequest(this.pageSize(), newStartMarker, this.types(), topic);
    }

    public PutEventsRequestEntry createNewEventEntry(
        String eventBusName,
        String detailType,
        String invokedFunctionArn
    ) {
        return PutEventsRequestEntry
                   .builder()
                   .eventBusName(eventBusName)
                   .detail(this.toJsonString())
                   .detailType(detailType)
                   .resources(invokedFunctionArn)
                   .time(Instant.now())
                   .source(invokedFunctionArn)
                   .build();
    }

    public void sendEvent(EventBridgeClient client, EventDetail details) {
        logger.info(LOG_INITIALIZATION_MESSAGE_TEMPLATE, this.pageSize());
        var event = this.createNewEventEntry(details.eventBus(), details.detail(), details.functionArn());
        var putEventsRequest = PutEventsRequest.builder().entries(event).build();
        var response = client.putEvents(putEventsRequest).toString();
        logger.info("Put event response: {}", response);
    }

    private boolean pageSizeWithinLimits(int pageSize) {
        return pageSize > 0 && pageSize <= MAX_PAGE_SIZE;
    }

    public static final class Builder {

        public static final ObjectMapper MAPPER = JsonUtils.dtoObjectMapper;
        private Map<String, String> startMarker;
        private int pageSize;
        private List<KeyField> types;

        private String topic;

        public Builder fromInputStream(InputStream inputStream) {
            var request = attempt(() -> MAPPER.readValue(streamToString(inputStream), ScanDatabaseRequest.class))
                              .orElseThrow();
            this.startMarker = request.startMarker();
            this.pageSize = request.pageSize();
            this.types = request.types();
            return this;
        }

        public Builder withTopic(String topic) {
            this.topic = topic;
            return this;
        }

        public ScanDatabaseRequest build() {
            return new ScanDatabaseRequest(pageSize, startMarker, types, topic);
        }
    }
}