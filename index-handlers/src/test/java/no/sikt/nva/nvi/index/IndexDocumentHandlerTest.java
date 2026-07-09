package no.sikt.nva.nvi.index;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEventWithOneInvalidRecord;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequest;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertCandidateRequestWithSingleAffiliation;
import static no.sikt.nva.nvi.common.UpsertRequestFixtures.createUpsertNonCandidateRequest;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.createCandidateDao;
import static no.sikt.nva.nvi.common.db.DbCandidateFixtures.randomCandidateBuilder;
import static no.sikt.nva.nvi.common.db.DbPointCalculationFixtures.randomPointCalculationBuilder;
import static no.sikt.nva.nvi.common.db.DbPublicationChannelFixtures.randomDbPublicationChannelBuilder;
import static no.sikt.nva.nvi.common.model.CandidateFixtures.setupRandomApplicableCandidate;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_AFFILIATIONS;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_BODY;
import static no.sikt.nva.nvi.common.utils.JsonPointers.JSON_PTR_TYPE;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import no.sikt.nva.nvi.common.db.CandidateDao;
import no.sikt.nva.nvi.common.db.ReportStatus;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.mapper.CandidateToIndexDocumentMapper;
import no.sikt.nva.nvi.index.model.PersistedIndexDocumentMessage;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.sqs.model.SqsException;

/**
 * Exercises the handler mechanics: fetching the candidate, driving the generator, persisting the
 * document to S3, emitting the follow-up event, and isolating per-record failures onto the DLQ.
 * Document content is covered by {@code IndexDocumentContentTest}.
 */
class IndexDocumentHandlerTest extends IndexDocumentHandlerTestBase {

  private static final String JSON_PTR_NVI_CONTRIBUTORS = "/publicationDetails/nviContributors";
  private static final String JSON_PTR_CONTRIBUTORS = "/publicationDetails/contributors";
  private static final String JSON_PTR_APPROVALS = "/approvals";
  private static final String INDEX_DLQ = "INDEX_DLQ";
  private static final String INDEX_DLQ_URL = ENVIRONMENT.readEnv(INDEX_DLQ);
  private static final String OUTPUT_QUEUE_URL =
      ENVIRONMENT.readEnv("PERSISTED_INDEX_DOCUMENT_QUEUE_URL");

  @Test
  void shouldBuildIndexDocumentAndPersistInS3WhenReceivingSqsEvent() {
    var candidate = randomApplicableCandidate(randomOrganizationId(), randomUri());
    var publicationDto = stubPublication(candidate);
    var expectedDocument =
        new CandidateToIndexDocumentMapper(candidate, publicationDto, ENVIRONMENT).generate();

    var actualDocument = generateIndexDocument(candidate);

    assertDocumentContentEquals(actualDocument, expectedDocument);
  }

  @Test
  void shouldSendSqsEventWhenIndexDocumentIsPersisted() {
    var candidate = setupRandomApplicableCandidate(scenario);
    stubPublication(candidate);

    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);

