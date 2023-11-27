package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

@JsonSerialize
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ListingResult {

    public static final String SHOULD_CONTINUE_SCAN = "shouldContinueScan";
    public static final String START_MARKER = "startMarker";
    public static final String TOTAL_ITEM = "totalItem";
    private final boolean shouldContinueScan;
    private final Map<String, String> startMarker;
    private final int totalItem;

    @JsonCreator
    public ListingResult(@JsonProperty(SHOULD_CONTINUE_SCAN) boolean shouldContinueScan,
                         @JsonProperty(START_MARKER) Map<String, String> startMarker,
                         @JsonProperty(TOTAL_ITEM) int totalItem) {
        this.shouldContinueScan = shouldContinueScan;
        this.startMarker = startMarker;
        this.totalItem = totalItem;
    }

    public boolean shouldContinueScan() {
        return shouldContinueScan;
    }

    public Map<String, String> startMarker() {
        return startMarker;
    }

    public int totalItem() {
        return totalItem;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(shouldContinueScan, startMarker, totalItem);
    }

    @Override
    @JacocoGenerated
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (ListingResult) obj;
        return this.shouldContinueScan == that.shouldContinueScan
               && Objects.equals(this.startMarker, that.startMarker)
               && this.totalItem == that.totalItem;
    }
}
