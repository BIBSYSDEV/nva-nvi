package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getIndexDocumentHandlerEnvironment;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.PublicationDtoFixtures.publicationDtoMirroring;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.GZIP_ENDING;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.NVI_CANDIDATES_FOLDER;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.s3.S3Driver.S3_SCHEME;
import static nva.commons.core.attempt.Try.attempt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.util.Optional;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.mapper.IndexDocumentGenerator;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.publication.PublicationLoaderService;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Shared harness for handler-level index-document tests. Drives the real {@link
 * IndexDocumentHandler} over a {@link TestScenario}, with the publication supplied by a mocked
 * {@link PublicationLoaderService} so each test controls the {@code PublicationDto} the generator
 * receives.
 */
class IndexDocumentHandlerTestBase {

  protected static final Environment ENVIRONMENT = getIndexDocumentHandlerEnvironment();
  protected static final String BUCKET_NAME = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
  protected static final Context CONTEXT = new FakeContext();

  protected final S3Client s3Client = new FakeS3Client();
  protected IndexDocumentHandler handler;
  protected CandidateRepository candidateRepository;
  protected CandidateService candidateService;
  protected S3Driver s3Writer;
  protected PublicationLoaderService publicationLoaderService;
  protected FakeSqsClient sqsClient;
  protected TestScenario scenario;

  @BeforeEach
  void baseSetup() {
    scenario = new TestScenario();
    setupOpenPeriod(scenario, CURRENT_YEAR);
    candidateRepository = scenario.getCandidateRepository();
    candidateService = scenario.getCandidateService();
    s3Writer = new S3Driver(s3Client, BUCKET_NAME);
    publicationLoaderService = mock(PublicationLoaderService.class);
    sqsClient = new FakeSqsClient();
    handler = handlerWith(new S3StorageWriter(s3Client, BUCKET_NAME), sqsClient);
  }

  protected IndexDocumentHandler handlerWith(S3StorageWriter storageWriter, QueueClient sqsClient) {
    return new IndexDocumentHandler(
        storageWriter,
        sqsClient,
        candidateService,
        new IndexDocumentGenerator(publicationLoaderService, ENVIRONMENT),
        ENVIRONMENT);
  }

  /**
   * Makes the generator receive a PublicationDto mirroring the candidate (the common case where the
   * publication still matches the candidate).
   */
  protected PublicationDto stubPublication(Candidate candidate) {
    return stubPublication(candidate, publicationDtoMirroring(candidate));
  }

  protected PublicationDto stubPublication(Candidate candidate, PublicationDto publicationDto) {
    when(publicationLoaderService.tryExtractAndTransform(
            candidate.publicationDetails().publicationBucketUri()))
        .thenReturn(Optional.ofNullable(publicationDto));
    return publicationDto;
  }

  /** Makes the generator receive no publication data, as when the persisted document is broken. */
  protected void stubPublicationParseFailure(Candidate candidate) {
    stubPublication(candidate, null);
  }

  protected NviCandidateIndexDocument generateIndexDocument(Candidate candidate) {
    handler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    return readPersistedDocument(candidate).indexDocument();
  }

  protected IndexDocumentWithConsumptionAttributes readPersistedDocument(Candidate candidate) {
    return parseJson(s3Writer.getFile(createPath(candidate)));
  }

  protected static UnixPath createPath(Candidate candidate) {
    return UnixPath.of(NVI_CANDIDATES_FOLDER)
        .addChild(candidate.identifier().toString() + GZIP_ENDING);
  }

  protected static URI generateBucketUri(Candidate candidate) {
    return new UriWrapper(S3_SCHEME, BUCKET_NAME).addChild(createPath(candidate)).getUri();
  }

  protected static IndexDocumentWithConsumptionAttributes parseJson(String persistedIndexDocument) {
    return attempt(
            () ->
                dtoObjectMapper.readValue(
                    persistedIndexDocument, IndexDocumentWithConsumptionAttributes.class))
        .orElseThrow();
  }
}
