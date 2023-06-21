import static java.util.Map.entry;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.index.IndexNviCandidateHandler;
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
    public static final String S3_URI_FIELD = "s3Uri";
    public static final String AFFILIATION_APPROVALS_FIELD = "affiliationApprovals";
    private IndexNviCandidateHandler handler;

    private S3Driver s3Driver;

    @BeforeEach
    void setup() {
        handler = new IndexNviCandidateHandler();
        var fakeS3Client = new FakeS3Client();
        s3Driver = new S3Driver(fakeS3Client, "ignored");
    }

    @Test
    void shouldAddDocumentToIndexWhenNviCandidateExistsInResourcesStorage() {
        var nviCandidateS3Id = prepareNviCandidateFile();
        var sqsEvent = createEventWithBodyWithS3Uri(nviCandidateS3Id);

        handler.handleRequest(sqsEvent, CONTEXT);

        //TODO: Assertion
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

    @Test
    void shouldLogErrorWhenMessageBodyWithoutS3Uri() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithBodyWithoutS3Uri();

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    private static SQSEvent createEventWithValidBody() {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(
            constructBody(randomUri().toString(), randomUri().toString(), List.of(randomUri().toString())));
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private static SQSEvent createEventWithBodyWithoutS3Uri() {
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
            constructBody(randomUri().toString(), s3Uri.toString(), List.of(randomUri().toString())));
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

    private static String constructBody(String publicationId, String s3Uri,
                                        List<String> affiliationApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(PUBLICATION_ID_FIELD, publicationId),
                    entry(AFFILIATION_APPROVALS_FIELD,
                          attempt(() -> objectMapper.writeValueAsString(affiliationApprovals)).orElseThrow()),
                    entry(S3_URI_FIELD, s3Uri)
                ))).orElseThrow();
    }

    private static String constructBody(String publicationId,
                                        List<String> affiliationApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(PUBLICATION_ID_FIELD, publicationId),
                    entry(AFFILIATION_APPROVALS_FIELD,
                          attempt(() -> objectMapper.writeValueAsString(
                              affiliationApprovals)).orElseThrow())))).orElseThrow();
    }

    private URI prepareNviCandidateFile() {
        var path = "candidate.json";
        var content = IoUtils.inputStreamFromResources(path);
        return attempt(() -> s3Driver.insertFile(UnixPath.of(path), content)).orElseThrow();
    }
}
