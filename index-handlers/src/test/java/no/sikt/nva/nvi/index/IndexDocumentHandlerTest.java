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
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.PeriodRepository;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.utils.JsonUtils;
import no.sikt.nva.nvi.index.model.Affiliation;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import no.sikt.nva.nvi.index.model.Contributor;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.test.ExpandedResourceGenerator;
import no.sikt.nva.nvi.test.LocalDynamoTest;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IndexDocumentHandlerTest extends LocalDynamoTest {

    private static final URI NVI_CONTEXT = URI.create("https://api.nva.aws.unit.no/context/nvi.jsonld");
    private static final String HARD_CODED_PART_OF = "HARD_CODED_PART_OF";
    private static final String RESOURCES_BUCKET_NAME = "RESOURCES_BUCKET_NAME";
    private static final String CANDIDATES_BUCKET_NAME = "CANDIDATES_BUCKET_NAME";
    private static final Context CONTEXT = mock(Context.class);
    private IndexDocumentHandler handler;
    private CandidateRepository candidateRepository;
    private PeriodRepository periodRepository;

    private S3Driver resourcesS3Driver;
    private S3Driver candidateS3Driver;

    @BeforeEach
    void setup() {
        var s3Client = new FakeS3Client();
        resourcesS3Driver = new S3Driver(s3Client, RESOURCES_BUCKET_NAME);
        candidateS3Driver = new S3Driver(s3Client, CANDIDATES_BUCKET_NAME);
        var localDynamoDbClient = initializeTestDatabase();
        candidateRepository = new CandidateRepository(localDynamoDbClient);
        periodRepository = new PeriodRepository(localDynamoDbClient);
        handler = new IndexDocumentHandler();
    }

    @Test
    void shouldBuildIndexDocumentAndPersistInS3WhenReceivingSQSEvent() {
        var candidate = randomApplicableCandidate();
        var expectedIndexDocument = setUpExistingResourceInS3AndGenerateExpectedDocument(candidate);
        var event = createSqsEventWithDynamoDbRecord(candidate.getIdentifier());
        handler.handleRequest(event, CONTEXT);
        var actualPersistedIndexDocument = candidateS3Driver.getFile(UnixPath.of(candidate.getIdentifier().toString()));
        var actualIndexDocument = parseJson(actualPersistedIndexDocument);
        assertEquals(expectedIndexDocument, actualIndexDocument);
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

    private NviCandidateIndexDocument setUpExistingResourceInS3AndGenerateExpectedDocument(
        Candidate persistedCandidate) {
        var expandedResource = createExpandedResource(persistedCandidate);
        insertResourceInS3(expandedResource,
                           UnixPath.of(persistedCandidate.getPublicationDetails().publicationBucketUri().toString()));
        return createExpectedIndexDocument(expandedResource, persistedCandidate);
    }

    private void insertResourceInS3(JsonNode expandedResource, UnixPath path) {
        attempt(() -> resourcesS3Driver.insertFile(path, asString(expandedResource)));
    }

    private NviCandidateIndexDocument createExpectedIndexDocument(JsonNode expandedResource, Candidate candidate) {
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
