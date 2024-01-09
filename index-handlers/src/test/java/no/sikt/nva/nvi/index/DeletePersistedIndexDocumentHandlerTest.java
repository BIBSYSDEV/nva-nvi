package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.S3StorageWriter.GZIP_ENDING;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.test.TestUtils.randomCandidate;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.test.FakeSqsClient;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.OperationType;
import software.amazon.awssdk.services.s3.S3Client;

public class DeletePersistedIndexDocumentHandlerTest {

    public static final String PERSISTED_NVI_CANDIDATES_FOLDER = "nvi-candidates";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
    private final S3Client s3Client = new FakeS3Client();
    private DeletePersistedIndexDocumentHandler handler;
    private S3Driver s3Driver;
    private FakeSqsClient sqsClient;

    @BeforeEach
    void setUp() {
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        sqsClient = new FakeSqsClient();
        handler = new DeletePersistedIndexDocumentHandler(new S3StorageWriter(s3Client, BUCKET_NAME), sqsClient,
                                                          new Environment());
    }

    @Test
    void shouldDeletePersistedIndexDocumentFromBucketWhenReceivingEvent() {
        var dao = randomCandidateDao();
        setUpExistingDocumentInS3(dao);
        var event = createEvent(dao, dao, OperationType.REMOVE);
        handler.handleRequest(event, null);
        assertEquals(0, s3Driver.listAllFiles(UnixPath.fromString(PERSISTED_NVI_CANDIDATES_FOLDER)).size());
    }

    @Test
    void shouldSendMessageToDlqWhenFailingToParseEvent() {
        var candidate = randomCandidateDao();
        var eventWithOneInvalidRecord = createEventWithOneInvalidRecord(candidate);
        handler.handleRequest(eventWithOneInvalidRecord, null);
        assertEquals(1, sqsClient.getSentMessages().size());
    }

    private static CandidateDao randomCandidateDao() {
        return new CandidateDao(UUID.randomUUID(), randomCandidate(), UUID.randomUUID().toString());
    }

    private void setUpExistingDocumentInS3(CandidateDao candidate) {
        var indexDocument = createIndexDocument(candidate);
        insertResourceInS3(indexDocument,
                           UnixPath.of(PERSISTED_NVI_CANDIDATES_FOLDER,
                                       candidate.identifier().toString() + GZIP_ENDING));
    }

    private void insertResourceInS3(IndexDocumentWithConsumptionAttributes indexDocument, UnixPath path) {
        attempt(() -> s3Driver.insertFile(path, indexDocument.toJsonString())).orElseThrow();
    }

    private IndexDocumentWithConsumptionAttributes createIndexDocument(CandidateDao candidate) {
        return IndexDocumentWithConsumptionAttributes.from(
            NviCandidateIndexDocument.builder().withIdentifier(candidate.identifier()).build());
    }
}
