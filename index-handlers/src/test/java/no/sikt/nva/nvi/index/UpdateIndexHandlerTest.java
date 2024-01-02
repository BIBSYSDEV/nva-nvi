package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.createExpandedResource;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.NVI_CONTEXT;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.createPath;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.expandApprovals;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.expandPublicationDetails;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.invalidSqsMessage;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.test.FakeSqsClient;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.OpenSearchException;
import software.amazon.awssdk.services.s3.S3Client;

class UpdateIndexHandlerTest extends LocalDynamoTest {

    public static final Context CONTEXT = mock(Context.class);
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    public static final Environment ENVIRONMENT = new Environment();
    private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
    public static final String INDEX_DLQ = "INDEX_DLQ";
    public static final String INDEX_DLQ_URL = ENVIRONMENT.readEnv(INDEX_DLQ);
    private final S3Client s3Client = new FakeS3Client();
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private S3Driver s3Driver;
    private UpdateIndexHandler handler;
    private OpenSearchClient openSearchClient;
    private QueueClient sqsClient;

    @BeforeEach
    void setUp() {
        var localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        openSearchClient = mock(OpenSearchClient.class);
        sqsClient = mock(FakeSqsClient.class);
        handler = new UpdateIndexHandler(openSearchClient, new S3StorageReader(s3Client, BUCKET_NAME), sqsClient);
    }

    @Test
    void shouldUpdateIndexWithDocumentFromS3WhenReceivingEventWithDocumentUri() {
        var candidate = randomApplicableCandidate();
        var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
        handler.handleRequest(createUpdateIndexEvent(List.of(candidate)), CONTEXT);
        verify(openSearchClient, times(1)).addDocumentToIndex(expectedIndexDocument);
    }

    @Test
    void shouldSendMessageToDlqWhenHandlingError() {
        var candidate = randomApplicableCandidate();
        var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
        var event = createUpdateIndexEvent(List.of(candidate));
        when(openSearchClient.addDocumentToIndex(expectedIndexDocument)).thenThrow(new RuntimeException());
        handler.handleRequest(event, CONTEXT);
        verify(sqsClient, times(1)).sendMessage(any(), eq(INDEX_DLQ_URL), eq(candidate.getIdentifier()));
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToParseOneMessageBody() {
        var candidateToSucceed = randomApplicableCandidate();
        setupExistingIndexDocumentInBucket(candidateToSucceed);
        var event = createUpdateIndexEventWithOneInvalidMessageBody(candidateToSucceed);
        assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotFailForWholeBatchWhenFailingToReadOneS3Blob() throws JsonProcessingException {
        var candidateToSucceed = randomApplicableCandidate();
        var candidateToFail = randomApplicableCandidate();
        var storageReader = setUpStorageReaderFailingForOneCandidate(candidateToSucceed, candidateToFail);
        handler = new UpdateIndexHandler(openSearchClient, storageReader, sqsClient);
        var event = createUpdateIndexEvent(List.of(candidateToSucceed, candidateToFail));
        handler.handleRequest(event, CONTEXT);
        verify(openSearchClient, times(0)).addDocumentToIndex(eq(null));
        verify(openSearchClient, times(1)).addDocumentToIndex(any(NviCandidateIndexDocument.class));
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToAddDocumentToIndex() {
        var candidate = randomApplicableCandidate();
        var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
        var event = createUpdateIndexEvent(List.of(candidate));
        when(openSearchClient.addDocumentToIndex(expectedIndexDocument)).thenThrow(OpenSearchException.class);
        handler.handleRequest(event, CONTEXT);
        assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
    }

    private static URI generateBucketUri(Candidate candidate) {
        return new UriWrapper(S3_SCHEME, BUCKET_NAME)
                   .addChild(createPath(candidate))
                   .getUri();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private StorageReader setUpStorageReaderFailingForOneCandidate(Candidate candidateToSucceed,
                                                                   Candidate candidateToFail)
        throws JsonProcessingException {
        var storageReader = mock(StorageReader.class);
        var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidateToSucceed);
        when(storageReader.read(generateBucketUri(candidateToSucceed))).thenReturn(
            expectedIndexDocument.toJsonString());
        when(storageReader.read(generateBucketUri(candidateToFail))).thenThrow(new RuntimeException());
        return storageReader;
    }

    private SQSEvent createUpdateIndexEventWithOneInvalidMessageBody(Candidate candidateToSucceed) {
        var event = new SQSEvent();
        var message = new SQSMessage();
        message.setBody(new PersistedIndexDocumentMessage(generateBucketUri(candidateToSucceed)).asJsonString());
        event.setRecords(List.of(message, invalidSqsMessage()));
        return event;
    }

    private SQSEvent createUpdateIndexEvent(List<Candidate> candidates) {
        var event = new SQSEvent();
        var messages = candidates.stream()
                           .map(candidate -> new PersistedIndexDocumentMessage(
                               generateBucketUri(candidate)).asJsonString())
                           .map(body -> {
                               var message = new SQSMessage();
                               message.setBody(body);
                               return message;
                           })
                           .toList();
        event.setRecords(messages);
        return event;
    }

    private IndexDocumentWithConsumptionAttributes setupExistingIndexDocumentInBucket(Candidate candidate) {
        var indexDocument =
            NviCandidateIndexDocument.builder()
                .withContext(NVI_CONTEXT)
                .withApprovals(expandApprovals(candidate))
                .withIdentifier(candidate.getIdentifier())
                .withPublicationDetails(expandPublicationDetails(candidate, createExpandedResource(candidate)))
                .withPoints(randomBigDecimal())
                .build();

        var indexDocumentWithConsumptionAttributes = IndexDocumentWithConsumptionAttributes.from(indexDocument);
        attempt(() -> s3Driver.insertFile(createPath(candidate),
                                          indexDocumentWithConsumptionAttributes.toJsonString())).orElseThrow();
        return indexDocumentWithConsumptionAttributes;
    }

    private Candidate randomApplicableCandidate() {
        return Candidate.fromRequest(createUpsertCandidateRequest(2023), candidateRepository, periodRepository)
                   .orElseThrow();
    }
}