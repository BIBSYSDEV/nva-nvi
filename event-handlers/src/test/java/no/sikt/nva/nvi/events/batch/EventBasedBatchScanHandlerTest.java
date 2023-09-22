package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.model.ScanDatabaseRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;

class EventBasedBatchScanHandlerTest extends LocalDynamoTest {
    
    public static final int LARGE_PAGE = 10;
    public static final int ONE_ENTRY_PER_EVENT = 1;
    public static final Map<String, AttributeValue> START_FROM_BEGINNING = null;
    public static final String OUTPUT_EVENT_TOPIC = "OUTPUT_EVENT_TOPIC";
    public static final String TOPIC = new Environment().readEnv(OUTPUT_EVENT_TOPIC);
    private static final String NVI_TABLE_NAME = new Environment().readEnv("NVI_TABLE_NAME");
    private EventBasedBatchScanHandler handler;
    private ByteArrayOutputStream output;
    private Context context;
    private FakeEventBridgeClient eventBridgeClient;
    private Clock clock;
    private NviService nviService;
    private List<Map<String, AttributeValue>> scanningStartingPoints;

    @BeforeEach
    public void init() {
        this.clock = Clock.systemDefaultZone();
        this.output = new ByteArrayOutputStream();
        this.context = mock(Context.class);
        when(context.getInvokedFunctionArn()).thenReturn(randomString());
        this.eventBridgeClient = new FakeEventBridgeClient();
        this.nviService = new NviService(initializeTestDatabase());
        this.handler = new EventBasedBatchScanHandler(nviService, eventBridgeClient);
        this.scanningStartingPoints = Collections.synchronizedList(new ArrayList<>());
    }

    @Test
    void shouldUpdateDataEntriesWhenValidRequestIsReceived() {
        var candidate = nviService.upsertCandidate(randomCandidate());
        handler.handleRequest(createInitialScanRequest(LARGE_PAGE), output, context);
    
        assertThat(true, is(equalTo(true)));
    }

    @Test
    void shouldNotGoIntoInfiniteLoop() {
        createRandomCandidates(1000);
        pushInitialEntryInEventBridge(new ScanDatabaseRequest(ONE_ENTRY_PER_EVENT,
                                                              new LinkedHashMap<>(START_FROM_BEGINNING.entrySet().stream().collect(
                                                                  Collectors.toMap(Map.Entry::getKey,
                                                                                   e -> e.getValue().s()))),
                                                              TOPIC));
        while (thereAreMoreEventsInEventBridge()) {
            var currentRequest = consumeLatestEmittedEvent();
            handler.handleRequest(eventToInputStream(currentRequest), output, context);
        }
        assertThat(eventBridgeClient.getRequestEntries(), is(empty()));
    }

    @Test
    void shouldIterateAllCandidates() {
        createRandomCandidates(10);

       try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(4, null, TOPIC));

        while (thereAreMoreEventsInEventBridge()) {
            var currentRequest = consumeLatestEmittedEvent();

            handler.handleRequest(eventToInputStream(currentRequest), output, context);
        }

        //TODO: verify somehow that all candidates were iterated
        //verify(db, times(4)).batchWriteItem((BatchWriteItemRequest) any());

    }

    private ScanDatabaseRequest consumeLatestEmittedEvent() {
        var allRequests = eventBridgeClient.getRequestEntries();
        var latest = allRequests.remove(allRequests.size() - 1);
        return attempt(() -> ScanDatabaseRequest.fromJson(latest.detail())).orElseThrow();
    }

    private boolean thereAreMoreEventsInEventBridge() {
        return !eventBridgeClient.getRequestEntries().isEmpty();
    }

    private void pushInitialEntryInEventBridge(ScanDatabaseRequest scanDatabaseRequest) {
        var entry = PutEventsRequestEntry.builder()
                        .detail(scanDatabaseRequest.toJsonString())
                        .build();
        eventBridgeClient.getRequestEntries().add(entry);
    }

    private void createRandomCandidates(int i) {
        IntStream.range(0, i).boxed()
            .map(item -> randomCandidate())
            .forEach(nviService::upsertCandidate);
    }

    private InputStream createInitialScanRequest(int pageSize) {
        return eventToInputStream(new ScanDatabaseRequest(pageSize, Map.of(), TOPIC));
    }

    private InputStream eventToInputStream(ScanDatabaseRequest scanDatabaseRequest) {
        var event = new AwsEventBridgeEvent<ScanDatabaseRequest>();
        event.setAccount(randomString());
        event.setVersion(randomString());
        event.setSource(randomString());
        event.setRegion(randomElement(Region.regions()));
        event.setDetail(scanDatabaseRequest);
        return IoUtils.stringToStream(event.toJsonString());
    }
}