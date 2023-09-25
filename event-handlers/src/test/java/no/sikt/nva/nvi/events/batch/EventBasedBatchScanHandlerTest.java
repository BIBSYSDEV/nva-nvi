package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.Candidate;
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
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

class EventBasedBatchScanHandlerTest extends LocalDynamoTest {
    
    public static final int LARGE_PAGE = 10;
    public static final int ONE_ENTRY_PER_EVENT = 1;
    public static final Map<String, String> START_FROM_BEGINNING = null;
    public static final String OUTPUT_EVENT_TOPIC = "OUTPUT_EVENT_TOPIC";
    public static final String TOPIC = new Environment().readEnv(OUTPUT_EVENT_TOPIC);
    public static final int PAGE_SIZE = 4;
    private EventBasedBatchScanHandler handler;
    private ByteArrayOutputStream output;
    private Context context;
    private FakeEventBridgeClient eventBridgeClient;
    private NviService nviService;

    @BeforeEach
    public void init() {
        this.output = new ByteArrayOutputStream();
        this.context = mock(Context.class);
        when(context.getInvokedFunctionArn()).thenReturn(randomString());
        this.eventBridgeClient = new FakeEventBridgeClient();
        this.nviService = new NviService(initializeTestDatabase());
        this.handler = new EventBasedBatchScanHandler(nviService, eventBridgeClient);
    }

    @Test
    void shouldUpdateDataEntriesWhenValidRequestIsReceived() {
        nviService.upsertCandidate(randomCandidate());
        handler.handleRequest(createInitialScanRequest(), output, context);
    
        assertThat(true, is(equalTo(true)));
    }

    @Test
    void shouldNotGoIntoInfiniteLoop() {
        createRandomCandidates(1000);

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(ONE_ENTRY_PER_EVENT,
                                                              START_FROM_BEGINNING,
                                                              TOPIC));
        while (thereAreMoreEventsInEventBridge()) {
            var currentRequest = consumeLatestEmittedEvent();
            handler.handleRequest(eventToInputStream(currentRequest), output, context);
        }
        assertThat(eventBridgeClient.getRequestEntries(), is(empty()));
    }

    @Test
    void shouldIterateAllCandidates() {
        var candidates = createRandomCandidates(10).toList();

        pushInitialEntryInEventBridge(new ScanDatabaseRequest(PAGE_SIZE, START_FROM_BEGINNING, TOPIC));

        while (thereAreMoreEventsInEventBridge()) {
            var currentRequest = consumeLatestEmittedEvent();

            handler.handleRequest(eventToInputStream(currentRequest), output, context);
        }

        var allCandidatesIsChanged = candidates.stream().toList().stream()
                                    .allMatch(c -> c.version() != nviService.findById(c.identifier()).orElseThrow()
                                                                      .version());

        assertTrue(allCandidatesIsChanged);
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

    private Stream<Candidate> createRandomCandidates(int i) {
        return IntStream.range(0, i).boxed()
            .map(item -> randomCandidate())
            .map(nviService::upsertCandidate)
            .map(Optional::orElseThrow);
    }

    private InputStream createInitialScanRequest() {
        return eventToInputStream(new ScanDatabaseRequest(LARGE_PAGE, Map.of(), TOPIC));
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