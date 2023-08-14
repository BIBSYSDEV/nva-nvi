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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.service.NviCandidateService;
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
    public static final String AFFILIATION_APPROVALS_FIELD = "approvalAffiliations";
    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");
    private UpsertNviCandidateHandler handler;
    private S3Driver s3Driver;

    private NviCandidateService nviCandidateService;

    @BeforeEach
    void setup() {
        var s3Client = new FakeS3Client();
        s3Driver = new S3Driver(s3Client, EXPANDED_RESOURCES_BUCKET);
        handler = new UpsertNviCandidateHandler();
        var fakeNviCandidateRepository = new FakeNviCandidateRepository();
        nviCandidateService = new NviCandidateService(fakeNviCandidateRepository);
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
        var identifier = UUID.randomUUID();
        var institutionApprovals = List.of(randomUri());
        var sqsEvent = createEventWithMessageBody(generateS3BucketUri(identifier), institutionApprovals);
        var publicationId = generatePublicationId(identifier);
        var expectedCandidate = generatePendingCandidate(publicationId, institutionApprovals);
        prepareS3File(expectedCandidate);

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(nviCandidateService.getCandidateByPublicationId(publicationId), is(equalTo(expectedCandidate)));
    }

    private static SQSEvent createEventWithMessageBody(URI publicationBucketUri, List<URI> affiliationApprovals) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = nonNull(publicationBucketUri)
                       ? constructBody(publicationBucketUri.toString(), affiliationApprovals)
                       : constructBody(affiliationApprovals);
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private static String constructBody(List<URI> affiliationApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(AFFILIATION_APPROVALS_FIELD,
                          affiliationApprovals
                    )))).orElseThrow();
    }

    private static String constructBody(String publicationId, List<URI> affiliationApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(PUBLICATION_BUCKET_URI_FIELD, publicationId),
                    entry(AFFILIATION_APPROVALS_FIELD,
                          affiliationApprovals
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
