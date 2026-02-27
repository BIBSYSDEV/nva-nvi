package no.sikt.nva.nvi.index;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getIndexDocumentHandlerEnvironment;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEventWithOneInvalidRecord;
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
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.randomNonNviContributor;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.model.PublicationDetailsFixtures.publicationDtoMatchingCandidate;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_BODY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_TYPE;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.GZIP_ENDING;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.HARD_CODED_TOP_LEVEL_ORG;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.NVI_CANDIDATES_FOLDER;
import static no.sikt.nva.nvi.test.TestConstants.EN_FIELD;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_ENGLISH_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.HARDCODED_NORWEGIAN_LABEL;
import static no.sikt.nva.nvi.test.TestConstants.NB_FIELD;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.db.model.DbPublicationChannel;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.model.ChannelType;
import no.sikt.nva.nvi.common.model.ScientificValue;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
import no.sikt.nva.nvi.index.model.document.OrganizationType;
import no.sikt.nva.nvi.index.utils.CandidateToIndexDocumentMapper;
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
import org.mockito.ArgumentMatcher;
import software.amazon.awssdk.services.sqs.model.SqsException;

// Should be refactored, technical debt task: https://sikt.atlassian.net/browse/NP-48093
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
class IndexDocumentHandlerTest {

  private static final String JSON_PTR_CONTRIBUTOR = "/publicationDetails/contributors";
  private static final String JSON_PTR_APPROVALS = "/approvals";
  private static final Environment ENVIRONMENT = getIndexDocumentHandlerEnvironment();
  private static final String EXPANDED_RESOURCES_BUCKET = "EXPANDED_RESOURCES_BUCKET";
  private static final Context CONTEXT = mock(Context.class);
  private static final String BUCKET_NAME = ENVIRONMENT.readEnv(EXPANDED_RESOURCES_BUCKET);
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String INDEX_DLQ_URL = ENVIRONMENT.readEnv(INDEX_DLQ);
  private static final String OUTPUT_QUEUE_URL =
      ENVIRONMENT.readEnv("PERSISTED_INDEX_DOCUMENT_QUEUE_URL");
  private final FakeS3Client s3Client = new FakeS3Client();
  private IndexDocumentHandler handler;
  private CandidateRepository candidateRepository;
  private CandidateService candidateService;
  private S3Driver s3Writer;
  private PublicationLoaderService publicationLoaderService;
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

