package no.sikt.nva.nvi.common.db;

import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

public class DynamoDbRetryWrapper {
    private final DynamoDbClient client;
    private final int writeRetriesMaxCount;
    private final long initialRetryWaitTimeMs;
    private final String tableName;

    public DynamoDbRetryWrapper(DynamoDbClient dynamoDbClient, String tableName, int writeRetriesMaxCount,
                                long initialRetryWaitTimeMs) {
        this.client = dynamoDbClient;
        this.writeRetriesMaxCount = writeRetriesMaxCount;
        this.initialRetryWaitTimeMs = initialRetryWaitTimeMs;
        this.tableName = tableName;
    }

    public int batchWriteItem(BatchWriteItemRequest initialRequest) {

        var batchWriteRequest = initialRequest;
        var retryNeeded = true;
        var requestCount = 0;

        while (retryNeeded) {
            if (requestCount++ >= writeRetriesMaxCount) {
                throw new RuntimeException("Some items were not processed and retries exhausted");
            }

            var response = client.batchWriteItem(batchWriteRequest);

            if (hasUnprocessedItems(response)) {
                sleep(calculateBackoffTime(requestCount));

                batchWriteRequest = buildBatchWriteRequest(getUnprocessedItems(response));
            } else {
                retryNeeded = false;
            }
        }

        return initialRequest.requestItems().size();
    }

    private static boolean hasUnprocessedItems(BatchWriteItemResponse response) {
        return response.hasUnprocessedItems() && !response.unprocessedItems().isEmpty();
    }

    private long calculateBackoffTime(int requestCount) {
        return initialRetryWaitTimeMs * (long) Math.pow(2, requestCount - 1d);
    }

    private static void sleep(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<WriteRequest> getUnprocessedItems(BatchWriteItemResponse initialRequest) {
        return initialRequest.unprocessedItems().values().stream()
                   .flatMap(List::stream).toList();
    }

    private BatchWriteItemRequest buildBatchWriteRequest(List<WriteRequest> unprocessedItems) {
        return BatchWriteItemRequest.builder()
                   .requestItems(Map.of(this.tableName, unprocessedItems))
                   .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private static final int WRITE_RETRIES_MAX_COUNT_DEFAULT = 5;
        private static final long INITIAL_RETRY_WAIT_TIME_MS_DEFAULT = 500L;
        private DynamoDbClient builderDynamoDbClient;
        private int builderWriteRetriesMaxCount = WRITE_RETRIES_MAX_COUNT_DEFAULT;
        private long builderInitialRetryWaitTimeMs = INITIAL_RETRY_WAIT_TIME_MS_DEFAULT;
        private String builderTableName;

        private Builder() {
        }

        public DynamoDbRetryWrapper.Builder dynamoDbClient(DynamoDbClient dynamoDbClient) {
            this.builderDynamoDbClient = dynamoDbClient;
            return this;
        }

        public DynamoDbRetryWrapper.Builder writeRetriesMaxCount(int writeRetriesMaxCount) {
            this.builderWriteRetriesMaxCount = writeRetriesMaxCount;
            return this;
        }

        public DynamoDbRetryWrapper.Builder initialRetryWaitTimeMs(long initialRetryWaitTimeMs) {
            this.builderInitialRetryWaitTimeMs = initialRetryWaitTimeMs;
            return this;
        }

        public DynamoDbRetryWrapper.Builder tableName(String tableName) {
            this.builderTableName = tableName;
            return this;
        }

        public DynamoDbRetryWrapper build() {
            return new DynamoDbRetryWrapper(builderDynamoDbClient, builderTableName, builderWriteRetriesMaxCount,
                                            builderInitialRetryWaitTimeMs);
        }
    }
}
