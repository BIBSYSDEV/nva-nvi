package no.sikt.nva.nvi.common.db;

import static no.sikt.nva.nvi.common.utils.ApplicationConstants.NVI_TABLE_NAME;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

@SuppressWarnings("PMD.DoNotUseThreads")
class DynamoDbRetryWrapperTest {

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
        when(dynamodb.batchWriteItem(any(BatchWriteItemRequest.class))).thenAnswer(a -> buildFailingResponse());

        var writeRequest = BatchWriteItemRequest.builder().build();

        assertThrows(RuntimeException.class, () -> dynamoDbRetryClient.batchWriteItem(writeRequest));
        verify(dynamodb, times(5)).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    @Test
    void firstFailAndThenSuccessShouldSucceed() {
        when(dynamodb.batchWriteItem(any(BatchWriteItemRequest.class)))
            .thenAnswer(a -> buildFailingResponse())
            .thenAnswer(a -> buildSuccessfulResponse());

        var writeRequest = buildMockWriteItemRequest();
        var result = dynamoDbRetryClient.batchWriteItem(writeRequest);

        verify(dynamodb, times(2)).batchWriteItem(any(BatchWriteItemRequest.class));
        assertThat(result, is(equalTo(1)));
    }

    @Test
    void successfulDbWriteShouldReturnNumberOfRequests() {
        when(dynamodb.batchWriteItem(any(BatchWriteItemRequest.class))).thenAnswer(a -> buildSuccessfulResponse());

        var writeRequest = buildMockWriteItemRequest();

        var result = dynamoDbRetryClient.batchWriteItem(writeRequest);

        verify(dynamodb, times(1)).batchWriteItem(any(BatchWriteItemRequest.class));
        assertThat(result, is(equalTo(1)));
    }

    @Test
    void sleepShouldRespectInterruption() throws InterruptedException {
        final var appender = LogUtils.getTestingAppender(DynamoDbRetryWrapper.class);
        var veryLongInitialRetryWaitTimeMs = 10_000;

        dynamoDbRetryClient = DynamoDbRetryWrapper.builder()
                                  .dynamoDbClient(dynamodb)
                                  .initialRetryWaitTimeMs(veryLongInitialRetryWaitTimeMs)
                                  .writeRetriesMaxCount(WRITE_RETRIES_MAX_COUNT)
                                  .tableName(NVI_TABLE_NAME)
                                  .build();

        when(dynamodb.batchWriteItem(any(BatchWriteItemRequest.class))).thenAnswer(
            a -> buildFailingResponse());

        var writeRequest = buildMockWriteItemRequest();

        var thread = new Thread(() -> dynamoDbRetryClient.batchWriteItem(writeRequest));
        thread.start();
        sleep(1000);
        thread.interrupt();
        thread.join();

        assertThat(appender.getMessages(), containsString("java.lang.InterruptedException: sleep interrupted"));
    }

    @Test
    void shouldLogFailedBatchItemsWhenOpenSearchErrorOccurs() {
        final var appender = LogUtils.getTestingAppender(DynamoDbRetryWrapper.class);
        var mockedDatabaseClient = mock(DynamoDbClient.class);
        var dynamoDbRetryWrapper = DynamoDbRetryWrapper.builder().dynamoDbClient(mockedDatabaseClient).build();
        var dao = new CandidateDao(UUID.randomUUID(), randomCandidate(), randomString(), randomString());
        var batchItemRequest = generateBatchItemRequest(dao);
        when(mockedDatabaseClient.batchWriteItem(eq(batchItemRequest))).thenThrow(DynamoDbException.class);
        assertThrows(DynamoDbException.class, () -> dynamoDbRetryWrapper.batchWriteItem(batchItemRequest));
        assertTrue(appender.getMessages().contains(dao.identifier().toString()));
    }

    private static BatchWriteItemRequest generateBatchItemRequest(CandidateDao dao) {
        return BatchWriteItemRequest.builder().requestItems(Map.of(randomString(),
                                                                   List.of(generateWriteRequest(dao)))).build();
    }

    private static WriteRequest generateWriteRequest(CandidateDao dao) {
        return WriteRequest.builder()
                   .putRequest(generatePutRequest(dao.toDynamoFormat()))
                   .build();
    }

    private static PutRequest generatePutRequest(Map<String, AttributeValue> item) {
        return PutRequest.builder()
                   .item(item)
                   .build();
    }

    private static BatchWriteItemRequest buildMockWriteItemRequest() {
        return BatchWriteItemRequest.builder().requestItems(mockWriteRequestItems()).build();
    }

    private static Map<String, List<WriteRequest>> mockWriteRequestItems() {
        return Map.of(TABLE_NAME, List.of(WriteRequest.builder().putRequest(PutRequest.builder().build()).build()));
    }

    private static BatchWriteItemResponse buildSuccessfulResponse() {
        return BatchWriteItemResponse.builder().build();
    }

    private static BatchWriteItemResponse buildFailingResponse() {
        return BatchWriteItemResponse.builder().unprocessedItems(mockWriteRequestItems()).build();
    }

    private static void sleep(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
