package no.sikt.nva.nvi.common.model;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonSerialize
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ListingResult<T> {

    public static final String MORE_ITEMS_TO_SCAN = "moreItemsToScan";
    public static final String START_MARKER = "startMarker";
    public static final String ITEM_COUNT = "totalItemCount";
    private final boolean moreItemsToScan;
    private final Map<String, String> startMarker;
    private final int totalItemCount;
    private final List<T> databaseEntries;

    @JsonCreator
    public ListingResult(@JsonProperty(MORE_ITEMS_TO_SCAN) boolean moreItemsToScan,
                         @JsonProperty(START_MARKER) Map<String, String> startMarker,
                         @JsonProperty(ITEM_COUNT) int totalItemCount,
                         List<T> databaseEntries) {
        this.moreItemsToScan = moreItemsToScan;
        this.startMarker = startMarker;
        this.totalItemCount = totalItemCount;
        this.databaseEntries = databaseEntries;
    }

    public boolean shouldContinueScan() {
        return moreItemsToScan;
    }

    public List<T> getDatabaseEntries() {
        return nonNull(databaseEntries) ? databaseEntries : Collections.emptyList();
    }

    public Map<String, String> getStartMarker() {
        return startMarker;
    }

    public int getTotalItemCount() {
        return totalItemCount;
    }
}
