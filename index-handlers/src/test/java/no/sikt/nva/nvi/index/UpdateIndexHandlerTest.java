package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.QueueServiceTestUtils.invalidSqsMessage;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.NVI_CONTEXT;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.createPath;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.expandApprovals;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.expandPublicationDetails;
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
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.OpenSearchException;
import software.amazon.awssdk.services.s3.S3Client;

class UpdateIndexHandlerTest {

  public static final Context CONTEXT = mock(Context.class);
  public static final Environment ENVIRONMENT = new Environment();
  public static final String INDEX_DLQ = "INDEX_DLQ";
  public static final String INDEX_DLQ_URL = ENVIRONMENT.readEnv(INDEX_DLQ);
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
  private final S3Client s3Client = new FakeS3Client();
  private S3Driver s3Driver;
  private UpdateIndexHandler handler;
  private OpenSearchClient openSearchClient;
  private QueueClient sqsClient;
  private TestScenario scenario;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
    openSearchClient = mock(OpenSearchClient.class);
    sqsClient = mock(FakeSqsClient.class);
    handler =
        new UpdateIndexHandler(
            openSearchClient, new S3StorageReader(s3Client, BUCKET_NAME), sqsClient);
  }

  @Test
  void shouldUpdateIndexWithDocumentFromS3WhenReceivingEventWithDocumentUri() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
    handler.handleRequest(createUpdateIndexEvent(List.of(candidate)), CONTEXT);
    verify(openSearchClient, times(1)).addDocumentToIndex(expectedIndexDocument);
  }

  @Test
  void shouldSendMessageToDlqWhenHandlingError() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
    var event = createUpdateIndexEvent(List.of(candidate));
    when(openSearchClient.addDocumentToIndex(expectedIndexDocument))
        .thenThrow(new RuntimeException());
    handler.handleRequest(event, CONTEXT);
    verify(sqsClient, times(1)).sendMessage(any(), eq(INDEX_DLQ_URL), eq(candidate.identifier()));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToParseOneMessageBody() {
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    setupExistingIndexDocumentInBucket(candidateToSucceed);
    var event = createUpdateIndexEventWithOneInvalidMessageBody(candidateToSucceed);
    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldNotFailForWholeBatchWhenFailingToReadOneS3Blob() throws JsonProcessingException {
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var storageReader =
        setupStorageReaderFailingForOneCandidate(candidateToSucceed, candidateToFail);
    handler = new UpdateIndexHandler(openSearchClient, storageReader, sqsClient);
    var event = createUpdateIndexEvent(List.of(candidateToSucceed, candidateToFail));
    handler.handleRequest(event, CONTEXT);
    verify(openSearchClient, times(0)).addDocumentToIndex(eq(null));
    verify(openSearchClient, times(1)).addDocumentToIndex(any(NviCandidateIndexDocument.class));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToAddDocumentToIndex() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
    var event = createUpdateIndexEvent(List.of(candidate));
    when(openSearchClient.addDocumentToIndex(expectedIndexDocument))
        .thenThrow(OpenSearchException.class);
    handler.handleRequest(event, CONTEXT);
    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  private static URI generateBucketUri(Candidate candidate) {
    return new UriWrapper(S3_SCHEME, BUCKET_NAME).addChild(createPath(candidate)).getUri();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private StorageReader setupStorageReaderFailingForOneCandidate(
      Candidate candidateToSucceed, Candidate candidateToFail) throws JsonProcessingException {
    var storageReader = mock(StorageReader.class);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidateToSucceed);
    when(storageReader.read(generateBucketUri(candidateToSucceed)))
        .thenReturn(expectedIndexDocument.toJsonString());
    when(storageReader.read(generateBucketUri(candidateToFail))).thenThrow(new RuntimeException());
    return storageReader;
  }

  private SQSEvent createUpdateIndexEventWithOneInvalidMessageBody(Candidate candidateToSucceed) {
    var event = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(
        new PersistedIndexDocumentMessage(generateBucketUri(candidateToSucceed)).asJsonString());
    event.setRecords(List.of(message, invalidSqsMessage()));
    return event;
  }

  private SQSEvent createUpdateIndexEvent(List<Candidate> candidates) {
    var event = new SQSEvent();
    var messages =
        candidates.stream()
            .map(
                candidate ->
                    new PersistedIndexDocumentMessage(generateBucketUri(candidate)).asJsonString())
            .map(
                body -> {
                  var message = new SQSMessage();
                  message.setBody(body);
                  return message;
                })
            .toList();
    event.setRecords(messages);
    return event;
  }

  private IndexDocumentWithConsumptionAttributes setupExistingIndexDocumentInBucket(
      Candidate candidate) {
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .build()
            .createExpandedResource();
    var expandedPublicationDetails = expandPublicationDetails(candidate, expandedResource);
    var indexDocument =
        NviCandidateIndexDocument.builder()
            .withContext(NVI_CONTEXT)
            .withApprovals(expandApprovals(candidate, expandedPublicationDetails.contributors()))
            .withIdentifier(candidate.identifier())
            .withPublicationDetails(expandedPublicationDetails)
            .withPoints(randomBigDecimal())
            .build();

    var indexDocumentWithConsumptionAttributes =
        IndexDocumentWithConsumptionAttributes.from(indexDocument);
    attempt(
            () ->
                s3Driver.insertFile(
                    createPath(candidate), indexDocumentWithConsumptionAttributes.toJsonString()))
        .orElseThrow();
    return indexDocumentWithConsumptionAttributes;
  }
}
