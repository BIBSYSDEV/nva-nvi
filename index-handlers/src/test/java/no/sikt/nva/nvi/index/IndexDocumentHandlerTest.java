package no.sikt.nva.nvi.index;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getIndexDocumentHandlerEnvironment;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.common.TestScenario.constructPublicationBucketPath;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequestWithSingleAffiliation;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateDao;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.setupReportedCandidate;
import static no.sikt.nva.nvi.common.db.DbApprovalStatusFixtures.randomApprovalDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationChannelFixtures.randomDbPublicationChannelBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationDetailsFixtures.randomPublicationBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_BODY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_TYPE;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.GZIP_ENDING;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.HARD_CODED_INTERMEDIATE_ORGANIZATION;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.HARD_CODED_TOP_LEVEL_ORG;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.NVI_CANDIDATES_FOLDER;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.expandApprovals;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.expandPublicationDetails;
import static no.sikt.nva.nvi.test.TestConstants.ADDITIONAL_IDENTIFIERS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.EN_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HANDLE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HANDLE_IDENTIFIER_TYPE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.LABELS_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.NB_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.TYPE_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.VALUE_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.ConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;
import no.unit.nva.auth.uriretriever.UriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.model.SqsException;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
class IndexDocumentHandlerTest {

  private static final String JSON_PTR_CONTRIBUTOR = "/publicationDetails/contributors";
  private static final String JSON_PTR_APPROVALS = "/approvals";
  private static final Environment ENVIRONMENT = getIndexDocumentHandlerEnvironment();
  private static final String BODY = "body";
  private static final String ORGANIZATION_CONTEXT =
      "https://bibsysdev.github.io/src/organization-context.json";
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final Context CONTEXT = mock(Context.class);
  private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String INDEX_DLQ_URL = ENVIRONMENT.readEnv(INDEX_DLQ);
  private static final String OUTPUT_QUEUE_URL =
      ENVIRONMENT.readEnv("PERSISTED_INDEX_DOCUMENT_QUEUE_URL");
  private static final String CRISTIN_VERSION = "; version=2023-05-26";
  private static final String MEDIA_TYPE_JSON_V2 = "application/json" + CRISTIN_VERSION;
  private final S3Client s3Client = new FakeS3Client();
  private IndexDocumentHandler handler;
  private CandidateRepository candidateRepository;
  private CandidateService candidateService;
  private S3Driver s3Reader;
  private S3Driver s3Writer;
  private UriRetriever uriRetriever;
  private FakeSqsClient sqsClient;
  private TestScenario scenario;

  public static Stream<Arguments> channelTypeIssnProvider() {
    return Stream.of(
        Arguments.of(ChannelType.JOURNAL, true),
        Arguments.of(ChannelType.JOURNAL, false),
        Arguments.of(ChannelType.SERIES, true),
        Arguments.of(ChannelType.SERIES, false));
  }

  @BeforeEach
  void setup() {
    scenario = new TestScenario();
    setupOpenPeriod(scenario, CURRENT_YEAR);
    candidateRepository = scenario.getCandidateRepository();
    candidateService = scenario.getCandidateService();

    s3Reader = new S3Driver(s3Client, BUCKET_NAME);
    s3Writer = new S3Driver(s3Client, BUCKET_NAME);
    uriRetriever = mock(UriRetriever.class);
    sqsClient = new FakeSqsClient();
    handler =
        new IndexDocumentHandler(
            new S3StorageReader(s3Client, BUCKET_NAME),
            new S3StorageWriter(s3Client, BUCKET_NAME),
            sqsClient,
            candidateService,
            uriRetriever,
            ENVIRONMENT);
  }

