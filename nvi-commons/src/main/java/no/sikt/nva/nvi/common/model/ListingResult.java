package no.sikt.nva.nvi.common.model;

import java.util.Map;

public record ListingResult(boolean shouldContinueScan,
                            Map<String, String> startMarker, int totalItem) {

}
