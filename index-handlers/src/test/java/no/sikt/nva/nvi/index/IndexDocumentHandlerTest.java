package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.createExpandedResource;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.GZIP_ENDING;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.HARD_CODED_PART_OF;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.NVI_CANDIDATES_FOLDER;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.NVI_CONTEXT;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.expandApprovals;
import static no.sikt.nva.nvi.test.IndexDocumentTestUtils.expandPublicationDetails;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.test.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.ConsumptionAttributes;
import no.sikt.nva.nvi.index.model.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.test.FakeSqsClient;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.model.SqsException;

public class IndexDocumentHandlerTest extends LocalDynamoTest {

    private static final Environment ENVIRONMENT = new Environment();
    private static final String BODY = "body";
    private static final String ORGANIZATION_CONTEXT = "https://bibsysdev.github.io/src/organization-context.json";
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private static final Context CONTEXT = mock(Context.class);
    private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
    private static final String INDEX_DLQ = "INDEX_DLQ";
    private static final String INDEX_DLQ_URL = ENVIRONMENT.readEnv(INDEX_DLQ);
    private static final String CRISTIN_VERSION = "; version=2023-05-26";
    private static final String MEDIA_TYPE_JSON_V2 = "application/json" + CRISTIN_VERSION;
    private final S3Client s3Client = new FakeS3Client();
    private IndexDocumentHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private S3Driver s3Reader;
    private S3Driver s3Writer;
    private UriRetriever uriRetriever;
    private FakeSqsClient sqsClient;

