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
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.index.IndexNviCandidateHandler;
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

    @BeforeEach
    void setup() {
        handler = new IndexNviCandidateHandler();
    }

    @Test
    void shouldNotLogErrorWhenMessageBodyValid() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        SQSEvent sqsEvent = createEventWithValidBody();

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
        SQSEvent sqsEvent = createEventWithBodyWithoutS3Uri();

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
}
