package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.QueueServiceTestUtils.invalidSqsMessage;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.PublicationDtoFixtures.publicationDtoMirroring;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.createPath;
import static no.sikt.nva.nvi.index.IndexHandlerEnvironments.forHandler;
import static no.sikt.nva.nvi.index.utils.SearchConstants.getSearchIndexName;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.io.IOException;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.CandidateSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClientException;
import no.sikt.nva.nvi.index.mapper.CandidateToIndexDocumentMapper;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

class UpdateIndexHandlerTest {

  private static final Context CONTEXT = new FakeContext();
  private static final Environment ENVIRONMENT = forHandler(UpdateIndexHandler.class);
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String INDEX_DLQ_URL = ENVIRONMENT.readEnv(INDEX_DLQ);
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
  private static final String CANDIDATE_IDENTIFIER_ATTRIBUTE = "candidateIdentifier";
  private static final String ERROR_TYPE_ATTRIBUTE = "errorType";
  private static final String STACK_TRACE_ATTRIBUTE = "stackTrace";
  private final S3Client s3Client = new FakeS3Client();
  private S3Driver s3Driver;
  private UpdateIndexHandler handler;
  private CandidateSearchClient searchClient;
  private QueueClient sqsClient;
  private TestScenario scenario;

  @BeforeEach
  void setUp() {
    scenario = new TestScenario();
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
    searchClient = mock(CandidateSearchClient.class);
    sqsClient = new FakeSqsClient();
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    handler = new UpdateIndexHandler(searchClient, storageReader, sqsClient, ENVIRONMENT);
    setupOpenPeriod(scenario, CURRENT_YEAR);
  }

  @Test
  void shouldUpdateIndexWithDocumentFromS3WhenReceivingEventWithDocumentUri() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
    handler.handleRequest(createUpdateIndexEvent(List.of(candidate)), CONTEXT);
    verify(searchClient, times(1)).addDocumentToIndex(expectedIndexDocument);
  }

  @Test
  void shouldSendMessageToDlqWhenHandlingError() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
    var event = createUpdateIndexEvent(List.of(candidate));
    when(searchClient.addDocumentToIndex(expectedIndexDocument)).thenThrow(searchClientException());
    handler.handleRequest(event, CONTEXT);
    assertEquals(1, sqsClient.receiveMessage(INDEX_DLQ_URL, 1).messages().size());
  }

  @Test
  void shouldSendOriginalMessageWithErrorContextToDlqWhenIndexingFails() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
    var event = createUpdateIndexEvent(List.of(candidate));
    when(searchClient.addDocumentToIndex(expectedIndexDocument)).thenThrow(searchClientException());
    handler.handleRequest(event, CONTEXT);
    var dlqMessage = sqsClient.receiveMessage(INDEX_DLQ_URL, 1).messages().getFirst();

    assertThat(dlqMessage.body()).isEqualTo(event.getRecords().getFirst().getBody());
    assertThat(dlqMessage.messageAttributes())
        .containsEntry(CANDIDATE_IDENTIFIER_ATTRIBUTE, candidate.identifier().toString())
        .containsKeys(ERROR_TYPE_ATTRIBUTE, STACK_TRACE_ATTRIBUTE);
  }

  private static SearchClientException searchClientException() {
    return new SearchClientException("Failed to add document to index", new IOException());
  }

  @Test
  void shouldSendOriginalMessageWithErrorContextToDlqWhenFetchingDocumentFromS3Fails() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var event = createUpdateIndexEvent(List.of(candidate));
    var mockedStorageReader = mock(S3StorageReader.class);
    when(mockedStorageReader.read(any())).thenThrow(s3SlowDownException());
    new UpdateIndexHandler(searchClient, mockedStorageReader, sqsClient, ENVIRONMENT)
        .handleRequest(event, CONTEXT);
    var dlqMessage = sqsClient.receiveMessage(INDEX_DLQ_URL, 1).messages().getFirst();

    assertThat(dlqMessage.body()).isEqualTo(event.getRecords().getFirst().getBody());
    assertThat(dlqMessage.messageAttributes())
        .containsEntry(CANDIDATE_IDENTIFIER_ATTRIBUTE, candidate.identifier().toString())
        .containsKeys(ERROR_TYPE_ATTRIBUTE, STACK_TRACE_ATTRIBUTE);
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
    handler = new UpdateIndexHandler(searchClient, storageReader, sqsClient, ENVIRONMENT);
    var event = createUpdateIndexEvent(List.of(candidateToSucceed, candidateToFail));
    handler.handleRequest(event, CONTEXT);
    verify(searchClient, times(0)).addDocumentToIndex(eq(null));
    verify(searchClient, times(1)).addDocumentToIndex(any(NviCandidateIndexDocument.class));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToAddDocumentToIndex() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument = setupExistingIndexDocumentInBucket(candidate).indexDocument();
    var event = createUpdateIndexEvent(List.of(candidate));
    when(searchClient.addDocumentToIndex(expectedIndexDocument))
        .thenThrow(SearchClientException.class);
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
    when(storageReader.read(generateBucketUri(candidateToFail))).thenThrow(s3SlowDownException());
    return storageReader;
  }

  private static S3Exception s3SlowDownException() {
    return (S3Exception) S3Exception.builder().statusCode(503).message("Slow Down").build();
  }

  private SQSEvent createUpdateIndexEventWithOneInvalidMessageBody(Candidate candidateToSucceed) {
    var event = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(
        new PersistedIndexDocumentMessage(generateBucketUri(candidateToSucceed)).toJsonString());
    event.setRecords(List.of(message, invalidSqsMessage()));
    return event;
  }

  private SQSEvent createUpdateIndexEvent(List<Candidate> candidates) {
    var event = new SQSEvent();
    var messages =
        candidates.stream()
            .map(
                candidate ->
                    new PersistedIndexDocumentMessage(generateBucketUri(candidate)).toJsonString())
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
    var indexDocument =
        new CandidateToIndexDocumentMapper(
                candidate, publicationDtoMirroring(candidate), ENVIRONMENT)
            .generate();
    var indexDocumentWithConsumptionAttributes =
        IndexDocumentWithConsumptionAttributes.from(indexDocument, getSearchIndexName(ENVIRONMENT));
    attempt(
            () ->
                s3Driver.insertFile(
                    createPath(candidate), indexDocumentWithConsumptionAttributes.toJsonString()))
        .orElseThrow();
    return indexDocumentWithConsumptionAttributes;
  }
}
