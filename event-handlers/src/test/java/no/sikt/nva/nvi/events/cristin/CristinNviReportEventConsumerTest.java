package no.sikt.nva.nvi.events.cristin;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.API_HOST;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getCristinNviReportEventConsumerEnvironment;
import static no.sikt.nva.nvi.common.db.CandidateDaoFixtures.getExpectedPublicationBucketUri;
import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupClosedPeriod;
import static no.sikt.nva.nvi.common.model.EnumFixtures.randomValidInstanceType;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomTopLevelOrganization;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.randomPublicationDateInYear;
import static no.sikt.nva.nvi.events.cristin.CristinNviReportEventConsumer.NVI_ERRORS;
import static no.sikt.nva.nvi.events.cristin.CristinTestUtils.createPublicationFactory;
import static no.sikt.nva.nvi.events.cristin.CristinTestUtils.getTopLevelOrganizations;
import static no.sikt.nva.nvi.events.cristin.CristinTestUtils.randomCristinLocale;
import static no.sikt.nva.nvi.test.TestUtils.generateUniqueIdAsString;
import static no.sikt.nva.nvi.test.TestUtils.randomYear;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.SampleExpandedPublicationFactory;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.sikt.nva.nvi.common.model.NviCreator;
import no.sikt.nva.nvi.common.service.CandidateService;
import no.sikt.nva.nvi.common.service.dto.VerifiedNviCreatorDto;
import no.sikt.nva.nvi.common.service.model.Approval;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;
import no.sikt.nva.nvi.common.service.model.Candidate;
import no.sikt.nva.nvi.common.service.model.PublicationDetails;
import no.sikt.nva.nvi.events.cristin.CristinNviReport.Builder;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

class CristinNviReportEventConsumerTest {

  private static final String CRISTIN_IMPORT_BUCKET = "not-important";
  private static final Context CONTEXT = mock(Context.class);
  private TestScenario scenario;
  private CristinNviReportEventConsumer handler;
  private CandidateService candidateService;
  private S3Driver s3Driver;

  @BeforeEach
  void setup() {
    scenario = new TestScenario();
    var candidateRepository = scenario.getCandidateRepository();
    var s3Client = scenario.getS3Client();
    s3Driver = new S3Driver(s3Client, CRISTIN_IMPORT_BUCKET);
    var environment = getCristinNviReportEventConsumerEnvironment();
    candidateService =
        new CandidateService(environment, scenario.getPeriodRepository(), candidateRepository);
    handler = new CristinNviReportEventConsumer(candidateRepository, s3Client, environment);
  }

  @Test
  void shouldCreateNviCandidateFromNviReport() {
    var cristinNviReport = createRandomCristinNviReport().build();
    setupPublicationInS3(cristinNviReport, createPublicationFactory(cristinNviReport, scenario));

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);

    var nviCandidate = getByPublicationIdOf(cristinNviReport);

