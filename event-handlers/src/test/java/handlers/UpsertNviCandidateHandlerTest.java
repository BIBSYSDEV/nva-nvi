package handlers;

import static handlers.TestUtils.generatePendingCandidate;
import static java.util.Map.entry;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.Utils.ExpandedResourceGenerator.createExpandedResource;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import handlers.model.InstitutionApprovals;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UpsertNviCandidateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";
    public static final String PUBLICATION_BUCKET_URI_FIELD = "publicationBucketUri";
    public static final String AFFILIATION_APPROVALS_FIELD = "institutionApprovals";
    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");
    private UpsertNviCandidateHandler handler;
    private S3Driver s3Driver;

    @BeforeEach
    void setup() {
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, EXPANDED_RESOURCES_BUCKET);
        handler = new UpsertNviCandidateHandler();
    }

    @Test
    void shouldLogErrorWhenMessageBodyInvalid() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithInvalidBody();

        handler.handleRequest(sqsEvent, CONTEXT);
        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    @Test
    void shouldLogErrorWhenMessageBodyPublicationBucketUriNull() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithMessageBody(null, Collections.emptyList());

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    @Test
    void shouldUpsertNewNviCandidate() {
        var publicationIdentifier = UUID.randomUUID();
        var publicationBucketUri = generateS3BucketUri(publicationIdentifier);
        var institution1 = randomUri();
        var institution2 = randomUri();
        var creator1 = randomUri();
        var creator2 = randomUri();
        var institutionApprovals = List.of(new InstitutionApprovals(institution1.toString(),
                                                                    List.of(creator1.toString(),
                                                                            creator2.toString())),
                                           new InstitutionApprovals(institution2.toString(),
                                                                    List.of(creator2.toString())));
        var sqsEvent = createEventWithMessageBody(publicationBucketUri, institutionApprovals);
        var expectedCandidate = generatePendingCandidate(generatePublicationId(publicationIdentifier),
                                                         institutionApprovals);
        prepareS3File(expectedCandidate);

        handler.handleRequest(sqsEvent, CONTEXT);
    }

    private static SQSEvent createEventWithMessageBody(URI publicationBucketUri,
                                                       List<InstitutionApprovals> institutionApprovals) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = nonNull(publicationBucketUri)
                       ? constructBody(publicationBucketUri.toString(), institutionApprovals)
                       : constructBody(institutionApprovals);
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private static String constructBody(List<InstitutionApprovals> institutionApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(AFFILIATION_APPROVALS_FIELD,
                          institutionApprovals
                    )))).orElseThrow();
    }

    private static String constructBody(String publicationId, List<InstitutionApprovals> institutionApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(PUBLICATION_BUCKET_URI_FIELD, publicationId),
                    entry(AFFILIATION_APPROVALS_FIELD,
                          institutionApprovals
                    )))).orElseThrow();
    }

    private static SQSEvent createEventWithInvalidBody() {
        var sqsEvent = new SQSEvent();
        var invalidSqsMessage = new SQSMessage();
        invalidSqsMessage.setBody(randomString());
        sqsEvent.setRecords(List.of(invalidSqsMessage));
        return sqsEvent;
    }

    private URI generatePublicationId(UUID identifier) {
        return UriWrapper.fromHost("example.org").addChild("resource").addChild(identifier.toString()).getUri();
    }

    private URI generateS3BucketUri(UUID identifier) {
        return UriWrapper.fromHost("example.org").addChild(identifier.toString()).getUri();
    }

    private void prepareS3File(Candidate candidate) {
        var expandedResource = createExpandedResource(candidate);
        attempt(() -> s3Driver.insertFile(UnixPath.of(candidate.publicationIdentifier()),
                                          stringToStream(expandedResource))).orElseThrow();
    }
}
