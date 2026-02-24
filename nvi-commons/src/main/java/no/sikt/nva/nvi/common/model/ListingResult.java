package no.sikt.nva.nvi.common.model;

import static no.sikt.nva.nvi.common.utils.CollectionUtils.copyOfNullable;

import java.util.List;
import java.util.Map;

/**
 * A single page from a paginated DynamoDB scan or query.
 *
 * @param hasNextPage whether more pages are available after this one
 * @param lastEvaluatedKey the primary key of the last item evaluated, used as the exclusive start
 *     key for the next page, or null if this is the last page
 * @param itemCount the number of items in this page
 * @param items the items returned in this page
 * @param <T> the type of items in the result
 */
public record ListingResult<T>(
    boolean hasNextPage, Map<String, String> lastEvaluatedKey, int itemCount, List<T> items) {

  public ListingResult {
    items = copyOfNullable(items);
  }
}
