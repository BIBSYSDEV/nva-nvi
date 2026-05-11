package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.EnvironmentFixtures.getIndexDocumentHandlerEnvironment;
import static no.sikt.nva.nvi.common.QueueServiceTestUtils.createEvent;
import static no.sikt.nva.nvi.common.UpsertRequestBuilder.randomUpsertRequestBuilder;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.dto.NviCreatorDtoFixtures.verifiedNviCreatorDtoFrom;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.GZIP_ENDING;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.NVI_CANDIDATES_FOLDER;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.randomBigDecimal;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.dto.PublicationDetailsDtoBuilder;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.queue.FakeSqsClient;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.IndexDocumentHandler;
import no.sikt.nva.nvi.index.IndexDocumentScenario;
import no.sikt.nva.nvi.index.aws.S3StorageWriter;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.test.uriretriever.CristinOrganizationFixtures;
import no.sikt.nva.nvi.test.uriretriever.FakeCristinOrganization;
import no.sikt.nva.nvi.test.uriretriever.FakeUriRetriever;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.Environment;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Handler-level tests exercising the {@link IndexDocumentGenerator} contract through the public
 * {@link IndexDocumentHandler} entry point. The {@link #handlerFor(IndexDocumentScenario)} method
 * is the swap point: replacing its body with code that wires in a different generator
 * implementation lets us run the same test suite against any {@code IndexDocumentGenerator}.
 */
class IndexDocumentGeneratorTest {

  private static final Environment ENVIRONMENT = getIndexDocumentHandlerEnvironment();
  private static final String BUCKET_NAME = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
  private static final Context CONTEXT = new FakeContext();
  private static final String CRISTIN_VERSION = "; version=2023-05-26";
  private static final String MEDIA_TYPE_JSON_V2 = "application/json" + CRISTIN_VERSION;
  private static final int HTTP_OK = 200;

  private final S3Client s3Client = new FakeS3Client();
  private S3Driver s3Reader;
  private S3Driver s3Writer;
  private CandidateService candidateService;
  private FakeUriRetriever uriRetriever;
  private IndexDocumentHandler handler;

  @BeforeEach
  void setup() {
    var testScenario = new TestScenario();
    setupOpenPeriod(testScenario, CURRENT_YEAR);
    candidateService = testScenario.getCandidateService();
    s3Reader = new S3Driver(s3Client, BUCKET_NAME);
    s3Writer = new S3Driver(s3Client, BUCKET_NAME);
    uriRetriever = FakeUriRetriever.newInstance();
    handler =
        new IndexDocumentHandler(
            new S3StorageReader(s3Client, BUCKET_NAME),
            new S3StorageWriter(s3Client, BUCKET_NAME),
            new FakeSqsClient(),
            candidateService,
            uriRetriever,
            ENVIRONMENT);
  }

  /**
   * Swap point. Wires the scenario into the handler in whatever way the mapper-under-test needs.
   * Today this uploads the expanded resource to S3 and registers stub Cristin responses on the fake
   * UriRetriever; the new SPARQL-based mapper will instead receive a {@code PublicationDto} via a
   * {@code PublicationLoaderService}. The same scenario object is the source of truth either way.
   */
  private IndexDocumentHandler handlerFor(IndexDocumentScenario scenario) {
    uploadExpandedResourceToS3(scenario);
    registerOrganizationsOnUriRetriever(scenario);
    return handler;
  }

  @ParameterizedTest
  @EnumSource(value = Sector.class, names = "UNKNOWN", mode = EnumSource.Mode.EXCLUDE)
  void shouldPopulateSectorInApprovalViewWhenSectorIsNotUnknown(Sector sector) {
    var institutionId = randomUri();
    var candidate = setupCandidateWithSector(institutionId, sector);

    var document = generateAndReadDocument(candidate);

    assertThat(approvalFor(document, institutionId).sector()).isEqualTo(sector.toString());
  }

  @Test
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsUnknown() {
    var institutionId = randomUri();
    var candidate = setupCandidateWithSector(institutionId, Sector.UNKNOWN);

    var document = generateAndReadDocument(candidate);

    assertThat(approvalFor(document, institutionId).sector()).isNull();
  }

  @Test
  void shouldNotPopulateSectorInApprovalViewWhenSectorIsNull() {
    var institutionId = randomUri();
    var candidate = setupCandidateWithSector(institutionId, null);

    var document = generateAndReadDocument(candidate);

    assertThat(approvalFor(document, institutionId).sector()).isNull();
  }

  @Test
  void shouldPopulateHandlesInIndexDocumentFromCandidatePublicationDetails() {
    var firstHandle = randomUri();
    var secondHandle = randomUri();
    var expectedHandles = Set.of(firstHandle, secondHandle);
    var candidate = setupCandidateWithHandles(expectedHandles);

    var document = generateAndReadDocument(candidate);

    assertThat(document.publicationDetails().handles())
        .containsExactlyInAnyOrderElementsOf(expectedHandles);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPopulateRboInstitutionInIndexDocumentApprovalView(boolean rboInstitution) {
    var institutionId = randomUri();
    var candidate = setupCandidateWithRboInstitution(institutionId, rboInstitution);

    var document = generateAndReadDocument(candidate);

    assertThat(approvalFor(document, institutionId).rboInstitution()).isEqualTo(rboInstitution);
  }

  // --- helpers ---

  private NviCandidateIndexDocument generateAndReadDocument(Candidate candidate) {
    var scenario = IndexDocumentScenario.forCandidate(candidate);
    var wiredHandler = handlerFor(scenario);
    wiredHandler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    return readPersistedDocument(candidate).indexDocument();
  }

  private IndexDocumentWithConsumptionAttributes readPersistedDocument(Candidate candidate) {
    var json = s3Writer.getFile(persistedDocumentPath(candidate));
    return attempt(
            () -> dtoObjectMapper.readValue(json, IndexDocumentWithConsumptionAttributes.class))
        .orElseThrow();
  }

  private static UnixPath persistedDocumentPath(Candidate candidate) {
    return UnixPath.of(NVI_CANDIDATES_FOLDER)
        .addChild(candidate.identifier().toString() + GZIP_ENDING);
  }

  private static ApprovalView approvalFor(NviCandidateIndexDocument document, URI institutionId) {
    return document.approvals().stream()
        .filter(approval -> approval.institutionId().equals(institutionId))
        .findFirst()
        .orElseThrow();
  }

  private void uploadExpandedResourceToS3(IndexDocumentScenario scenario) {
    var resourceIdentifier =
        scenario.candidate().publicationDetails().publicationIdentifier().toString();
    var resourceIndexDocument = dtoObjectMapper.createObjectNode();
    resourceIndexDocument.set("body", scenario.expandedResource());
    var path = TestScenario.constructPublicationBucketPath(resourceIdentifier);
    attempt(
            () ->
                s3Reader.insertFile(
                    path, dtoObjectMapper.writeValueAsString(resourceIndexDocument)))
        .orElseThrow();
  }

  private void registerOrganizationsOnUriRetriever(IndexDocumentScenario scenario) {
    scenario
        .candidate()
        .publicationDetails()
        .getNviCreatorAffiliations()
        .forEach(this::registerOrganizationHierarchy);
  }

  private void registerOrganizationHierarchy(URI affiliationId) {
    var leaf = FakeCristinOrganization.asLeafNode(affiliationId);
    var orgWithSubunits =
        CristinOrganizationFixtures.randomCristinOrganization(affiliationId)
            .withHasPart(List.of(leaf))
            .build();
    uriRetriever.registerResponse(
        affiliationId, HTTP_OK, MEDIA_TYPE_JSON_V2, orgWithSubunits.toJsonString());
  }

  private Candidate setupCandidateWithSector(URI institutionId, Sector sector) {
    return setupCandidateWithInstitutionPoints(institutionId, sector, false);
  }

  private Candidate setupCandidateWithRboInstitution(URI institutionId, boolean rboInstitution) {
    return setupCandidateWithInstitutionPoints(institutionId, Sector.UHI, rboInstitution);
  }

  private Candidate setupCandidateWithInstitutionPoints(
      URI institutionId, Sector sector, boolean rboInstitution) {
    var points = randomBigDecimal();
    var creatorPoints = randomBigDecimal();
    var verifiedCreator = verifiedNviCreatorDtoFrom(institutionId);
    var topLevelOrganization = Organization.builder().withId(institutionId).build();
    var institutionPoints =
        buildInstitutionPoints(institutionId, sector, rboInstitution, points, creatorPoints);
    var request =
        randomUpsertRequestBuilder()
            .withCreatorsAndPoints(Map.of(topLevelOrganization, List.of(verifiedCreator)))
            .withPoints(List.of(institutionPoints))
            .build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
  }

  private static InstitutionPoints buildInstitutionPoints(
      URI institutionId,
      Sector sector,
      boolean rboInstitution,
      BigDecimal points,
      BigDecimal creatorPoints) {
    var verifiedCreatorId = randomUri();
    var creatorAffiliationPoints =
        new CreatorAffiliationPoints(verifiedCreatorId, institutionId, creatorPoints);
    return new InstitutionPoints(
        institutionId, points, sector, rboInstitution, List.of(creatorAffiliationPoints));
  }

  private Candidate setupCandidateWithHandles(Set<URI> handles) {
    var request = randomUpsertRequestBuilder().build();
    var modifiedDetails =
        new PublicationDetailsDtoBuilder(request.publicationDetails()).withHandles(handles).build();
    var modifiedRequest =
        UpsertRequestBuilder.fromRequest(request).withPublicationDetails(modifiedDetails).build();
    candidateService.upsertCandidate(modifiedRequest);
    return candidateService.getCandidateByPublicationId(modifiedRequest.publicationId());
  }
}
