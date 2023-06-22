package no.sikt.nva.nvi.index;

import static java.util.Map.entry;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.common.StorageReader;
import no.sikt.nva.nvi.common.model.IndexDocument;
import no.sikt.nva.nvi.index.model.NviCandidate;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndexNviCandidateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";

    public static final String PUBLICATION_ID_FIELD = "publicationId";
    public static final String AFFILIATION_APPROVALS_FIELD = "affiliationApprovals";
    public static final String INDEX_NVI_CANDIDATES = "nviCandidates";
    private IndexNviCandidateHandler handler;

    private S3Driver s3Driver;

    private IndexClient indexClient;

    @BeforeEach
    void setup() {
        var s3Client = new FakeS3Client();
        indexClient = new FakeIndexClient();
        s3Driver = new S3Driver(s3Client, "bucketName");
        StorageReader<NviCandidate> storageReader = new FakeStorageReader(s3Client);
        handler = new IndexNviCandidateHandler(storageReader, indexClient);
    }

    @Test
    void shouldAddDocumentToIndexWhenNviCandidateExistsInResourcesStorage() {
        var nviCandidateS3Uri = prepareNviCandidateFile();
        var sqsEvent = createEventWithBodyWithS3Uri(nviCandidateS3Uri);

        handler.handleRequest(sqsEvent, CONTEXT);
        var allIndexDocuments = indexClient.listAllDocuments(INDEX_NVI_CANDIDATES);

        var expectedIndexDocument = getExpectedIndexDocument();
        assertThat(allIndexDocuments, containsInAnyOrder(expectedIndexDocument));
    }

    @Test
    void shouldNotLogErrorWhenMessageBodyValid() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithValidBody();

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(appender.getMessages(), is(emptyString()));
    }

    @Test
    void shouldLogErrorWhenMessageBodyInvalid() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithInvalidBody();

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    private static IndexDocument getExpectedIndexDocument() {
        var content = IoUtils.stringFromResources(Path.of("indexDocumentSample.json"));
        return attempt(() -> objectMapper.readValue(content, IndexDocument.class)).orElseThrow();
    }

    private static SQSEvent createEventWithValidBody() {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(
            constructBody(randomUri().toString(), List.of(randomUri().toString())));
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private static SQSEvent createEventWithBodyWithS3Uri(URI s3Uri) {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(
            constructBody(randomUri().toString(), List.of(randomUri().toString())));
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private static SQSEvent createEventWithInvalidBody() {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(randomString());
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private static String constructBody(String publicationId,
                                        List<String> affiliationApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(PUBLICATION_ID_FIELD, publicationId),
                    entry(AFFILIATION_APPROVALS_FIELD,
                          attempt(() -> objectMapper.writeValueAsString(affiliationApprovals)).orElseThrow())
                ))).orElseThrow();
    }

    private URI prepareNviCandidateFile() {
        var path = "s3resourceSample.json";
        var content = IoUtils.inputStreamFromResources(path);
        return attempt(() -> s3Driver.insertFile(UnixPath.of(path), content)).orElseThrow();
    }
}
