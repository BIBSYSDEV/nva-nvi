package no.sikt.nva.nvi.index.apigateway.utils;

import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.GlobalAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;

public class AggregateResponseTestUtil {

    public static Aggregate organizationApprovalStatusAggregate(String topLevelOrg) {
        var pendingBucket = getStringTermsBucket("Pending", Map.of(), 2);
        var statusBuckets = createBuckets(pendingBucket);
        var statusAggregation = createTermsAggregateWithBuckets(statusBuckets);
        var orgBucket = getStringTermsBucket("someOrgId", Map.of("status", statusAggregation), 3);
        var orgBuckets = createBuckets(orgBucket);
        var orgAggregation = createTermsAggregateWithBuckets(orgBuckets);
        var filterAggregate = getFilterAggregate(3, Map.of("organizations", orgAggregation));
        return new NestedAggregate.Builder().docCount(randomInteger())
                   .aggregations(topLevelOrg, filterAggregate)
                   .docCount(3)
                   .build()._toAggregate();
    }

    public static Aggregate getGlobalAggregate() {
        return new GlobalAggregate.Builder().docCount(1).build()._toAggregate();
    }

    public static Aggregate filterAggregate(Integer docCount) {
        return getFilterAggregate(docCount, Map.of());
    }

    private static Aggregate getFilterAggregate(int docCount, Map<String, Aggregate> aggregations) {
        return new FilterAggregate.Builder().docCount(docCount)
                   .aggregations(aggregations)
                   .build()._toAggregate();
    }

    private static Aggregate createTermsAggregateWithBuckets(Buckets<StringTermsBucket> buckets) {
        return new StringTermsAggregate.Builder().buckets(buckets).sumOtherDocCount(2).build()._toAggregate();
    }

    private static StringTermsBucket getStringTermsBucket(String key, Map<String, Aggregate> aggregateMap,
                                                          int docCount) {
        return new StringTermsBucket.Builder()
                   .key(key)
                   .aggregations(aggregateMap)
                   .docCount(docCount)
                   .build();
    }

    private static Buckets<StringTermsBucket> createBuckets(StringTermsBucket bucket) {
        return new Buckets.Builder<StringTermsBucket>().array(List.of(bucket)).build();
    }
}
