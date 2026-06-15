package no.sikt.nva.nvi.index.report;

import static no.sikt.nva.nvi.common.db.PeriodRepositoryFixtures.setupOpenPeriod;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.createRandomIndexDocument;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentForYear;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.documentWithApprovals;
import static no.sikt.nva.nvi.index.IndexDocumentFixtures.randomApproval;
import static no.sikt.nva.nvi.index.report.request.ReportType.AUTHOR_SHARES_CONTROL;
import static no.sikt.nva.nvi.index.report.request.ReportType.DEFAULT_REPORT;
import static no.sikt.nva.nvi.report.generators.utils.CsvReader.parseCsvToRows;
import static no.sikt.nva.nvi.test.TestConstants.THIS_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.UUID;
import no.sikt.nva.nvi.common.TestScenario;
import no.sikt.nva.nvi.index.OpenSearchContainerContext;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportFormat;
import no.sikt.nva.nvi.index.report.request.ReportType;
import no.sikt.nva.nvi.index.report.response.GenerateReportMessage;
import no.sikt.nva.nvi.report.model.authorshares.ReportHeader;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.apigateway.MediaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class ReportGeneratorTest {

  private static final OpenSearchContainerContext CONTAINER = new OpenSearchContainerContext();
  private static final String BUCKET = randomString();

  private FakeS3Client s3Client;
  private ReportGenerator reportGenerator;

  @BeforeAll
  static void setupContainer() {
    CONTAINER.start();
  }

  @AfterAll
  static void stopContainer() {
    CONTAINER.stop();
  }

  @BeforeEach
  void setUp() {
    CONTAINER.createIndex();
    var scenario = new TestScenario();
    setupOpenPeriod(scenario, THIS_YEAR);
    s3Client = new FakeS3Client();
    reportGenerator =
        new ReportGenerator(
            scenario.getPeriodService(),
            CONTAINER.getReportDocumentClient(),
            CONTAINER.getReportAggregationClient(),
            s3Client);
  }

  @AfterEach
  void tearDown() {
    CONTAINER.deleteIndex();
  }

  @Test
  void shouldUploadCsvReportForAllInstitutions() {
    CONTAINER.addDocumentsToIndex(documentForYear(THIS_YEAR, false, randomApproval()));

    var message = allInstitutionsMessage(MediaType.CSV_UTF_8, DEFAULT_REPORT);
    reportGenerator.generateReport(message);

    assertTrue(readPersistedReport(message).contentLength() > 0);
  }

  @Test
  void shouldUploadXlsxReportForAllInstitutions() {
    CONTAINER.addDocumentsToIndex(documentForYear(THIS_YEAR, false, randomApproval()));

    var message = allInstitutionsMessage(MediaType.OOXML_SHEET, DEFAULT_REPORT);
    reportGenerator.generateReport(message);

    assertTrue(readPersistedReport(message).contentLength() > 0);
  }

  @Test
  void shouldUploadCsvReportForSpecificInstitution() {
    var institutionId = randomOrganizationId();
    var approval = randomApproval(randomString(), institutionId);
    CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

    var message = institutionMessage(MediaType.OOXML_SHEET, institutionId);
    reportGenerator.generateReport(message);

    assertTrue(readPersistedReport(message).contentLength() > 0);
  }

  @Test
  void shouldUploadXlsxReportForSpecificInstitution() {
    var institution = randomOrganizationId();
    var approval = randomApproval(randomString(), institution);
    CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

    var message = institutionMessage(MediaType.OOXML_SHEET, institution);
    reportGenerator.generateReport(message);

    var persistedReport = readPersistedReport(message);

    assertTrue(persistedReport.contentLength() > 0);
  }

  @Test
  void shouldUploadCsvPublicationPointsReportForAllInstitutions() {
    CONTAINER.addDocumentsToIndex(documentForYear(THIS_YEAR, false, randomApproval()));

    var message = allInstitutionsPublicationPointsMessage(MediaType.CSV_UTF_8);
    reportGenerator.generateReport(message);

    assertTrue(readPersistedReport(message).contentLength() > 0);
  }

  @Test
  void shouldUploadXlsxPublicationPointsReportForAllInstitutions() {
    CONTAINER.addDocumentsToIndex(documentForYear(THIS_YEAR, false, randomApproval()));

    var message = allInstitutionsPublicationPointsMessage(MediaType.OOXML_SHEET);
    reportGenerator.generateReport(message);

    assertTrue(readPersistedReport(message).contentLength() > 0);
  }

  @Test
  void shouldUploadCsvPublicationPointsReportForSpecificInstitution() {
    var institutionId = randomOrganizationId();
    var approval = randomApproval(randomString(), institutionId);
    CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

    var message = institutionPublicationPointsMessage(MediaType.CSV_UTF_8, institutionId);
    reportGenerator.generateReport(message);

    assertTrue(readPersistedReport(message).contentLength() > 0);
  }

  @Test
  void shouldUploadXlsxPublicationPointsReportForSpecificInstitution() {
    var institutionId = randomOrganizationId();
    var approval = randomApproval(randomString(), institutionId);
    CONTAINER.addDocumentsToIndex(documentWithApprovals(approval));

    var message = institutionPublicationPointsMessage(MediaType.OOXML_SHEET, institutionId);
    reportGenerator.generateReport(message);

    assertTrue(readPersistedReport(message).contentLength() > 0);
  }

  @Test
  void shouldContainSector() {
    var institutionId = randomOrganizationId();
    CONTAINER.addDocumentsToIndex(createRandomIndexDocument(institutionId, THIS_YEAR));

    var message = institutionMessage(MediaType.CSV_UTF_8, institutionId);
    reportGenerator.generateReport(message);

    var rows = parseCsvToRows(read(message), ReportHeader.class);

    var sector =
        rows.getLast().cells().stream()
            .filter(cell -> ReportHeader.SEKTORKODE == cell.header())
            .findFirst()
            .orElseThrow();

    assertThat(sector.string()).isNotEmpty();
  }

  @Test
  void shouldContainRboInstitution() {
    var institutionId = randomOrganizationId();
    CONTAINER.addDocumentsToIndex(createRandomIndexDocument(institutionId, THIS_YEAR));

    var message = institutionMessage(MediaType.CSV_UTF_8, institutionId);
    reportGenerator.generateReport(message);

    var rows = parseCsvToRows(read(message), ReportHeader.class);

    var rboInstitution =
        rows.getLast().cells().stream()
            .filter(cell -> ReportHeader.STATUS_RBO == cell.header())
            .findFirst()
            .orElseThrow();

    assertThat(rboInstitution.string()).isNotEmpty();
  }

  @Test
  void shouldCreateReportContainingAllContributorsWhenGeneratingAuthorSharesControlReport() {
    CONTAINER.addDocumentsToIndex(createRandomIndexDocument(randomOrganizationId(), THIS_YEAR));

    var message = allInstitutionsMessage(MediaType.CSV_UTF_8, AUTHOR_SHARES_CONTROL);
    reportGenerator.generateReport(message);

    var rows = parseCsvToRows(read(message), ReportHeader.class);

    assertThat(rows.size()).isEqualTo(5);
  }

  private byte[] read(GenerateReportMessage message) {
    return s3Client
        .getObject(
            GetObjectRequest.builder()
                .bucket(message.reportPresignedUrl().bucket())
                .key(message.reportPresignedUrl().key())
                .build(),
            ResponseTransformer.toBytes())
        .asByteArray();
  }

  private static ReportPresignedUrl presignedFile(MediaType mediaType) {
    var key =
        "%s.%s"
            .formatted(UUID.randomUUID(), MediaType.CSV_UTF_8.equals(mediaType) ? "csv" : "xlsx");
    return new ReportPresignedUrl(BUCKET, key, randomUri());
  }

  private GetObjectResponse readPersistedReport(GenerateReportMessage message) {
    return s3Client
        .getObject(
            GetObjectRequest.builder()
                .bucket(message.reportPresignedUrl().bucket())
                .key(message.reportPresignedUrl().key())
                .build())
        .response();
  }

  private GenerateReportMessage allInstitutionsMessage(MediaType mediaType, ReportType reportType) {
    var request =
        new AllInstitutionsReportRequest(
            randomUri(), THIS_YEAR, new ReportFormat(mediaType, reportType));
    return GenerateReportMessage.create(request, presignedFile(mediaType));
  }

  private GenerateReportMessage institutionMessage(MediaType mediaType, URI institutionId) {
    var request =
        new InstitutionReportRequest(
            randomUri(), THIS_YEAR, institutionId, new ReportFormat(mediaType, DEFAULT_REPORT));
    return GenerateReportMessage.create(request, presignedFile(mediaType));
  }

  private GenerateReportMessage allInstitutionsPublicationPointsMessage(MediaType mediaType) {
    var request =
        new AllInstitutionsReportRequest(
            randomUri(),
            THIS_YEAR,
            new ReportFormat(mediaType.toString(), ReportType.PUBLICATION_POINTS));
    return GenerateReportMessage.create(request, presignedFile(mediaType));
  }

  private GenerateReportMessage institutionPublicationPointsMessage(
      MediaType mediaType, URI institutionId) {
    var request =
        new InstitutionReportRequest(
            randomUri(),
            THIS_YEAR,
            institutionId,
            new ReportFormat(mediaType.toString(), ReportType.PUBLICATION_POINTS));
    return GenerateReportMessage.create(request, presignedFile(mediaType));
  }
}