    assertThatNviCandidateHasExpectedValues(nviCandidate, cristinNviReport);
  }

  @Test
  void shouldNotFailAndNotPersistErrorReportWhenAttemptingToImportAlreadyExistingNviCandidate() {
    var cristinNviReport = createRandomCristinNviReport().build();
    setupPublicationInS3(cristinNviReport, createPublicationFactory(cristinNviReport, scenario));

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
    var existingCandidate = getByPublicationIdOf(cristinNviReport);
    assertThat(existingCandidate).isNotNull();

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);

    var s3ReportPath = expectedImportErrorBucketPath(cristinNviReport);
    assertThrows(NoSuchKeyException.class, () -> s3Driver.getFile(s3ReportPath));
  }

  @Test
  void shouldStoreErrorReportWhenFailInCristinMapper() {
    var cristinNviReport =
        createRandomCristinNviReport()
            .withScientificResources(emptyList())
            .withCristinLocales(emptyList())
            .build();
    setupPublicationInS3(cristinNviReport, createPublicationFactory(cristinNviReport, scenario));

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);

    var s3ReportPath = expectedImportErrorBucketPath(cristinNviReport);
    var expectedReport = s3Driver.getFile(s3ReportPath);
    assertThat(expectedReport).contains("Could not create nvi candidate");
  }

  @Test
  void shouldStoreErrorReportWhenYearReportedFromHistoricalDataIsMissing() {
    var institutionIdentifier = generateUniqueIdAsString();
    var cristinNviReport =
        createRandomCristinNviReport()
            .withCristinLocales(List.of(randomCristinLocale(institutionIdentifier)))
            .withScientificResources(List.of(scientificResource(institutionIdentifier, null)))
            .build();
    setupPublicationInS3(cristinNviReport, createPublicationFactory(cristinNviReport, scenario));

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);

    var s3ReportPath = expectedImportErrorBucketPath(cristinNviReport);
    var expectedReport = s3Driver.getFile(s3ReportPath);
    assertThat(expectedReport).contains("Reported year is missing!");
  }

  @Test
  void shouldIncludeTopLevelOrganizationHierarchy() {
    var cristinNviReport = createRandomCristinNviReport().build();
    setupPublicationInS3(cristinNviReport, createPublicationFactory(cristinNviReport, scenario));

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
    var nviCandidate = getByPublicationIdOf(cristinNviReport);

    var expectedTopLevelOrganizationIds =
        cristinNviReport.cristinLocales().stream()
            .map(CristinIdWrapper::from)
            .map(CristinIdWrapper::getInstitutionId)
            .toList();
    assertThat(nviCandidate.getPublicationDetails().topLevelOrganizations())
        .extracting(Organization::id)
        .containsExactlyInAnyOrderElementsOf(expectedTopLevelOrganizationIds);
  }

  @Test
  void shouldNotIncludeCreatorsMissingFromCristinReport() {
    var cristinNviReport = createRandomCristinNviReport().build();
    var topLevelOrganization = getTopLevelOrganizations(cristinNviReport).getFirst();
    var publicationFactoryWithExtraCreator =
        createPublicationFactory(cristinNviReport, scenario)
            .withCreatorAffiliatedWith(topLevelOrganization);
    setupPublicationInS3(cristinNviReport, publicationFactoryWithExtraCreator);

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
    var nviCandidate = getByPublicationIdOf(cristinNviReport);

    var actualCreatorIds =
        nviCandidate.getPublicationDetails().nviCreators().stream().map(NviCreator::id).toList();
    var expectedCreatorIds =
        cristinNviReport.getCreators().stream().map(CristinTestUtils::expectedCreatorId).toList();
    assertThat(actualCreatorIds).containsExactlyInAnyOrderElementsOf(expectedCreatorIds);
  }

  @Test
  void shouldNotAddPointsForCreatorsMissingFromCristinReport() {
    var cristinNviReport = createRandomCristinNviReport().build();
    var topLevelOrganization = randomTopLevelOrganization();
    var publicationFactoryWithExtraCreator =
        createPublicationFactory(cristinNviReport, scenario)
            .withTopLevelOrganizations(List.of(topLevelOrganization))
            .withCreatorAffiliatedWith(topLevelOrganization);
    setupPublicationInS3(cristinNviReport, publicationFactoryWithExtraCreator);

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
    var nviCandidate = getByPublicationIdOf(cristinNviReport);

    var expectedTotalPoints =
        cristinNviReport.scientificResources().stream()
            .map(ScientificResource::getCreators)
            .flatMap(List::stream)
            .map(ScientificPerson::getAuthorPointsForAffiliation)
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(nviCandidate.getTotalPoints().setScale(4, RoundingMode.HALF_UP))
        .isEqualTo(expectedTotalPoints.setScale(4, RoundingMode.HALF_UP))
        .isNotZero();
  }

  @Test
  void shouldHandleImportedCandidateWithNoAffiliations() {
    var cristinNviReport = createImportedPublicationWithMissingTopLevelOrganization();

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
    var nviCandidate = getByPublicationIdOf(cristinNviReport);

    assertThat(nviCandidate)
        .extracting(Candidate::isApplicable, Candidate::isReported)
        .containsOnly(true);

    assertThat(nviCandidate.getPublicationDetails().topLevelOrganizations()).isEmpty();
  }

  @Test
  void shouldMigrateAuthorsWithoutTopLevelOrganizations() {
    var cristinNviReport = createRandomCristinNviReport().withCristinLocales(emptyList()).build();
    var publicationFactoryWithExtraCreator =
        createPublicationFactory(cristinNviReport, scenario)
            .withTopLevelOrganizations(emptyList())
            .withCreatorAffiliatedWith(emptyList());
    setupPublicationInS3(cristinNviReport, publicationFactoryWithExtraCreator);

    handler.handleRequest(createEvent(cristinNviReport), CONTEXT);
    var nviCandidate = getByPublicationIdOf(cristinNviReport);

    var expectedCreatorIdentifiers =
        cristinNviReport.scientificResources().stream()
            .map(ScientificResource::getCreators)
            .flatMap(List::stream)
            .map(ScientificPerson::getCristinPersonIdentifier)
            .toList();

    assertThat(nviCandidate.getPublicationDetails().nviCreators())
        .extracting(NviCreator::id)
        .map(UriWrapper::fromUri)
        .map(UriWrapper::getLastPathElement)
        .containsExactlyInAnyOrderElementsOf(expectedCreatorIdentifiers);
  }

  private CristinNviReport createImportedPublicationWithMissingTopLevelOrganization() {
    var cristinNviReport = createRandomCristinNviReport().withCristinLocales(emptyList()).build();
    var publicationFactoryWithExtraCreator =
        createPublicationFactory(cristinNviReport, scenario)
            .withTopLevelOrganizations(emptyList())
            .withCreatorAffiliatedWith(emptyList());
    setupPublicationInS3(cristinNviReport, publicationFactoryWithExtraCreator);
    return cristinNviReport;
  }

  private Candidate getByPublicationIdOf(CristinNviReport cristinNviReport) {
    var publicationId = expectedPublicationId(cristinNviReport);
    return candidateService.getByPublicationId(publicationId);
  }

  private void assertThatNviCandidateHasExpectedValues(
      Candidate candidate, CristinNviReport cristinNviReport) {

    var expectedApprovalIds =
        cristinNviReport.cristinLocales().stream()
            .map(CristinIdWrapper::from)
            .map(CristinIdWrapper::getInstitutionId)
            .toList();
    assertThat(candidate.getApprovals().keySet().stream().toList())
        .containsExactlyInAnyOrderElementsOf(expectedApprovalIds);

    assertThat(candidate)
        .extracting(
            Candidate::getPublicationId,
            Candidate::isApplicable,
            actual -> actual.getPeriod().year(),
            actual -> actual.getPublicationType().getValue(),
            actual -> actual.getPublicationChannel().scientificValue().getValue())
        .containsExactly(
            expectedPublicationId(cristinNviReport),
            true,
            cristinNviReport.getYearReportedFromHistoricalData().orElseThrow(),
            cristinNviReport.instanceType(),
            "LevelOne");

    var expectedPublicationBucketUri =
        getExpectedPublicationBucketUri(cristinNviReport.publicationIdentifier());
    assertThat(candidate.getPublicationDetails())
        .extracting(PublicationDetails::publicationDate, PublicationDetails::publicationBucketUri)
        .containsExactlyInAnyOrder(
            cristinNviReport.publicationDate(), expectedPublicationBucketUri);

    assertThat(candidate.getPublicationDetails().allCreators())
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedCreators(cristinNviReport));

    assertThat(candidate.getApprovals().values())
        .extracting(Approval::getStatus)
        .containsOnly(ApprovalStatus.APPROVED);
  }

  private List<VerifiedNviCreatorDto> expectedCreators(CristinNviReport cristinNviReport) {
    return cristinNviReport.getCreators().stream().map(CristinTestUtils::expectedCreator).toList();
  }

  private static UnixPath expectedImportErrorBucketPath(CristinNviReport cristinNviReport) {
    return UriWrapper.fromHost(CRISTIN_IMPORT_BUCKET)
        .addChild(NVI_ERRORS)
        .addChild(cristinNviReport.publicationIdentifier())
        .toS3bucketPath();
  }

  private static URI expectedPublicationId(CristinNviReport cristinNviReport) {
    return UriWrapper.fromHost(API_HOST.getValue())
        .addChild("publication")
        .addChild(cristinNviReport.publicationIdentifier())
        .getUri();
  }

  private Builder createRandomCristinNviReport() {
    var year = randomYear();
    setupClosedPeriod(scenario, year);

    var topLevelCristinLocales =
        List.of(
            randomCristinLocale(generateUniqueIdAsString()),
            randomCristinLocale(generateUniqueIdAsString()));
    var scientificResources =
        topLevelCristinLocales.stream()
            .map(locale -> scientificResource(locale.getInstitutionIdentifier(), year))
            .toList();
    return CristinNviReport.builder()
        .withCristinIdentifier(generateUniqueIdAsString())
        .withPublicationIdentifier(randomUUID().toString())
        .withYearReported(year)
        .withPublicationDate(randomPublicationDateInYear(Integer.parseInt(year)))
        .withCristinLocales(topLevelCristinLocales)
        .withScientificResources(scientificResources)
        .withInstanceType(randomValidInstanceType().getValue())
        .withReference(null);
  }

  private void setupPublicationInS3(
      CristinNviReport cristinNviReport, SampleExpandedPublicationFactory factory) {
    var publicationIdentifier = UUID.fromString(cristinNviReport.publicationIdentifier());
    var expandedPublication =
        factory
            .getExpandedPublicationBuilder()
            .withId(expectedPublicationId(cristinNviReport))
            .withIdentifier(publicationIdentifier)
            .build();

    scenario.setupExpandedPublicationInS3(expandedPublication);
  }

  private ScientificResource scientificResource(String institutionIdentifier, String reportedYear) {
    return ScientificResource.build()
        .withQualityCode("1")
        .withReportedYear(reportedYear)
        .withScientificPeople(
            List.of(
                scientificPersonWithCristinIdentifier(institutionIdentifier),
                scientificPersonWithCristinIdentifier(institutionIdentifier)))
        .build();
  }

  private ScientificPerson scientificPersonWithCristinIdentifier(String institutionIdentifier) {
    return ScientificPerson.builder()
        .withCristinPersonIdentifier(generateUniqueIdAsString())
        .withInstitutionIdentifier(institutionIdentifier)
        .withDepartmentIdentifier(generateUniqueIdAsString())
        .withSubDepartmentIdentifier(generateUniqueIdAsString())
        .withGroupIdentifier(generateUniqueIdAsString())
        .withAuthorPointsForAffiliation("1.6")
        .withCollaborationFactor("1.0")
        .withPublicationTypeLevelPoints("1.5")
        .build();
  }

  public URI setupCristinNviReportInS3(CristinNviReport cristinNviReport) {
    var fullPath = UnixPath.of(randomString(), randomString());
    try {
      return s3Driver.insertFile(fullPath, cristinNviReport.toJsonString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to add file to S3", e);
    }
  }

  private SQSEvent createEvent(CristinNviReport cristinNviReport) {
    var fileUri = setupCristinNviReportInS3(cristinNviReport);
    var eventReference = new EventReference(randomString(), randomString(), fileUri, Instant.now());
    var sqsEvent = new SQSEvent();
    var message = new SQSMessage();
    message.setBody(eventReference.toJsonString());
    sqsEvent.setRecords(List.of(message));
    return sqsEvent;
  }
}
