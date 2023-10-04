package no.sikt.nva.nvi.common.model;

import java.util.Map;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JacocoGenerated
 public record ListingResult(boolean shouldContinueScan,
                               Map<String, AttributeValue> startMarker, int totalItem, int unprocessedItemsForTable) {
}