  @Test
  void shouldBuildIndexDocumentAndPersistInS3WhenReceivingSqsEvent() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).indexDocument();
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldExtractNviContributorsInSeparateList() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).indexDocument();
    mockUriRetrieverOrgResponse(candidate);

    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);

    var actualNviContributors =
        parseJson(s3Writer.getFile(createPath(candidate)))
            .indexDocument()
            .publicationDetails()
            .nviContributors();
    var expectedNviContributors = expectedIndexDocument.getNviContributors();
    assertEquals(expectedNviContributors, actualNviContributors);
    assertNotEquals(
        expectedIndexDocument.publicationDetails().contributors(), actualNviContributors);
  }

  @Test
  void shouldNotFailWhenCandidateIsMissingChannelTypeOrChannelId() {
    // This is not a valid state for candidates created in nva-nvi, but it might occur for
    // candidates imported via
    // Cristin
    var institutionId = randomUri();
    var dao = createCandidateDao(createDbCandidateWithoutChannelIdOrType(institutionId));
    var approvals = List.of(randomApprovalDao(dao.identifier(), institutionId));
    candidateRepository.create(dao, approvals);

    var candidate = candidateService.getCandidateByIdentifier(dao.identifier());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).indexDocument();
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  private static CandidateDao.DbCandidate createDbCandidateWithoutChannelIdOrType(
      URI organizationId) {
    var channel = new DbPublicationChannel(null, null, ScientificValue.LEVEL_ONE.getValue());
    var publicationDetails = randomPublicationBuilder(organizationId).build();
    var pointCalculation =
        randomPointCalculationBuilder(randomUri(), organizationId)
            .publicationChannel(channel)
            .build();
    return randomCandidateBuilder(organizationId, publicationDetails, pointCalculation).build();
  }

  @Test
  void shouldNotFailWhenCreatorIsUnverified() {
    var institutionId = randomUri();
    var unverifiedCreator =
        UnverifiedNviCreatorDto.builder()
            .withName(randomString())
            .withAffiliations(List.of(institutionId))
            .build();
    var request = randomUpsertRequestBuilder().withNviCreators(unverifiedCreator).build();
    candidateService.upsertCandidate(request);
    var candidate = candidateService.getCandidateByPublicationId(request.publicationId());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).indexDocument();

    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void involvedOrganizationsShouldContainTopLevelAffiliation() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, HARD_CODED_TOP_LEVEL_ORG);
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).indexDocument();
    var event = createEvent(candidate.identifier());
    mockUriResponseForTopLevelAffiliation(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void topLevelAffiliationShouldNotHavePartOf() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, HARD_CODED_TOP_LEVEL_ORG);
    setupExistingResourceInS3AndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    mockUriResponseForTopLevelAffiliation(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertEquals(
        emptyList(), extractPartOfAffiliation(actualIndexDocument, HARD_CODED_TOP_LEVEL_ORG));
  }

  @Test
  void subUnitAffiliationShouldHavePartOf() {
    var subUnitAffiliation = randomUri();
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, subUnitAffiliation);
    setupExistingResourceInS3AndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    var expectedPartOf = List.of(HARD_CODED_TOP_LEVEL_ORG, HARD_CODED_INTERMEDIATE_ORGANIZATION);
    assertThat(
        extractPartOfAffiliation(actualIndexDocument, subUnitAffiliation),
        containsInAnyOrder(expectedPartOf.toArray()));
  }

  @Test
  void shouldBuildIndexDocumentWithReportedPeriodWhenCandidateIsReported() {
    // Using repository to create reported candidate because setting Candidate as reported is not
    // implemented yet
    // TODO: Use Candidate.setReported when implemented
    var dao = setupReportedCandidate(candidateRepository, String.valueOf(CURRENT_YEAR));
    var candidate = candidateService.getCandidateByIdentifier(dao.identifier());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).indexDocument();
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldReturnEmptyLabelsWhenExpandedResourceIsMissingTopLevelOrganization() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .build()
            .createExpandedResource();
    setupResourceMissingTopLevelOrganizationsInS3(expandedResource, candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    Assertions.assertThat(actualIndexDocument.approvals())
        .hasSizeGreaterThanOrEqualTo(1)
        .extracting(ApprovalView::labels)
        .allMatch(Map::isEmpty);
  }

  @Test
  void shouldBuildIndexDocumentWithConsumptionAttributes() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedConsumptionAttributes =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).consumptionAttributes();
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate)));
    assertEquals(expectedConsumptionAttributes, actualIndexDocument.consumptionAttributes());
  }

  @Test
  void shouldSetApprovalStatusNewWhenApprovalIsPendingAndUnassigned() {
    var institutionId = randomUri();
    var candidate = randomApplicableCandidate(institutionId, institutionId);
    setupExistingResourceInS3AndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertEquals(
        ApprovalStatus.NEW,
        actualIndexDocument.getApprovalForInstitution(institutionId).approvalStatus());
  }

  @Test
  void shouldNotBuildIndexDocumentForNonApplicableCandidate() {
    var request = createUpsertCandidateRequest(randomOrganizationId()).build();
    candidateService.upsertCandidate(request);
    var candidate = candidateService.getCandidateByPublicationId(request.publicationId());
    mockUriRetrieverOrgResponse(candidate);

    candidateService.updateCandidate(createUpsertNonCandidateRequest(candidate.getPublicationId()));
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    assertThrows(NoSuchKeyException.class, () -> s3Writer.getFile(createPath(candidate)));
  }

  @Test
  void shouldNotExpandAffiliationsWhenContributorIsNotNviCreator() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedConsumptionAttributes =
        setupExistingResourceWithNonNviCreatorAffiliations(candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate)));
    assertEquals(expectedConsumptionAttributes, actualIndexDocument.consumptionAttributes());
  }

  @ParameterizedTest(name = "shouldExtractLanguageFromExpandedResource: {0}")
  @ValueSource(booleans = {true, false})
  void shouldExtractOptionalLanguageFromExpandedResource(boolean languageExists) {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .withPopulateLanguage(languageExists)
            .build()
            .createExpandedResource();
    insertInS3(expandedResource, extractResourceIdentifier(candidate));
    var expectedIndexDocument = createExpectedNviIndexDocument(expandedResource, candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @ParameterizedTest(name = "shouldExtractOptionalPrintIssnFromExpandedResource: {0}")
  @MethodSource("channelTypeIssnProvider")
  void shouldExtractOptionalPrintIssnFromExpandedResource(
      ChannelType channelType, boolean printIssnExists) {
    var candidate = randomApplicableCandidate(channelType);
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .withPopulateIssn(printIssnExists)
            .build()
            .createExpandedResource();
    insertInS3(expandedResource, extractResourceIdentifier(candidate));
    var expectedIndexDocument = createExpectedNviIndexDocument(expandedResource, candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @ParameterizedTest(name = "shouldGenerateIndexDocumentForAllPublicationChannelTypes: {0}")
  @EnumSource(
      value = ChannelType.class,
      names = {"NON_CANDIDATE"},
      mode = Mode.EXCLUDE)
  void shouldGenerateIndexDocumentForAllPublicationChannelTypes(ChannelType channelType) {
    var candidate = randomApplicableCandidate(channelType);
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .build()
            .createExpandedResource();
    insertInS3(expandedResource, extractResourceIdentifier(candidate));
    var expectedIndexDocument = createExpectedNviIndexDocument(expandedResource, candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @ParameterizedTest(name = "shouldExtractOptionalAbstractFromExpandedResource: {0}")
  @ValueSource(booleans = {true, false})
  void shouldExtractOptionalAbstractFromExpandedResource(boolean abstractExists) {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .withPopulateAbstract(abstractExists)
            .build()
            .createExpandedResource();
    insertInS3(expandedResource, extractResourceIdentifier(candidate));
    var expectedIndexDocument = createExpectedNviIndexDocument(expandedResource, candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldProduceIndexDocumentWithTypeInfo() throws JsonProcessingException {
    var candidate = setupRandomApplicableCandidate(scenario);
    setupExistingResourceInS3AndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument =
        dtoObjectMapper.readTree(s3Writer.getFile(createPath(candidate))).at(JSON_PTR_BODY);
    assertEquals("NviCandidate", actualIndexDocument.at(JSON_PTR_TYPE).textValue());
    assertEquals(
        "NviContributor",
        actualIndexDocument.at(JSON_PTR_CONTRIBUTOR).get(0).at(JSON_PTR_TYPE).textValue());
    assertEquals(
        "NviOrganization",
        actualIndexDocument
            .at(JSON_PTR_CONTRIBUTOR)
            .get(0)
            .at(JSON_PTR_AFFILIATIONS)
            .get(0)
            .at(JSON_PTR_TYPE)
            .textValue());
    assertEquals(
        "Approval",
        actualIndexDocument.at(JSON_PTR_APPROVALS).get(0).at(JSON_PTR_TYPE).textValue());
  }

  @Test
  void shouldSendSqsEventWhenIndexDocumentIsPersisted() {
    var candidate = setupRandomApplicableCandidate(scenario);
    setupExistingResourceInS3(candidate);
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    var expectedEvent = createExpectedEventMessageBody(candidate);
    var actualEvent = sqsClient.getSentMessages().getFirst().messageBody();
    assertEquals(expectedEvent, actualEvent);
  }

  @Test
  void shouldSendMessageToDlqWhenFailingToProcessEvent() {
    var candidate = setupRandomApplicableCandidate(scenario);
    setupExistingResourceInS3(candidate);
    mockUriRetrieverOrgResponse(candidate);
    sqsClient.disableDestinationQueue(OUTPUT_QUEUE_URL);

    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);

    var dlqMessages = sqsClient.getAllSentSqsEvents(INDEX_DLQ_URL);
    Assertions.assertThat(dlqMessages).hasSize(1);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToSendEventForOneCandidate() {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    setupExistingResourceInS3(candidateToSucceed);
    setupExistingResourceInS3(candidateToFail);
    mockUriRetrieverOrgResponse(candidateToSucceed);
    mockUriRetrieverOrgResponse(candidateToFail);
    var mockedSqsClient = setupFailingSqsClient(candidateToFail);
    var handler =
        new IndexDocumentHandler(
            new S3StorageReader(s3Client, BUCKET_NAME),
            new S3StorageWriter(s3Client, BUCKET_NAME),
            mockedSqsClient,
            candidateService,
            uriRetriever,
            ENVIRONMENT);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());
    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToReadOneResourceFromStorage() {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidateToSucceed);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());
    mockUriRetrieverOrgResponse(candidateToSucceed);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Reader.getFile(createPath(candidateToSucceed)));
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToGenerateDocumentForOneCandidate() {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    setupExistingResourceInS3AndGenerateExpectedDocument(candidateToFail);
    mockUriRetrieverFailure(candidateToFail);
    mockUriRetrieverOrgResponse(candidateToSucceed);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidateToSucceed);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Reader.getFile(createPath(candidateToSucceed)));
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToPersistDocumentForOneCandidate() throws IOException {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());
    mockUriRetrieverOrgResponse(candidateToSucceed);
    mockUriRetrieverOrgResponse(candidateToFail);
    var s3Writer = mockS3WriterFailingForOneCandidate(candidateToSucceed, candidateToFail);
    var handler =
        new IndexDocumentHandler(
            new S3StorageReader(s3Client, BUCKET_NAME),
            s3Writer,
            sqsClient,
            candidateService,
            uriRetriever,
            ENVIRONMENT);
    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToFetchOneCandidate() {
    var candidateToSucceed = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidateToSucceed);
    var event = createEvent(UUID.randomUUID(), candidateToSucceed.identifier());
    mockUriRetrieverOrgResponse(candidateToSucceed);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidateToSucceed)));
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingParseOneEventRecord() {
    var candidateToSucceed = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidateToSucceed);
    var event = createEventWithOneInvalidRecord(candidateToSucceed.identifier());
    mockUriRetrieverOrgResponse(candidateToSucceed);
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidateToSucceed)));
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldBuildIndexDocumentForReportedCandidateWithInvalidProperties() {
    // Given an already reported Candidate with non-applicable values
    // When the candidate is processed for indexing
    // Then an index document is created successfully
    var candidateDao = setupReportedCandidateWithInvalidProperties();
    var candidate = candidateService.getCandidateByIdentifier(candidateDao.identifier());
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidate).indexDocument();

    var event = createEvent(candidate.identifier());
    mockUriRetrieverOrgResponse(candidate);
    handler.handleRequest(event, CONTEXT);

    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  private CandidateDao setupReportedCandidateWithInvalidProperties() {
    var organizationId = randomUri();
    var publicationChannel =
        randomDbPublicationChannelBuilder()
            .channelType("Shallow")
            .scientificValue("Amazing")
            .build();
    var pointCalculation =
        randomPointCalculationBuilder(organizationId, randomUri())
            .instanceType("ComicBook")
            .publicationChannel(publicationChannel)
            .build();
    var dbCandidate =
        randomCandidateBuilder(true)
            .pointCalculation(pointCalculation)
            .reportStatus(ReportStatus.REPORTED)
            .build();
    var candidateDao = createCandidateDao(dbCandidate);
    candidateRepository.create(candidateDao, emptyList());
    return candidateDao;
  }

  private static List<URI> extractPartOfAffiliation(
      NviCandidateIndexDocument actualIndexDocument, URI affiliationId) {
    return actualIndexDocument.getNviContributors().stream()
        .map(contributor -> getTopLevelAffiliation(contributor, affiliationId))
        .findFirst()
        .map(OrganizationType::partOf)
        .orElseThrow();
  }

  private static OrganizationType getTopLevelAffiliation(
      NviContributor contributor, URI affiliationId) {
    return contributor.affiliations().stream()
        .filter(affiliation -> affiliation.id().equals(affiliationId))
        .findFirst()
        .get();
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<String> createResponse(String body) {
    var response = (HttpResponse<String>) mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(body);
    return response;
  }

  private static FakeSqsClient setupFailingSqsClient(Candidate candidate) {
    var expectedFailingMessage =
        new PersistedIndexDocumentMessage(generateBucketUri(candidate)).toJsonString();
    var mockedSqsClient = mock(FakeSqsClient.class);
    var sqsException = SqsException.builder().message("Some exception message").build();
    when(mockedSqsClient.sendMessage(eq(expectedFailingMessage), anyString()))
        .thenThrow(sqsException);
    return mockedSqsClient;
  }

  private static URI extractOneAffiliation(Candidate candidateToFail) {
    return candidateToFail.publicationDetails().getNviCreatorAffiliations().getFirst();
  }

  private static UnixPath createPath(Candidate candidate) {
    return UnixPath.of(NVI_CANDIDATES_FOLDER)
        .addChild(candidate.identifier().toString() + GZIP_ENDING);
  }

  private static URI generateBucketUri(Candidate candidate) {
    return new UriWrapper(S3_SCHEME, BUCKET_NAME).addChild(createPath(candidate)).getUri();
  }

  private static IndexDocumentWithConsumptionAttributes parseJson(
      String actualPersistedIndexDocument) {
    return attempt(
            () ->
                dtoObjectMapper.readValue(
                    actualPersistedIndexDocument, IndexDocumentWithConsumptionAttributes.class))
        .orElseThrow();
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

  private static ObjectNode generateOrganizationNodeWithHasPart(String organizationId) {
    var organizationNode = generateOrganizationNode(organizationId);
    var hasPartNode = dtoObjectMapper.createArrayNode();
    hasPartNode.add(generateOrganizationNodeWithPartOf(randomUri().toString(), organizationId));
    organizationNode.set("subUnits", hasPartNode);
    return organizationNode;
  }

  private static ObjectNode generateOrganizationNodeWithPartOf(
      String organizationId, String partOfId) {
    var organizationNode = generateOrganizationNode(organizationId);
    var partOfNode = dtoObjectMapper.createArrayNode();
    partOfNode.add(generateOrganizationNode(partOfId));
    organizationNode.set("partOf", partOfNode);
    return organizationNode;
  }

  private static void addLabels(ObjectNode organizationNode) {
    var labels = dtoObjectMapper.createObjectNode();
    labels.put(NB_FIELD, HARDCODED_NORWEGIAN_LABEL);
    labels.put(EN_FIELD, HARDCODED_ENGLISH_LABEL);
    organizationNode.set(LABELS_FIELD, labels);
  }

  private static String extractResourceIdentifier(Candidate persistedCandidate) {
    return persistedCandidate.publicationDetails().publicationIdentifier().toString();
  }

  private static ObjectNode orgWithThreeLevels(URI affiliationId) {
    var lowLevel = generateOrganizationNode(affiliationId.toString());
    var partOfArrayNode = dtoObjectMapper.createArrayNode();
    var intermediateLevel =
        generateOrganizationNode(HARD_CODED_INTERMEDIATE_ORGANIZATION.toString());
    var intermediateLevelPartOfArrayNode = dtoObjectMapper.createArrayNode();
    var topLevel = generateOrganizationNodeWithHasPart(HARD_CODED_TOP_LEVEL_ORG.toString());
    intermediateLevelPartOfArrayNode.add(topLevel);
    intermediateLevel.set("partOf", intermediateLevelPartOfArrayNode);
    partOfArrayNode.add(intermediateLevel);
    lowLevel.set("partOf", partOfArrayNode);
    return lowLevel;
  }

  private void mockUriResponseForTopLevelAffiliation(Candidate candidate) {
    candidate.publicationDetails().getNviCreatorAffiliations().forEach(this::mockTopLevelResponse);

    candidate.approvals().keySet().forEach(this::mockTopLevelResponse);
  }

  private void mockTopLevelResponse(URI affiliationId) {
    var httpResponse =
        Optional.of(
            createResponse(
                attempt(
                        () ->
                            dtoObjectMapper.writeValueAsString(
                                generateOrganizationNodeWithHasPart(affiliationId.toString())))
                    .orElseThrow()));
    when(uriRetriever.fetchResponse(eq(affiliationId), eq(MEDIA_TYPE_JSON_V2)))
        .thenReturn(httpResponse);
  }

  private ConsumptionAttributes setupExistingResourceWithNonNviCreatorAffiliations(
      Candidate candidate) {
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(candidate)
            .build()
            .createExpandedResource(List.of(randomUri()));
    insertInS3(expandedResource, extractResourceIdentifier(candidate));
    var indexDocument = createExpectedNviIndexDocument(expandedResource, candidate);
    return IndexDocumentWithConsumptionAttributes.from(indexDocument).consumptionAttributes();
  }

  private void setupResourceMissingTopLevelOrganizationsInS3(
      JsonNode expandedResource, Candidate candidate) {
    var expandedResourceWithoutTopLevelOrganization =
        removeTopLevelOrganization((ObjectNode) expandedResource);
    insertResourceInS3(
        createResourceIndexDocument(expandedResourceWithoutTopLevelOrganization),
        constructPublicationBucketPath(extractResourceIdentifier(candidate)));
  }

  private void setupResourceWithInvalidObjectInS3(JsonNode expandedResource, Candidate candidate) {
    var invalidObject = objectMapper.createObjectNode();
    invalidObject.put("invalidKey", "invalidValue");
    ((ObjectNode) expandedResource).remove("topLevelOrganizations");
    ((ObjectNode) expandedResource).set("topLevelOrganizations", invalidObject);
    insertResourceInS3(
        createResourceIndexDocument(expandedResource),
        constructPublicationBucketPath(extractResourceIdentifier(candidate)));
  }

  private JsonNode removeTopLevelOrganization(ObjectNode expandedResource) {
    expandedResource.remove("topLevelOrganizations");
    return expandedResource;
  }

  private String createExpectedEventMessageBody(Candidate candidate) {
    return new PersistedIndexDocumentMessage(generateBucketUri(candidate)).toJsonString();
  }

  private void mockUriRetrieverFailure(Candidate candidate) {
    when(uriRetriever.getRawContent(eq(extractOneAffiliation(candidate)), any()))
        .thenReturn(Optional.empty());
  }

  private S3StorageWriter mockS3WriterFailingForOneCandidate(
      Candidate candidateToSucceed, Candidate candidateToFail) throws IOException {
    var expectedIndexDocumentToFail =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidateToFail);
    var expectedIndexDocument =
        setupExistingResourceInS3AndGenerateExpectedDocument(candidateToSucceed);
    var mockedS3Writer = mock(S3StorageWriter.class);
    when(mockedS3Writer.write(argThat(matchesDocumentIdOf(expectedIndexDocumentToFail))))
        .thenThrow(new IOException("Some exception message"));
    when(mockedS3Writer.write(argThat(matchesDocumentIdOf(expectedIndexDocument))))
        .thenReturn(s3BucketUri().addChild(candidateToSucceed.identifier().toString()).getUri());
    return mockedS3Writer;
  }

  private static ArgumentMatcher<IndexDocumentWithConsumptionAttributes> matchesDocumentIdOf(
      IndexDocumentWithConsumptionAttributes expectedDocument) {
    var expectedId = expectedDocument.indexDocument().id();
    return doc -> nonNull(doc) && doc.indexDocument().id().equals(expectedId);
  }

  private UriWrapper s3BucketUri() {
    return new UriWrapper(S3_SCHEME, BUCKET_NAME);
  }

  private void mockUriRetrieverOrgResponse(Candidate candidate) {
    candidate
        .publicationDetails()
        .getNviCreatorAffiliations()
        .forEach(this::mockOrganizationResponse);
  }

  private void mockOrganizationResponse(URI affiliationId) {
    var httpResponse =
        Optional.of(
            createResponse(
                attempt(() -> dtoObjectMapper.writeValueAsString(orgWithThreeLevels(affiliationId)))
                    .orElseThrow()));
    when(uriRetriever.fetchResponse(eq(affiliationId), eq(MEDIA_TYPE_JSON_V2)))
        .thenReturn(httpResponse);
  }

  private IndexDocumentWithConsumptionAttributes
      setupExistingResourceInS3AndGenerateExpectedDocument(Candidate persistedCandidate) {
    var expandedResource = setupExistingResourceInS3(persistedCandidate);
    var indexDocument = createExpectedNviIndexDocument(expandedResource, persistedCandidate);
    return IndexDocumentWithConsumptionAttributes.from(indexDocument);
  }

  private JsonNode setupExistingResourceInS3(Candidate persistedCandidate) {
    var expandedResource =
        ExpandedResourceGenerator.builder()
            .withCandidate(persistedCandidate)
            .build()
            .createExpandedResource();
    insertInS3(expandedResource, extractResourceIdentifier(persistedCandidate));
    return expandedResource;
  }

  private void insertInS3(JsonNode expandedResource, String resourceIdentifier) {
    var resourceIndexDocument = createResourceIndexDocument(expandedResource);
    insertResourceInS3(resourceIndexDocument, constructPublicationBucketPath(resourceIdentifier));
  }

  private JsonNode createResourceIndexDocument(JsonNode expandedResource) {
    var root = objectMapper.createObjectNode();
    root.set(BODY, expandedResource);
    return root;
  }

  private void insertResourceInS3(JsonNode indexDocument, UnixPath path) {
    attempt(() -> s3Reader.insertFile(path, asString(indexDocument))).orElseThrow();
  }

  private NviCandidateIndexDocument createExpectedNviIndexDocument(
      JsonNode expandedResource, Candidate candidate) {
    var expandedPublicationDetails = expandPublicationDetails(candidate, expandedResource);
    return NviCandidateIndexDocument.builder()
        .withContext(candidate.getContextUri())
        .withId(candidate.getId())
        .withIsApplicable(candidate.isApplicable())
        .withIdentifier(candidate.identifier())
        .withApprovals(
            expandApprovals(candidate, expandedPublicationDetails.contributors(), expandedResource))
        .withPoints(candidate.getTotalPoints())
        .withPublicationDetails(expandedPublicationDetails)
        .withNumberOfApprovals(candidate.approvals().size())
        .withCreatorShareCount(candidate.getCreatorShareCount())
        .withReported(candidate.isReported())
        .withGlobalApprovalStatus(candidate.getGlobalApprovalStatus())
        .withPublicationTypeChannelLevelPoints(candidate.getBasePoints())
        .withInternationalCollaborationFactor(candidate.getCollaborationFactor())
        .withCreatedDate(candidate.createdDate())
        .withModifiedDate(candidate.modifiedDate())
        .withReportingPeriod(ReportingPeriod.fromCandidate(candidate))
        .withHandles(extractHandles(expandedResource))
        .build();
  }

  private static Set<URI> extractHandles(JsonNode expandedResource) {
    return Stream.concat(
            extractHandlesFromAdditionalIdentifiers(expandedResource).stream(),
            extractHandle(expandedResource).stream())
        .collect(Collectors.toSet());
  }

  private static List<URI> extractHandlesFromAdditionalIdentifiers(JsonNode expandedResource) {
    return StreamSupport.stream(
            expandedResource.withArray(ADDITIONAL_IDENTIFIERS_FIELD).spliterator(), false)
        .filter(node -> HANDLE_IDENTIFIER_TYPE_FIELD.equals(node.path(TYPE_FIELD).asText()))
        .map(node -> node.path(VALUE_FIELD).textValue())
        .filter(Objects::nonNull)
        .map(URI::create)
        .toList();
  }

  private static Optional<URI> extractHandle(JsonNode expandedResource) {
    return Optional.ofNullable(expandedResource.get(HANDLE_FIELD))
        .map(JsonNode::textValue)
        .map(URI::create);
  }

  private Candidate randomApplicableCandidate(URI topLevelOrg, URI affiliation) {
    var request = createUpsertCandidateRequestWithSingleAffiliation(topLevelOrg, affiliation);
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private Candidate randomApplicableCandidate(ChannelType channelType) {
    var channel =
        PublicationChannelDto.builder()
            .withId(randomUri())
            .withChannelType(channelType)
            .withScientificValue(ScientificValue.LEVEL_ONE)
            .build();
    var request = randomUpsertRequestBuilder().withPublicationChannel(channel).build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private void assertContentIsEqual(
      IndexDocumentWithConsumptionAttributes expected,
      IndexDocumentWithConsumptionAttributes actual) {
    Assertions.assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields("indexDocument.indexDocumentCreatedAt")
        .isEqualTo(expected);
  }

  private void assertContentIsEqual(
      NviCandidateIndexDocument expected, NviCandidateIndexDocument actual) {
    Assertions.assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields("indexDocumentCreatedAt")
        .isEqualTo(expected);
  }
}
