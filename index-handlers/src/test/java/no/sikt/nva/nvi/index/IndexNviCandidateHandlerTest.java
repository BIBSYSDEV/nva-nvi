package no.sikt.nva.nvi.index;

import static java.util.Map.entry;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.ExpandedResourceGenerator.createExpandedResource;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomLocalDate;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.ioutils.IoUtils.stringToStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contexts;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IndexNviCandidateHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String ERROR_MESSAGE_BODY_INVALID = "Message body invalid";

    public static final String PUBLICATION_ID_FIELD = "publicationBucketUri";
    public static final String AFFILIATION_APPROVALS_FIELD = "approvalAffiliations";
    public static final String HOST = "https://localhost";

    private static final String EXPANDED_RESOURCES_BUCKET = new Environment().readEnv(
        "EXPANDED_RESOURCES_BUCKET");
    private IndexNviCandidateHandler handler;

    private S3Driver s3Driver;

    private FakeSearchClient indexClient;

    @BeforeEach
    void setup() {
        var s3Client = new FakeS3Client();
        indexClient = new FakeSearchClient();
        s3Driver = new S3Driver(s3Client, EXPANDED_RESOURCES_BUCKET);
        var storageReader = new FakeStorageReader(s3Client);
        handler = new IndexNviCandidateHandler(storageReader, indexClient);
    }

    @Test
    void shouldAddDocumentToIndexWhenNviCandidateExistsInResourcesStorage() {
        var identifier = UUID.randomUUID();
        var publicationId = constructPublicationId(identifier);
        var affiliationUri = randomUri();
        var expectedIndexDocument = constructExpectedDocumentAndPrepareStoredResource(identifier,
                                                                                      publicationId,
                                                                                      affiliationUri,
                                                                                      randomLocalDate().toString());
        var sqsEvent = createEventWithMessageBody(publicationId, List.of(affiliationUri.toString()));

        handler.handleRequest(sqsEvent, CONTEXT);
        var allIndexDocuments = indexClient.listAllDocuments();

        assertThat(allIndexDocuments, containsInAnyOrder(expectedIndexDocument));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-01-01", "2023"})
    void shouldAddDocumentToIndexWhenNviCandidateExistsInResourcesStorageWithDifferentDateFormats(String date) {
        var identifier = UUID.randomUUID();
        var publicationId = constructPublicationId(identifier);
        var affiliationUri = randomUri();
        var expectedIndexDocument = constructExpectedDocumentAndPrepareStoredResource(identifier,
                                                                                      publicationId,
                                                                                      affiliationUri,
                                                                                      date);
        var sqsEvent = createEventWithMessageBody(publicationId, List.of(affiliationUri.toString()));

        handler.handleRequest(sqsEvent, CONTEXT);
        var allIndexDocuments = indexClient.listAllDocuments();

        assertThat(allIndexDocuments, containsInAnyOrder(expectedIndexDocument));
    }

    @Test
    void shouldAddDocumentToIndexWhenResourceContainsContributorWithNullValues() {
        var identifier = UUID.randomUUID();
        var publicationId = constructPublicationId(identifier);
        var affiliationUri = randomUri();
        var expectedIndexDocument =
            constructExpectedDocumentAndPrepareStoredResourceWithContributorWithNullValues(
                identifier,
                publicationId,
                affiliationUri);
        var sqsEvent = createEventWithMessageBody(publicationId, List.of(affiliationUri.toString()));

        handler.handleRequest(sqsEvent, CONTEXT);
        var allIndexDocuments = indexClient.listAllDocuments();

        assertThat(allIndexDocuments, containsInAnyOrder(expectedIndexDocument));
    }

    @Test
    void shouldLogErrorWhenMessageBodyInvalid() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithInvalidBody();

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    @Test
    void shouldLogErrorWhenMessageBodyPublicationIdNull() {
        var appender = LogUtils.getTestingAppenderForRootLogger();
        var sqsEvent = createEventWithMessageBody(null, Collections.emptyList());

        handler.handleRequest(sqsEvent, CONTEXT);

        assertThat(appender.getMessages(), containsString(ERROR_MESSAGE_BODY_INVALID));
    }

    //TODO: TEST Should log error if unexpected error occurs

    private static URI constructPublicationId(UUID identifier) {
        return UriWrapper.fromUri(HOST).addChild(identifier.toString()).getUri();
    }

    private static SQSEvent createEventWithMessageBody(URI publicationBucketUri, List<String> affiliationApprovals) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        var body = nonNull(publicationBucketUri)
                       ? constructBody(publicationBucketUri.toString(), affiliationApprovals)
                       : constructBody(affiliationApprovals);
        message.setBody(body);
        sqsEvent.setRecords(List.of(message));
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
                          affiliationApprovals
                    )))).orElseThrow();
    }

    private static String constructBody(List<String> affiliationApprovals) {
        return attempt(
            () -> objectMapper.writeValueAsString(
                Map.ofEntries(
                    entry(AFFILIATION_APPROVALS_FIELD,
                          affiliationApprovals
                    )))).orElseThrow();
    }

    private static PublicationDetails createPublication(URI publicationId, String instanceType, String publicationDate,
                                                        List<Contributor> contributors, String title) {
        return new PublicationDetails(publicationId.toString(),
                                      instanceType,
                                      title,
                                      publicationDate,
                                      contributors);
    }

    private NviCandidateIndexDocument constructExpectedDocumentAndPrepareStoredResource(UUID identifier,
                                                                                        URI publicationId,
                                                                                        URI affiliationUri,
                                                                                        String publicationDate) {

        var documentType = "NviCandidate";
        var affiliation = new Affiliation(
            affiliationUri.toString(),
            Map.of("nb", randomString(), "en",
                   randomString()), ApprovalStatus.PENDING);
        var year = publicationDate.length() > 4
                       ? String.valueOf(LocalDate.parse(publicationDate).getYear())
                       : publicationDate;
        return prepareNviCandidateFile(identifier, publicationId, year, documentType, randomString(), publicationDate,
                                       List.of(affiliation), List.of(new Contributor(
                randomUri().toString(),
                randomString(),
                randomUri().toString())));
    }

    private NviCandidateIndexDocument constructExpectedDocumentAndPrepareStoredResourceWithContributorWithNullValues(
        UUID identifier,
        URI publicationId,
        URI affiliationUri) {
        var year = "2023";
        var documentType = "NviCandidate";
        var instanceType = "AcademicArticle";
        var publicationDate = "2023-01-01";
        var affiliation = new Affiliation(
            affiliationUri.toString(),
            Map.of("nb", randomString(), "en",
                   randomString()), ApprovalStatus.PENDING);
        return prepareNviCandidateFile(identifier, publicationId, year, documentType, instanceType, publicationDate,
                                       List.of(affiliation), List.of(new Contributor(null,
                                                                                     randomString(),
                                                                                     null)));
    }

    private NviCandidateIndexDocument prepareNviCandidateFile(UUID identifier, URI publicationId, String year,
                                                              String documentType, String instanceType,
                                                              String publicationDate,
                                                              List<Affiliation> affiliations,
                                                              List<Contributor> contributors) {
        var expectedNviCandidateIndexDocument = new NviCandidateIndexDocument(URI.create(Contexts.NVI_CONTEXT),
                                                                              identifier.toString(),
                                                                              year,
                                                                              documentType,
                                                                              createPublication(publicationId,
                                                                                                instanceType,
                                                                                                publicationDate,
                                                                                                contributors,
                                                                                                instanceType),
                                                                              affiliations);
        var expandedResource = createExpandedResource(expectedNviCandidateIndexDocument, HOST);
        attempt(() -> s3Driver.insertFile(UnixPath.of(expectedNviCandidateIndexDocument.identifier()),
                                          stringToStream(expandedResource))).orElseThrow();

        return expectedNviCandidateIndexDocument;
    }
}
