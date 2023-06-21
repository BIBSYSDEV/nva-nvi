package no.sikt.nva.nvi.evaluator;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class EvaluateNviNviCandidateHandlerTest {

    public static final String BUCKET_NAME = "ignoredBucket";
    private final Context context = mock(Context.class);
    private SqsMessageClient queueClient;
    private S3Driver s3Driver;
    private FakeStorageReader storageReader;
    private EvaluateNviCandidateHandler handler;
    private FakeSqsClient sqsClient;
    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() {
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        storageReader = new FakeStorageReader(s3Client);
        sqsClient = new FakeSqsClient();
        queueClient = new SqsMessageClient(sqsClient);
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        output = new ByteArrayOutputStream();
    }

    @Test
    void shouldCreateNewCandidateEventOnValidCandidate() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "candidate.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var validNviCandidateIdentifier = "01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d";
        assertThat(message.messageBody(),
                   containsString(validNviCandidateIdentifier));
    }

    @Test
    void shouldEvaluateStrippedCandidate() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "candidate_stripped.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var validNviCandidateIdentifier = "01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d";
        assertThat(message.messageBody(),
                   containsString(validNviCandidateIdentifier));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicChapter() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "candidate_academicChapter.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var validNviCandidateIdentifier = "0188beb8f346-330c9426-4757-4e36-b08f-4d698d295bb4";
        assertThat(message.messageBody(),
                   containsString(validNviCandidateIdentifier));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicChapterWithoutSeriesLevelWithPublisherLevel()
        throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "candidate_academicChapter_seriesLevelEmptyPublisherLevelOne.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var validNviCandidateIdentifier = "0188beb8f346-330c9426-4757-4e36-b08f-4d698d295bb4";
        assertThat(message.messageBody(),
                   containsString(validNviCandidateIdentifier));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicMonograph() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "candidate_academicMonograph.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var validNviCandidateIdentifier = "0188bee5a7f1-17acf7d5-5658-4a5a-89b2-b2ea73032661";
        assertThat(message.messageBody(),
                   containsString(validNviCandidateIdentifier));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicLiteratureReview() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "candidate_academicLiteratureReview.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var validNviCandidateIdentifier = "0188bee8530e-bb78f9a0-d167-4c53-8e70-cedf9994f055";
        assertThat(message.messageBody(),
                   containsString(validNviCandidateIdentifier));
    }

    @Test
    void shouldCreateNewCandidateEventOnValidAcademicArticle() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "candidate_academicArticle.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, hasSize(1));
        var message = sentMessages.get(0);
        var validNviCandidateIdentifier = "01888b283f29-cae193c7-80fa-4f92-a164-c73b02c19f2d";
        assertThat(message.messageBody(),
                   containsString(validNviCandidateIdentifier));
    }

    @Test
    void shouldNotCreateNewCandidateEventOnAcademicChapterWithSeriesLevelZero() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "nonCandidate_academicChapter_seriesLevelZero.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    @Disabled
    void shouldNotCreateNewCandidateEventWhenIdentityIsNotVerified() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "nonCandidate_nonVerified.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    void shouldNotCreateNewCandidateEventWhenPublicationIsNotPublished() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "nonCandidate_notPublished.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    void shouldNotCreateCandidateForMusicalArts() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "nonCandidate_musicalArts.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    void shouldNotCreateNewCandidateEventWhenPublicationIsPublishedBeforeCurrentYear()
        throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "nonCandidate_publishedBeforeCurrentNviYear.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    void shouldNotCreateNewCandidateEventWhenPublicationIsPublishedAfterCurrentYear()
        throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "nonCandidate_publishedAfterCurrentNviYear.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    void shouldNotCreateCandidateIfSeriesInMonographHasNviLevelZero() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var path = "nonCandidate_notValidMonographArticle.json";
        var content = IoUtils.inputStreamFromResources(path);
        var fileUri = s3Driver.insertFile(UnixPath.of(path),
                                          content);
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    void shouldThrowExceptionIfFileDoesntExist() throws IOException {
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var event = createS3Event(UriWrapper.fromUri("s3://dummy").getUri());
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThat(sentMessages, is(empty()));
    }

    @Test
    @Disabled
    void shouldLogSomething() throws IOException {
        //        var logger = LogUtils.getTestingAppenderForRootLogger();
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient);
        var event = createS3Event(UriWrapper.fromUri("s3://dummy").getUri());
        handler.handleRequest(event, output, context);
        //        System.out.println(logger.getMessages());
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

    private InputStream createEventInputStream(EventReference eventReference) throws IOException {
        var detail = new AwsEventBridgeDetail<EventReference>();
        detail.setResponsePayload(eventReference);
        var event = new AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>>();
        event.setDetail(detail);
        return new ByteArrayInputStream(dtoObjectMapper.writeValueAsBytes(event));
    }

    private InputStream createS3Event(URI uri) throws IOException {
        return createEventInputStream(new EventReference("", uri));
    }
}
