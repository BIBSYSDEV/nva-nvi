package no.sikt.nva.nvi.events.batch;

import static no.sikt.nva.nvi.test.TestUtils.randomCandidateBuilder;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCandidate;
import no.sikt.nva.nvi.common.db.CandidateDao.DbPublicationDate;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.NviService;
import no.sikt.nva.nvi.events.evaluator.FakeSqsClient;
import no.sikt.nva.nvi.events.model.ReEvaluateRequest;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReEvaluateNviCandidatesHandlerTest extends LocalDynamoTest {

    private static final Environment environment = new Environment();
    private final Context context = mock(Context.class);
    private ByteArrayOutputStream outputStream;
    private ReEvaluateNviCandidatesHandler handler;
    private FakeSqsClient sqsClient;
    private CandidateRepository candidateRepository;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        sqsClient = new FakeSqsClient();
        var localDynamoDbClient = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamoDbClient);
        var periodRepository = new PeriodRepository(localDynamoDbClient);
        var nviService = new NviService(periodRepository, candidateRepository);
        handler = new ReEvaluateNviCandidatesHandler(nviService, sqsClient, environment);
    }

    @Test
    void shouldThrowExceptionWhenRequestDoesNotContainYear() {
        assertThrows(IllegalArgumentException.class, () -> {
            handler.handleRequest(eventStream(emptyRequest()), outputStream, context);
        });
    }

    @Test
    void shouldSendMessagesBatchWithSize10() {
        var numberOfCandidates = 11;
        var batchSize = 10;
        var randomYear = 2020;
        createNumberOfCandidatesForYear(randomYear, numberOfCandidates);
        handler.handleRequest(eventStream(requestWithYear(randomYear)), outputStream, context);
        var sentBatches = sqsClient.getSentBatches();
        var expectedBatches = 2;
        assertEquals(expectedBatches, sentBatches.size());
        var firstBatch = sentBatches.get(0);
        var secondBatch = sentBatches.get(1);
        assertEquals(batchSize, firstBatch.entries().size());
        assertEquals(numberOfCandidates - batchSize, secondBatch.entries().size());
    }

    private static ReEvaluateRequest emptyRequest() {
        return ReEvaluateRequest.builder().build();
    }

    private static DbPublicationDate publicationDate(int year) {
        return new DbPublicationDate(String.valueOf(year), null, null);
    }

    private static DbCandidate createCandidate(int year) {
        return randomCandidateBuilder(true).publicationDate(publicationDate(year)).build();
    }

    private void createNumberOfCandidatesForYear(int year, int numberOfCandidates) {
        IntStream.range(0, numberOfCandidates).forEach(i -> {
            candidateRepository.create(createCandidate(year), List.of());
        });
    }

    private ReEvaluateRequest requestWithYear(int year) {
        return ReEvaluateRequest.builder().withYear(String.valueOf(year)).build();
    }

    private InputStream eventStream(ReEvaluateRequest request) {
        var event = new AwsEventBridgeEvent<ReEvaluateRequest>();
        event.setDetail(request);
        event.setId(randomString());
        var jsonString = attempt(() -> objectMapper.writeValueAsString(event)).orElseThrow();
        return IoUtils.stringToStream(jsonString);
    }
}