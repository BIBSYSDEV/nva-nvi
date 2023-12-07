package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.test.DynamoDbTestUtils.eventWithCandidateIdentifier;
import static no.sikt.nva.nvi.test.DynamoDbTestUtils.mapToString;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.EN_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.NB_FIELD;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.createExpandedResource;
import static no.sikt.nva.nvi.test.ExpandedResourceGenerator.extractAffiliations;
import static no.sikt.nva.nvi.test.TestUtils.createUpsertCandidateRequest;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.utils.NviCandidateIndexDocumentGenerator;
import no.sikt.nva.nvi.test.ExpandedResourceGenerator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IndexDocumentHandlerTest extends LocalDynamoTest {

    private static final String BODY = "body";
    private static final String NVI_CANDIDATES_FOLDER = "nvi-candidates";
    private static final String GZIP_ENDING = ".gz";
    private static final String ORGANIZATION_CONTEXT = "https://bibsysdev.github.io/src/organization-context.json";
    private static final URI NVI_CONTEXT = URI.create("https://bibsysdev.github.io/src/nvi-context.json");
    private static final String HARD_CODED_PART_OF = "https://example.org/organization/hardCodedPartOf";
    private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
    private static final Context CONTEXT = mock(Context.class);
    private IndexDocumentHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;
    private S3Driver s3Reader;
    private S3Driver s3Writer;
    private AuthorizedBackendUriRetriever uriRetriever;

    @BeforeEach
    void setup() {
        var s3Client = new FakeS3Client();
        var environment = new Environment();
        var bucketName = environment.readEnv(EXPANDED_RESOURCES_BUCKET);
        s3Reader = new S3Driver(s3Client, bucketName);
        var storageReader = new S3StorageReader(s3Client, bucketName);
        s3Writer = new S3Driver(s3Client, bucketName);
        var storageWriter = new S3StorageWriter(s3Client, bucketName);
        var localDynamoDbClient = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamoDbClient);
        periodRepository = new PeriodRepository(localDynamoDbClient);
        uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        var documentGenerator = new NviCandidateIndexDocumentGenerator(uriRetriever);
        handler = new IndexDocumentHandler(storageReader, storageWriter, candidateRepository, periodRepository,
                                           documentGenerator);
    }

    @Test
    void shouldBuildIndexDocumentAndPersistInS3WhenReceivingSqsEvent() {
        var candidate = randomApplicableCandidate();
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(candidate);
        var event = createSqsEventWithDynamoDbRecord(candidate.getIdentifier());
        mockUriRetrieverOrgResponse(candidate);
        handler.handleRequest(event, CONTEXT);
        var actualPersistedIndexDocument = s3Writer.getFile(createPath(candidate));
        var actualIndexDocument = parseJson(actualPersistedIndexDocument);
        assertEquals(expectedIndexDocument, actualIndexDocument);
    }

    private static UnixPath createPath(Candidate candidate) {
        return UnixPath.of(NVI_CANDIDATES_FOLDER).addChild(candidate.getIdentifier().toString() + GZIP_ENDING);
    }

    private static NviCandidateIndexDocument parseJson(String actualPersistedIndexDocument) {
        return attempt(() -> dtoObjectMapper.readValue(
            actualPersistedIndexDocument, NviCandidateIndexDocument.class)).orElseThrow();
    }

    private static String asString(JsonNode jsonNode) {
        return attempt(() -> dtoObjectMapper.writeValueAsString(jsonNode)).orElseThrow();
    }

    private static String generateSingleDynamoDbEventRecord(UUID candidateIdentifier) {
        return mapToString(eventWithCandidateIdentifier(candidateIdentifier).getRecords().get(0));
    }

    @NotNull
    private static ObjectNode generateOrganizationNode(String hardCodedPartOf) {
        var hardCodedPartOfNode = dtoObjectMapper.createObjectNode();
        hardCodedPartOfNode.put("id", hardCodedPartOf);
        hardCodedPartOfNode.put("type", "Organization");
        hardCodedPartOfNode.put("@context", ORGANIZATION_CONTEXT);
        return hardCodedPartOfNode;
    }

    private void mockUriRetrieverOrgResponse(Candidate candidate) {
        candidate.getPublicationDetails()
            .creators()
            .stream()
            .flatMap(creator -> creator.affiliations().stream())
            .forEach(this::mockOrganizationResponse);
    }

    private void mockOrganizationResponse(URI affiliation) {
        when(uriRetriever.getRawContent(eq(affiliation), any())).thenReturn(
            generateResponse(affiliation));
    }

    private Optional<String> generateResponse(URI affiliation) {
        var affiliationOrganizationNode = generateOrganizationNode(affiliation.toString());
        var partOfArrayNode = dtoObjectMapper.createArrayNode();
        var partOfOrganizationNode = generateOrganizationNode(HARD_CODED_PART_OF);
        partOfArrayNode.add(partOfOrganizationNode);
        affiliationOrganizationNode.set("partOf", partOfArrayNode);
        return Optional.of(
            attempt(() -> dtoObjectMapper.writeValueAsString(affiliationOrganizationNode)).orElseThrow());
    }

    private NviCandidateIndexDocument setUpExistingResourceInS3AndGenerateExpectedDocument(
        Candidate persistedCandidate) {
        var expandedResource = createExpandedResource(persistedCandidate);
        var resourceIndexDocument = createResourceIndexDocument(expandedResource);
        var resourcePath =
            UriWrapper.fromUri(persistedCandidate.getPublicationDetails().publicationBucketUri())
                .getPath()
                .getLastPathElement();
        insertResourceInS3(resourceIndexDocument, UnixPath.of(resourcePath));
        return createExpectedNviIndexDocument(expandedResource, persistedCandidate);
    }

    private JsonNode createResourceIndexDocument(JsonNode expandedResource) {
        var root = objectMapper.createObjectNode();
        root.set(BODY, expandedResource);
        return root;
    }

    private void insertResourceInS3(JsonNode indexDocument, UnixPath path) {
        attempt(() -> s3Reader.insertFile(path, asString(indexDocument)));
    }

    private NviCandidateIndexDocument createExpectedNviIndexDocument(JsonNode expandedResource, Candidate candidate) {
        return NviCandidateIndexDocument.builder()
                   .withContext(NVI_CONTEXT)
                   .withIdentifier(candidate.getIdentifier().toString())
                   .withApprovals(expandApprovals(candidate))
                   .withPoints(candidate.getTotalPoints())
                   .withPublicationDetails(expandPublicationDetails(candidate, expandedResource))
                   .withNumberOfApprovals(candidate.getApprovals().size())
                   .build();
    }

    private PublicationDetails expandPublicationDetails(Candidate candidate, JsonNode expandedResource) {
        return PublicationDetails.builder()
                   .withType(ExpandedResourceGenerator.extractType(expandedResource))
                   .withId(candidate.getPublicationDetails().publicationId().toString())
                   .withTitle(ExpandedResourceGenerator.extractTitle(expandedResource))
                   .withPublicationDate(mapToPublicationDate(candidate.getPublicationDetails().publicationDate()))
                   .withContributors(mapToContributors(ExpandedResourceGenerator.extractContributors(expandedResource)))
                   .build();
    }

    private PublicationDate mapToPublicationDate(
        no.sikt.nva.nvi.common.service.model.PublicationDetails.PublicationDate publicationDate) {
        return new PublicationDate(publicationDate.year(), publicationDate.month(), publicationDate.day());
    }

    private List<Contributor> mapToContributors(ArrayNode contributorNodes) {
        return JsonUtils.streamNode(contributorNodes)
                   .map(this::toContributor)
                   .toList();
    }

    private Contributor toContributor(JsonNode contributorNode) {
        return Contributor.builder()
                   .withId(ExpandedResourceGenerator.extractId(contributorNode))
                   .withName(ExpandedResourceGenerator.extractName(contributorNode))
                   .withOrcid(ExpandedResourceGenerator.extractOrcid(contributorNode))
                   .withRole(ExpandedResourceGenerator.extractRole(contributorNode))
                   .withAffiliations(mapToAffiliations(extractAffiliations(contributorNode)))
                   .build();
    }

    private List<Affiliation> mapToAffiliations(List<URI> uris) {
        return uris.stream().map(this::toAffiliation).toList();
    }

    private Affiliation toAffiliation(URI uri) {
        return Affiliation.builder()
                   .withId(uri.toString())
                   .withPartOf(List.of(HARD_CODED_PART_OF))
                   .build();
    }

    private List<no.sikt.nva.nvi.index.model.Approval> expandApprovals(Candidate candidate) {
        return candidate.getApprovals()
                   .entrySet()
                   .stream()
                   .map(this::toApproval)
                   .toList();
    }

    private no.sikt.nva.nvi.index.model.Approval toApproval(Entry<URI, Approval> approvalEntry) {
        var assignee = approvalEntry.getValue().getAssignee();
        return no.sikt.nva.nvi.index.model.Approval.builder()
                   .withId(approvalEntry.getKey().toString())
                   .withApprovalStatus(ApprovalStatus.fromValue(approvalEntry.getValue().getStatus().getValue()))
                   .withAssignee(Objects.nonNull(assignee) ? assignee.value() : null)
                   .withLabels(Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD, HARDCODED_NORWEGIAN_LABEL))
                   .build();
    }

    private Candidate randomApplicableCandidate() {
        return Candidate.fromRequest(createUpsertCandidateRequest(2023), candidateRepository, periodRepository)
                   .orElseThrow();
    }

    private SQSEvent createSqsEventWithDynamoDbRecord(UUID candidateIdentifier) {
        var sqsEvent = new SQSEvent();
        var message = new SQSMessage();
        message.setBody(generateSingleDynamoDbEventRecord(candidateIdentifier));
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }
}
