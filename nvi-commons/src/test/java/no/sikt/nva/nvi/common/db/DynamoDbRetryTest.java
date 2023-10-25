package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

class DynamoDbRetryTest {

    private static final int WRITE_RETRIES_MAX_COUNT = 5;
    private static final long INITIAL_RETRY_WAIT_TIME_MS = 10;
    private static final String TABLE_NAME = "some-table";
    private DynamoDbClient dynamodb;
    private DynamoDbRetryWrapper dynamoDbRetryClient;

    @BeforeEach
    public void setUp() {
        dynamodb = mock(DynamoDbClient.class);
        dynamoDbRetryClient = DynamoDbRetryWrapper.builder()
                                  .dynamoDbClient(dynamodb)
                                  .initialRetryWaitTimeMs(INITIAL_RETRY_WAIT_TIME_MS)
                                  .writeRetriesMaxCount(WRITE_RETRIES_MAX_COUNT)
                                  .tableName(NVI_TABLE_NAME)
                                  .build();
    }

    @Test
    void failingDbWriteShouldRetry() {
        when(dynamodb.batchWriteItem(any(BatchWriteItemRequest.class))).thenAnswer(
            a -> BatchWriteItemResponse.builder().unprocessedItems(mockWriteRequest()).build());

        var writeRequest = BatchWriteItemRequest.builder().build();

        assertThrows(RuntimeException.class, () -> dynamoDbRetryClient.batchWriteItem(writeRequest));
        verify(dynamodb, times(5)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    @Test
    void firstFailAndThenSuccessShouldSucceed() {
        when(dynamodb.batchWriteItem(any(BatchWriteItemRequest.class)))
            .thenAnswer(a -> BatchWriteItemResponse.builder().unprocessedItems(mockWriteRequest()).build())
            .thenAnswer(a -> BatchWriteItemResponse.builder().build());

        var writeRequest = BatchWriteItemRequest.builder().requestItems(mockWriteRequest()).build();
        var result = dynamoDbRetryClient.batchWriteItem(writeRequest);

        verify(dynamodb, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
        assertThat(result, is(equalTo(1)));
    }

    @Test
    void successfulDbWriteShouldReturnNumberOfRequests() {
        when(dynamodb.batchWriteItem(any(BatchWriteItemRequest.class))).thenAnswer(
            a -> BatchWriteItemResponse.builder().build());

        var writeRequest = BatchWriteItemRequest.builder().requestItems(mockWriteRequest()).build();

        var result = dynamoDbRetryClient.batchWriteItem(writeRequest);

        verify(dynamodb, times(1)).batchWriteItem(any(BatchWriteItemRequest.class));
        assertThat(result, is(equalTo(1)));
    }

    private static Map<String, List<WriteRequest>> mockWriteRequest() {
        return Map.of(TABLE_NAME, List.of(WriteRequest.builder().putRequest(PutRequest.builder().build()).build()));
    }
}
