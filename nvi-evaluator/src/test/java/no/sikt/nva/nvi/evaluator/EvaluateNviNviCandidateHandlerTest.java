package no.sikt.nva.nvi.evaluator;

import static no.sikt.nva.nvi.evaluator.calculator.NviCalculator.COULD_NOT_FETCH_AFFILIATION_MESSAGE;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.evaluator.aws.SqsMessageClient;
import no.sikt.nva.nvi.evaluator.calculator.NviCalculator;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.auth.uriretriever.BackendClientCredentials;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.stubs.FakeSecretsManagerClient;
import nva.commons.core.StringUtils;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class EvaluateNviNviCandidateHandlerTest {

    public static final String BUCKET_NAME = "ignoredBucket";
    public static final String CUSTOMER_NVI_RESPONSE = "{"
                                                       + "\"nviInstitution\" : \"true\""
                                                       + "}";
    public static final String ACADEMIC_ARTICLE_PATH = "candidate_academicArticle.json";
    private final Context context = mock(Context.class);
    public HttpResponse<String> notFoundResponse;
    private HttpResponse<String> badResponse;
    private HttpResponse<String> okResponse;
    private S3Driver s3Driver;
    private EvaluateNviCandidateHandler handler;
    private FakeSqsClient sqsClient;
    private ByteArrayOutputStream output;
    private AuthorizedBackendUriRetriever uriRetriever;

    @BeforeEach
    void setUp() {
        notFoundResponse = createResponse(404, StringUtils.EMPTY_STRING);
        badResponse = createResponse(500, StringUtils.EMPTY_STRING);
        okResponse = createResponse(200, CUSTOMER_NVI_RESPONSE);
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, BUCKET_NAME);
        FakeStorageReader storageReader = new FakeStorageReader(s3Client);
        sqsClient = new FakeSqsClient();
        SqsMessageClient queueClient = new SqsMessageClient(sqsClient);
        var secretsManagerClient = new FakeSecretsManagerClient();
        var credentials = new BackendClientCredentials("id", "secret");
        secretsManagerClient.putPlainTextSecret("secret", credentials.toString());
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        var calculator = new NviCalculator(uriRetriever);
        handler = new EvaluateNviCandidateHandler(storageReader, queueClient, calculator);
        output = new ByteArrayOutputStream();
    }

    @Test
    void shouldCreateNewCandidateEventOnValidCandidate() throws IOException {
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
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
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
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
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
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
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
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
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
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
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
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
    void shouldNotCreateNewCandidateEventOnAcademicChapterWithSeriesLevelZero() throws IOException {
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
        var event = createS3Event(UriWrapper.fromUri("s3://dummy").getUri());
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThatMessageIsInDlq(sentMessages);
    }

    @Test
    void shouldNotCreateNewCandidateEventWhenZeroNviInstitutions() throws IOException {
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(notFoundResponse));
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        assertThatMessageIsInDlq(sqsClient.getSentMessages());
    }

    @Test
    void shouldThrowExceptionWhenProblemsFetchingAffiliation() throws IOException {
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(badResponse));
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createS3Event(fileUri);
        var appender = LogUtils.getTestingAppenderForRootLogger();
        handler.handleRequest(event, output, context);
        assertThatMessageIsInDlq(sqsClient.getSentMessages());
        assertThat(appender.getMessages(), containsString(COULD_NOT_FETCH_AFFILIATION_MESSAGE));
    }

    @Test
    void shouldCreateNewCandidateEventWhenAffiliationAreNviInstitutions() throws IOException {
        createResponse(200, CUSTOMER_NVI_RESPONSE);
        when(uriRetriever.fetchResponse(any(), any())).thenReturn(Optional.of(okResponse));
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        assertThat(sqsClient.getSentMessages(), hasSize(1));
    }

    @Test
    void shouldCreateDlqWhenFailingToGetCandidateInfo() throws IOException {
        when(uriRetriever.getRawContent(any(), any())).thenThrow(RuntimeException.class);
        var fileUri = s3Driver.insertFile(UnixPath.of(ACADEMIC_ARTICLE_PATH),
                                          IoUtils.inputStreamFromResources(ACADEMIC_ARTICLE_PATH));
        var event = createS3Event(fileUri);
        handler.handleRequest(event, output, context);
        var sentMessages = sqsClient.getSentMessages();
        assertThatMessageIsInDlq(sentMessages);
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

    private static void assertThatMessageIsInDlq(List<SendMessageRequest> sentMessages) {
        assertThat(sentMessages, hasSize(1));
        assertThat(sentMessages.get(0).messageBody(),
                   containsString("Exception"));
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

    @SuppressWarnings("unchecked")
    private HttpResponse<String> createResponse(int status, String body) {
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