    @BeforeEach
    void setup() {
        s3Reader = new S3Driver(s3Client, BUCKET_NAME);
        s3Writer = new S3Driver(s3Client, BUCKET_NAME);
        var localDynamoDbClient = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamoDbClient);
        periodRepository = new PeriodRepository(localDynamoDbClient);
        uriRetriever = mock(UriRetriever.class);
        sqsClient = new FakeSqsClient();
        handler = new IndexDocumentHandler(new S3StorageReader(s3Client, BUCKET_NAME),
                                           new S3StorageWriter(s3Client, BUCKET_NAME),
                                           sqsClient,
                                           candidateRepository, periodRepository, uriRetriever,
                                           ENVIRONMENT);
    }

    @Test
    void shouldBuildIndexDocumentAndPersistInS3WhenReceivingSqsEvent() {
        var candidate = randomApplicableCandidate();
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(
            candidate).indexDocument();
        var event = createEvent(candidate.getIdentifier());
        mockUriRetrieverOrgResponse(candidate);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
        assertEquals(expectedIndexDocument, actualIndexDocument);
    }

    @Test
    void shouldNotBuildIndexDocumentIfCandidateIsNotApplicable() {
        var candidate = randomApplicableCandidate();
        setUpExistingResourceInS3AndGenerateExpectedDocument(candidate);
        mockUriRetrieverOrgResponse(candidate);
        makeNonApplicable(candidate);
        var event = createEvent(candidate.getIdentifier());
        handler.handleRequest(event, CONTEXT);
        assertThrows(NoSuchKeyException.class, () -> s3Reader.getFile(createPath(candidate)));
    }

    @Test
    void shouldFetchOrganizationLabelsFromCristinApiWhenExpandedResourceIsMissingTopLevelOrganization() {
        var candidate = randomApplicableCandidate();
        var expandedResource = createExpandedResource(candidate);
        setupResourceMissingTopLevelOrganizationsInS3(expandedResource, candidate);
        var expectedIndexDocument = IndexDocumentWithConsumptionAttributes.from(
            createExpectedNviIndexDocument(expandedResource, candidate)).indexDocument();
        var event = createEvent(candidate.getIdentifier());
        mockUriRetrieverOrgResponse(candidate);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
        assertEquals(expectedIndexDocument, actualIndexDocument);
    }

    @Test
    void shouldBuildIndexDocumentWithConsumptionAttributes() {
        var candidate = randomApplicableCandidate();
        var expectedConsumptionAttributes = setUpExistingResourceInS3AndGenerateExpectedDocument(
            candidate).consumptionAttributes();
        var event = createEvent(candidate.getIdentifier());
        mockUriRetrieverOrgResponse(candidate);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate)));
        assertEquals(expectedConsumptionAttributes, actualIndexDocument.consumptionAttributes());
    }

    @Test
    void shouldNotExpandAffiliationsWhenContributorIsNotNviCreator() {
        var candidate = randomApplicableCandidate();
        var expectedConsumptionAttributes = setUpExistingResourceWithNonNviCreatorAffiliations(candidate);
        var event = createEvent(candidate.getIdentifier());
        mockUriRetrieverOrgResponse(candidate);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate)));
        assertEquals(expectedConsumptionAttributes, actualIndexDocument.consumptionAttributes());
    }

    @Test
    void shouldSendSqsEventWhenIndexDocumentIsPersisted() {
        var candidate = randomApplicableCandidate();
        setUpExistingResourceInS3(candidate);
        mockUriRetrieverOrgResponse(candidate);
        handler.handleRequest(createEvent(candidate.getIdentifier()), CONTEXT);
        var expectedEvent = createExpectedEventMessageBody(candidate);
        var actualEvent = sqsClient.getSentMessages().get(0).messageBody();
        assertEquals(expectedEvent, actualEvent);
    }

    @Test
    void shouldSendMessageToDlqWhenFailingToProcessEvent() {
        var candidate = randomApplicableCandidate();
        setUpExistingResourceInS3(candidate);
        mockUriRetrieverOrgResponse(candidate);
        var mockedSqsClient = setupFailingSqsClient(candidate);
        var handler = new IndexDocumentHandler(new S3StorageReader(s3Client, BUCKET_NAME),
                                               new S3StorageWriter(s3Client, BUCKET_NAME),
                                               mockedSqsClient,
                                               candidateRepository, periodRepository, uriRetriever,
                                               ENVIRONMENT);
        var event = createEvent(List.of(candidate.getIdentifier()));
        handler.handleRequest(event, CONTEXT);
        verify(mockedSqsClient, times(1)).sendMessage(any(), eq(INDEX_DLQ_URL), eq(candidate.getIdentifier()));
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToSendEventForOneCandidate() {
        var candidateToFail = randomApplicableCandidate();
        var candidateToSucceed = randomApplicableCandidate();
        setUpExistingResourceInS3(candidateToSucceed);
        setUpExistingResourceInS3(candidateToFail);
        mockUriRetrieverOrgResponse(candidateToSucceed);
        mockUriRetrieverOrgResponse(candidateToFail);
        var mockedSqsClient = setupFailingSqsClient(candidateToFail);
        var handler = new IndexDocumentHandler(new S3StorageReader(s3Client, BUCKET_NAME),
                                               new S3StorageWriter(s3Client, BUCKET_NAME),
                                               mockedSqsClient,
                                               candidateRepository, periodRepository, uriRetriever,
                                               ENVIRONMENT);
        var event = createEvent(List.of(candidateToFail.getIdentifier(), candidateToSucceed.getIdentifier()));
        assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToReadOneResourceFromStorage() {
        var candidateToFail = randomApplicableCandidate();
        var candidateToSucceed = randomApplicableCandidate();
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(
            candidateToSucceed);
        var event = createEvent(List.of(candidateToFail.getIdentifier(), candidateToSucceed.getIdentifier()));
        mockUriRetrieverOrgResponse(candidateToSucceed);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Reader.getFile(createPath(candidateToSucceed)));
        assertEquals(expectedIndexDocument, actualIndexDocument);
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToGenerateDocumentForOneCandidate() {
        var candidateToFail = randomApplicableCandidate();
        var candidateToSucceed = randomApplicableCandidate();
        setUpExistingResourceInS3AndGenerateExpectedDocument(candidateToFail);
        mockUriRetrieverFailure(candidateToFail);
        mockUriRetrieverOrgResponse(candidateToSucceed);
        var event = createEvent(List.of(candidateToFail.getIdentifier(), candidateToSucceed.getIdentifier()));
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(
            candidateToSucceed);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Reader.getFile(createPath(candidateToSucceed)));
        assertEquals(expectedIndexDocument, actualIndexDocument);
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToPersistDocumentForOneCandidate() throws IOException {
        var candidateToFail = randomApplicableCandidate();
        var candidateToSucceed = randomApplicableCandidate();
        var event = createEvent(List.of(candidateToFail.getIdentifier(), candidateToSucceed.getIdentifier()));
        mockUriRetrieverOrgResponse(candidateToSucceed);
        mockUriRetrieverOrgResponse(candidateToFail);
        var s3Writer = mockS3WriterFailingForOneCandidate(candidateToSucceed, candidateToFail
        );
        var handler = new IndexDocumentHandler(new S3StorageReader(s3Client, BUCKET_NAME),
                                               s3Writer, sqsClient, candidateRepository, periodRepository, uriRetriever,
                                               ENVIRONMENT);
        assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingToFetchOneCandidate() {
        var candidateToSucceed = randomApplicableCandidate();
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(
            candidateToSucceed);
        var event = createEvent(List.of(UUID.randomUUID(), candidateToSucceed.getIdentifier()));
        mockUriRetrieverOrgResponse(candidateToSucceed);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidateToSucceed)));
        assertEquals(expectedIndexDocument, actualIndexDocument);
    }

    @Test
    void shouldNotFailForWholeBatchWhenFailingParseOneEventRecord() {
        var candidateToSucceed = randomApplicableCandidate();
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(
            candidateToSucceed);
        var event = createEventWithOneInvalidRecord(candidateToSucceed.getIdentifier());
        mockUriRetrieverOrgResponse(candidateToSucceed);
        handler.handleRequest(event, CONTEXT);
        var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidateToSucceed)));
        assertEquals(expectedIndexDocument, actualIndexDocument);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> createResponse(String body) {
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static FakeSqsClient setupFailingSqsClient(Candidate candidate) {
        var expectedFailingMessage = new PersistedIndexDocumentMessage(
            generateBucketUri(candidate)).asJsonString();
        var mockedSqsClient = mock(FakeSqsClient.class);
        var sqsException = SqsException.builder().message("Some exception message").build();
        when(mockedSqsClient.sendMessage(eq(expectedFailingMessage), anyString())).thenThrow(
            sqsException);
        return mockedSqsClient;
    }

    private static URI extractOneAffiliation(Candidate candidateToFail) {
        return candidateToFail.getPublicationDetails()
                   .creators()
                   .stream()
                   .flatMap(creator -> creator.affiliations().stream())
                   .findFirst()
                   .orElseThrow();
    }

    private static UnixPath createPath(Candidate candidate) {
        return UnixPath.of(NVI_CANDIDATES_FOLDER).addChild(candidate.getIdentifier().toString() + GZIP_ENDING);
    }

    private static URI generateBucketUri(Candidate candidate) {
        return new UriWrapper(S3_SCHEME, BUCKET_NAME)
                   .addChild(createPath(candidate))
                   .getUri();
    }

    private static IndexDocumentWithConsumptionAttributes parseJson(String actualPersistedIndexDocument) {
        return attempt(() -> dtoObjectMapper.readValue(
            actualPersistedIndexDocument, IndexDocumentWithConsumptionAttributes.class)).orElseThrow();
    }

    private static String asString(JsonNode jsonNode) {
        return attempt(() -> dtoObjectMapper.writeValueAsString(jsonNode)).orElseThrow();
    }

    private static ObjectNode generateOrganizationNode(String organizationId) {
        var organizationNode = dtoObjectMapper.createObjectNode();
        organizationNode.put("id", organizationId);
        organizationNode.put("type", "Organization");
        addLabels(organizationNode);
        organizationNode.put("@context", ORGANIZATION_CONTEXT);
        return organizationNode;
    }

    private static void addLabels(ObjectNode organizationNode) {
        var labels = dtoObjectMapper.createObjectNode();
        labels.put("nb", HARDCODED_NORWEGIAN_LABEL);
        labels.put("en", HARDCODED_ENGLISH_LABEL);
        organizationNode.set("labels", labels);
    }

    private static String extractResourceIdentifier(Candidate persistedCandidate) {
        return UriWrapper.fromUri(persistedCandidate.getPublicationDetails().publicationBucketUri())
                   .getPath()
                   .getLastPathElement();
    }

    private void makeNonApplicable(Candidate candidate) {
        Candidate.updateNonCandidate(() -> candidate.getPublicationDetails().publicationId(), candidateRepository);
    }

    private ConsumptionAttributes setUpExistingResourceWithNonNviCreatorAffiliations(Candidate candidate) {
        var expandedResource = createExpandedResource(candidate, List.of(randomUri()));
        var resourceIndexDocument = createResourceIndexDocument(expandedResource);
        var resourcePath = extractResourceIdentifier(candidate);
        insertResourceInS3(resourceIndexDocument, UnixPath.of(resourcePath));
        var indexDocument = createExpectedNviIndexDocument(expandedResource, candidate);
        return IndexDocumentWithConsumptionAttributes.from(indexDocument)
                   .consumptionAttributes();
    }

    private void setupResourceMissingTopLevelOrganizationsInS3(JsonNode expandedResource, Candidate candidate) {
        var expandedResourceWithoutTopLevelOrganization = removeTopLevelOrganization((ObjectNode) expandedResource);
        insertResourceInS3(createResourceIndexDocument(expandedResourceWithoutTopLevelOrganization), UnixPath.of(
            extractResourceIdentifier(candidate)));
    }

    private JsonNode removeTopLevelOrganization(ObjectNode expandedResource) {
        expandedResource.remove("topLevelOrganizations");
        return expandedResource;
    }

    private String createExpectedEventMessageBody(Candidate candidate) {
        return new PersistedIndexDocumentMessage(generateBucketUri(candidate)).asJsonString();
    }

    private void mockUriRetrieverFailure(Candidate candidate) {
        when(uriRetriever.getRawContent(eq(extractOneAffiliation(candidate)), any())).thenReturn(Optional.empty());
    }

    private S3StorageWriter mockS3WriterFailingForOneCandidate(Candidate candidateToSucceed,
                                                               Candidate candidateToFail)
        throws IOException {
        var expectedIndexDocumentToFail = setUpExistingResourceInS3AndGenerateExpectedDocument(candidateToFail
        );
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(candidateToSucceed
        );
        var s3Writer = mock(S3StorageWriter.class);
        when(s3Writer.write(eq(expectedIndexDocumentToFail))).thenThrow(new IOException("Some exception message"));
        when(s3Writer.write(eq(expectedIndexDocument))).thenReturn(
            s3BucketUri().addChild(candidateToSucceed.getIdentifier().toString()).getUri());
        return s3Writer;
    }

    private UriWrapper s3BucketUri() {
        return new UriWrapper(S3_SCHEME, BUCKET_NAME);
    }

    private void mockUriRetrieverOrgResponse(Candidate candidate) {
        candidate.getPublicationDetails()
            .creators()
            .stream()
            .flatMap(creator -> creator.affiliations().stream())
            .forEach(this::mockOrganizationResponse);

        candidate.getApprovals().keySet().forEach(this::mockOrganizationResponse);
    }

    private void mockOrganizationResponse(URI affiliationId) {
        var httpResponse = generateResponse(affiliationId);
        when(uriRetriever.fetchResponse(eq(affiliationId), eq(MEDIA_TYPE_JSON_V2))).thenReturn(httpResponse);
    }

    private Optional<HttpResponse<String>> generateResponse(URI affiliation) {
        var affiliationOrganizationNode = generateOrganizationNode(affiliation.toString());
        var partOfArrayNode = dtoObjectMapper.createArrayNode();
        var partOfOrganizationNode = generateOrganizationNode(HARD_CODED_PART_OF);
        partOfArrayNode.add(partOfOrganizationNode);
        affiliationOrganizationNode.set("partOf", partOfArrayNode);
        return Optional.of(createResponse(
            attempt(() -> dtoObjectMapper.writeValueAsString(affiliationOrganizationNode)).orElseThrow()));
    }

    private IndexDocumentWithConsumptionAttributes setUpExistingResourceInS3AndGenerateExpectedDocument(
        Candidate persistedCandidate) {
        var expandedResource = createExpandedResource(persistedCandidate);
        var resourceIndexDocument = createResourceIndexDocument(expandedResource);
        var resourcePath = extractResourceIdentifier(persistedCandidate);
        insertResourceInS3(resourceIndexDocument, UnixPath.of(resourcePath));
        var indexDocument = createExpectedNviIndexDocument(expandedResource, persistedCandidate);
        return IndexDocumentWithConsumptionAttributes.from(indexDocument);
    }

    private void setUpExistingResourceInS3(Candidate persistedCandidate) {
        var expandedResource = createExpandedResource(persistedCandidate);
        var resourceIndexDocument = createResourceIndexDocument(expandedResource);
        var resourcePath = extractResourceIdentifier(persistedCandidate);
        insertResourceInS3(resourceIndexDocument, UnixPath.of(resourcePath));
    }

    private JsonNode createResourceIndexDocument(JsonNode expandedResource) {
        var root = objectMapper.createObjectNode();
        root.set(BODY, expandedResource);
        return root;
    }

    private void insertResourceInS3(JsonNode indexDocument, UnixPath path) {
        attempt(() -> s3Reader.insertFile(path, asString(indexDocument))).orElseThrow();
    }

    private NviCandidateIndexDocument createExpectedNviIndexDocument(JsonNode expandedResource, Candidate candidate) {
        return NviCandidateIndexDocument.builder()
                   .withContext(NVI_CONTEXT)
                   .withIdentifier(candidate.getIdentifier())
                   .withApprovals(expandApprovals(candidate))
                   .withPoints(candidate.getTotalPoints())
                   .withPublicationDetails(expandPublicationDetails(candidate, expandedResource))
                   .withNumberOfApprovals(candidate.getApprovals().size())
                   .build();
    }

    private Candidate randomApplicableCandidate() {
        return Candidate.upsert(createUpsertCandidateRequest(2023), candidateRepository, periodRepository)
                   .orElseThrow();
    }
}
