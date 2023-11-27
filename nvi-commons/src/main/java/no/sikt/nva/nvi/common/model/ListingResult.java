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

    public static final String MORE_ITEMS_TO_SCAN = "moreItemsToScan";
    public static final String START_MARKER = "startMarker";
    public static final String ITEM_COUNT = "totalItemCount";
    private final boolean moreItemsToScan;
    private final Map<String, String> startMarker;
    private final int totalItemCount;

    @JsonCreator
    public ListingResult(@JsonProperty(MORE_ITEMS_TO_SCAN) boolean moreItemsToScan,
                         @JsonProperty(START_MARKER) Map<String, String> startMarker,
                         @JsonProperty(ITEM_COUNT) int totalItemCount) {
        this.moreItemsToScan = moreItemsToScan;
        this.startMarker = startMarker;
        this.totalItemCount = totalItemCount;
    }

    public boolean shouldContinueScan() {
        return moreItemsToScan;
    }

    public Map<String, String> getStartMarker() {
        return startMarker;
    }

    public int getTotalItemCount() {
        return totalItemCount;
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(moreItemsToScan, startMarker, totalItemCount);
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
        return this.moreItemsToScan == that.moreItemsToScan
               && Objects.equals(this.startMarker, that.startMarker)
               && this.totalItemCount == that.totalItemCount;
    }
}
