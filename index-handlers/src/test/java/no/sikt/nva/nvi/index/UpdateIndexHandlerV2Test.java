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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class UpdateIndexHandlerV2Test extends LocalDynamoTest {

    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private static final String BUCKET_NAME = new Environment().readEnv(EXPANDED_RESOURCES_BUCKET);
    private final S3Client s3Client = new FakeS3Client();
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private S3Driver s3Driver;
    private UpdateIndexHandlerV2 handler;
    private OpenSearchClient openSearchClient;

    @BeforeEach
    void setUp() {
        var localDynamo = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamo);
        periodRepository = new PeriodRepository(localDynamo);
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        openSearchClient = mock(OpenSearchClient.class);
        handler = new UpdateIndexHandlerV2(openSearchClient, new S3StorageReader(s3Client, BUCKET_NAME));
    }

    @Test
    void shouldUpdateIndexWithDocumentFromS3WhenReceivingEventWithDocumentUri() {
        var candidate = randomApplicableCandidate();
        var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate);
        handler.handleRequest(createUpdateIndexEvent(candidate), null);
        verify(openSearchClient, times(1)).addDocumentToIndex(expectedIndexDocument);
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToParseOneMessageBody() {
        var candidateToSucceed = randomApplicableCandidate();
        setupExistingIndexDocumentInBucket(candidateToSucceed);
        var event = createUpdateIndexEventWithOneInvalidMessageBody(candidateToSucceed);
        assertDoesNotThrow(() -> handler.handleRequest(event, null));
    }

    private static URI generateBucketUri(Candidate candidate) {
        return new UriWrapper(S3_SCHEME, BUCKET_NAME)
                   .addChild(createPath(candidate))
                   .getUri();
    }

    private SQSEvent createUpdateIndexEventWithOneInvalidMessageBody(Candidate candidateToSucceed) {
        var event = new SQSEvent();
        var message = new SQSMessage();
        message.setBody(new PersistedIndexDocumentMessage(generateBucketUri(candidateToSucceed)).asJsonString());
        event.setRecords(List.of(message, invalidSqsMessage()));
        return event;
    }

    private SQSEvent createUpdateIndexEvent(Candidate candidate) {
        var event = new SQSEvent();
        var message = new SQSMessage();
        message.setBody(new PersistedIndexDocumentMessage(generateBucketUri(candidate)).asJsonString());
        event.setRecords(List.of(message));
        return event;
    }

    private NviCandidateIndexDocument setupExistingIndexDocumentInBucket(Candidate candidate) {
        var indexDocument =
            NviCandidateIndexDocument.builder()
                .withContext(NVI_CONTEXT)
                .withApprovals(expandApprovals(candidate))
                .withIdentifier(candidate.getIdentifier())
                .withPublicationDetails(expandPublicationDetails(candidate, createExpandedResource(candidate)))
                .withPoints(randomBigDecimal())
                .build();

        attempt(() -> s3Driver.insertFile(createPath(candidate),
                                          IndexDocumentWithConsumptionAttributes.from(indexDocument)
                                              .toJsonString())).orElseThrow();
        return indexDocument;
    }

    private Candidate randomApplicableCandidate() {
        return Candidate.fromRequest(createUpsertCandidateRequest(2023), candidateRepository, periodRepository)
                   .orElseThrow();
    }
}