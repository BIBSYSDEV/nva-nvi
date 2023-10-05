package no.sikt.nva.nvi.common.model;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record ListingResult(boolean shouldContinueScan,
                            Map<String, AttributeValue> startMarker, int totalItem, int unprocessedItemsForTable) {

}
