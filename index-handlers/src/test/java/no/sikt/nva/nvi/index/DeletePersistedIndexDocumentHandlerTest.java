package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEventWithOneRecordMissingIdentifier;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEventWithOnlyOneRecordMissingIdentifier;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidate;
import static no.sikt.nva.nvi.index.aws.S3StorageWriter.GZIP_ENDING;
import static no.sikt.nva.nvi.index.aws.S3StorageWriter.NVI_CANDIDATES_FOLDER;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class DeletePersistedIndexDocumentHandlerTest {

  public static final String PERSISTED_NVI_CANDIDATES_FOLDER = "nvi-candidates";
  private static final Environment ENVIRONMENT = new Environment();
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
  private DeletePersistedIndexDocumentHandler handler;
  private S3Driver s3Driver;
  private FakeSqsClient sqsClient;

  @BeforeEach
  void setUp() {
    sqsClient = new FakeSqsClient();
    var s3Client = new FakeS3Client();
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
    handler =
        new DeletePersistedIndexDocumentHandler(
            new S3StorageWriter(s3Client, BUCKET_NAME), sqsClient, new Environment());
  }

  @Test
  void shouldDeletePersistedIndexDocumentFromBucketWhenReceivingEvent() {
    var dao = randomCandidateDao();
    setUpExistingDocumentInS3(dao);
    var event = createEvent(dao, dao, OperationType.REMOVE);
    handler.handleRequest(event, null);
    assertEquals(
        0, s3Driver.listAllFiles(UnixPath.fromString(PERSISTED_NVI_CANDIDATES_FOLDER)).size());
  }

  @Test
  void shouldSendMessageToDlqWhenFailingToDeletePersistedIndexDocument() {
    var dao = randomCandidateDao();
    var event = createEvent(dao, dao, OperationType.REMOVE);
    handler =
        new DeletePersistedIndexDocumentHandler(
            new S3StorageWriter(setupFailingS3Client(dao.identifier()), BUCKET_NAME),
            sqsClient,
            new Environment());
    handler.handleRequest(event, null);
    assertEquals(1, sqsClient.getSentMessages().size());
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToDeletePersistedIndexDocument() {
    var daoToFail = randomCandidateDao();
    var event = createEvent(List.of(randomCandidateDao().identifier(), daoToFail.identifier()));
    handler =
        new DeletePersistedIndexDocumentHandler(
            new S3StorageWriter(setupFailingS3Client(daoToFail.identifier()), BUCKET_NAME),
            sqsClient,
            new Environment());
    assertDoesNotThrow(() -> handler.handleRequest(event, null));
  }

  @Test
  void shouldSendMessageToDlqWhenFailingToParseEvent() {
    var eventWithOneInvalidRecord = createEventWithOneInvalidRecord(randomCandidateDao());
    handler.handleRequest(eventWithOneInvalidRecord, null);
    assertEquals(1, sqsClient.getSentMessages().size());
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToParseOneEvent() {
    var candidateToSucceed = randomCandidateDao();
    setUpExistingDocumentInS3(candidateToSucceed);
    var eventWithOneInvalidRecord = createEventWithOneInvalidRecord(candidateToSucceed);
    handler.handleRequest(eventWithOneInvalidRecord, null);
    assertEquals(
        0, s3Driver.listAllFiles(UnixPath.fromString(PERSISTED_NVI_CANDIDATES_FOLDER)).size());
  }

  @Test
  void shouldSendMessageToDqlWhenFailingToExtractIdentifierFromRecord() {
    var event = createEventWithOnlyOneRecordMissingIdentifier();
    handler.handleRequest(event, null);
    assertEquals(1, sqsClient.getSentMessages().size());
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToExtractIdentifierFromRecord() {
    var daoToSucceed = randomCandidateDao();
    setUpExistingDocumentInS3(daoToSucceed);
    var event = createEventWithOneRecordMissingIdentifier(daoToSucceed);
    handler.handleRequest(event, null);
    assertEquals(
        0, s3Driver.listAllFiles(UnixPath.fromString(PERSISTED_NVI_CANDIDATES_FOLDER)).size());
  }

  private static CandidateDao randomCandidateDao() {
    return new CandidateDao(
        UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString(), randomString());
  }

  private static DeleteObjectRequest getDeleteObjectRequest(UUID identifier) {
    return DeleteObjectRequest.builder()
        .bucket(BUCKET_NAME)
        .key(UnixPath.of(NVI_CANDIDATES_FOLDER, identifier.toString() + GZIP_ENDING).toString())
        .build();
  }

  private S3Client setupFailingS3Client(UUID identifier) {
    var s3Client = mock(FakeS3Client.class);
    when(s3Client.deleteObject(getDeleteObjectRequest(identifier))).thenThrow(S3Exception.class);
    return s3Client;
  }

  private void setUpExistingDocumentInS3(CandidateDao candidate) {
    var indexDocument = createIndexDocument(candidate);
    insertResourceInS3(
        indexDocument,
        UnixPath.of(
            PERSISTED_NVI_CANDIDATES_FOLDER, candidate.identifier().toString() + GZIP_ENDING));
  }

  private void insertResourceInS3(
      IndexDocumentWithConsumptionAttributes indexDocument, UnixPath path) {
    attempt(() -> s3Driver.insertFile(path, indexDocument.toJsonString())).orElseThrow();
  }

  private IndexDocumentWithConsumptionAttributes createIndexDocument(CandidateDao candidate) {
    return IndexDocumentWithConsumptionAttributes.from(
        NviCandidateIndexDocument.builder().withIdentifier(candidate.identifier()).build());
  }
}
