package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.test.TestUtils.createNumberOfCandidatesForYear;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.sikt.nva.nvi.test.TestUtils.sortByIdentifier;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.evaluator.FakeSqsClient;
import no.sikt.nva.nvi.events.model.ReEvaluateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.stubs.FakeEventBridgeClient;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReEvaluateNviCandidatesHandlerTest extends LocalDynamoTest {

    private static final Environment environment = new Environment();
    public static final String EVENT_BUS_NAME = environment.readEnv("EVENT_BUS_NAME");
    private static final int BATCH_SIZE = 10;
    private final Context context = mock(Context.class);
    private ByteArrayOutputStream outputStream;
    private ReEvaluateNviCandidatesHandler handler;
    private FakeSqsClient sqsClient;
    private CandidateRepository candidateRepository;
    private FakeEventBridgeClient eventBridgeClient;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        sqsClient = new FakeSqsClient();
        var localDynamoDbClient = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamoDbClient);
        var periodRepository = new PeriodRepository(localDynamoDbClient);
        var nviService = new NviService(periodRepository, candidateRepository);
        this.eventBridgeClient = new FakeEventBridgeClient();
        handler = new ReEvaluateNviCandidatesHandler(nviService, sqsClient, environment, eventBridgeClient);
    }

    @Test
    void shouldThrowExceptionWhenRequestDoesNotContainYear() {
        assertThrows(IllegalArgumentException.class, () -> {
            handler.handleRequest(eventStream(emptyRequest()), outputStream, context);
        });
    }

    @Test
    void shouldSendMessageBatchWithSize10() {
        var numberOfCandidates = 11;
        var year = randomYear();
        createNumberOfCandidatesForYear(year, numberOfCandidates, candidateRepository);
        handler.handleRequest(eventStream(createRequest(year)), outputStream, context);
        var sentBatches = sqsClient.getSentBatches();
        var expectedBatches = 2;
        assertEquals(expectedBatches, sentBatches.size());
        var firstBatch = sentBatches.get(0);
        var secondBatch = sentBatches.get(1);
        assertEquals(BATCH_SIZE, firstBatch.entries().size());
        assertEquals(numberOfCandidates - BATCH_SIZE, secondBatch.entries().size());
    }

    @Test
    void shouldEmitNewEventWhenBatchIsNotComplete() {
        var numberOfCandidates = 10;
        var pageSize = 5;
        var year = randomYear();
        var candidates = createNumberOfCandidatesForYear(year, numberOfCandidates, candidateRepository);
        handler.handleRequest(eventStream(createRequest(year, pageSize)), outputStream, context);
        var expectedStartMarker = getStartMarker(sortByIdentifier(candidates, numberOfCandidates).get(pageSize - 1));
        var expectedEmittedEvent = new ReEvaluateRequest(pageSize, expectedStartMarker, year);
        var actualEmittedEvent = getEmittedEvent();
        assertEquals(expectedEmittedEvent, actualEmittedEvent);
    }

    @Test
    void shouldNotEmitNewEventWhenBatchIsComplete() {
        var numberOfCandidates = 9;
        var pageSize = 10;
        var year = randomYear();
        createNumberOfCandidatesForYear(year, numberOfCandidates, candidateRepository);
        handler.handleRequest(eventStream(createRequest(year, pageSize)), outputStream, context);
        var emittedEvents = eventBridgeClient.getRequestEntries();
        assertEquals(0, emittedEvents.size());
    }

    private static Map<String, String> getStartMarker(CandidateDao dao) {
        return Map.of("PrimaryKeyRangeKey", dao.primaryKeyRangeKey(),
                      "PrimaryKeyHashKey", dao.primaryKeyHashKey(),
                      "SearchByYearHashKey", String.valueOf(dao.searchByYearHashKey()),
                      "SearchByYearRangeKey", dao.searchByYearSortKey());
    }

    private static ReEvaluateRequest emptyRequest() {
        return ReEvaluateRequest.builder().build();
    }

    private ReEvaluateRequest getEmittedEvent() {
        var emittedEvents = eventBridgeClient.getRequestEntries();
        assertEquals(1, emittedEvents.size());
        return attempt(() -> ReEvaluateRequest.fromJson(emittedEvents.get(0).detail())).orElseThrow();
    }

    private ReEvaluateRequest createRequest(String year) {
        return ReEvaluateRequest.builder().withYear(year).build();
    }

    private ReEvaluateRequest createRequest(String year, int pageSize) {
        return ReEvaluateRequest.builder().withYear(year).withPageSize(pageSize).build();
    }

    private InputStream eventStream(ReEvaluateRequest request) {
        var event = new AwsEventBridgeEvent<ReEvaluateRequest>();
        event.setDetail(request);
        event.setId(randomString());
        var jsonString = attempt(() -> objectMapper.writeValueAsString(event)).orElseThrow();
        return IoUtils.stringToStream(jsonString);
    }
}