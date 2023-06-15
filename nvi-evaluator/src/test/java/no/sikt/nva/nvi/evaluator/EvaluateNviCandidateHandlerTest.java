package no.sikt.nva.nvi.evaluator;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.RequestParametersEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.ResponseElementsEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3BucketEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3Entity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3ObjectEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.UserIdentityEntity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class EvaluateNviCandidateHandlerTest {

    private static final RequestParametersEntity EMPTY_REQUEST_PARAMETERS = null;
    private static final ResponseElementsEntity EMPTY_RESPONSE_ELEMENTS = null;
    private static final UserIdentityEntity EMPTY_USER_IDENTITY = null;
    private static final long SOME_FILE_SIZE = 100L;
    private final Context context = mock(Context.class);
    private ByteArrayOutputStream outputStream;
    private FakeS3Client s3Client;
    private StubSqsClient sqsClient;
    private S3Driver s3Driver;
    private EvaluateNviCandidateHandler handler;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, "ingoredBucket");
        sqsClient = new StubSqsClient();
        handler = new EvaluateNviCandidateHandler();
    }

    @Test
    void shouldCreateNewCandidateEventOnValidCandidate() throws IOException {
        handler = new EvaluateNviCandidateHandler(s3Client, sqsClient);
        var path = "candidate.json.gz";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, context);
        List<SendMessageRequest> sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        SendMessageRequest message = sentMessages.get(0);
        var validNviAffiliaction = "01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d";
        assertThat(message.messageBody(),
                   containsString(validNviAffiliaction));
    }

    @Test
    void shouldNotCreateNewCandidateEventWhenIdentityIsNotVerified() throws IOException {
        handler = new EvaluateNviCandidateHandler(s3Client, sqsClient);
        var path = "noncandidate_nonVerified.json.gz";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, context);
        List<SendMessageRequest> sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));

    }

    @Test
    void shouldNotCreateNewCandidateEventWithNotApprovalAffiliations() {

    }

    @Test
    void shouldCreateDWhenFailingToGetCandidateInfo() {

    }

    @Test
    void shouldCreateDlqWhenUnableToConnectToResources() {

    }

    @Test
    void shouldCreateDlqWhenUnableToCreateNewCandidateEvent() {

    }

    @Test
    void shouldReadDlqAndRetryAfterGivenTime() {

    }

    private S3Event createInputEventForFile(URI fileUri) {
        return null;
    }

    private S3Event createS3Event(URI uri) {
        return createS3Event(UriWrapper.fromUri(uri).toS3bucketPath().toString());
    }

    private S3Event createS3Event(String val) {
        var eventNotification = new S3EventNotificationRecord(
            randomString(),
            randomString(),
            randomString(),
            randomDate(),
            randomString(),
            EMPTY_REQUEST_PARAMETERS,
            EMPTY_RESPONSE_ELEMENTS,
            createS3Entity(val),
            EMPTY_USER_IDENTITY);
        return new S3Event(List.of(eventNotification));
    }

    private S3Entity createS3Entity(String val) {
        var bucket = new S3BucketEntity(randomString(), EMPTY_USER_IDENTITY, randomString());
        var object = new S3ObjectEntity(
            val, SOME_FILE_SIZE, randomString(), randomString(), randomString());
        var schemaVersion = randomString();
        return new S3Entity(randomString(), bucket, object, schemaVersion);
    }

    private String randomDate() {
        return Instant.now().toString();
    }

    private UnixPath randomPath() {
        return UnixPath.of(randomString(), randomString());
    }
}