    s3Writer = new S3Driver(s3Client, BUCKET_NAME);
    publicationLoaderService = mock(PublicationLoaderService.class);
    sqsClient = new FakeSqsClient();
    handler =
        new IndexDocumentHandler(
            new S3StorageWriter(s3Client, BUCKET_NAME),
            sqsClient,
            candidateService,
            publicationLoaderService,
            ENVIRONMENT);
  }

  @Test
  void shouldBuildIndexDocumentAndPersistInS3WhenReceivingSqsEvent() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedDocument = setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedDocument.indexDocument(), actualIndexDocument);
  }

  @Test
  void shouldExtractNviContributorsInSeparateList() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedDocument = setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var expectedIndexDocument = expectedDocument.indexDocument();

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
    var expectedDocument = setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedDocument.indexDocument(), actualIndexDocument);
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
    var expectedDocument = setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);

    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedDocument.indexDocument(), actualIndexDocument);
  }

  @Test
  void involvedOrganizationsShouldContainTopLevelAffiliation() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, HARD_CODED_TOP_LEVEL_ORG);
    var expectedDocument = setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedDocument.indexDocument(), actualIndexDocument);
  }

  @Test
  void topLevelAffiliationShouldNotHavePartOf() {
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, HARD_CODED_TOP_LEVEL_ORG);
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertEquals(
        emptyList(), extractPartOfAffiliation(actualIndexDocument, HARD_CODED_TOP_LEVEL_ORG));
  }

  @Test
  void subUnitAffiliationShouldHavePartOf() {
    var subUnitAffiliation = randomUri();
    var candidate = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, subUnitAffiliation);
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    var expectedPartOf =
        List.of(HARD_CODED_TOP_LEVEL_ORG); // FIXME: This seems like a regression? Missing a level?
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
        setupPublicationLoaderMockAndGenerateExpectedDocument(candidate).indexDocument();
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedIndexDocument, actualIndexDocument);
  }

  @Test
  void shouldBuildIndexDocumentWithLabelsFromCandidateTopLevelOrganizations() {
    var candidate = randomApplicableCandidateWithLabels();
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    Assertions.assertThat(actualIndexDocument.approvals())
        .hasSizeGreaterThanOrEqualTo(1)
        .extracting(ApprovalView::labels)
        .allSatisfy(
            labels ->
                Assertions.assertThat(labels)
                    .containsEntry(EN_FIELD, HARDCODED_ENGLISH_LABEL)
                    .containsEntry(NB_FIELD, HARDCODED_NORWEGIAN_LABEL));
  }

  @Test
  void shouldBuildIndexDocumentWithConsumptionAttributes() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var expectedConsumptionAttributes =
        setupPublicationLoaderMockAndGenerateExpectedDocument(candidate).consumptionAttributes();
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate)));
    assertEquals(expectedConsumptionAttributes, actualIndexDocument.consumptionAttributes());
  }

  @Test
  void shouldSetApprovalStatusNewWhenApprovalIsPendingAndUnassigned() {
    var institutionId = randomUri();
    var candidate = randomApplicableCandidate(institutionId, institutionId);
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
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

    candidateService.updateCandidate(createUpsertNonCandidateRequest(candidate.getPublicationId()));
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    Assertions.assertThat(sqsClient.getSentMessages()).isEmpty();
  }

  @Test
  void shouldNotExpandAffiliationsWhenContributorIsNotNviCreator() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var nonNviAffiliation = Organization.builder().withId(randomUri()).build();
    var extraContributor = randomNonNviContributor(List.of(nonNviAffiliation));
    var baseDto = publicationDtoMatchingCandidate(candidate).build();
    var allContributors = new ArrayList<>(baseDto.contributors());
    allContributors.add(extraContributor);
    var publicationDto =
        publicationDtoMatchingCandidate(candidate).withContributors(allContributors).build();
    mockPublicationLoaderService(candidate, publicationDto);
    var expectedDocument = IndexDocumentWithConsumptionAttributes.from(candidate, publicationDto);
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate)));
    assertEquals(
        expectedDocument.consumptionAttributes(), actualIndexDocument.consumptionAttributes());
  }

  @ParameterizedTest(name = "shouldExtractOptionalPrintIssnFromExpandedResource: {0}")
  @MethodSource("channelTypeIssnProvider")
  void shouldExtractOptionalPrintIssnFromExpandedResource(
      ChannelType channelType, boolean printIssnExists) {
    var candidate = randomApplicableCandidate(channelType);
    var builder = publicationDtoMatchingCandidate(candidate);
    if (!printIssnExists) {
      var channelWithoutIssn = buildChannelWithoutIssn(candidate);
      builder.withPublicationChannels(List.of(channelWithoutIssn));
    }
    var publicationDto = builder.build();
    mockPublicationLoaderService(candidate, publicationDto);
    var expectedIndexDocument =
        new CandidateToIndexDocumentMapper(candidate, publicationDto).toIndexDocument();
    var event = createEvent(candidate.identifier());
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
    var expectedDocument = setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedDocument.indexDocument(), actualIndexDocument);
  }

  @Test
  void shouldProduceIndexDocumentWithTypeInfo() throws JsonProcessingException {
    var candidate = setupRandomApplicableCandidate(scenario);
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    var event = createEvent(candidate.identifier());
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
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    var expectedEvent = createExpectedEventMessageBody(candidate);
    var actualEvent = sqsClient.getSentMessages().getFirst().messageBody();
    assertEquals(expectedEvent, actualEvent);
  }

  @Test
  void shouldSendMessageToDlqWhenFailingToProcessEvent() {
    var candidate = setupRandomApplicableCandidate(scenario);
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);
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
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidateToSucceed);
    setupPublicationLoaderMockAndGenerateExpectedDocument(candidateToFail);
    var mockedSqsClient = setupFailingSqsClient(candidateToFail);
    var handler =
        new IndexDocumentHandler(
            new S3StorageWriter(s3Client, BUCKET_NAME),
            mockedSqsClient,
            candidateService,
            publicationLoaderService,
            ENVIRONMENT);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());
    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToLoadPublicationForOneCandidate() {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    var expectedDocument =
        setupPublicationLoaderMockAndGenerateExpectedDocument(candidateToSucceed);
    mockPublicationLoaderFailure(candidateToFail);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidateToSucceed)));
    assertContentIsEqual(expectedDocument, actualIndexDocument);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToPersistDocumentForOneCandidate() throws IOException {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());
    var expectedDocumentToFail =
        setupPublicationLoaderMockAndGenerateExpectedDocument(candidateToFail);
    var expectedDocument =
        setupPublicationLoaderMockAndGenerateExpectedDocument(candidateToSucceed);
    var s3Writer =
        mockS3WriterFailingForOneCandidate(
            expectedDocument, expectedDocumentToFail, candidateToSucceed);
    var handler =
        new IndexDocumentHandler(
            s3Writer, sqsClient, candidateService, publicationLoaderService, ENVIRONMENT);
    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToFetchOneCandidate() {
    var candidateToSucceed = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedDocument =
        setupPublicationLoaderMockAndGenerateExpectedDocument(candidateToSucceed);
    var event = createEvent(UUID.randomUUID(), candidateToSucceed.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidateToSucceed)));
    assertContentIsEqual(expectedDocument, actualIndexDocument);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingParseOneEventRecord() {
    var candidateToSucceed = randomApplicableCandidate(HARD_CODED_TOP_LEVEL_ORG, randomUri());
    var expectedDocument =
        setupPublicationLoaderMockAndGenerateExpectedDocument(candidateToSucceed);
    var event = createEventWithOneInvalidRecord(candidateToSucceed.identifier());
    handler.handleRequest(event, CONTEXT);
    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidateToSucceed)));
    assertContentIsEqual(expectedDocument, actualIndexDocument);
  }

  @Test
  void shouldBuildIndexDocumentForReportedCandidateWithInvalidProperties() {
    // Given an already reported Candidate with non-applicable values
    // When the candidate is processed for indexing
    // Then an index document is created successfully
    var candidateDao = setupReportedCandidateWithInvalidProperties();
    var candidate = candidateService.getCandidateByIdentifier(candidateDao.identifier());
    var expectedDocument = setupPublicationLoaderMockAndGenerateExpectedDocument(candidate);

    var event = createEvent(candidate.identifier());
    handler.handleRequest(event, CONTEXT);

    var actualIndexDocument = parseJson(s3Writer.getFile(createPath(candidate))).indexDocument();
    assertContentIsEqual(expectedDocument.indexDocument(), actualIndexDocument);
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

  private static FakeSqsClient setupFailingSqsClient(Candidate candidate) {
    var expectedFailingMessage =
        new PersistedIndexDocumentMessage(generateBucketUri(candidate)).toJsonString();
    var mockedSqsClient = mock(FakeSqsClient.class);
    var sqsException = SqsException.builder().message("Some exception message").build();
    when(mockedSqsClient.sendMessage(eq(expectedFailingMessage), anyString()))
        .thenThrow(sqsException);
    return mockedSqsClient;
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

  private String createExpectedEventMessageBody(Candidate candidate) {
    return new PersistedIndexDocumentMessage(generateBucketUri(candidate)).toJsonString();
  }

  private S3StorageWriter mockS3WriterFailingForOneCandidate(
      IndexDocumentWithConsumptionAttributes expectedDocument,
      IndexDocumentWithConsumptionAttributes expectedDocumentToFail,
      Candidate candidateToSucceed)
      throws IOException {
    var mockedS3Writer = mock(S3StorageWriter.class);
    when(mockedS3Writer.write(argThat(matchesDocumentIdOf(expectedDocumentToFail))))
        .thenThrow(new IOException("Some exception message"));
    when(mockedS3Writer.write(argThat(matchesDocumentIdOf(expectedDocument))))
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

  private IndexDocumentWithConsumptionAttributes
      setupPublicationLoaderMockAndGenerateExpectedDocument(Candidate candidate) {
    var publicationDto = publicationDtoMatchingCandidate(candidate).build();
    mockPublicationLoaderService(candidate, publicationDto);
    return IndexDocumentWithConsumptionAttributes.from(candidate, publicationDto);
  }

  private void mockPublicationLoaderService(Candidate candidate, PublicationDto publicationDto) {
    when(publicationLoaderService.extractAndTransform(
            candidate.publicationDetails().publicationBucketUri()))
        .thenReturn(publicationDto);
  }

  private void mockPublicationLoaderFailure(Candidate candidate) {
    when(publicationLoaderService.extractAndTransform(
            candidate.publicationDetails().publicationBucketUri()))
        .thenThrow(new RuntimeException("Failed to load publication"));
  }

  private Candidate randomApplicableCandidate(URI topLevelOrg, URI affiliation) {
    var request = createUpsertCandidateRequestWithSingleAffiliation(topLevelOrg, affiliation);
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private Candidate randomApplicableCandidateWithLabels() {
    var affiliation = randomUri();
    var labels = Map.of(EN_FIELD, HARDCODED_ENGLISH_LABEL, NB_FIELD, HARDCODED_NORWEGIAN_LABEL);
    var subOrganization =
        Organization.builder()
            .withId(affiliation)
            .withPartOf(List.of(Organization.builder().withId(HARD_CODED_TOP_LEVEL_ORG).build()))
            .build();
    var topLevelOrganization =
        Organization.builder()
            .withId(HARD_CODED_TOP_LEVEL_ORG)
            .withHasPart(List.of(subOrganization))
            .withLabels(labels)
            .build();
    var verifiedCreator = verifiedNviCreatorDtoFrom(affiliation);
    var request =
        randomUpsertRequestBuilder()
            .withCreatorsAndPoints(Map.of(topLevelOrganization, List.of(verifiedCreator)))
            .build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private static PublicationChannelDto buildChannelWithoutIssn(Candidate candidate) {
    var channel = candidate.getPublicationChannel();
    return PublicationChannelDto.builder()
        .withId(channel.id())
        .withChannelType(channel.channelType())
        .withScientificValue(channel.scientificValue())
        .withName(randomString())
        .build();
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
