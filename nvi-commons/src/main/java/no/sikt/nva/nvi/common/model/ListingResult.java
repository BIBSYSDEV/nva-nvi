package no.sikt.nva.nvi.common.model;

import java.util.Map;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public class ListingResult {

    private final boolean shouldContinueScan;
    private final Map<String, String> startMarker;
    private final int totalItem;

    public ListingResult(boolean shouldContinueScan, Map<String, String> startMarker, int totalItem) {
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
        return this.shouldContinueScan == that.shouldContinueScan &&
               Objects.equals(this.startMarker, that.startMarker) &&
               this.totalItem == that.totalItem;
    }

    @Override
    public String toString() {
        return "ListingResult[" +
               "shouldContinueScan=" + shouldContinueScan + ", " +
               "startMarker=" + startMarker + ", " +
               "totalItem=" + totalItem + ']';
    }
}