    var expectedEvent =
        new PersistedIndexDocumentMessage(generateBucketUri(candidate)).toJsonString();
    var actualEvent = sqsClient.getSentMessages().getFirst().messageBody();
    assertEquals(expectedEvent, actualEvent);
  }

  @Test
  void shouldBuildIndexDocumentWithConsumptionAttributes() {
    var candidate = setupRandomApplicableCandidate(scenario);
    var publicationDto = stubPublication(candidate);
    var expectedConsumptionAttributes =
        IndexDocumentWithConsumptionAttributes.from(
                new CandidateToIndexDocumentMapper(candidate, publicationDto, ENVIRONMENT)
                    .generate())
            .consumptionAttributes();

    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);

    var actualDocument = readPersistedDocument(candidate);
    assertEquals(expectedConsumptionAttributes, actualDocument.consumptionAttributes());
  }

  @Test
  void shouldProduceIndexDocumentWithTypeInfo() throws JsonProcessingException {
    var candidate = randomApplicableCandidate(randomOrganizationId(), randomUri());
    stubPublication(candidate);

    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);

    var body = dtoObjectMapper.readTree(s3Writer.getFile(createPath(candidate))).at(JSON_PTR_BODY);
    assertEquals("NviCandidate", body.at(JSON_PTR_TYPE).textValue());
    assertEquals(
        "NviContributor", body.at(JSON_PTR_NVI_CONTRIBUTORS).get(0).at(JSON_PTR_TYPE).textValue());
    assertEquals(
        "NviOrganization",
        body.at(JSON_PTR_NVI_CONTRIBUTORS)
            .get(0)
            .at(JSON_PTR_AFFILIATIONS)
            .get(0)
            .at(JSON_PTR_TYPE)
            .textValue());
    assertEquals(
        "Contributor", body.at(JSON_PTR_CONTRIBUTORS).get(0).at(JSON_PTR_TYPE).textValue());
    assertEquals("Approval", body.at(JSON_PTR_APPROVALS).get(0).at(JSON_PTR_TYPE).textValue());
  }

  @Test
  void shouldNotBuildIndexDocumentForNonApplicableCandidate() {
    var request = createUpsertCandidateRequest(randomOrganizationId()).build();
    candidateService.upsertCandidate(request);
    var candidate = candidateService.getCandidateByPublicationId(request.publicationId());
    candidateService.updateCandidate(createUpsertNonCandidateRequest(candidate.getPublicationId()));

    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);

    assertThrows(NoSuchKeyException.class, () -> s3Writer.getFile(createPath(candidate)));
  }

  @Test
  void shouldBuildIndexDocumentForReportedCandidateWithInvalidProperties() {
    var candidateDao = setupReportedCandidateWithInvalidProperties();
    var candidate = candidateService.getCandidateByIdentifier(candidateDao.identifier());
    stubPublication(candidate);

    assertDoesNotThrow(() -> handler.handleRequest(createEvent(candidate.identifier()), CONTEXT));

    assertThat(parseJson(s3Writer.getFile(createPath(candidate))).indexDocument().identifier())
        .isEqualTo(candidate.identifier());
  }

  @Test
  void shouldSendMessageToDlqWhenFailingToProcessEvent() {
    var candidate = setupRandomApplicableCandidate(scenario);
    stubPublication(candidate);
    sqsClient.disableDestinationQueue(OUTPUT_QUEUE_URL);

    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);

    assertThat(sqsClient.getAllSentSqsEvents(INDEX_DLQ_URL)).hasSize(1);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToSendEventForOneCandidate() {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    stubPublication(candidateToFail);
    stubPublication(candidateToSucceed);
    var failingSqsClient = setupFailingSqsClient(candidateToFail);
    var handler = handlerWith(new S3StorageWriter(s3Client, BUCKET_NAME), failingSqsClient);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());

    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToLoadPublicationForOneCandidate() {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    stubPublicationFailure(candidateToFail);
    var expectedDocument = stubbedDocument(candidateToSucceed);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());

    handler.handleRequest(event, CONTEXT);

    var actualDocument =
        parseJson(s3Writer.getFile(createPath(candidateToSucceed))).indexDocument();
    assertDocumentContentEquals(actualDocument, expectedDocument);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToPersistDocumentForOneCandidate() throws IOException {
    var candidateToFail = setupRandomApplicableCandidate(scenario);
    var candidateToSucceed = setupRandomApplicableCandidate(scenario);
    stubPublication(candidateToFail);
    stubPublication(candidateToSucceed);
    var failingWriter = mockS3WriterFailingForOneCandidate(candidateToSucceed, candidateToFail);
    var handler = handlerWith(failingWriter, sqsClient);
    var event = createEvent(candidateToFail.identifier(), candidateToSucceed.identifier());

    assertDoesNotThrow(() -> handler.handleRequest(event, CONTEXT));
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingToFetchOneCandidate() {
    var candidateToSucceed = randomApplicableCandidate(randomOrganizationId(), randomUri());
    var expectedDocument = stubbedDocument(candidateToSucceed);
    var event = createEvent(UUID.randomUUID(), candidateToSucceed.identifier());

    handler.handleRequest(event, CONTEXT);

    var actualDocument =
        parseJson(s3Writer.getFile(createPath(candidateToSucceed))).indexDocument();
    assertDocumentContentEquals(actualDocument, expectedDocument);
  }

  @Test
  void shouldNotFailForWholeBatchWhenFailingParseOneEventRecord() {
    var candidateToSucceed = randomApplicableCandidate(randomOrganizationId(), randomUri());
    var expectedDocument = stubbedDocument(candidateToSucceed);
    var event = createEventWithOneInvalidRecord(candidateToSucceed.identifier());

    handler.handleRequest(event, CONTEXT);

    var actualDocument =
        parseJson(s3Writer.getFile(createPath(candidateToSucceed))).indexDocument();
    assertDocumentContentEquals(actualDocument, expectedDocument);
  }

  private void stubPublicationFailure(Candidate candidate) {
    when(publicationLoaderService.extractAndTransform(
            candidate.publicationDetails().publicationBucketUri()))
        .thenThrow(new RuntimeException("Failed to load publication"));
  }

  private NviCandidateIndexDocument stubbedDocument(Candidate candidate) {
    var publicationDto = stubPublication(candidate);
    return new CandidateToIndexDocumentMapper(candidate, publicationDto, ENVIRONMENT).generate();
  }

  private Candidate randomApplicableCandidate(URI topLevelOrg, URI affiliation) {
    var request = createUpsertCandidateRequestWithSingleAffiliation(topLevelOrg, affiliation);
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
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
            .reportedDate(Instant.now())
            .build();
    var candidateDao = createCandidateDao(dbCandidate);
    candidateRepository.create(candidateDao, emptyList());
    return candidateDao;
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

  private S3StorageWriter mockS3WriterFailingForOneCandidate(
      Candidate candidateToSucceed, Candidate candidateToFail) throws IOException {
    var mockedS3Writer = mock(S3StorageWriter.class);
    when(mockedS3Writer.write(argThat(matchesDocumentIdOf(candidateToFail))))
        .thenThrow(new IOException("Some exception message"));
    when(mockedS3Writer.write(argThat(matchesDocumentIdOf(candidateToSucceed))))
        .thenReturn(s3BucketUri().addChild(candidateToSucceed.identifier().toString()).getUri());
    return mockedS3Writer;
  }

  private static ArgumentMatcher<IndexDocumentWithConsumptionAttributes> matchesDocumentIdOf(
      Candidate candidate) {
    return document ->
        nonNull(document) && document.indexDocument().identifier().equals(candidate.identifier());
  }

  private static UriWrapper s3BucketUri() {
    return new UriWrapper(S3_SCHEME, BUCKET_NAME);
  }

  private static void assertDocumentContentEquals(
      NviCandidateIndexDocument actual, NviCandidateIndexDocument expected) {
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields("indexDocumentCreatedAt")
        .isEqualTo(expected);
  }
}
