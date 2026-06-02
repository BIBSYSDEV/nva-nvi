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
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.Context;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.UpsertRequestBuilder;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.db.CandidateRepository;
import no.sikt.nva.nvi.common.dto.PublicationDetailsDtoBuilder;
import no.sikt.nva.nvi.common.model.Sector;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints;
import no.sikt.nva.nvi.common.service.model.InstitutionPoints.CreatorAffiliationPoints;
import no.sikt.nva.nvi.index.IndexDocumentHandler;
import no.sikt.nva.nvi.index.IndexDocumentScenario;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.model.document.IndexDocumentWithConsumptionAttributes;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.NviContributor;
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
 * Shared test surface for any {@link IndexDocumentGenerator} implementation. Each concrete subclass
 * provides its own {@link #handlerFor(IndexDocumentScenario)} that wires the scenario into an
 * {@link IndexDocumentHandler} using whichever generator it wants to exercise. The test cases
 * assert observable properties of the persisted document so they're agnostic to how the generator
 * built it.
 */
abstract class IndexDocumentGeneratorTestBase {

  protected static final Environment ENVIRONMENT = getIndexDocumentHandlerEnvironment();
  protected static final String BUCKET_NAME = ENVIRONMENT.readEnv("EXPANDED_RESOURCES_BUCKET");
  protected static final Context CONTEXT = new FakeContext();

  protected final S3Client s3Client = new FakeS3Client();
  protected S3Driver s3Writer;
  protected CandidateService candidateService;
  protected CandidateRepository candidateRepository;

  @BeforeEach
  void baseSetup() {
    var testScenario = new TestScenario();
    setupOpenPeriod(testScenario, CURRENT_YEAR);
    candidateService = testScenario.getCandidateService();
    candidateRepository = testScenario.getCandidateRepository();
    s3Writer = new S3Driver(s3Client, BUCKET_NAME);
  }

  /**
   * Swap point. Wires the scenario into an {@link IndexDocumentHandler} in whatever way the
   * mapper-under-test needs. The returned handler must be ready to receive an SQS event for the
   * scenario's candidate.
   */
  protected abstract IndexDocumentHandler handlerFor(IndexDocumentScenario scenario);

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

  @Test
  void shouldIncludeUnverifiedNviCreatorInNviContributors() {
    var institutionId = randomUri();
    var expectedName = randomString();
    var candidate = setupCandidateWithUnverifiedCreator(institutionId, expectedName);

    var document = generateAndReadDocument(candidate);

    assertThat(document.publicationDetails().nviContributors())
        .extracting(NviContributor::name)
        .contains(expectedName);
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

  protected NviCandidateIndexDocument generateAndReadDocument(Candidate candidate) {
    var scenario = IndexDocumentScenario.forCandidate(candidate);
    var wiredHandler = handlerFor(scenario);
    wiredHandler.handleRequest(createEvent(candidate.identifier()), CONTEXT);
    return readPersistedDocument(candidate).indexDocument();
  }

  protected IndexDocumentWithConsumptionAttributes readPersistedDocument(Candidate candidate) {
    var json = s3Writer.getFile(persistedDocumentPath(candidate));
    return attempt(
            () -> dtoObjectMapper.readValue(json, IndexDocumentWithConsumptionAttributes.class))
        .orElseThrow();
  }

  protected static UnixPath persistedDocumentPath(Candidate candidate) {
    return UnixPath.of(NVI_CANDIDATES_FOLDER)
        .addChild(candidate.identifier().toString() + GZIP_ENDING);
  }

  protected static ApprovalView approvalFor(NviCandidateIndexDocument document, URI institutionId) {
    return document.approvals().stream()
        .filter(approval -> approval.institutionId().equals(institutionId))
        .findFirst()
        .orElseThrow();
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

  private Candidate setupCandidateWithUnverifiedCreator(URI institutionId, String creatorName) {
    var unverifiedCreator =
        no.sikt.nva.nvi.common.service.dto.UnverifiedNviCreatorDto.builder()
            .withName(creatorName)
            .withAffiliations(List.of(institutionId))
            .build();
    var request = randomUpsertRequestBuilder().withNviCreators(unverifiedCreator).build();
    candidateService.upsertCandidate(request);
    return candidateService.getCandidateByPublicationId(request.publicationId());
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
